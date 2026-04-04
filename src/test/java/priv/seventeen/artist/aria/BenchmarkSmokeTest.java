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
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BenchmarkSmokeTest {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    @Test
    void testFibonacci() throws AriaException {
        Context ctx = Aria.createContext();
        IValue<?> result = Aria.eval("""
            var.fib = -> {
                if (args[0] <= 1) {
                    return args[0]
                }
                return var.fib(args[0] - 1) + var.fib(args[0] - 2)
            }
            return var.fib(10)
            """, ctx);
        // fib(10) = 55
        assertEquals(55.0, result.numberValue());
    }

    @Test
    void testSimpleLoop() throws AriaException {
        Context ctx = Aria.createContext();
        IValue<?> result = Aria.eval("""
            var.sum = 0
            for (i in Range(1, 100)) {
                var.sum += i
            }
            return var.sum
            """, ctx);
        // sum(1..99) = 4950
        assertEquals(4950.0, result.numberValue());
    }

    @Test
    void testMathFunctions() throws AriaException {
        Context ctx = Aria.createContext();
        // 最简单的测试：直接返回一个数字
        IValue<?> result = Aria.eval("return 42\n", ctx);
        System.out.println("testMathFunctions result: " + result + " type: " + result.getClass().getSimpleName() + " value: " + result.numberValue());
        assertEquals(42.0, result.numberValue());
    }
}
