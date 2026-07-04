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
import org.junit.jupiter.api.Timeout;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.value.BooleanValue;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.MapValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.StringValue;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A4 JIT 全面 parity 回归守卫：同一脚本「第 1 次执行(解释器)」与「热身后(JIT 生成码)」
 * 结果必须逐位一致(JIT_THRESHOLD=1 → 第 2 次执行起走编译码)。
 * 覆盖任务清单 1-14：JUMP_IF_NONE c=1 哨兵、fast 路径选路收紧+入口守卫(jit-1..4)、
 * 除零/负数真值(jit-5/6)、list 越界/字符串索引(jit-7/8)、CHECKCAST 消灭(jit-9)、
 * SmallMapValue 停用(jit-18)、调用点缓存(jit-15)、lambda 内联(jit-12/13)、
 * 内联白名单(jit-17)、闭包捕获(jit-19)、回边中断(controlflow-15)、STORE_VAL(jit-20/variables-8)。
 */
public class ShimmerAlignmentA4Test {

    @BeforeAll static void s() { Aria.getEngine().initialize(); }
    @BeforeEach void r() { Interpreter.resetCallDepth(); Interpreter.clearSandbox(); }

    private static final int WARM = 5;

    private Context ctx() { return Aria.createContext(); }

    /** 同一编译产物：第 1 次(解释)与后续 WARM 次(JIT)结果字符串/类型完全一致，返回首个结果。
     *  并断言程序确实被 JIT(jit-22：防「canCompile 拒绝/编译失败 → 全程解释」造成的假覆盖)。 */
    private IValue<?> assertParity(String code, Supplier<Context> ctxFactory) throws AriaException {
        AriaCompiledRoutine r = Aria.compile("a4", code.endsWith("\n") ? code : code + "\n");
        IValue<?> first = r.execute(ctxFactory.get());
        for (int i = 0; i < WARM; i++) {
            IValue<?> v = r.execute(ctxFactory.get());
            assertEquals(first.getClass(), v.getClass(), "第" + (i + 2) + "次执行结果类型漂移");
            assertEquals(first.stringValue(), v.stringValue(), "第" + (i + 2) + "次执行结果值漂移");
        }
        assertTrue(r.getProgram().isCompiled(),
                "程序应已被 JIT(否则本用例只测了解释器,属假覆盖): " + code.lines().findFirst().orElse(""));
        return first;
    }

    /** 抛错脚本：每次执行(冷/热)都抛且消息一致。 */
    private void assertThrowsParity(String code, String expectMsgPart) throws AriaException {
        AriaCompiledRoutine r = Aria.compile("a4e", code.endsWith("\n") ? code : code + "\n");
        String firstMsg = null;
        for (int i = 0; i <= WARM; i++) {
            final int round = i;
            AriaException ex = assertThrows(AriaException.class, () -> r.execute(ctx()),
                    "第" + (round + 1) + "次执行应抛错");
            if (firstMsg == null) firstMsg = ex.getMessage();
            assertTrue(ex.getMessage() != null && ex.getMessage().contains(expectMsgPart),
                    "第" + (round + 1) + "次异常消息 [" + ex.getMessage() + "] 应含 [" + expectMsgPart + "]");
        }
    }

    // ===== 1. JUMP_IF_NONE c=1：ITER_END 身份判终，none 元素不截断 =====
    @Test void forInListWithNoneElement_jit() throws Exception {
        IValue<?> v = assertParity(
                "var.l = [1, none, 3]\nvar.count = 0\nfor (e in var.l) { var.count = var.count + 1 }\nreturn var.count",
                this::ctx);
        assertEquals(3.0, v.numberValue(), 1e-9, "含 none 元素的列表 for-in 必须完整遍历");
    }
    @Test void forInMapWithNoneValue_jit() throws Exception {
        IValue<?> v = assertParity(
                "var.m = {'a': none, 'b': 2}\nvar.c = 0\nfor (e in var.m) { var.c = var.c + 1 }\nreturn var.c",
                this::ctx);
        assertEquals(2.0, v.numberValue(), 1e-9);
    }

