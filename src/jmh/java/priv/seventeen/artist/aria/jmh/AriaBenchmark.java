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
import org.openjdk.jmh.infra.Blackhole;
import priv.seventeen.artist.aria.Aria;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.value.IValue;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for Aria runtime. Measures pre-compiled routine execution time only
 * (compilation cost is paid once in @Setup). Uses Blackhole to prevent dead code
 * elimination from hoisting result computation away.
 *
 * 注：依然是 micro-benchmark 性质，但 JMH 处理了：
 *   - JIT 充分预热（5 轮 warmup × 5 秒）
 *   - JVM 隔离 fork（避免与其他测试 JIT 状态污染）
 *   - Blackhole 防 DCE
 *   - 多轮统计（avg + ±error）
 * 结论应当比手写 nanoTime 循环可靠得多，但仍不能反映真实业务负载（IO/GC/缓存压力等）。
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, warmups = 0)
public class AriaBenchmark {

    private AriaCompiledRoutine loopArith;
    private AriaCompiledRoutine stringConcat;
    private AriaCompiledRoutine arrayOps;
    private AriaCompiledRoutine floatArith;
    private AriaCompiledRoutine objectOps;
    private AriaCompiledRoutine branchHeavy;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        Aria.getEngine().initialize();

        loopArith = Aria.compile("loop", """
                var.sum = 0
                for (var.i = 0; var.i < 1000000; var.i += 1) {
                    var.sum += var.i
                }
                return var.sum
                """);

        stringConcat = Aria.compile("strcat", """
                var.s = ''
                for (var.i = 0; var.i < 100000; var.i += 1) {
                    var.s = var.s + 'a'
                }
                return string.length(var.s)
                """);

        arrayOps = Aria.compile("array", """
                var.list = []
                for (var.i = 0; var.i < 10000; var.i += 1) {
                    list.add(var.i)
                }
                return list.size()
                """);

        floatArith = Aria.compile("float", """
                var.result = 0.0
                for (var.i = 1; var.i <= 1000000; var.i += 1) {
                    var.result += 1.0 / var.i
                }
                return var.result
                """);

        objectOps = Aria.compile("object", """
                var.sum = 0
                for (var.i = 0; var.i < 10000; var.i += 1) {
                    var.obj = {'x': var.i, 'y': var.i * 2}
                    var.sum += obj['x'] + obj['y']
                }
                return var.sum
                """);

        branchHeavy = Aria.compile("branch", """
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
    }

    @Benchmark
    public void loopArithmetic1M(Blackhole bh) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> r = loopArith.execute(ctx);
        bh.consume(r);
    }

    @Benchmark
    public void stringConcat100K(Blackhole bh) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> r = stringConcat.execute(ctx);
        bh.consume(r);
    }

    @Benchmark
    public void arrayOps10K(Blackhole bh) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> r = arrayOps.execute(ctx);
        bh.consume(r);
    }

    @Benchmark
    public void floatArithmetic1M(Blackhole bh) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> r = floatArith.execute(ctx);
        bh.consume(r);
    }

    @Benchmark
    public void objectOps10K(Blackhole bh) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> r = objectOps.execute(ctx);
        bh.consume(r);
    }

    @Benchmark
    public void branchIntensive100K(Blackhole bh) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> r = branchHeavy.execute(ctx);
        bh.consume(r);
    }
}
