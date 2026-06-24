/*
 * Copyright 2026 17Artist
 */

package priv.seventeen.artist.aria.jmh;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

import static priv.seventeen.artist.aria.jmh.BenchmarkScripts.*;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, warmups = 0)
public class GraalJSBenchmark {

    private Context ctx;
    private Source loopArith, floatArith, stringConcat, arrayOps, objectOps, branchHeavy, fib, fnCall;

    @Setup(Level.Trial)
    public void setup() {
        ctx = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        loopArith    = Source.create("js", LOOP_ARITH_1M);
        floatArith   = Source.create("js", FLOAT_ARITH_1M);
        stringConcat = Source.create("js", STRING_CONCAT_100K);
        arrayOps     = Source.create("js", ARRAY_OPS_10K);
        objectOps    = Source.create("js", OBJECT_OPS_10K);
        branchHeavy  = Source.create("js", BRANCH_INTENSIVE_100K);
        fib          = Source.create("js", FIBONACCI_25);
        fnCall       = Source.create("js", FUNCTION_CALL_100K);
    }

    @TearDown(Level.Trial)
    public void teardown() { ctx.close(); }

    @Benchmark public void loopArithmetic1M   (Blackhole bh) { Value v = ctx.eval(loopArith);    bh.consume(v); }
    @Benchmark public void floatArithmetic1M  (Blackhole bh) { Value v = ctx.eval(floatArith);   bh.consume(v); }
    @Benchmark public void stringConcat100K   (Blackhole bh) { Value v = ctx.eval(stringConcat); bh.consume(v); }
    @Benchmark public void arrayOps10K        (Blackhole bh) { Value v = ctx.eval(arrayOps);     bh.consume(v); }
    @Benchmark public void objectOps10K       (Blackhole bh) { Value v = ctx.eval(objectOps);    bh.consume(v); }
    @Benchmark public void branchIntensive100K(Blackhole bh) { Value v = ctx.eval(branchHeavy);  bh.consume(v); }
    @Benchmark public void fibonacci25        (Blackhole bh) { Value v = ctx.eval(fib);          bh.consume(v); }
    @Benchmark public void functionCall100K   (Blackhole bh) { Value v = ctx.eval(fnCall);       bh.consume(v); }
}
