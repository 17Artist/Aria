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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A2 解释器语义对齐回归守卫(Shimmer 基准, bug-for-bug)。
 * 覆盖: operators-1(算术不可变)、variables-1/2(VAR_* NPE/静默跳过)、variables-7/8/9(命名空间隔离/val 写忽略)、
 * controlflow-07(args 越界 none)、controlflow-09/10/11(for-in 哨兵/解构缺位/循环变量可见性)、
 * builtins-static-1/6(裸名构造器兜底/命名空间函数缺失报错)、operators-6(~~ 非 Range 恒 false)、
 * builtins-object-11(3)(函数体内构造器)、async-3/4(args 透传/作用域隔离)、controlflow-16(深度 2048)、
 * operators-8(NEG/INC 值模型)。
 */
public class ShimmerAlignmentA2Test {
    @BeforeAll static void s() { Aria.getEngine().initialize(); }
    @BeforeEach void r() { Interpreter.resetCallDepth(); Interpreter.clearSandbox(); }
    private IValue<?> eval(String c) throws AriaException { return Aria.eval(c, Aria.createContext()); }
    private double num(String c) throws AriaException { return eval(c).numberValue(); }
    private String str(String c) throws AriaException { return eval(c).stringValue(); }
    private boolean bool(String c) throws AriaException { return eval(c).booleanValue(); }

    // ===== operators-1/gui-chain-2/variables-5：算术不可变，循环历史值不被原地改写 =====
    @Test void loopAppendKeepsHistory() throws Exception {
        assertEquals("[101.0, 102.0, 103.0]",
                str("var.out = []\nfor (i in [1, 2, 3]) { var.out.add(i + 100) }\nreturn var.out"));
    }
    @Test void scopeStoredNumbersIndependent() throws Exception {
        // 经裸名临时变量存入 list 的数字不被后续迭代改写
        assertEquals("[100.0, 101.0, 102.0]",
                str("l = []\ni = 0\nwhile (i < 3) { t = i + 100\n l.add(t)\n i = i + 1 }\nreturn l"));
    }

    // ===== variables-1/2/async-1：VAR_* 预扫描 + 通用加法值模型 =====
    @Test void lonelyVarCompoundAddNoNpe() throws Exception {
        assertEquals(1.0, num("var.n += 1\nreturn var.n"), 1e-9);          // none+1=1
        assertEquals(1.0, num("var.m = var.m + 1\nreturn var.m"), 1e-9);   // VAR_ADD_REG
    }
    @Test void varIncOnNumericString() throws Exception {
        assertEquals(6.0, num("var.s = '5'\nvar.s += 1\nreturn var.s"), 1e-9);
    }
    @Test void varIncOnNonNumericString() throws Exception {
        assertEquals("a1.0", str("var.s = 'a'\nvar.s += 1\nreturn var.s"));
    }

    // ===== variables-7/9/controlflow-13：裸名与 var./val. 完全隔离 =====
    @Test void bareWriteDoesNotTouchVar() throws Exception {
        assertEquals(5.0, num("var.x = 5\nx = 1\nreturn var.x"), 1e-9);
    }
    @Test void bareReadDoesNotSeeVar() throws Exception {
        assertTrue(eval("var.y = 7\nreturn y") instanceof NoneValue);
    }
    @Test void valReadDoesNotSeeScope() throws Exception {
        assertTrue(eval("z = 9\nreturn val.z") instanceof NoneValue);
    }

    // ===== variables-8：脚本写 val 静默 no-op =====
    @Test void scriptValWriteSilentlyIgnored() throws Exception {
        assertTrue(eval("val.k = 5\nreturn val.k") instanceof NoneValue);
        // 重赋也不抛(不再 Cannot reassign)
        assertTrue(eval("val.k = 5\nval.k = 6\nreturn val.k") instanceof NoneValue);
    }

    // ===== controlflow-07：args 索引越界 → none(常量与动态下标) =====
    @Test void argsOutOfBoundsIsNone() throws Exception {
        assertEquals("default", str(
                "var.f = -> { if (args[0] == none) { return 'default' }\n return args[0] }\nreturn f()"));
        // 动态下标同样 none 不抛
        assertEquals("default", str(
                "var.f = -> { i = 0\n if (args[i] == none) { return 'default' }\n return args[i] }\nreturn f()"));
    }

