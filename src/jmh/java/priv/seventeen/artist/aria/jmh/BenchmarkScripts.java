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

/**
 * 各引擎 benchmark 共用的 JS 脚本片段。
 * 所有片段都是 ES5 兼容语法，可直接在 Rhino / Nashorn / GraalJS 里运行。
 * Groovy 另写一套（语法略有差异）。
 */
public final class BenchmarkScripts {
    private BenchmarkScripts() {}

    public static final String LOOP_ARITH_1M =
            "var sum = 0; for (var i = 0; i < 1000000; i++) sum += i; sum;";

    public static final String FLOAT_ARITH_1M =
            "var r = 0.0; for (var i = 1; i <= 1000000; i++) r += 1.0/i; r;";

    public static final String STRING_CONCAT_100K =
            "var s = ''; for (var i = 0; i < 100000; i++) s = s + 'a'; s.length;";

    public static final String ARRAY_OPS_10K =
            "var a = []; for (var i = 0; i < 10000; i++) a.push(i); a.length;";

    public static final String OBJECT_OPS_10K =
            "var sum = 0;" +
            "for (var i = 0; i < 10000; i++) {" +
            "  var o = {x: i, y: i*2};" +
            "  sum += o.x + o.y;" +
            "} sum;";

    public static final String BRANCH_INTENSIVE_100K =
            "var c = 0;" +
            "for (var i = 0; i < 100000; i++) {" +
            "  if (i % 3 == 0) c += 1;" +
            "  else if (i % 3 == 1) c += 2;" +
            "  else c += 3;" +
            "} c;";

    public static final String FIBONACCI_25 =
            "function fib(n) { return n <= 1 ? n : fib(n-1) + fib(n-2); } fib(25);";

    public static final String FUNCTION_CALL_100K =
            "function inc(v) { return v + 1; }" +
            "var x = 0;" +
            "for (var i = 0; i < 100000; i++) x = inc(x); x;";

    // Groovy 版本（语法略有调整）
    public static final String GROOVY_LOOP_ARITH_1M =
            "def sum = 0L; for (int i = 0; i < 1000000; i++) sum += i; sum";

    public static final String GROOVY_FLOAT_ARITH_1M =
            "def r = 0.0d; for (int i = 1; i <= 1000000; i++) r += 1.0d/i; r";

    public static final String GROOVY_STRING_CONCAT_100K =
            "def sb = new StringBuilder(); for (int i = 0; i < 100000; i++) sb.append('a' as char); sb.length()";

    public static final String GROOVY_ARRAY_OPS_10K =
            "def a = []; for (int i = 0; i < 10000; i++) a.add(i); a.size()";

    public static final String GROOVY_OBJECT_OPS_10K =
            "def sum = 0L;" +
            "for (int i = 0; i < 10000; i++) {" +
            "  def o = [x: i, y: i*2];" +
            "  sum += o.x + o.y;" +
            "} sum";

    public static final String GROOVY_BRANCH_INTENSIVE_100K =
            "def c = 0L;" +
            "for (int i = 0; i < 100000; i++) {" +
            "  if (i % 3 == 0) c += 1;" +
            "  else if (i % 3 == 1) c += 2;" +
            "  else c += 3;" +
            "} c";

    public static final String GROOVY_FIBONACCI_25 =
            "def fib; fib = { n -> n <= 1 ? n : fib(n-1) + fib(n-2) }; fib(25)";

    public static final String GROOVY_FUNCTION_CALL_100K =
            "def inc = { v -> v + 1 };" +
            "int x = 0;" +
            "for (int i = 0; i < 100000; i++) x = inc(x); x";
}
