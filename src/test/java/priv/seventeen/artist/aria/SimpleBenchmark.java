/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package priv.seventeen.artist.aria;

import groovy.lang.GroovyShell;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;

/**
 * Function Call / Fibonacci 专项测试 — 所有脚本引擎对照（不走 JMH）。
 *
 * 反折叠策略：
 *   - Fibonacci：每次轮换一个不同 N（22..26），每个 N 一个独立预编译 routine
 *   - Function Call：变长循环计数（100000-100006）
 *   - 累加返回值 hashCode 防 DCE
 *
 * 跑法：./gradlew :test --tests "*.SimpleBenchmark" --rerun --info
 */
@Tag("benchmark")
public class SimpleBenchmark {

    private static final int WARMUP = 50;
    private static final int RUNS = 200;
    private static long sink;

    private static String fmt(long ns) {
        if (ns < 10_000) return String.format("%.0f ns", (double) ns);
        if (ns < 1_000_000) return String.format("%.2f us", ns / 1_000.0);
        return String.format("%.3f ms", ns / 1_000_000.0);
    }

    private static long measureRunner(Runnable[] tasks) {
        for (int w = 0; w < WARMUP; w++) tasks[w % tasks.length].run();
        long total = 0;
        for (int r = 0; r < RUNS; r++) {
            long start = System.nanoTime();
            tasks[r % tasks.length].run();
            total += System.nanoTime() - start;
        }
        return total / RUNS;
    }

    // ============ Fibonacci(22..26) 各引擎 ============

    @Test
    void fibonacci() throws Exception {
        Aria.getEngine().initialize();
        System.out.println();
        System.out.println("=== Fibonacci(22..26 轮换) ===");
        System.out.printf("  Aria      : %s%n", fmt(measureRunner(buildAriaFib())));
        System.out.printf("  Rhino     : %s%n", fmt(measureRunner(buildRhinoFib())));
        System.out.printf("  Nashorn   : %s%n", fmt(measureRunner(buildNashornFib())));
        System.out.printf("  GraalJS   : %s%n", fmt(measureRunner(buildGraalJSFib())));
        System.out.printf("  Groovy    : %s%n", fmt(measureRunner(buildGroovyFib())));
        System.out.printf("  Java 原生 : %s%n", fmt(measureRunner(buildJavaFib())));
    }

    // ============ Function Call 100K 各引擎 ============

    @Test
    void functionCall() throws Exception {
        Aria.getEngine().initialize();
        System.out.println();
        System.out.println("=== Function Call ~100K (变长 100000-100006) ===");
        System.out.printf("  Aria      : %s%n", fmt(measureRunner(buildAriaFnCall())));
        System.out.printf("  Rhino     : %s%n", fmt(measureRunner(buildRhinoFnCall())));
        System.out.printf("  Nashorn   : %s%n", fmt(measureRunner(buildNashornFnCall())));
        System.out.printf("  GraalJS   : %s%n", fmt(measureRunner(buildGraalJSFnCall())));
        System.out.printf("  Groovy    : %s%n", fmt(measureRunner(buildGroovyFnCall())));
        System.out.printf("  Java 原生 : %s%n", fmt(measureRunner(buildJavaFnCall())));
    }

    // ============ Aria runners ============

    private static Runnable[] buildAriaFib() throws Exception {
        Runnable[] r = new Runnable[5];
        for (int i = 0; i < 5; i++) {
            int n = 22 + i;
            AriaCompiledRoutine routine = Aria.compile("fib_" + n,
                    "var.fib = -> { if (args[0] <= 1) { return args[0] }; return fib(args[0]-1) + fib(args[0]-2) }\n" +
                    "return fib(" + n + ")\n");
            r[i] = () -> { try { sink ^= routine.execute(Aria.createContext()).hashCode(); } catch (Exception e) { throw new RuntimeException(e); } };
        }
        return r;
    }

    private static Runnable[] buildAriaFnCall() throws Exception {
        Runnable[] r = new Runnable[7];
        for (int i = 0; i < 7; i++) {
            int n = 100_000 + i;
            AriaCompiledRoutine routine = Aria.compile("fn_" + n,
                    "var.inc = -> { return args[0] + 1 }\n" +
                    "var.x = 0\n" +
                    "for (var.i = 0; var.i < " + n + "; var.i += 1) { var.x = inc(var.x) }\n" +
                    "return var.x\n");
            r[i] = () -> { try { sink ^= routine.execute(Aria.createContext()).hashCode(); } catch (Exception e) { throw new RuntimeException(e); } };
        }
        return r;
    }

    // ============ Rhino runners ============

    private static Runnable[] buildRhinoFib() {
        Runnable[] r = new Runnable[5];
        for (int i = 0; i < 5; i++) {
            int n = 22 + i;
            String code = "function fib(n){return n<=1?n:fib(n-1)+fib(n-2)}fib(" + n + ");";
            Context cx = Context.enter();
            cx.setOptimizationLevel(9);
            Script script;
            try { script = cx.compileString(code, "fib_" + n, 1, null); }
            finally { Context.exit(); }
            r[i] = () -> {
                Context c = Context.enter();
                c.setOptimizationLevel(9);
                try { Scriptable scope = c.initStandardObjects(); sink ^= script.exec(c, scope).hashCode(); }
                finally { Context.exit(); }
            };
        }
        return r;
    }

