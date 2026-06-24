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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.module.ModuleLoader;
import priv.seventeen.artist.aria.staticcompile.AriaFileWriter;
import priv.seventeen.artist.aria.staticcompile.AriaPackageReader;
import priv.seventeen.artist.aria.staticcompile.AriaPackager;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.SandboxConfig;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NumberValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 基于文件的模块系统 + 打包测试。
 */
public class FileModuleTest {

    static final Path MODULES_DIR = Path.of("src/test/resources/modules");

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
        Aria.getEngine().getModuleLoader().getResolver().addSearchPath(MODULES_DIR);
    }

    /** 从磁盘加载一个 .aria 源码/入口脚本并执行，返回其返回值。 */
    private static IValue<?> runFile(String modulePath) throws Exception {
        ModuleLoader loader = Aria.getEngine().getModuleLoader();
        var program = loader.load(modulePath);
        Context ctx = Aria.createContext();
        return new Interpreter().execute(program, ctx).getValue();
    }

    private static String readSource(String relative) throws IOException {
        return Files.readString(MODULES_DIR.resolve(relative));
    }

    // ----------------------------------------------------------------------
    // 一、import 调用：命名 / 别名(dot) / 子目录(点号) / 依赖链 / export 可见性
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("命名导入：import { add, square } from 'mathlib'，裸名调用")
    void namedImportCallsFunctions() throws Exception {
        assertEquals(19.0, runFile("entry_named").numberValue(), 1e-9);
    }

    @Test
    @DisplayName("别名导入 + dot 访问：import mathlib as m → m.square(4)")
    void aliasImportDotAccess() throws Exception {
        assertEquals(19.14159, runFile("entry_alias").numberValue(), 1e-6);
    }

    @Test
    @DisplayName("点号路径映射子目录：import text.strutil → text/strutil，dot 访问成员")
    void dottedPathMapsToSubdir() throws Exception {
        assertEquals("ababab", runFile("entry_subdir").stringValue());
    }

    @Test
    @DisplayName("模块依赖链 + 闭包：entry → geometry（闭包引用 mathlib.square/PI）→ mathlib")
    void moduleChainWithClosureOverImports() throws Exception {
        assertEquals(12.56636, runFile("entry_chain").numberValue(), 1e-6);
    }

    @Test
    @DisplayName("export 可见性：只用 export 的模块，消费者可 dot 访问其符号")
    void exportVisibleViaDot() throws Exception {
        assertEquals(42.0, runFile("entry_export").numberValue(), 1e-9);
    }

    @Test
    @DisplayName("向后兼容：return-map 旧式模块仍可被命名导入")
    void backCompatReturnMapModule() throws Exception {
        assertEquals("hi bob", runFile("entry_oldstyle").stringValue());
    }

    // ----------------------------------------------------------------------
    // 二、模块缓存
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("模块缓存：同一路径加载两次返回同一 IRProgram 实例")
    void moduleCacheReturnsSameInstance() throws Exception {
        ModuleLoader loader = Aria.getEngine().getModuleLoader();
        assertSame(loader.load("mathlib"), loader.load("mathlib"));
    }

    // ----------------------------------------------------------------------
    // 三、静态编译 / 打包（从源码文件出发，全程基于文件）
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("预编译 .aria 二进制：源码 → AriaFileWriter → 模块系统按二进制加载")
    void precompiledAriaBinaryLoads(@TempDir Path tmp) throws Exception {
        var program = Aria.compile("pkgmod", readSource("pkgmod")).getProgram();
        Path binary = tmp.resolve("precompiled.aria");
        AriaFileWriter.write(program, binary);

        Aria.getEngine().getModuleLoader().getResolver().addSearchPath(tmp);
        assertEquals(42.0, runFile("precompiled").numberValue(), 1e-9);
    }

    @Test
    @DisplayName(".ariapkg 打包往返：打包模块 + 资源 + 清单 → 读回 → 执行")
    void ariaPackageRoundTrip(@TempDir Path tmp) throws Exception {
        var program = Aria.compile("pkgmod", readSource("pkgmod")).getProgram();
        byte[] configBytes = Files.readAllBytes(MODULES_DIR.resolve("resources/config.txt"));

        Path pkg = tmp.resolve("sample.ariapkg");

        AriaPackager packager = new AriaPackager();
        packager.setManifestEntry("name", "sample-package");
        packager.setManifestEntry("version", "1.0.0");
        packager.setManifestEntry("entry", "pkgmod");
        packager.addModule("pkgmod", program);
        packager.addResource("config.txt", configBytes);
        packager.writeTo(pkg);

        assertTrue(Files.exists(pkg), "包文件应已写出");

        AriaPackageReader reader = AriaPackageReader.read(pkg);

        Properties manifest = reader.getManifest();
        assertEquals("sample-package", manifest.getProperty("name"));
        assertEquals("1.0.0", manifest.getProperty("version"));
        assertEquals("pkgmod", manifest.getProperty("entry"));

        // 注意：getModuleNames() 返回带 .ariac 后缀的内部名（编译产物，API 细节）
        Set<String> names = reader.getModuleNames();
        assertTrue(names.contains("pkgmod.ariac"), "模块名集合应包含 pkgmod.ariac，实际=" + names);

        assertArrayEquals(configBytes, reader.getResource("config.txt"), "资源应原样往返");

        var loaded = reader.getModule("pkgmod");
        var r = new Interpreter().execute(loaded, Aria.createContext()).getValue();
        assertEquals(42.0, r.numberValue(), 1e-9);
    }

    // ----------------------------------------------------------------------
    // 四、P1：magic 探测（源码/二进制按内容区分）与模块单例缓存
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("P1 magic：.aria 源码文件可正常加载（不再被当二进制）")
    void ariaSourceExtensionLoads(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("p1src.aria"), "return 1 + 1\n");
        Aria.getEngine().getModuleLoader().getResolver().addSearchPath(tmp);

        var program = Aria.getEngine().getModuleLoader().load("p1src");
        var r = new Interpreter().execute(program, Aria.createContext()).getValue();
        assertEquals(2.0, r.numberValue(), 1e-9);
    }

    @Test
    @DisplayName("P1 magic：.ariac 预编译二进制按 magic 识别并加载")
    void ariacBinaryExtensionLoads(@TempDir Path tmp) throws Exception {
        var program = Aria.compile("p1bin", "return 5 * 9\n").getProgram();
        AriaFileWriter.write(program, tmp.resolve("p1bin.ariac"));
        Aria.getEngine().getModuleLoader().getResolver().addSearchPath(tmp);

        var loaded = Aria.getEngine().getModuleLoader().load("p1bin");
        var r = new Interpreter().execute(loaded, Aria.createContext()).getValue();
        assertEquals(45.0, r.numberValue(), 1e-9);
    }

    @Test
    @DisplayName("P1 单例：同一模块多次 import 返回同一实例，且顶层只执行一次")
    void moduleSingletonExecutedOnce() throws Exception {
        // Java 端初始化 global 计数器（非内嵌 Aria 脚本，仅测试 setup）
        Aria.getEngine().getGlobalStorage()
                .getGlobalVariable(VariableKey.of("singletonRuns"))
                .setValue(new NumberValue(0));

        IValue<?> first = runFile("entry_singleton");
        IValue<?> second = runFile("entry_singleton");

        assertSame(first, second, "单例缓存应让两次 import 返回同一模块实例");

        double runs = Aria.getEngine().getGlobalStorage()
                .getGlobalVariable(VariableKey.of("singletonRuns"))
                .getValue().numberValue();
        assertEquals(1.0, runs, 1e-9, "模块顶层应只执行一次（计数器为 1）");
    }

    // ----------------------------------------------------------------------
    // 五、P2：循环依赖检测与沙箱继承
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("P2 循环依赖：cyc_a ↔ cyc_b 互相 import 时报清晰错误（非栈溢出）")
    void circularDependencyDetected() {
        AriaException ex = assertThrows(AriaException.class, () -> runFile("entry_cyc"));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("Circular"),
                "应报循环依赖错误，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("P2 沙箱继承：受限沙箱下 import 的模块顶层代码同样受命名空间白名单约束")
    void sandboxAppliesToImportedModule() {
        // 只允许 math，被 import 的模块顶层用了 fs → 应被阻止（沙箱经 ThreadLocal 传播到模块执行）
        SandboxConfig config = SandboxConfig.builder()
                .allowedNamespaces("math")
                .maxInstructions(100000)
                .build();
        assertThrows(AriaException.class, () ->
                Aria.eval("import sandbox_fs_mod as m\n", Aria.createContext(), config));
    }
}
