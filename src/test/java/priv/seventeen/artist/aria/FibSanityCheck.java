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
}