    // ===== 2. fast 路径入口守卫(jit-1)：字符串/布尔 var 不被强转销毁 =====
    @Test void stringVarSurvivesNumericHotPath() throws Exception {
        Context persist = ctx();
        Aria.eval("var.limit = \"10\"\n", persist);
        AriaCompiledRoutine r = Aria.compile("a4g", "var.t = var.limit + 1\nreturn var.t\n");
        for (int i = 0; i <= WARM; i++) {
            IValue<?> v = r.execute(persist);
            assertEquals(11.0, v.numberValue(), 1e-9, "第" + (i + 1) + "次：\"10\"+1 应为 11");
        }
        IValue<?> limit = persist.getLocalStorage()
                .getVarVariable(VariableKey.of("limit")).getValue();
        assertTrue(limit instanceof StringValue, "var.limit 不得被 fast 路径回写销毁(仍应为字符串)");
        assertEquals("10", limit.stringValue());
    }
    @Test void boolVarSurvivesNumericHotPath() throws Exception {
        Context persist = ctx();
        Aria.eval("var.flag = true\n", persist);
        AriaCompiledRoutine r = Aria.compile("a4g2", "var.probe = var.flag\nreturn var.probe\n");
        for (int i = 0; i <= WARM; i++) {
            IValue<?> v = r.execute(persist);
            assertTrue(v instanceof BooleanValue, "第" + (i + 1) + "次：读布尔 var 应保持 BooleanValue");
            assertEquals("true", v.stringValue());
        }
        assertTrue(persist.getLocalStorage().getVarVariable(VariableKey.of("flag"))
                .getValue() instanceof BooleanValue, "var.flag 不得被回写为 NumberValue");
    }

    // ===== 2b. server/global 读不被 fast 路径 no-op 冻结(jit-2) =====
    @Test void serverVarReadNotFrozen_jit() throws Exception {
        GlobalStorage storage = new GlobalStorage((k, v) -> { }, key -> null);
        storage.getServerVariable(VariableKey.of("hp")).forceSetValue(new NumberValue(15));
        AriaCompiledRoutine r = Aria.compile("a4s", "var.x = server.hp + 1\nreturn var.x\n");
        for (int i = 0; i <= WARM; i++) {
            assertEquals(16.0, r.execute(new Context(storage)).numberValue(), 1e-9,
                    "第" + (i + 1) + "次：server.hp+1 应恒为 16(不得被 fast 路径冻结为 1)");
        }
        // 宿主更新后热代码必须看到新值
        storage.getServerVariable(VariableKey.of("hp")).forceSetValue(new NumberValue(30));
        assertEquals(31.0, r.execute(new Context(storage)).numberValue(), 1e-9);
    }
    @Test void globalVarReadNotFrozen_jit() throws Exception {
        Context persist = ctx();
        Aria.eval("global.g = 7\n", persist);
        AriaCompiledRoutine r = Aria.compile("a4gl", "var.x = global.g + 1\nreturn var.x\n");
        for (int i = 0; i <= WARM; i++) {
            assertEquals(8.0, r.execute(persist).numberValue(), 1e-9);
        }
    }

    // ===== 2c. ?? 空值合并热身后不失效(jit-4) =====
    @Test void nullishCoalescingSurvivesWarmup() throws Exception {
        IValue<?> v = assertParity("var.a = var.unset ?? 5\nreturn var.a", this::ctx);
        assertEquals(5.0, v.numberValue(), 1e-9, "?? 兜底值热身后不得变 0");
    }

