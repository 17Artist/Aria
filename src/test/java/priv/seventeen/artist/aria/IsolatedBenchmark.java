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

@Tag("benchmark")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IsolatedBenchmark {

    private static final int WARMUP = 15;
    private static final int RUNS = 5;

    private static final String[] NAMES = {
        "Fibonacci(25)", "Loop Arithmetic 1M", "String Concat 100K",
        "Array/List Ops 10K", "Float Arithmetic 1M", "Object/Map Ops 10K",
        "Function Call 100K", "Branch Intensive 100K"
    };

    private static final String[] ARIA_CODES = {
        // Fibonacci
        "var.fib = -> {\n  if (args[0] <= 1) { return args[0] }\n  return fib(args[0] - 1) + fib(args[0] - 2)\n}\nfib(25)",
        // Loop Arithmetic
        "var.sum = 0\nfor (var.i = 0; var.i < 1000000; var.i += 1) {\n  var.sum += var.i\n}\nreturn var.sum",
        // String Concat
        "var.s = ''\nfor (var.i = 0; var.i < 100000; var.i += 1) {\n  var.s = var.s + 'a'\n}\nreturn string.length(var.s)",
        // Array/List
        "var.list = []\nfor (var.i = 0; var.i < 10000; var.i += 1) {\n  list.add(var.i)\n}\nreturn list.size()",
        // Float Arithmetic
        "var.result = 0.0\nfor (var.i = 1; var.i <= 1000000; var.i += 1) {\n  var.result += 1.0 / var.i\n}\nreturn var.result",
        // Object/Map
        "var.sum = 0\nfor (var.i = 0; var.i < 10000; var.i += 1) {\n  var.obj = {'x': var.i, 'y': var.i * 2}\n  var.sum += obj['x'] + obj['y']\n}\nreturn var.sum",
        // Function Call
        "var.inc = -> { return args[0] + 1 }\nvar.x = 0\nfor (var.i = 0; var.i < 100000; var.i += 1) {\n  var.x = inc(var.x)\n}\nreturn var.x",
        // Branch Intensive
        "var.count = 0\nfor (var.i = 0; var.i < 100000; var.i += 1) {\n  if (var.i % 3 == 0) {\n    var.count += 1\n  } elif (var.i % 3 == 1) {\n    var.count += 2\n  } else {\n    var.count += 3\n  }\n}\nreturn var.count"
    };

    private static String fmt(long ns) {
        if (ns < 1_000_000) return String.format("%.2f ms", ns / 1_000_000.0);
        return String.format("%.1f ms", ns / 1_000_000.0);
    }

    @Test @Order(1) void aria() throws Exception {
        Aria.getEngine().initialize();
        System.out.println("\n=== Aria ===");
        for (int i = 0; i < 8; i++) {
            var routine = Aria.compile("bench", ARIA_CODES[i], Aria.Mode.ARIA);
            for (int w = 0; w < WARMUP; w++) routine.execute(Aria.createContext());
            long total = 0;
            for (int r = 0; r < RUNS; r++) {
                long start = System.nanoTime();
                routine.execute(Aria.createContext());
                total += System.nanoTime() - start;
            }
            System.out.printf("  %-25s %s%n", NAMES[i], fmt(total / RUNS));
        }
    }

    @Test @Order(2) void javaNative() throws Exception {
        System.out.println("\n=== Java Native ===");
        Runnable[] tasks = {
            () -> javaFib(25),
            () -> { long s = 0; for (int i = 0; i < 1000000; i++) s += i; },
            () -> { StringBuilder sb = new StringBuilder(); for (int i = 0; i < 100000; i++) sb.append('a'); },
            () -> { java.util.List<Integer> l = new java.util.ArrayList<>(); for (int i = 0; i < 10000; i++) l.add(i); },
            () -> { double r = 0; for (int i = 1; i <= 1000000; i++) r += 1.0 / i; },
            () -> { long s = 0; for (int i = 0; i < 10000; i++) { java.util.Map<String,Integer> m = new java.util.HashMap<>(); m.put("x",i); m.put("y",i*2); s += m.get("x") + m.get("y"); } },
            () -> { int x = 0; for (int i = 0; i < 100000; i++) x = x + 1; },
            () -> { long c = 0; for (int i = 0; i < 100000; i++) { if (i%3==0) c+=1; else if (i%3==1) c+=2; else c+=3; } }
        };
        for (int i = 0; i < 8; i++) {
            for (int w = 0; w < WARMUP; w++) tasks[i].run();
            long total = 0;
            for (int r = 0; r < RUNS; r++) {
                long start = System.nanoTime();
                tasks[i].run();
                total += System.nanoTime() - start;
            }
            System.out.printf("  %-25s %s%n", NAMES[i], fmt(total / RUNS));
        }
    }

    private static double javaFib(double n) {
        if (n <= 1) return n;
        return javaFib(n - 1) + javaFib(n - 2);
    }
}
