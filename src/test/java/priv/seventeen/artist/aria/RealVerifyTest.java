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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.staticcompile.AriaFileReader;
import priv.seventeen.artist.aria.staticcompile.AriaFileWriter;
import priv.seventeen.artist.aria.staticcompile.AriaPackageReader;
import priv.seventeen.artist.aria.staticcompile.AriaPackager;
import priv.seventeen.artist.aria.value.IValue;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真验证：针对本系列改动（STORE_SCOPE 语义、DECLARE_SCOPE、MAP_MERGE、沙箱闸门）
 * 在「函数体内(executeInline 路径)」「.aria 二进制往返」「async」等此前未直接覆盖的路径做实测。
 */
public class RealVerifyTest {
    @BeforeAll static void s() { Aria.getEngine().initialize(); }
    @BeforeEach void r() { Interpreter.resetCallDepth(); Interpreter.clearSandbox(); }

    private IValue<?> eval(String c) throws AriaException { return Aria.eval(c, Aria.createContext()); }
    private double num(String c) throws AriaException { return eval(c).numberValue(); }
    private String str(String c) throws AriaException { return eval(c).stringValue(); }

    // ---- 函数体内(executeInline 路径)----
    @Test void lambdaBlockAssignUpdatesOuter() throws Exception {
        assertEquals(5.0, num("var.f=-> { r=0\n if (true) { r=5 }\n return r }\nreturn f()"), 1e-9);
    }
    @Test void lambdaTryCatchUsesVar() throws Exception {
        assertEquals("x", str("var.f=-> { try { throw 'x' } catch (e) { return e } }\nreturn f()"));
    }
    @Test void lambdaTryFinally() throws Exception {
        assertEquals(2.0, num("var.f=-> { r=0\n try { r=1 } finally { r=2 }\n return r }\nreturn f()"), 1e-9);
    }
    @Test void lambdaCatchVarShadows() throws Exception {
        assertEquals("in", str("var.f=-> { e='in'\n try { throw 'b' } catch (e) { }\n return e }\nreturn f()"));
    }
    @Test void lambdaMapSpread() throws Exception {
        assertEquals(2.0, num("var.f=-> { b={'x':1}\n return {...b,'y':2}.size() }\nreturn f()"), 1e-9);
    }
    @Test void lambdaMapSpreadNonMapThrows() {
        assertThrows(AriaException.class, () -> eval("var.f=-> { return {...5} }\nreturn f()"));
    }
    // 多次调用以触发任何 fast-path 阈值
    @Test void lambdaTryCatchRepeated() throws Exception {
        assertEquals("ok", str("var.f=-> { try { throw 'ok' } catch (e) { return e } }\n" +
            "last=''\nfor (i in Range(0,20)) { last = f() }\nreturn last"));
    }

    // ---- .aria 二进制往返(DECLARE_SCOPE / MAP_MERGE 序列化)----
    @Test void ariaRoundTripTryCatch(@TempDir Path tmp) throws Exception {
        String code = "e='outer'\nr=''\ntry { throw 'boom' } catch (e) { r = e }\nreturn r + '|' + e\n";
        assertEquals("boom|outer", str(code));
        var prog = Aria.compile("rt1", code).getProgram();
        Path f = tmp.resolve("rt1.aria");
        AriaFileWriter.write(prog, f);
        var loaded = AriaFileReader.read(f);
        var res = new Interpreter().execute(loaded, Aria.createContext()).getValue();
        assertEquals("boom|outer", res.stringValue());
    }
    @Test void ariaRoundTripMapSpread(@TempDir Path tmp) throws Exception {
        String code = "b={'x':1}\nreturn {...b,'y':2}.size()\n";
        assertEquals(2.0, num(code), 1e-9);
        var prog = Aria.compile("rt2", code).getProgram();
        Path f = tmp.resolve("rt2.aria");
        AriaFileWriter.write(prog, f);
        var loaded = AriaFileReader.read(f);
        var res = new Interpreter().execute(loaded, Aria.createContext()).getValue();
        assertEquals(2.0, res.numberValue(), 1e-9);
    }

    // ---- .ariapkg 打包往返：模块含新 opcode(DECLARE_SCOPE + MAP_MERGE)----
    @Test void ariaPackageRoundTripWithNewOpcodes(@TempDir Path tmp) throws Exception {
        // 同一模块同时含 try/catch(DECLARE_SCOPE)与 map 展开(MAP_MERGE)
        String code = "r=''\ntry { throw 'X' } catch (e) { r = e }\nm={...{'a':1},'b':2}\nreturn r + '|' + m.size()\n";
        assertEquals("X|2.0", str(code)); // 直接执行基线（Shimmer 对齐：size()→2.0）

        var prog = Aria.compile("complex", code).getProgram();
        Path pkg = tmp.resolve("complex.ariapkg");

        AriaPackager packager = new AriaPackager();
        packager.setManifestEntry("name", "complex-pkg");
        packager.addModule("complex", prog);
        packager.addResource("note.txt", "hi".getBytes());
        packager.writeTo(pkg);

        AriaPackageReader reader = AriaPackageReader.read(pkg);
        assertEquals("complex-pkg", reader.getManifest().getProperty("name"));
        Set<String> names = reader.getModuleNames();
        assertTrue(names.contains("complex.ariac"), "模块名应含 complex.ariac，实际=" + names);
        assertArrayEquals("hi".getBytes(), reader.getResource("note.txt"));

        var loaded = reader.getModule("complex");
        var res = new Interpreter().execute(loaded, Aria.createContext()).getValue();
        assertEquals("X|2.0", res.stringValue(), "打包往返后含新 opcode 的模块应正确执行");
    }

    @Test void ariaPackageBackCompatOldAriaSuffix(@TempDir Path tmp) throws Exception {
        // 旧包内部模块用 .aria 后缀：reader 应仍能读（向后兼容）
        var prog = Aria.compile("legacy", "return 41 + 1\n").getProgram();
        Path binTmp = tmp.resolve("legacy.aria");
        AriaFileWriter.write(prog, binTmp);
        byte[] bin = java.nio.file.Files.readAllBytes(binTmp);

        Path pkg = tmp.resolve("legacy.ariapkg");
        try (var zos = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(pkg))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("META-INF/MANIFEST.ARIA"));
            zos.write("name=legacy\n".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("modules/legacy.aria")); // 旧式 .aria 内部名
            zos.write(bin);
            zos.closeEntry();
        }
        AriaPackageReader reader = AriaPackageReader.read(pkg);
        var loaded = reader.getModule("legacy");
        assertEquals(42.0, new Interpreter().execute(loaded, Aria.createContext()).getValue().numberValue(), 1e-9);
    }

    // ---- 真异步（提交线程池 + 真 Promise + 沙箱传播 + 上下文隔离）----
    @Test void asyncTryCatch() throws Exception {
        assertEquals("z", str("var.p=async { try { throw 'z' } catch (e) { return e } }\nreturn await var.p"));
    }
    @Test void asyncMapSpread() throws Exception {
        assertEquals(2.0, num("var.p=async { return {...{'a':1},'b':2} }\nvar.m=await var.p\nreturn var.m.size()"), 1e-9);
    }
}