    // ===== 3. fast 路径除零/模零/负数真值(jit-5/6) =====
    @Test void divZeroHot_returnsZero() throws Exception {
        IValue<?> v = assertParity("var.b = 0\nvar.r = 10 / var.b\nreturn var.r", this::ctx);
        assertEquals(0.0, v.numberValue(), 1e-9, "除零热身后应仍为 0(非 Infinity)");
        assertEquals("0.0", v.stringValue());
    }
    @Test void modZeroHot_returnsZero() throws Exception {
        IValue<?> v = assertParity("var.b = 0\nvar.r = 10 % var.b\nreturn var.r", this::ctx);
        assertEquals(0.0, v.numberValue(), 1e-9, "模零热身后应仍为 0(非 NaN/ArithmeticException)");
    }
    @Test void notOnNegativeHot() throws Exception {
        IValue<?> v = assertParity(
                "var.x = 0 - 1\nvar.r = 0\nif (!var.x) { var.r = 1 } else { var.r = 2 }\nreturn var.r",
                this::ctx);
        assertEquals(1.0, v.numberValue(), 1e-9, "!(-1) 真值(Shimmer >0 语义)热身后不得反转");
    }
    @Test void andWithNegativeHot() throws Exception {
        IValue<?> v = assertParity(
                "var.a = 1\nvar.b = 0 - 1\nvar.r = 0\nif (var.a && var.b) { var.r = 1 } else { var.r = 2 }\nreturn var.r",
                this::ctx);
        assertEquals(2.0, v.numberValue(), 1e-9, "1 && -1 应为假(负数真值)");
    }

    // ===== 4. list 越界抛错/字符串索引/args 越界(jit-7/8, controlflow-14, interop-8) =====
    @Test void listOutOfBoundsThrows_coldAndHot() throws Exception {
        assertThrowsParity("var.l = [1, 2]\nreturn var.l[5]", "列表索引越界: 5 (size=2)");
    }
    @Test void stringIndexHot() throws Exception {
        IValue<?> v = assertParity("var.s = \"abc\"\nreturn var.s[1]", this::ctx);
        assertEquals("b", v.stringValue(), "字符串索引热身后不得变 none");
    }
    @Test void argsIndexOutOfBounds_noneBothWays() throws Exception {
        IValue<?> v = assertParity("var.f = -> { return args[5] }\nreturn var.f(1)", this::ctx);
        assertTrue(v instanceof NoneValue, "args 越界应为 none(冷热一致)");
    }

    // ===== 5. 通用路径 CHECKCAST 消灭(jit-9) =====
    @Test void numericStringArgsIntoHotFunction() throws Exception {
        // 函数体先用数字热身，再传数字字符串——解释器 "3"*"4"=12，JIT 不得 CCE
        AriaCompiledRoutine r = Aria.compile("a4cc",
                "var.f = -> { return args[0] * args[1] }\nreturn var.f(args[0], args[1])\n");
        Context c1 = ctx();
        c1.setArgs(new IValue<?>[]{ new NumberValue(3), new NumberValue(4) });
        double warm = r.execute(c1).numberValue();
        assertEquals(12.0, warm, 1e-9);
        for (int i = 0; i < WARM; i++) {
            Context c = ctx();
            c.setArgs(new IValue<?>[]{ new StringValue("3"), new StringValue("4") });
            assertEquals(12.0, r.execute(c).numberValue(), 1e-9,
                    "热身后传数字字符串应得 12(不得 CCE)");
        }
    }
    @Test void varAddConstOnStringVar() throws Exception {
        IValue<?> v = assertParity("var.s = \"a\"\nvar.s += 1\nreturn var.s", this::ctx);
        assertEquals("a1.0", v.stringValue(), "\"a\"+=1 走加法值模型(冷热一致)");
    }
    @Test void negOnListThrows_coldAndHot() throws Exception {
        assertThrowsParity("var.l = [1]\nreturn -var.l", "不支持的反转操作");
    }
    @Test void geOnListThrows_coldAndHot() throws Exception {
        assertThrowsParity("var.l = [1]\nreturn var.l >= 2", "类型不支持比较运算");
    }

