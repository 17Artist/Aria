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

import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 递归正确性回归：覆盖 JIT detectSelfRecursion 修复后的互递归/自递归。
 * 修复前 dot 形式互递归 var.fb(n) 会被误判为自递归 → fast 路径 INVOKESTATIC 调回自身 → 结果错误。
 * 这些用例预热足够次数触发 JIT，断言结果仍等于真实斐波那契值。
 */
public class RecursionCorrectnessTest {

    private double run(String code, int warmup) {
        try {
            AriaCompiledRoutine r = Aria.compile("t", code);
            double last = 0;
            for (int i = 0; i <= warmup; i++) last = r.execute(Aria.createContext()).numberValue();
            return last;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mutualRecursionDotFormIsCorrect() {
        String code =
                "var.fa = -> { if (args[0] <= 1) { return args[0] } return var.fb(args[0]-1) + var.fb(args[0]-2) }\n" +
                "var.fb = -> { if (args[0] <= 1) { return args[0] } return var.fa(args[0]-1) + var.fa(args[0]-2) }\n" +
                "return var.fa(25)\n";
        assertEquals(75025.0, run(code, 60), "dot 互递归预热触发 JIT 后必须仍等于 fib(25)=75025");
    }

    @Test
    void mutualRecursionBareFormIsCorrect() {
        String code =
                "var.fa = -> { if (args[0] <= 1) { return args[0] } return fb(args[0]-1) + fb(args[0]-2) }\n" +
                "var.fb = -> { if (args[0] <= 1) { return args[0] } return fa(args[0]-1) + fa(args[0]-2) }\n" +
                "return fa(25)\n";
        assertEquals(75025.0, run(code, 60), "裸名互递归必须等于 fib(25)=75025");
    }

    @Test
    void selfRecursionStillCorrectBothForms() {
        String bare = "var.fib = -> { if (args[0] <= 1) { return args[0] } return fib(args[0]-1) + fib(args[0]-2) }\nreturn fib(25)\n";
        String dot  = "var.fib = -> { if (args[0] <= 1) { return args[0] } return var.fib(args[0]-1) + var.fib(args[0]-2) }\nreturn var.fib(25)\n";
        assertEquals(75025.0, run(bare, 60), "裸名自递归仍应正确");
        assertEquals(75025.0, run(dot, 60), "dot 自递归仍应正确（修复未破坏自递归 fast path）");
    }
}