    private static Runnable[] buildRhinoFnCall() {
        Runnable[] r = new Runnable[7];
        for (int i = 0; i < 7; i++) {
            int n = 100_000 + i;
            String code = "function inc(v){return v+1}var x=0;for(var i=0;i<" + n + ";i++)x=inc(x);x;";
            Context cx = Context.enter();
            cx.setOptimizationLevel(9);
            Script script;
            try { script = cx.compileString(code, "fn_" + n, 1, null); }
            finally { Context.exit(); }
            r[i] = () -> {
                Context c = Context.enter();
                c.setOptimizationLevel(9);
                try { Scriptable scope = c.initStandardObjects(); sink ^= script.exec(c, scope).hashCode(); }
                finally { Context.exit(); }
            };
        }
        return r;
    }

    // ============ Nashorn runners ============

    private static Runnable[] buildNashornFib() throws Exception {
        ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        Compilable c = (Compilable) engine;
        Runnable[] r = new Runnable[5];
        for (int i = 0; i < 5; i++) {
            int n = 22 + i;
            CompiledScript cs = c.compile("function fib(n){return n<=1?n:fib(n-1)+fib(n-2)}fib(" + n + ");");
            r[i] = () -> { try { sink ^= cs.eval().hashCode(); } catch (Exception e) { throw new RuntimeException(e); } };
        }
        return r;
    }

    private static Runnable[] buildNashornFnCall() throws Exception {
        ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        Compilable c = (Compilable) engine;
        Runnable[] r = new Runnable[7];
        for (int i = 0; i < 7; i++) {
            int n = 100_000 + i;
            CompiledScript cs = c.compile("function inc(v){return v+1}var x=0;for(var i=0;i<" + n + ";i++)x=inc(x);x;");
            r[i] = () -> { try { sink ^= cs.eval().hashCode(); } catch (Exception e) { throw new RuntimeException(e); } };
        }
        return r;
    }

    // ============ GraalJS runners ============

    private static Runnable[] buildGraalJSFib() {
        org.graalvm.polyglot.Context gctx = org.graalvm.polyglot.Context.create("js");
        Runnable[] r = new Runnable[5];
        for (int i = 0; i < 5; i++) {
            int n = 22 + i;
            Source src = Source.create("js", "function fib(n){return n<=1?n:fib(n-1)+fib(n-2)}fib(" + n + ");");
            r[i] = () -> sink ^= gctx.eval(src).hashCode();
        }
        return r;
    }

    private static Runnable[] buildGraalJSFnCall() {
        org.graalvm.polyglot.Context gctx = org.graalvm.polyglot.Context.create("js");
        Runnable[] r = new Runnable[7];
        for (int i = 0; i < 7; i++) {
            int n = 100_000 + i;
            Source src = Source.create("js", "function inc(v){return v+1}var x=0;for(var i=0;i<" + n + ";i++)x=inc(x);x;");
            r[i] = () -> sink ^= gctx.eval(src).hashCode();
        }
        return r;
    }

    // ============ Groovy runners ============

    private static Runnable[] buildGroovyFib() {
        GroovyShell shell = new GroovyShell();
        Runnable[] r = new Runnable[5];
        for (int i = 0; i < 5; i++) {
            int n = 22 + i;
            groovy.lang.Script s = shell.parse("def fib; fib = { n -> n <= 1 ? n : fib(n-1) + fib(n-2) }; fib(" + n + ")");
            r[i] = () -> sink ^= s.run().hashCode();
        }
        return r;
    }

    private static Runnable[] buildGroovyFnCall() {
        GroovyShell shell = new GroovyShell();
        Runnable[] r = new Runnable[7];
        for (int i = 0; i < 7; i++) {
            int n = 100_000 + i;
            groovy.lang.Script s = shell.parse(
                    "def inc = { v -> v + 1 }; int x = 0; for (int i = 0; i < " + n + "; i++) x = inc(x); x");
            r[i] = () -> sink ^= s.run().hashCode();
        }
        return r;
    }

    // ============ Java native runners ============

    private static Runnable[] buildJavaFib() {
        Runnable[] r = new Runnable[5];
        for (int i = 0; i < 5; i++) {
            final int n = 22 + i;
            r[i] = () -> sink ^= Double.hashCode(javaFib(n));
        }
        return r;
    }

    private static Runnable[] buildJavaFnCall() {
        Runnable[] r = new Runnable[7];
        for (int i = 0; i < 7; i++) {
            final int n = 100_000 + i;
            r[i] = () -> {
                int x = 0;
                for (int j = 0; j < n; j++) x = javaInc(x);
                sink ^= x;
            };
        }
        return r;
    }

    private static double javaFib(double n) {
        if (n <= 1) return n;
        return javaFib(n - 1) + javaFib(n - 2);
    }

    private static int javaInc(int v) { return v + 1; }
}
