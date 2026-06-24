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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.runtime.SandboxConfig;

import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 长时间 soak：多线程持续混合负载（真异步/并发 eval/模块导入/分配/沙箱 async）
 */
public class SoakTest {

    private static long heapMB() {
        for (int i = 0; i < 3; i++) { System.gc(); try { Thread.sleep(30); } catch (InterruptedException ignored) {} }
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
    }

    @Test
    void soak() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soak"), "soak 仅在 -Dsoak=true 时运行");
        doSoak(Long.getLong("soakMinutes", 20));
    }

    public static void main(String[] args) throws Exception {
        long minutes = args.length > 0 ? Long.parseLong(args[0]) : 20;
        doSoak(minutes);
        System.out.println("[soak] PASS");
    }

    /** 运行 soak；失败抛 RuntimeException（不依赖 JUnit 断言，便于纯 java 运行）。 */
    static void doSoak(long minutes) throws Exception {
        Aria.getEngine().initialize();
        Aria.getEngine().getModuleLoader().getResolver().addSearchPath(Path.of("src/test/resources/modules"));

        final long start = System.currentTimeMillis();
        final long deadline = start + minutes * 60_000L;
        final int threads = 8;
        final AtomicLong ops = new AtomicLong(), errors = new AtomicLong();
        final AtomicReference<Throwable> lastErr = new AtomicReference<>();
        final java.util.concurrent.atomic.AtomicLongArray errByCase = new java.util.concurrent.atomic.AtomicLongArray(6);
        final java.util.concurrent.ConcurrentHashMap<Integer, String> firstMsg = new java.util.concurrent.ConcurrentHashMap<>();
        final SandboxConfig restricted = SandboxConfig.builder().allowedNamespaces("math").maxInstructions(200000).build();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        System.out.println("[soak] start: " + minutes + " min, " + threads + " threads");

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                long it = 0;
                while (System.currentTimeMillis() < deadline) {
                    int kind = (int) (it % 6);
                    try {
                        switch (kind) {
                            case 0 -> req(Aria.eval("var.p=async { return 6*7 }\nreturn await p", Aria.createContext()).numberValue() == 42.0, "async!=42");
                            case 1 -> req(Aria.eval("var.f=-> { if(args[0]<=1){return args[0]}\nreturn f(args[0]-1)+f(args[0]-2) }\nreturn f(14)", Aria.createContext()).numberValue() == 377.0, "fib!=377");
                            case 2 -> req(Aria.eval("import mathlib as m\nreturn m.square(5)", Aria.createContext()).numberValue() == 25.0, "mod!=25");
                            case 3 -> req(Aria.eval("var.l=[]\nvar.i=0\nwhile(i<40){l[]=i\ni=i+1}\nvar.mp={...{'a':1},'b':2}\nvar.g=-> { return args[0]+l.size() }\nreturn g(mp.size())", Aria.createContext()).numberValue() == 42.0, "alloc!=42");
                            case 4 -> {
                                try { Aria.eval("var.p=async { return fs.exists('x') }\nreturn await p", Aria.createContext(), restricted); req(false, "sandbox async fs not blocked"); }
                                catch (RuntimeException ae) { throw ae; }
                                catch (Exception expected) { /* ok：沙箱挡住 */ }
                            }
                            case 5 -> req(Aria.eval("var.a=async{return 1}\nvar.b=async{return 2}\nreturn (await a)+(await b)", Aria.createContext()).numberValue() == 3.0, "multiAsync!=3");
                        }
                        ops.incrementAndGet();
                    } catch (Throwable e) {
                        errors.incrementAndGet(); lastErr.set(e);
                        errByCase.incrementAndGet(kind);
                        firstMsg.putIfAbsent(kind, e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                    it++;
                }
            });
        }
        pool.shutdown();

        long warmupHeap = -1, startThreads = Thread.activeCount();
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(30_000);
            long elapsedMin = (System.currentTimeMillis() - start) / 60_000L;
            long h = heapMB();
            if (warmupHeap < 0 && elapsedMin >= 1) warmupHeap = h;
            System.out.printf("[soak] +%dmin ops=%,d errors=%d heap=%dMB threads=%d%n",
                    elapsedMin, ops.get(), errors.get(), h, Thread.activeCount());
            if (errors.get() > 0) break;
        }
        if (!pool.awaitTermination(60, TimeUnit.SECONDS)) throw new RuntimeException("worker 未在截止后结束");

        long endHeap = heapMB(), endThreads = Thread.activeCount();
        long growth = warmupHeap > 0 ? endHeap - warmupHeap : 0;
        System.out.printf("[soak] DONE ops=%,d errors=%d baselineHeap=%dMB endHeap=%dMB growth=%dMB threads %d->%d%n",
                ops.get(), errors.get(), warmupHeap, endHeap, growth, startThreads, endThreads);
        String[] caseName = {"async", "fib", "module", "alloc", "sandboxAsync", "multiAsync"};
        for (int k = 0; k < 6; k++) {
            if (errByCase.get(k) > 0)
                System.out.printf("[soak]   case %s: errors=%d firstMsg=%s%n", caseName[k], errByCase.get(k), firstMsg.get(k));
        }
        if (lastErr.get() != null) lastErr.get().printStackTrace();

        if (errors.get() != 0) throw new RuntimeException("soak 期间有 " + errors.get() + " 个错误");
        if (ops.get() <= 1000) throw new RuntimeException("操作数过少=" + ops.get());
        if (growth >= 256) throw new RuntimeException("堆增长过大(疑似泄漏) +" + growth + "MB");
        if (endThreads >= startThreads + 64) throw new RuntimeException("线程无界增长 " + startThreads + "->" + endThreads);
    }

    private static void req(boolean ok, String msg) { if (!ok) throw new RuntimeException(msg); }
}
