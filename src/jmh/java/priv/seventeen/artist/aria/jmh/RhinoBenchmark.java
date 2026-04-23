/*
 * Copyright 2026 17Artist
 */

package priv.seventeen.artist.aria.jmh;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
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
public class RhinoBenchmark {

    private Script loopArith, floatArith, stringConcat, arrayOps, objectOps, branchHeavy;

    @Setup(Level.Trial)
    public void setup() {
        Context cx = Context.enter();
        cx.setOptimizationLevel(9);
        try {
            loopArith    = cx.compileString(LOOP_ARITH_1M,        "loop",   1, null);
            floatArith   = cx.compileString(FLOAT_ARITH_1M,       "float",  1, null);
            stringConcat = cx.compileString(STRING_CONCAT_100K,   "string", 1, null);
            arrayOps     = cx.compileString(ARRAY_OPS_10K,        "array",  1, null);
            objectOps    = cx.compileString(OBJECT_OPS_10K,       "obj",    1, null);
            branchHeavy  = cx.compileString(BRANCH_INTENSIVE_100K,"branch", 1, null);
        } finally {
            Context.exit();
        }
    }

    private Object run(Script script) {
        Context cx = Context.enter();
        cx.setOptimizationLevel(9);
        try {
            Scriptable scope = cx.initStandardObjects();
            return script.exec(cx, scope);
        } finally {
            Context.exit();
        }
    }

    @Benchmark public void loopArithmetic1M   (Blackhole bh) { bh.consume(run(loopArith));    }
    @Benchmark public void floatArithmetic1M  (Blackhole bh) { bh.consume(run(floatArith));   }
    @Benchmark public void stringConcat100K   (Blackhole bh) { bh.consume(run(stringConcat)); }
    @Benchmark public void arrayOps10K        (Blackhole bh) { bh.consume(run(arrayOps));     }
    @Benchmark public void objectOps10K       (Blackhole bh) { bh.consume(run(objectOps));    }
    @Benchmark public void branchIntensive100K(Blackhole bh) { bh.consume(run(branchHeavy));  }
}
