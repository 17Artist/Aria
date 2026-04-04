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

import org.junit.jupiter.api.*;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.context.Context;

@Tag("benchmark")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AriaOnlyBenchmark {

    private static final int WARMUP = 15;
    private static final int RUNS = 5;

    @BeforeAll
    static void init() { Aria.getEngine().initialize(); }

    private static long bench(String code) throws Exception {
        AriaCompiledRoutine routine = Aria.compile("bench", code, Aria.Mode.ARIA);
        for (int i = 0; i < WARMUP; i++) {
            Context ctx = Aria.createContext();
            routine.execute(ctx);
        }
        long total = 0;
        for (int i = 0; i < RUNS; i++) {
            Context ctx = Aria.createContext();
            long start = System.nanoTime();
            routine.execute(ctx);
            total += System.nanoTime() - start;
        }
        return total / RUNS;
    }

    private static String fmt(long ns) {
        if (ns < 1_000_000) return String.format("%.2f ms", ns / 1_000_000.0);
        return String.format("%.1f ms", ns / 1_000_000.0);
    }

    @Test @Order(1) void fibonacci() throws Exception {
        long t = bench("""
            var.fib = -> {
                if (args[0] <= 1) { return args[0] }
                return fib(args[0] - 1) + fib(args[0] - 2)
            }
            fib(25)
            """);
        System.out.println("Fibonacci(25):         " + fmt(t));
    }

    @Test @Order(2) void loopArithmetic() throws Exception {
        long t = bench("""
            var.sum = 0
            for (var.i = 0; var.i < 1000000; var.i += 1) {
                var.sum += var.i
            }
            return var.sum
            """);
        System.out.println("Loop Arithmetic 1M:    " + fmt(t));
    }

    @Test @Order(3) void stringConcat() throws Exception {
        long t = bench("""
            var.s = ''
            for (var.i = 0; var.i < 100000; var.i += 1) {
                var.s = var.s + 'a'
            }
            return string.length(var.s)
            """);
        System.out.println("String Concat 100K:    " + fmt(t));
    }

    @Test @Order(4) void arrayList() throws Exception {
        long t = bench("""
            var.list = []
            for (var.i = 0; var.i < 10000; var.i += 1) {
                list.add(var.i)
            }
            return list.size()
            """);
        System.out.println("Array/List Ops 10K:    " + fmt(t));
    }

    @Test @Order(5) void floatArithmetic() throws Exception {
        long t = bench("""
            var.result = 0.0
            for (var.i = 1; var.i <= 1000000; var.i += 1) {
                var.result += 1.0 / var.i
            }
            return var.result
            """);
        System.out.println("Float Arithmetic 1M:   " + fmt(t));
    }

    @Test @Order(6) void objectMap() throws Exception {
        long t = bench("""
            var.sum = 0
            for (var.i = 0; var.i < 10000; var.i += 1) {
                var.obj = {'x': var.i, 'y': var.i * 2}
                var.sum += obj['x'] + obj['y']
            }
            return var.sum
            """);
        System.out.println("Object/Map Ops 10K:    " + fmt(t));
    }

    @Test @Order(7) void functionCall() throws Exception {
        long t = bench("""
            var.inc = -> { return args[0] + 1 }
            var.x = 0
            for (var.i = 0; var.i < 100000; var.i += 1) {
                var.x = inc(var.x)
            }
            return var.x
            """);
        System.out.println("Function Call 100K:    " + fmt(t));
    }

    @Test @Order(8) void branchIntensive() throws Exception {
        long t = bench("""
            var.count = 0
            for (var.i = 0; var.i < 100000; var.i += 1) {
                if (var.i % 3 == 0) {
                    var.count += 1
                } elif (var.i % 3 == 1) {
                    var.count += 2
                } else {
                    var.count += 3
                }
            }
            return var.count
            """);
        System.out.println("Branch Intensive 100K: " + fmt(t));
    }
}
