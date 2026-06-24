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
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.SandboxConfig;

import static org.junit.jupiter.api.Assertions.*;

/** 沙箱逃逸对抗测试：allowJavaInterop(false) 必须真正堵住 use()/Java.* 的 Java 互操作 RCE 路径。 */
public class SandboxEscapeTest {
    @BeforeAll static void s() { Aria.getEngine().initialize(); }
    @BeforeEach void r() { Interpreter.resetCallDepth(); Interpreter.clearSandbox(); }

    private SandboxConfig noJava() {
        return SandboxConfig.builder().allowJavaInterop(false).maxInstructions(100000).build();
    }

    @Test void useBlockedWithoutWhitelist() {
        // 无白名单，仅 allowJavaInterop(false)：use(...) 必须被阻止（此前可逃逸）
        assertThrows(AriaException.class, () ->
            Aria.eval("val.R = use('java.lang.Runtime')\nreturn 1\n", Aria.createContext(), noJava()));
    }

    @Test void runtimeExecBlocked() {
        // 经 use 拿 Runtime 再 exec 的 RCE 链：必须从源头(use)被阻止
        assertThrows(AriaException.class, () ->
            Aria.eval("val.R = use('java.lang.Runtime')\nval.rt = R.getRuntime()\nrt.exec('calc')\nreturn 1\n",
                Aria.createContext(), noJava()));
    }

    @Test void fileApiViaJavaBlocked() {
        assertThrows(AriaException.class, () ->
            Aria.eval("val.P = use('java.nio.file.Paths')\nreturn 1\n", Aria.createContext(), noJava()));
    }

    @Test void javaTypeBlocked() {
        assertThrows(AriaException.class, () ->
            Aria.eval("Java.type('java.lang.System')\nreturn 1\n", Aria.createContext(), noJava()));
    }

    @Test void javaToBlocked() {
        assertThrows(AriaException.class, () ->
            Aria.eval("return Java.to([1,2,3])\n", Aria.createContext(), noJava()));
    }

    @Test void javaInteropAllowedByDefault() throws Exception {
        // 默认(allowJavaInterop=true)仍可用，确认闸门不误伤
        SandboxConfig ok = SandboxConfig.builder().maxInstructions(100000).build();
        assertEquals(9.0, Aria.eval("val.M = use('java.lang.Math')\nreturn M.max(4,9)\n",
            Aria.createContext(), ok).numberValue(), 1e-9);
    }

    @Test void importTraversalBlockedUnderSandbox() {
        // E10：沙箱下禁止路径穿越 import（越出搜索路径读任意文件）
        SandboxConfig config = SandboxConfig.builder().allowedNamespaces("math").maxInstructions(100000).build();
        assertThrows(AriaException.class, () ->
            Aria.eval("import { a } from '../../evil'\n", Aria.createContext(), config));
    }

    @Test void cacheHitDoesNotBypassNamespaceCheck() throws Exception {
        // E9：先无沙箱预热 CALL_STATIC 的 inst.cache，再加沙箱复用同一 routine，
        // 命名空间检查不应被 cache 命中绕过
        var routine = Aria.compile("warm", "return fs.exists('nope.txt')\n");
        routine.execute(Aria.createContext()); // 预热（无沙箱）
        SandboxConfig config = SandboxConfig.builder().allowedNamespaces("math").maxInstructions(100000).build();
        Interpreter.setSandbox(config);
        try {
            assertThrows(AriaException.class, () -> routine.execute(Aria.createContext()));
        } finally {
            Interpreter.clearSandbox();
        }
    }

    @Test void noSandboxJavaInteropWorks() throws Exception {
        // 无沙箱时 Java 互操作正常
        assertEquals(9.0, Aria.eval("val.M = use('java.lang.Math')\nreturn M.max(4,9)\n",
            Aria.createContext()).numberValue(), 1e-9);
    }
}