    // ===== controlflow-09：含 none 元素的列表完整遍历(ITER_END 哨兵) =====
    @Test void forInListWithNoneElements() throws Exception {
        assertEquals(3.0, num("var.l = [1, none, 3]\nr = 0\nfor (x in var.l) { r = r + 1 }\nreturn r"), 1e-9);
    }

    // ===== controlflow-10(b)：map 多变量解构第 3 变量缺位 → none(不抛越界) =====
    @Test void mapDestructureThirdVarIsNone() throws Exception {
        assertEquals(1.0, num(
                "var.m = {'a': 1}\nc = 0\nfor (k, v, x in var.m) { if (x == none) { c = c + 1 } }\nreturn c"), 1e-9);
    }

    // ===== controlflow-11：循环变量真实作用域存储——循环后可见=末值 =====
    @Test void forInVarVisibleAfterLoop() throws Exception {
        assertEquals(3.0, num("i = 99\nfor (i in [1,2,3]) { }\nreturn i"), 1e-9);
    }

    // ===== builtins-static-1：小写裸名构造器兜底(range 可达) =====
    @Test void lowercaseRangeConstructorReachable() throws Exception {
        assertEquals(3.0, num("c = 0\nfor (i in range(1,3)) { c = c + 1 }\nreturn c"), 1e-9); // 双端闭 1,2,3
    }
    @Test void lowercaseRangeInFunctionBody() throws Exception {
        // builtins-object-11(3)：executeInlineInternal 的 CALL_CONSTRUCTOR/裸名构造器兜底
        assertEquals(36.0, num("var.f = -> { return ('' + UUID()).length() }\nreturn f()"), 1e-9);
    }

    // ===== builtins-static-6：命名空间存在但函数不存在 → 抛错(不再静默 none) =====
    @Test void unknownFunctionInKnownNamespaceThrows() {
        assertThrows(AriaException.class, () -> eval("return Math.florr(3.7)"));
    }

    // ===== operators-6/syntax-03：~~ 非 Range 右值恒 false =====
    @Test void inRangeNonRangeAlwaysFalse() throws Exception {
        assertFalse(bool("return 5 ~~ 5"));
        assertFalse(bool("return 2 ~~ [1, 3]"));
        assertFalse(bool("return 'a' ~~ 'a'"));
        assertTrue(bool("return 3 ~~ range(1, 3)")); // 双端闭 + 小写构造器兜底
    }

    // ===== async-3/4：args 透传 + 全新 ScopeStack 隔离 =====
    @Test void asyncSeesOuterArgs() throws Exception {
        assertEquals(42.0, num(
                "var.f = -> { var.p = async { return args[0] }\n return await var.p }\nreturn f(42)"), 1e-9);
    }
    @Test void asyncScopeIsolated() throws Exception {
        // 外层裸名临时变量对 async 体不可见(none)；写入也不穿透
        assertTrue(eval("x = 5\nvar.p = async { return x }\nreturn await var.p") instanceof NoneValue);
        assertEquals(1.0, num(
                "y = 1\nvar.p = async { y = 99\n return 0 }\nawait var.p\nreturn y"), 1e-9);
    }

    // ===== controlflow-16：MAX_CALL_DEPTH 512→2048 =====
    @Test void deepRecursion1000Works() throws Exception {
        assertEquals(0.0, num(
                "var.f = -> { if (args[0] <= 0) { return 0 }\n return f(args[0] - 1) }\nreturn f(1000)"), 1e-9);
    }

    // ===== operators-8：NEG/INC 值模型 =====
    @Test void negNonNumericStringThrows() {
        assertThrows(AriaException.class, () -> eval("s = 'abc'\nreturn -s"));
    }
    @Test void negListThrows() {
        assertThrows(AriaException.class, () -> eval("l = [1]\nreturn -l"));
    }
    @Test void incNonNumericStringConcats() throws Exception {
        assertEquals("a1.0", str("s = 'a'\ns++\nreturn s"));
    }
}
