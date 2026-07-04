/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.CallableWithInvoker;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.MapValue;
import priv.seventeen.artist.aria.value.StoreOnlyValue;
import priv.seventeen.artist.aria.value.StringValue;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A8 / interop-9：map['key'](args) —— 当 map 的 'key' 值是宿主动作回调
 * {@code StoreOnlyValue<CallableWithInvoker>} 时的后缀链求值时机对齐 Shimmer。
 *
 * <p>Shimmer 语义(双 jar 差分实测,基准 Shimmer-1.56.58)：后缀链先接完(方括号取<b>原始</b> CWI、
 * 圆括号<b>带参</b>调用),CWI 只在<b>消费点</b>(无后续调用)才无参 auto-invoke：
 * <ul>
 *   <li>{@code m['init']('MODE')} → 带参调用 → {@code CALLED_argc=1_arg0=MODE}</li>
 *   <li>{@code m['init']}         → 无后续调用 → 消费点 auto-invoke → {@code CALLED_argc=0}</li>
 *   <li>{@code m['init']()}       → Shimmer 解析器丢弃空括号 → 原始 CWI 落到消费点 auto-invoke → {@code CALLED_argc=0}</li>
 *   <li>{@code m['a']['b']('X')}  → 链式索引后带参调用 → {@code CALLED_argc=1_arg0=X}</li>
 * </ul>
 *
 * <p>此前 Aria 的 GET_INDEX 在取值时无条件 {@code resolveLazyProperty}(无参执行 CWI),
 * 导致 {@code m['init']('MODE')} 拿到结果(StringValue)后 {@code ('MODE')} 无法再当函数调用
 * → 抛"不支持的后缀运算"。修法：compileCall 对 IndexExpr callee 以 no-resolve 模式(GET_INDEX c=3)
 * 发射,取原始 CWI 交给 CALL 带参调用;两个解释循环 + JIT rtGetIndex 同步该三态。
 *
 * <p>每个断言冷(解释器,首次执行)+热(JIT,阈值=1 后 ≥3 次)一致。
 */
public class ShimmerAlignmentA8Test {

    private static final String NS = "a8ns";

    @BeforeAll
    static void setup() {
        // 注册返回「含 StoreOnlyValue<CallableWithInvoker> 值的 map」的静态函数。
        // CWI 报告实际 argc/arg0——藉此区分「带参调用」与「无参 auto-invoke」。
        CallableManager.INSTANCE.registerStaticFunction(NS, "cwimap", d -> {
            MapValue m = new MapValue(new LinkedHashMap<>());
            m.jvmValue().put(new StringValue("init"), new StoreOnlyValue<>(mkCwi()));
            MapValue inner = new MapValue(new LinkedHashMap<>());
            inner.jvmValue().put(new StringValue("b"), new StoreOnlyValue<>(mkCwi()));
            m.jvmValue().put(new StringValue("a"), inner);
            return m;
        });
    }

    private static CallableWithInvoker mkCwi() {
        return new CallableWithInvoker(inv -> {
            int n = inv.getArgs().length;
            return new StringValue("CALLED_argc=" + n
                    + (n > 0 ? "_arg0=" + inv.getArgs()[0].stringValue() : ""));
        }, null);
    }

    /** 解释器(冷)执行一次。 */
    private static String cold(String code) throws AriaException {
        IValue<?> v = Aria.eval(code, Aria.createContext());
        return v == null ? "null" : v.stringValue();
    }

    /** 编译一次、执行 N 次越过 JIT 阈值(=1),返回最后一次(热/JIT)结果。 */
    private static String hot(String code) throws AriaException {
        AriaCompiledRoutine r = Aria.compile("a8", code.endsWith("\n") ? code : code + "\n");
        String last = null;
        for (int i = 0; i < 5; i++) {
            IValue<?> v = r.execute(Aria.createContext());
            last = v == null ? "null" : v.stringValue();
        }
        return last;
    }

    /** 断言冷热一致且等于期望(对齐 Shimmer 基准)。 */
    private static void assertColdHot(String expected, String exprBody) throws AriaException {
        String code = "m = " + NS + ".cwimap()\nreturn " + exprBody + "\n";
        assertEquals(expected, cold(code), "冷(解释器): " + exprBody);
        assertEquals(expected, hot(code), "热(JIT): " + exprBody);
    }

    @Test
    void indexCallWithArg_isParametricCall() throws AriaException {
        // m['init']('MODE') → 带参调用(此前抛"不支持的后缀运算")
        assertColdHot("CALLED_argc=1_arg0=MODE", "m['init']('MODE')");
    }

    @Test
    void bareIndex_isNoArgAutoInvoke() throws AriaException {
        // m['init'] → 无后续调用 → 消费点 auto-invoke argc=0(保持)
        assertColdHot("CALLED_argc=0", "m['init']");
    }

    @Test
    void indexEmptyParens_dropsParensThenAutoInvoke() throws AriaException {
        // m['init']() → 空括号被丢弃 → 原始 CWI 落到消费点 auto-invoke argc=0
        assertColdHot("CALLED_argc=0", "m['init']()");
    }

    @Test
    void chainedIndexCallWithArg_isParametricCall() throws AriaException {
        // m['a']['b']('X') → 链式索引后带参调用
        assertColdHot("CALLED_argc=1_arg0=X", "m['a']['b']('X')");
    }

    @Test
    void chainedIndexBare_isNoArgAutoInvoke() throws AriaException {
        // m['a']['b'] → 无后续调用 → 消费点 auto-invoke argc=0
        assertColdHot("CALLED_argc=0", "m['a']['b']");
    }

    /** 直接内联链(不经中间变量)也一致：ns.cwimap()['init']('MODE')。 */
    @Test
    void inlineChainNoIntermediateVar() throws AriaException {
        String withArg = "return " + NS + ".cwimap()['init']('MODE')\n";
        assertEquals("CALLED_argc=1_arg0=MODE", cold(withArg), "冷: 内联链带参");
        assertEquals("CALLED_argc=1_arg0=MODE", hot(withArg), "热: 内联链带参");

        String bare = "return " + NS + ".cwimap()['init']\n";
        assertEquals("CALLED_argc=0", cold(bare), "冷: 内联链裸取");
        assertEquals("CALLED_argc=0", hot(bare), "热: 内联链裸取");
    }
}