    // ===== 6. SmallMapValue 停用(jit-18/operators-13/builtins-object-6/10/interop-9) =====
    @Test void smallMapMethodsHot() throws Exception {
        IValue<?> v = assertParity(
                "var.m = {'k': 1}\nvar.m.put('x', 2)\nreturn var.m.size()", this::ctx);
        assertEquals(2.0, v.numberValue(), 1e-9, "≤4 键 map 的 put/size 热身后不得失效");
    }
    @Test void smallMapKeysHot() throws Exception {
        IValue<?> v = assertParity("var.m = {'a': 1}\nreturn var.m.keys()", this::ctx);
        assertFalse(v instanceof NoneValue, "map.keys() 热身后不得变 none");
    }
    @Test void smallMapLiteralIsMapValue_hot() throws Exception {
        AriaCompiledRoutine r = Aria.compile("a4m", "return {'a': 1}\n");
        for (int i = 0; i <= WARM; i++) {
            IValue<?> v = r.execute(ctx());
            assertTrue(v instanceof MapValue,
                    "第" + (i + 1) + "次：map 字面量应恒为 MapValue(MapBridge instanceof 依赖)");
        }
    }
    @Test void smallMapPutAllHot() throws Exception {
        IValue<?> v = assertParity(
                "var.m = {'a':1,'b':2,'c':3,'d':4,'e':5}\nvar.m.putAll({'x': 9})\nreturn var.m.size()",
                this::ctx);
        assertEquals(6.0, v.numberValue(), 1e-9, "putAll(小 map 字面量) 热身后不得静默丢合并");
    }
    @Test void smallMapMergeHot() throws Exception {
        IValue<?> v = assertParity(
                "var.big = {'a':1,'b':2,'c':3,'d':4,'e':5}\nvar.small = {'x':9}\nvar.big = var.big + var.small\nreturn var.big.size()",
                this::ctx);
        assertEquals(6.0, v.numberValue(), 1e-9, "map+map 合并热身后不得丢失");
    }
    @Test void smallMapForInHot() throws Exception {
        IValue<?> v = assertParity(
                "var.m = {'a': 10, 'b': 20}\nvar.s = 0\nfor (e in var.m) { var.s = var.s + e[1] }\nreturn var.s",
                this::ctx);
        assertEquals(30.0, v.numberValue(), 1e-9);
    }

    // ===== 7. 调用点缓存(jit-15)：scope 遮蔽 + 重注册失效 =====
    @Test void scopeReceiverNotHijackedByVarCache() throws Exception {
        IValue<?> v = assertParity(
                "var.box = [\"V\"]\nbox = [\"S\"]\nbox.add(\"x\")\nreturn \"\" + box[1] + \"|\" + var.box.size()",
                this::ctx);
        assertEquals("x|1.0", v.stringValue(), "scope 同名接收者不得被 var 缓存劫持");
    }
    @Test void objectFunctionReRegisterInvalidatesCache() throws Exception {
        CallableManager.INSTANCE.registerObjectFunction(
                priv.seventeen.artist.aria.value.ListValue.class, "a4psize",
                d -> new NumberValue(111));
        try {
            AriaCompiledRoutine r = Aria.compile("a4rr", "var.l = [1]\nreturn var.l.a4psize()\n");
            for (int i = 0; i <= WARM; i++) {
                assertEquals(111.0, r.execute(ctx()).numberValue(), 1e-9);
            }
            // 宿主重注册(reload)后，已热的调用点必须调到新实现
            CallableManager.INSTANCE.registerObjectFunction(
                    priv.seventeen.artist.aria.value.ListValue.class, "a4psize",
                    d -> new NumberValue(222));
            assertEquals(222.0, r.execute(ctx()).numberValue(), 1e-9,
                    "重注册后热调用点应调新实现(注册代数失效)");
        } finally {
            CallableManager.INSTANCE.registerObjectFunction(
                    priv.seventeen.artist.aria.value.ListValue.class, "a4psize",
                    d -> NoneValue.NONE);
        }
    }

