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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.runtime.SandboxConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真验证：内存（长跑无泄漏）与并发（多线程执行正确性 + 沙箱隔离 + 模块缓存线程安全）。
 */
public class RobustnessTest {
    @BeforeAll static void s() {
        Aria.getEngine().initialize();
        Aria.getEngine().getModuleLoader().getResolver().addSearchPath(Path.of("src/test/resources/modules"));
    }

    private static long usedHeapAfterGc() throws InterruptedException {
        for (int i = 0; i < 4; i++) { System.gc(); Thread.sleep(40); }
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    // ---------------- 内存 ----------------

    @Test void memoryNoLeakOnRepeatedEval() throws Exception {
        String code = "var.l=[]\nvar.i=0\nwhile (i<50) { l[]=i*2\n i=i+1 }\nvar.m={'a':1,'b':2}\n"
                + "var.f=-> { return args[0]+1 }\nreturn l.size() + m.size() + f(1)\n";
        for (int i = 0; i < 3000; i++) Aria.eval(code, Aria.createContext()); // 预热 + 越过 JIT
        long base = usedHeapAfterGc();
        for (int i = 0; i < 60000; i++) Aria.eval(code, Aria.createContext());
        long growthMB = (usedHeapAfterGc() - base) / (1024 * 1024);
        assertTrue(growthMB < 64, "6 万次 eval 后堆增长应有界，实际 +" + growthMB + "MB（疑似泄漏）");
    }

    @Test void memoryModuleSingletonBounded() throws Exception {
        String code = "import { add } from 'mathlib'\nreturn add(1,2)\n";
        for (int i = 0; i < 2000; i++) Aria.eval(code, Aria.createContext());
        long base = usedHeapAfterGc();
        for (int i = 0; i < 60000; i++) Aria.eval(code, Aria.createContext());
        long growthMB = (usedHeapAfterGc() - base) / (1024 * 1024);
        assertTrue(growthMB < 64, "重复 import 同一模块（单例缓存）后堆增长应有界，实际 +" + growthMB + "MB");
    }

    // ---------------- 并发 ----------------

    @Test void concurrentEvalCorrectness() throws Exception {
        final int threads = 8, iters = 3000;
        // fib(15) = 610（自递归，会触发 JIT，考验 JIT 缓存并发）
        final String code = "var.f=-> { if (args[0] <= 1) { return args[0] }\n return f(args[0]-1)+f(args[0]-2) }\nreturn f(15)\n";
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> futs = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futs.add(pool.submit(() -> {
                    int ok = 0;
                    for (int i = 0; i < iters; i++) {
                        if (Aria.eval(code, Aria.createContext()).numberValue() == 610.0) ok++;
                    }
                    return ok;
                }));
            }
            int total = 0;
            for (Future<Integer> f : futs) total += f.get(120, TimeUnit.SECONDS);
            assertEquals(threads * iters, total, "并发 eval 全部应得正确结果（无竞态/损坏）");
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentSandboxIsolation() throws Exception {
        // 一半线程用禁 fs 沙箱、一半无限制，并发执行；ThreadLocal 沙箱应互不干扰
        final int threads = 8, iters = 1500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        final AtomicInteger blocked = new AtomicInteger(), allowed = new AtomicInteger();
        try {
            List<Future<?>> futs = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final boolean restricted = (t % 2 == 0);
                futs.add(pool.submit(() -> {
                    for (int i = 0; i < iters; i++) {
                        if (restricted) {
                            SandboxConfig sb = SandboxConfig.builder().allowedNamespaces("math").maxInstructions(100000).build();
                            try { Aria.eval("return fs.exists('x')\n", Aria.createContext(), sb); fail("fs 应被沙箱阻止"); }
                            catch (Exception expected) { blocked.incrementAndGet(); }
                        } else {
                            // 无沙箱线程：math 正常
                            try {
                                if (Aria.eval("return math.abs(-7)\n", Aria.createContext()).numberValue() == 7.0) allowed.incrementAndGet();
                            } catch (Exception e) { fail("无限制线程 math 不应报错: " + e.getMessage()); }
                        }
                    }
                }));
            }
            for (Future<?> f : futs) f.get(120, TimeUnit.SECONDS);
            assertEquals(4 * iters, blocked.get(), "受限线程每次 fs 都应被阻止");
            assertEquals(4 * iters, allowed.get(), "无限制线程每次 math 都应通过");
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentModuleImport() throws Exception {
        // 多线程并发 import 同一模块（单例缓存 + 循环依赖栈的线程安全）
        final int threads = 8, iters = 2000;
        final String code = "import mathlib as m\nreturn m.square(6)\n"; // 36
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> futs = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futs.add(pool.submit(() -> {
                    int ok = 0;
                    for (int i = 0; i < iters; i++) {
                        if (Aria.eval(code, Aria.createContext()).numberValue() == 36.0) ok++;
                    }
                    return ok;
                }));
            }
            int total = 0;
            for (Future<Integer> f : futs) total += f.get(120, TimeUnit.SECONDS);
            assertEquals(threads * iters, total, "并发 import 应全部正确");
        } finally { pool.shutdownNow(); }
    }
}
