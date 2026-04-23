/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package priv.seventeen.artist.aria.jmh;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Java native baseline — same workloads as AriaBenchmark, written in plain Java.
 * 用作 Aria 性能的"绝对下界"参照，不存在解释器/JIT 启动开销。
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, warmups = 0)
public class JavaNativeBenchmark {

    @Benchmark
    public long loopArithmetic1M() {
        long sum = 0;
        for (int i = 0; i < 1_000_000; i++) sum += i;
        return sum;
    }

    @Benchmark
    public int stringConcat100K() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100_000; i++) sb.append('a');
        return sb.length();
    }

    @Benchmark
    public int arrayOps10K() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) list.add(i);
        return list.size();
    }

    @Benchmark
    public double floatArithmetic1M() {
        double r = 0.0;
        for (int i = 1; i <= 1_000_000; i++) r += 1.0 / i;
        return r;
    }

    /**
     * Aria 所有数值变量都是 double，循环变量也不例外。
     * Java 惯用 int 循环变量，会在 1.0/i 处触发 i2d 转换，每次迭代多一条指令。
     * 这个变体使用 double 循环变量，是与 Aria 的真正对等比较。
     */
    @Benchmark
    public double floatArithmetic1M_doubleLoop() {
        double r = 0.0;
        for (double i = 1.0; i <= 1_000_000.0; i += 1.0) r += 1.0 / i;
        return r;
    }

    @Benchmark
    public long objectOps10K() {
        long sum = 0;
        for (int i = 0; i < 10_000; i++) {
            Map<String, Integer> m = new HashMap<>();
            m.put("x", i);
            m.put("y", i * 2);
            sum += m.get("x") + m.get("y");
        }
        return sum;
    }

    @Benchmark
    public long branchIntensive100K() {
        long c = 0;
        for (int i = 0; i < 100_000; i++) {
            if (i % 3 == 0) c += 1;
            else if (i % 3 == 1) c += 2;
            else c += 3;
        }
        return c;
    }

    @Benchmark
    public double fibonacci25() {
        return fib(25);
    }

    private static double fib(double n) {
        if (n <= 1) return n;
        return fib(n - 1) + fib(n - 2);
    }

    @Benchmark
    public int functionCall100K() {
        int x = 0;
        for (int i = 0; i < 100_000; i++) x = inc(x);
        return x;
    }

    private static int inc(int v) { return v + 1; }
}
