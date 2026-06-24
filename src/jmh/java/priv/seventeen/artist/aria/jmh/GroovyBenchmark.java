/*
 * Copyright 2026 17Artist
 */

package priv.seventeen.artist.aria.jmh;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
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
public class GroovyBenchmark {

    private GroovyShell shell;
    private Script loopArith, floatArith, stringConcat, arrayOps, objectOps, branchHeavy, fib, fnCall;

    @Setup(Level.Trial)
    public void setup() {
        shell = new GroovyShell();
        loopArith    = shell.parse(GROOVY_LOOP_ARITH_1M);
        floatArith   = shell.parse(GROOVY_FLOAT_ARITH_1M);
        stringConcat = shell.parse(GROOVY_STRING_CONCAT_100K);
        arrayOps     = shell.parse(GROOVY_ARRAY_OPS_10K);
        objectOps    = shell.parse(GROOVY_OBJECT_OPS_10K);
        branchHeavy  = shell.parse(GROOVY_BRANCH_INTENSIVE_100K);
        fib          = shell.parse(GROOVY_FIBONACCI_25);
        fnCall       = shell.parse(GROOVY_FUNCTION_CALL_100K);
    }

    @Benchmark public void loopArithmetic1M   (Blackhole bh) { bh.consume(loopArith.run());    }
    @Benchmark public void floatArithmetic1M  (Blackhole bh) { bh.consume(floatArith.run());   }
    @Benchmark public void stringConcat100K   (Blackhole bh) { bh.consume(stringConcat.run()); }
    @Benchmark public void arrayOps10K        (Blackhole bh) { bh.consume(arrayOps.run());     }
    @Benchmark public void objectOps10K       (Blackhole bh) { bh.consume(objectOps.run());    }
    @Benchmark public void branchIntensive100K(Blackhole bh) { bh.consume(branchHeavy.run());  }
    @Benchmark public void fibonacci25        (Blackhole bh) { bh.consume(fib.run());          }
    @Benchmark public void functionCall100K   (Blackhole bh) { bh.consume(fnCall.run());       }
}
