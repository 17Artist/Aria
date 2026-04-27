/*
 * Copyright 2026 17Artist
 */

package priv.seventeen.artist.aria;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;

/**
 * 回归保护：Aria fib(25) 在 JIT 触发后必须仍返回 75025.0。
 * Stage 4b 曾引入 CALL_STATIC 自递归 JIT 路径，JIT 触发后 fib 返回 0.0 而非
 * 75025.0，且 StressTest.testRecursiveFibonacci 因为单次 eval 不触发 JIT
 * 没能发现该问题。这个测试用反复调用同一 routine 的方式强制 JIT 触发。
 */
@Tag("benchmark")
public class FibSanityCheck {

    @Test
    void ariaFibCorrectAfterJitTrigger() throws Exception {
        Aria.getEngine().initialize();
        AriaCompiledRoutine routine = Aria.compile("fib", """
                var.fib = -> {
                    if (args[0] <= 1) { return args[0] }
                    return fib(args[0] - 1) + fib(args[0] - 2)
                }
                return fib(25)
                """);

        for (int i = 0; i < 20; i++) {
            var result = routine.execute(Aria.createContext());
            double v = result.numberValue();
            if (v != 75025.0) {
                throw new AssertionError("Run " + i + " got " + v + " expected 75025.0 (likely JIT correctness regression)");
            }
        }
    }

    @Test
    void ariaFuncCallCorrectAfterJitTrigger() throws Exception {
        Aria.getEngine().initialize();
        AriaCompiledRoutine routine = Aria.compile("fncall", """
                var.inc = -> { return args[0] + 1 }
                var.x = 0
                for (var.i = 0; var.i < 100000; var.i += 1) {
                    var.x = inc(var.x)
                }
                return var.x
                """);

        for (int i = 0; i < 20; i++) {
            var result = routine.execute(Aria.createContext());
            double v = result.numberValue();
            if (v != 100000.0) {
                throw new AssertionError("Run " + i + " got " + v + " expected 100000.0");
            }
        }
    }

    @Test
    void ariaStringConcatCorrect() throws Exception {
        Aria.getEngine().initialize();
        // 先验证字符串内容正确（不依赖 length 调用）
        AriaCompiledRoutine accum = Aria.compile("accum", """
                var.s = ''
                for (var.i = 0; var.i < 100000; var.i += 1) {
                    var.s = var.s + 'a'
                }
                return var.s
                """);
        for (int i = 0; i < 20; i++) {
            var result = accum.execute(Aria.createContext());
            int len = result.stringValue().length();
            if (len != 100000) {
                throw new AssertionError("accum run " + i + " got len " + len + " (class=" + result.getClass().getSimpleName() + ")");
            }
        }

        // 再验证 length() 调用
        AriaCompiledRoutine routine = Aria.compile("strcat", """
                var.s = ''
                for (var.i = 0; var.i < 100000; var.i += 1) {
                    var.s = var.s + 'a'
                }
                return var.s.length()
                """);
        for (int i = 0; i < 20; i++) {
            var result = routine.execute(Aria.createContext());
            double v = result.numberValue();
            if (v != 100000.0) {
                throw new AssertionError("length run " + i + " got " + v + " (class=" + result.getClass().getSimpleName() + ")");
            }
        }
    }

    @Test
    void floatProbe() throws Exception {
        Aria.getEngine().initialize();
        AriaCompiledRoutine routine = Aria.compile("float", """
                var.result = 0.0
                for (var.i = 1; var.i <= 100; var.i += 1) {
                    var.result += 1.0 / var.i
                }
                return var.result
                """);
        for (int i = 0; i < 30; i++) routine.execute(Aria.createContext());
        var r = routine.execute(Aria.createContext());
        System.out.println("float probe: " + r.numberValue());
    }

    /**
     * GET_PROP / LOAD_GLOBAL JIT 路径回归保护：用 obj.prop 和 global.x 构造一段会被 JIT
     * 编译的循环，反复执行触发 JIT，验证结果正确。
     */
    @Test
    void ariaGetPropAndLoadGlobalCorrectAfterJit() throws Exception {
        Aria.getEngine().initialize();
        AriaCompiledRoutine routine = Aria.compile("getprop_loadglobal", """
                global.counter = 0
                var.obj = {'x': 7, 'y': 11}
                var.sum = 0
                for (var.i = 0; var.i < 50; var.i += 1) {
                    var.sum += obj.x + obj.y
                    global.counter += 1
                }
                return var.sum
                """);
        // (7+11) * 50 = 900
        for (int i = 0; i < 20; i++) {
            var result = routine.execute(Aria.createContext());
            double v = result.numberValue();
            if (v != 900.0) {
                throw new AssertionError("Run " + i + " got " + v + " expected 900.0");
            }
        }
    }

    /**
     * bridge.xxx（闭包捕获对象的方法调用）JIT 路径回归保护：
     * scope 里有一个 map（充当 bridge），通过 bridge.somekey 拿到值。
     * 这种 obj.method 形式以前在 JIT 路径下走 rtCallByName 返回 NoneValue，现在应正确分派。
     */
    @Test
    void ariaListAddCorrectAfterJit() throws Exception {
        Aria.getEngine().initialize();
        // 这个 routine 里有 list.add(...)，CALL_STATIC name="list.add"。
        // 修复前 rtCallByName 不识别 obj.method 形式，每次 add 静默返回 NoneValue，
        // 导致 list 永远是空的，list.size() 返回 0。
        AriaCompiledRoutine routine = Aria.compile("listadd", """
                var.lst = []
                for (var.i = 0; var.i < 10000; var.i += 1) {
                    lst.add(var.i)
                }
                return lst.size()
                """);
        for (int i = 0; i < 20; i++) {
            var result = routine.execute(Aria.createContext());
            double v = result.numberValue();
            if (v != 10000.0) {
                throw new AssertionError("Run " + i + " got " + v + " expected 10000.0 (list 应有 10000 个元素)");
            }
        }
    }
}