    // ===== 8. lambda 内联(jit-12/13)：操作数顺序/MOD =====
    @Test void reversedOperandLambda() throws Exception {
        IValue<?> v = assertParity(
                "var.sub = -> { return args[1] - args[0] }\nreturn var.sub(3, 10)", this::ctx);
        assertEquals(7.0, v.numberValue(), 1e-9, "args[1]-args[0] 不得被算成 args[0]-args[1]");
    }
    @Test void modLambda() throws Exception {
        IValue<?> v = assertParity(
                "var.m = -> { return args[0] % args[1] }\nreturn var.m(7, 3)", this::ctx);
        assertEquals(1.0, v.numberValue(), 1e-9, "MOD lambda 不得落 0/none");
    }
    @Test void multiOpLambdaNotMisInlined() throws Exception {
        IValue<?> v = assertParity(
                "var.f = -> { return args[0] * 2 + args[1] }\nreturn var.f(3, 4)", this::ctx);
        assertEquals(10.0, v.numberValue(), 1e-9, "多运算 lambda 热身后不得被臆断内联成 args0*args1");
    }
    @Test void modZeroLambda() throws Exception {
        IValue<?> v = assertParity(
                "var.m = -> { return args[0] % args[1] }\nreturn var.m(7, 0)", this::ctx);
        assertEquals(0.0, v.numberValue(), 1e-9, "lambda 模零应为 0(IData.mod 语义)");
    }

    // ===== 9. 内联白名单(jit-17)：宿主覆盖 math.* 后热身不失效；io.* 双端 none =====
    @Test void overriddenMathRoundHonoredHot() throws Exception {
        CallableManager.INSTANCE.registerStaticFunction("math", "round",
                d -> new NumberValue(99));
        try {
            AriaCompiledRoutine r = Aria.compile("a4mr", "return math.round(5.4)\n");
            for (int i = 0; i <= WARM; i++) {
                assertEquals(99.0, r.execute(ctx()).numberValue(), 1e-9,
                        "第" + (i + 1) + "次：覆盖后的 math.round 热身后不得被硬编码 Math.round 顶掉");
            }
        } finally {
            CallableManager.INSTANCE.registerStaticFunction("math", "round",
                    d -> new NumberValue(Math.round(d.get(0).numberValue())));
        }
    }
    @Test void overriddenMathPowHonoredHot() throws Exception {
        CallableManager.INSTANCE.registerStaticFunction("math", "pow",
                d -> new NumberValue(77));
        try {
            AriaCompiledRoutine r = Aria.compile("a4mp", "return math.pow(2, 3)\n");
            for (int i = 0; i <= WARM; i++) {
                assertEquals(77.0, r.execute(ctx()).numberValue(), 1e-9);
            }
        } finally {
            CallableManager.INSTANCE.registerStaticFunction("math", "pow",
                    d -> new NumberValue(Math.pow(d.get(0).numberValue(), d.get(1).numberValue())));
        }
    }
    @Test void ioPrintlnIsNoneBothWays() throws Exception {
        // Shimmer R5 (probes Z01/Z05): io namespace does not exist -> member call WITH args on a
        // none receiver throws (Shimmer Parenthesis only allows CWI), interpreter and JIT alike.
        assertThrowsParity("return io.println(\"hi\")", "不支持的后缀运算");
    }

    // ===== 10. 闭包捕获(jit-19) =====
    @Test void closureCaptureParity() throws Exception {
        assertParity(
                "x = 1\nvar.f = -> { x = x + 10\nreturn x }\nvar.a = var.f()\nvar.b = var.f()\nreturn \"\" + var.a + \",\" + var.b + \",\" + x",
                this::ctx);
    }
    @Test void closureCapturedScopeVarHot() throws Exception {
        assertParity(
                "base = 100\nvar.f = -> { return base + args[0] }\nreturn var.f(1) + var.f(2)",
                this::ctx);
    }

