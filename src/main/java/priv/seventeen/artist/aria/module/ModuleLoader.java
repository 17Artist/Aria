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

package priv.seventeen.artist.aria.module;

import priv.seventeen.artist.aria.compiler.Compiler;
import priv.seventeen.artist.aria.compiler.Optimizer;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.CompileException;
import priv.seventeen.artist.aria.parser.Lexer;
import priv.seventeen.artist.aria.parser.aria.AriaParser;
import priv.seventeen.artist.aria.staticcompile.AriaFileReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class ModuleLoader {
    private final ModuleResolver resolver;
    private final ModuleCache cache;
    private final Optimizer optimizer = new Optimizer();


    private final Map<String, IRProgram> hashCache = new ConcurrentHashMap<>();


    private static final ExecutorService compilePool = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
        r -> { Thread t = new Thread(r, "aria-compile"); t.setDaemon(true); return t; }
    );

    public ModuleLoader() {
        this.resolver = new ModuleResolver();
        this.cache = new ModuleCache();
    }

    public ModuleLoader(ModuleResolver resolver, ModuleCache cache) {
        this.resolver = resolver;
        this.cache = cache;
    }

    public IRProgram load(String modulePath) throws AriaException, IOException {
        // 1. 内存缓存
        if (cache.contains(modulePath)) return cache.get(modulePath);

        Path resolved = resolver.resolve(modulePath);
        if (resolved == null) {
            throw new CompileException("Module not found: " + modulePath);
        }

        IRProgram program;
        String fileName = resolved.getFileName().toString();
        if (fileName.endsWith(".aria")) {
            program = AriaFileReader.read(resolved);
        } else {
            // 2. 增量编译：检查源码哈希
            String source = Files.readString(resolved);
            String hash = sha256(source);
            IRProgram cached = hashCache.get(hash);
            if (cached != null) {
                cache.put(modulePath, cached);
                return cached;
            }

            // 3. 编译
            Lexer lexer = new Lexer(source);
            AriaParser parser = new AriaParser(lexer);
            var ast = parser.parse();
            program = new Compiler().compile(modulePath, ast);
            program = optimizer.optimize(program);

            hashCache.put(hash, program);
        }

        cache.put(modulePath, program);
        return program;
    }

        public Map<String, IRProgram> loadAll(List<String> modulePaths) throws AriaException {
        Map<String, IRProgram> results = new ConcurrentHashMap<>();
        List<Future<Void>> futures = new java.util.ArrayList<>();

        for (String path : modulePaths) {
            futures.add(compilePool.submit(() -> {
                try {
                    results.put(path, load(path));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load module: " + path, e);
                }
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            try {
                f.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new CompileException("Module compilation timed out");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof AriaException se) throw se;
                throw new CompileException("Module compilation error: " + cause.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompileException("Module compilation interrupted");
            }
        }

        return results;
    }

    public ModuleResolver getResolver() { return resolver; }
    public ModuleCache getCache() { return cache; }

    /**
     * 模块源码内容哈希，用于增量编译缓存键。
     * 故意不做 fallback：SHA-256 是 JDK 1.4.2 起的强制算法，若 getInstance 失败说明
     * JRE 安装异常，此时回落到 String.hashCode 会引入碰撞风险（不同源码命中同一缓存条目，
     * 返回错误的 IRProgram）。直接抛出让上层感知，比悄悄返回弱键安全。
     */
    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable in current JRE", e);
        }
    }
}
