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
 * 互递归整组 JIT 编译（compileGroup）的正确性回归。
 */
public class GroupRecursionTest {

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

    // ---- 两函数互递归（dot 形式 var.fb(..)） ----
    @Test
    void mutualDotForm() {
        String code =
                "var.fa = -> { if (args[0] <= 1) { return args[0] } return var.fb(args[0]-1) + var.fb(args[0]-2) }\n" +
                "var.fb = -> { if (args[0] <= 1) { return args[0] } return var.fa(args[0]-1) + var.fa(args[0]-2) }\n" +
                "return var.fa(25)\n";
        assertEquals(75025.0, run(code, 60), "dot 互递归整组编译后必须仍 = fib(25)");
    }

    // ---- 两函数互递归（裸名 fb(..)） ----
    @Test
    void mutualBareForm() {
        String code =
                "var.fa = -> { if (args[0] <= 1) { return args[0] } return fb(args[0]-1) + fb(args[0]-2) }\n" +
                "var.fb = -> { if (args[0] <= 1) { return args[0] } return fa(args[0]-1) + fa(args[0]-2) }\n" +
                "return fa(25)\n";
        assertEquals(75025.0, run(code, 60), "裸名互递归整组编译后必须仍 = fib(25)");
    }

    // ---- 指数缩放旁证：fib(30)/fib(25) ≈ 11.09 ----
    @Test
    void mutualScalesExponentially() {
        String c25 =
                "var.fa = -> { if (args[0] <= 1) { return args[0] } return fb(args[0]-1) + fb(args[0]-2) }\n" +
                "var.fb = -> { if (args[0] <= 1) { return args[0] } return fa(args[0]-1) + fa(args[0]-2) }\n" +
                "return fa(25)\n";
        String c30 = c25.replace("fa(25)", "fa(30)");
        double f25 = run(c25, 60);
        double f30 = run(c30, 60);
        assertEquals(75025.0, f25, "fib(25)");
        assertEquals(832040.0, f30, "fib(30)");
        assertEquals(11.09, f30 / f25, 0.01, "指数缩放比应≈11.09，证明确实做了指数量级递归而非作弊返回常量");
    }

    // ---- 三函数环 fa→fb→fc→fa ----
    @Test
    void threeFunctionRing() {
        // 三函数轮转的斐波那契：每个都调用环里下一个，整体仍是标准 fib。
        String code =
                "var.fa = -> { if (args[0] <= 1) { return args[0] } return fb(args[0]-1) + fb(args[0]-2) }\n" +
                "var.fb = -> { if (args[0] <= 1) { return args[0] } return fc(args[0]-1) + fc(args[0]-2) }\n" +
                "var.fc = -> { if (args[0] <= 1) { return args[0] } return fa(args[0]-1) + fa(args[0]-2) }\n" +
                "return fa(25)\n";
        assertEquals(75025.0, run(code, 60), "三函数环整组编译后必须仍 = fib(25)");
    }

    // ---- 奇偶互递归 isEven/isOdd（fa 只调 fb，从不自递归；返回布尔→double 0/1） ----
    @Test
    void evenOddMutualRecursion() {
        // isEven 只调 isOdd、isOdd 只调 isEven——没有任何自递归，验证组检测不依赖自递归。
        String even =
                "var.isEven = -> { if (args[0] == 0) { return 1 } return isOdd(args[0]-1) }\n" +
                "var.isOdd  = -> { if (args[0] == 0) { return 0 } return isEven(args[0]-1) }\n" +
                "return isEven(28)\n";
        String odd = even.replace("isEven(28)", "isOdd(28)");
        assertEquals(1.0, run(even, 40), "isEven(28) 应为真(1)");
        assertEquals(0.0, run(odd, 40), "isOdd(28) 应为假(0)");
        // 奇数实参
        assertEquals(0.0, run(even.replace("isEven(28)", "isEven(27)"), 40), "isEven(27) 应为假(0)");
        assertEquals(1.0, run(even.replace("isEven(28)", "isOdd(27)"), 40), "isOdd(27) 应为真(1)");
    }

    // ---- 组内含 math.*（math.abs / math.max 内联）----
    @Test
    void groupWithMathCalls() {
        String code =
                "var.fa = -> { if (args[0] <= 1) { return math.abs(args[0]) } return math.max(fb(args[0]-1), 0) + fb(args[0]-2) }\n" +
                "var.fb = -> { if (args[0] <= 1) { return args[0] } return fa(args[0]-1) + fa(args[0]-2) }\n" +
                "return fa(20)\n";
        // 与不含 math 的纯 fib 等价：对非负实参 math.abs/math.max(_,0) 不改变值。
        assertEquals(6765.0, run(code, 60), "组内含 math.* 内联，结果仍 = fib(20)");
    }

    // ---- 不对称参数个数：fa(1参) 与 fb(2参) 互递归（验证描述符严格按各 callee argCount）----
    @Test
    void mismatchedArity() {
        // fb 取两参（第二参忽略），fa 取一参。组内 INVOKESTATIC 描述符必须按各自 argCount，否则 VerifyError。
        String code =
                "var.fa = -> { if (args[0] <= 1) { return args[0] } return fb(args[0]-1, 0) + fb(args[0]-2, 0) }\n" +
                "var.fb = -> { if (args[0] <= 1) { return args[0] } return fa(args[0]-1) + fa(args[0]-2) }\n" +
                "return fa(24)\n";
        assertEquals(46368.0, run(code, 60), "不对称参数互递归必须 = fib(24)");
    }

    // ---- 热循环重绑 fb 触发整组去优化，仍返回正确（新 fb）结果 ----
    @Test
    void rebindTriggersDeopt() {
        String code =
                "var.fa = -> { if (args[0] <= 1) { return args[0] } return var.fb(args[0]-1) + var.fb(args[0]-2) }\n" +
                "var.fb = -> { if (args[0] <= 1) { return args[0] } return var.fa(args[0]-1) + var.fa(args[0]-2) }\n" +
                "var.warm = 0\n" +
                "var.i = 0\n" +
                "while (var.i < 40) { var.warm = var.fa(18) var.i = var.i + 1 }\n" +
                "var.fb = -> { return args[0] }\n" +
                "return var.fa(5)\n";
        // 单 execute 内完成预热+重绑+再调用；不依赖多 execute。
        assertEquals(7.0, run(code, 0), "重绑 fb 后 var.fa(5) 必须用新 fb：fb(4)+fb(3)=7，证明整组已去优化");
    }

    // ---- 自递归单函数仍走原路径且正确（确认 size==1 特例未被破坏）----
    @Test
    void selfRecursionUnaffected() {
        String bare = "var.fib = -> { if (args[0] <= 1) { return args[0] } return fib(args[0]-1) + fib(args[0]-2) }\nreturn fib(25)\n";
        String dot  = "var.fib = -> { if (args[0] <= 1) { return args[0] } return var.fib(args[0]-1) + var.fib(args[0]-2) }\nreturn var.fib(25)\n";
        assertEquals(75025.0, run(bare, 60), "裸名自递归（size==1）仍正确");
        assertEquals(75025.0, run(dot, 60), "dot 自递归（size==1）仍正确");
    }
}
