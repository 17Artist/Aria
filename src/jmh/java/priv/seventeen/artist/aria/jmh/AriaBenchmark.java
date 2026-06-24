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
    private AriaCompiledRoutine fib;
    private AriaCompiledRoutine mutualFib;
    private AriaCompiledRoutine fnCall;
    private AriaCompiledRoutine deepCallString;
    private AriaCompiledRoutine recursiveList;
    // 多维能耗补充维度
    private AriaCompiledRoutine exceptionHandling;
    private AriaCompiledRoutine closureAlloc;
    private AriaCompiledRoutine classDispatch;
    private AriaCompiledRoutine stringInterp;
    private AriaCompiledRoutine mapOps;
    private AriaCompiledRoutine listFunctional;
    private AriaCompiledRoutine mathFunctions;
    private AriaCompiledRoutine jsonRoundtrip;
    private AriaCompiledRoutine nestedAccess;
    private AriaCompiledRoutine sortReverse;
    private AriaCompiledRoutine ternaryBranch;

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

        fib = Aria.compile("fib", """
                var.fib = -> {
                    if (args[0] <= 1) { return args[0] }
                    return fib(args[0] - 1) + fib(args[0] - 2)
                }
                return fib(25)
                """);

        // 互递归 fib（dot 形式 var.fb(..)）：触发 B 档互递归整组 JIT 编译——
        // fa/fb 整组编入一个类，组内调用 INVOKESTATIC callFast_j 直跳，与自递归 fast path 同构。
        mutualFib = Aria.compile("mutualfib", """
                var.fa = -> {
                    if (args[0] <= 1) { return args[0] }
                    return var.fb(args[0] - 1) + var.fb(args[0] - 2)
                }
                var.fb = -> {
                    if (args[0] <= 1) { return args[0] }
                    return var.fa(args[0] - 1) + var.fa(args[0] - 2)
                }
                return var.fa(25)
                """);

        fnCall = Aria.compile("fncall", """
                var.inc = -> { return args[0] + 1 }
                var.x = 0
                for (var.i = 0; var.i < 100000; var.i += 1) {
                    var.x = inc(var.x)
                }
                return var.x
                """);

        // 通用递归基准（非数值，刻意不命中数值 fast path，走解释器通用调用路径）——
        // 衡量"每次函数调用固定开销"这块短板（C 档调用优化的对象）。
        // 深度线性递归，返回字符串：隔离纯调用机制开销（每 execute = 400 次调用）。
        deepCallString = Aria.compile("deepcall", """
                var.f = -> {
                    if (args[0] <= 0) { return "done" }
                    return f(args[0] - 1)
                }
                return f(400)
                """);

        // 分叉递归，返回嵌套 list：贴近真实数据结构递归（调用 + 分配 + 对象返回）。
        // 直接在 list 字面量里嵌套调用，避免 var.a/var.b 跨递归层共享导致的额外 var 访问开销。
        recursiveList = Aria.compile("reclist", """
                var.g = -> {
                    if (args[0] <= 1) { return [args[0]] }
                    return [g(args[0] - 1), g(args[0] - 2)]
                }
                return g(20)
                """);

        // 多维能耗:异常处理吞吐(try/catch/throw 10K 次)
        exceptionHandling = Aria.compile("exc", """
                var.c = 0
                for (var.i = 0; var.i < 10000; var.i += 1) {
                    try { throw 'e' } catch (e) { var.c += 1 }
                }
                return var.c
                """);

        // 多维能耗:闭包创建 + 调用(10K 次创建捕获闭包并调用)
        closureAlloc = Aria.compile("clo", """
                var.s = 0
                for (var.i = 0; var.i < 10000; var.i += 1) {
                    var.base = var.i
                    var.f = -> { return args[0] + var.base }
                    var.s += f(1)
                }
                return var.s
                """);

        // 多维能耗:类方法分发(10K 次实例方法调用 + self 字段访问)
        classDispatch = Aria.compile("cls", """
                class Counter { new = -> { self.n = 0 } inc = -> { self.n = self.n + args[0] } get = -> { return self.n } }
                val.c = Counter()
                for (var.i = 0; var.i < 10000; var.i += 1) { c.inc(2) }
                return c.get()
                """);

        // 多维能耗:字符串插值(10K 次构造插值串)
        stringInterp = Aria.compile("interp", """
                var.last = ''
                for (var.i = 0; var.i < 10000; var.i += 1) {
                    var.last = "item {var.i} = {var.i * 2}"
                }
                return var.last.length
                """);

        // 多维能耗:Map 读写(10K 次 put + get)
        mapOps = Aria.compile("map", """
                var.m = {}
                var.s = 0
                for (var.i = 0; var.i < 10000; var.i += 1) {
                    m['k'] = var.i
                    var.s += m['k']
                }
                return var.s
                """);

        // 函数式集合操作:filter + map + reduce 链(1000 元素)
        listFunctional = Aria.compile("func", """
                var.list = []
                for (i in Range(0, 1000)) { list.add(i) }
                return list.filter(-> { return args[0] % 2 == 0 }).map(-> { return args[0] * 2 }).reduce(-> { return args[0] + args[1] }, 0)
                """);

        // 数学函数:math.sqrt/sin/pow 密集循环(100K)
        mathFunctions = Aria.compile("mathfn", """
                var.s = 0.0
                for (var.i = 1; var.i < 100000; var.i += 1) {
                    var.s += math.sqrt(var.i) + math.sin(var.i)
                }
                return var.s
                """);

        // JSON 序列化往返:stringify + parse(10K 次)
        jsonRoundtrip = Aria.compile("json", """
                var.m = {'a': 1, 'b': 'hello', 'c': true}
                var.n = 0
                for (var.i = 0; var.i < 10000; var.i += 1) {
                    var.s = json.stringify(m)
                    var.p = json.parse(var.s)
                    var.n += 1
                }
                return var.n
                """);

        // 深层嵌套结构访问(100K 次链式索引)
        nestedAccess = Aria.compile("nested", """
                var.data = {'a': {'b': {'c': [1, 2, 3, 42]}}}
                var.sum = 0
                for (var.i = 0; var.i < 100000; var.i += 1) {
                    var.sum += data['a']['b']['c'][3]
                }
                return var.sum
                """);

        // 列表反转(100 元素 × 1000 次)
        sortReverse = Aria.compile("sortrev", """
                var.list = []
                for (i in Range(0, 100)) { list.add(100 - i) }
                var.last = list
                for (var.i = 0; var.i < 1000; var.i += 1) { var.last = list.reverse() }
                return var.last.length
                """);

        // 三元分支密集(100K)
        ternaryBranch = Aria.compile("ternary", """
                var.c = 0
                for (var.i = 0; var.i < 100000; var.i += 1) {
                    var.c += var.i % 2 == 0 ? 1 : 2
                }
                return var.c
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

    @Benchmark
    public void fibonacci25(Blackhole bh) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> r = fib.execute(ctx);
        bh.consume(r);
    }

    @Benchmark
    public void mutualFibonacci25(Blackhole bh) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> r = mutualFib.execute(ctx);
        bh.consume(r);
    }

    @Benchmark
    public void functionCall100K(Blackhole bh) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> r = fnCall.execute(ctx);
        bh.consume(r);
    }

    @Benchmark
    public void deepCallString400(Blackhole bh) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> r = deepCallString.execute(ctx);
        bh.consume(r);
    }

    @Benchmark
    public void recursiveList20(Blackhole bh) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> r = recursiveList.execute(ctx);
        bh.consume(r);
    }

    @Benchmark
    public void exceptionHandling10K(Blackhole bh) throws Exception {
        bh.consume(exceptionHandling.execute(Aria.createContext()));
    }

    @Benchmark
    public void closureAlloc10K(Blackhole bh) throws Exception {
        bh.consume(closureAlloc.execute(Aria.createContext()));
    }

    @Benchmark
    public void classDispatch10K(Blackhole bh) throws Exception {
        bh.consume(classDispatch.execute(Aria.createContext()));
    }

    @Benchmark
    public void stringInterp10K(Blackhole bh) throws Exception {
        bh.consume(stringInterp.execute(Aria.createContext()));
    }

    @Benchmark
    public void mapOps10K(Blackhole bh) throws Exception {
        bh.consume(mapOps.execute(Aria.createContext()));
    }

    @Benchmark
    public void listFunctional1K(Blackhole bh) throws Exception {
        bh.consume(listFunctional.execute(Aria.createContext()));
    }

    @Benchmark
    public void mathFunctions100K(Blackhole bh) throws Exception {
        bh.consume(mathFunctions.execute(Aria.createContext()));
    }

    @Benchmark
    public void jsonRoundtrip10K(Blackhole bh) throws Exception {
        bh.consume(jsonRoundtrip.execute(Aria.createContext()));
    }

    @Benchmark
    public void nestedAccess100K(Blackhole bh) throws Exception {
        bh.consume(nestedAccess.execute(Aria.createContext()));
    }

    @Benchmark
    public void sortReverse100x1K(Blackhole bh) throws Exception {
        bh.consume(sortReverse.execute(Aria.createContext()));
    }

    @Benchmark
    public void ternaryBranch100K(Blackhole bh) throws Exception {
        bh.consume(ternaryBranch.execute(Aria.createContext()));
    }
}
