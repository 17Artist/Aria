/*
 * Copyright 2026 17Artist
 */

package priv.seventeen.artist.aria.jmh;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.concurrent.TimeUnit;

import static priv.seventeen.artist.aria.jmh.BenchmarkScripts.*;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, warmups = 0)
public class NashornBenchmark {

    private CompiledScript loopArith, floatArith, stringConcat, arrayOps, objectOps, branchHeavy, fib, fnCall;

    @Setup(Level.Trial)
    public void setup() throws ScriptException {
        ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        Compilable c = (Compilable) engine;
        loopArith    = c.compile(LOOP_ARITH_1M);
        floatArith   = c.compile(FLOAT_ARITH_1M);
        stringConcat = c.compile(STRING_CONCAT_100K);
        arrayOps     = c.compile(ARRAY_OPS_10K);
        objectOps    = c.compile(OBJECT_OPS_10K);
        branchHeavy  = c.compile(BRANCH_INTENSIVE_100K);
        fib          = c.compile(FIBONACCI_25);
        fnCall       = c.compile(FUNCTION_CALL_100K);
    }

    @Benchmark public void loopArithmetic1M   (Blackhole bh) throws ScriptException { bh.consume(loopArith.eval());    }
    @Benchmark public void floatArithmetic1M  (Blackhole bh) throws ScriptException { bh.consume(floatArith.eval());   }
    @Benchmark public void stringConcat100K   (Blackhole bh) throws ScriptException { bh.consume(stringConcat.eval()); }
    @Benchmark public void arrayOps10K        (Blackhole bh) throws ScriptException { bh.consume(arrayOps.eval());     }
    @Benchmark public void objectOps10K       (Blackhole bh) throws ScriptException { bh.consume(objectOps.eval());    }
    @Benchmark public void branchIntensive100K(Blackhole bh) throws ScriptException { bh.consume(branchHeavy.eval());  }
    @Benchmark public void fibonacci25        (Blackhole bh) throws ScriptException { bh.consume(fib.eval());          }
    @Benchmark public void functionCall100K   (Blackhole bh) throws ScriptException { bh.consume(fnCall.eval());       }
}