    // ===== 11. 回边中断检查(controlflow-15)：解释与 JIT 轮各一次 =====
    @Test @Timeout(30) void infiniteLoopInterruptible_fastVars() throws Exception {
        AriaCompiledRoutine r = Aria.compile("a4int1",
                "var.i = 0\nwhile (var.i >= 0) { var.i = var.i + 1 }\nreturn var.i\n");
        assertLoopInterruptible(r);
    }
    @Test @Timeout(30) void infiniteLoopInterruptible_genericPath() throws Exception {
        AriaCompiledRoutine r = Aria.compile("a4int2",
                "var.l = []\nvar.i = 0\nwhile (var.i >= 0) { var.i = var.i + 1 }\nreturn var.i\n");
        assertLoopInterruptible(r);
    }
    private void assertLoopInterruptible(AriaCompiledRoutine r) throws Exception {
        // round 0 = 解释执行(编译在首次执行开头调度)；round 1 = JIT 生成码
        for (int round = 0; round < 2; round++) {
            AtomicReference<Throwable> err = new AtomicReference<>();
            Thread t = new Thread(() -> {
                try { r.execute(Aria.createContext()); } catch (Throwable e) { err.set(e); }
            });
            t.start();
            Thread.sleep(300);
            t.interrupt();
            t.join(10_000);
            assertFalse(t.isAlive(), "round=" + round + "：被中断的死循环线程应在限时内终止");
        }
    }

    // ===== 12. STORE_VAL no-op(variables-8)：含 val 写的程序不 JIT、双端 none =====
    @Test void storeValNoopBothWays() throws Exception {
        AriaCompiledRoutine r = Aria.compile("a4val", "val.k = 5\nreturn val.k\n");
        for (int i = 0; i <= WARM; i++) {
            assertTrue(r.execute(ctx()) instanceof NoneValue,
                    "第" + (i + 1) + "次：脚本写 val 静默忽略，读回 none");
        }
        assertFalse(r.getProgram().isCompiled(), "含 STORE_VAL 的程序应整体走解释器");
    }

    // ===== 13. 兜底复核附加：三目+flag 寄存器、比较结果存 var 的类型保持 =====
    @Test void ternaryPlusArithmeticHot() throws Exception {
        IValue<?> v = assertParity(
                "var.c = 1\nvar.y = (var.c > 0 ? 1 : 2) + 3\nreturn var.y", this::ctx);
        assertEquals(4.0, v.numberValue(), 1e-9);
    }
    @Test void comparisonResultStoredKeepsBooleanType() throws Exception {
        AriaCompiledRoutine r = Aria.compile("a4b", "var.a = 1\nvar.flag = var.a > 0\nreturn var.flag\n");
        for (int i = 0; i <= WARM; i++) {
            IValue<?> v = r.execute(ctx());
            assertTrue(v instanceof BooleanValue,
                    "第" + (i + 1) + "次：比较结果存 var 应保持 BooleanValue(不得变 NumberValue 1.0)");
            assertEquals("true", v.stringValue());
        }
    }

    // ===== 14. 数值递归 fast 路径仍工作(选路收紧不误伤) =====
    @Test void fibRecursionStillFastAndCorrect() throws Exception {
        AriaCompiledRoutine r = Aria.compile("a4fib",
                "var.fib = -> { if (args[0] < 2) { return args[0] }\nreturn fib(args[0] - 1) + fib(args[0] - 2) }\nreturn var.fib(12)\n");
        for (int i = 0; i <= WARM; i++) {
            assertEquals(144.0, r.execute(ctx()).numberValue(), 1e-9);
        }
    }
    @Test void whileCounterStillFastAndCorrect() throws Exception {
        IValue<?> v = assertParity(
                "var.i = 0\nvar.s = 0\nwhile (var.i < 100) { var.s = var.s + var.i\nvar.i = var.i + 1 }\nreturn var.s",
                this::ctx);
        assertEquals(4950.0, v.numberValue(), 1e-9);
    }
}
