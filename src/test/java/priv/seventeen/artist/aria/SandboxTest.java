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
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.SandboxConfig;
import priv.seventeen.artist.aria.value.IValue;

import static org.junit.jupiter.api.Assertions.*;

public class SandboxTest {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    //  指令限制

    @Test
    void testInstructionLimitStopsInfiniteLoop() {
        assertThrows(Exception.class, () -> {
            Context ctx = Aria.createContext();
            SandboxConfig sandbox = SandboxConfig.builder()
                    .maxInstructions(100)
                    .build();
            Aria.eval("""
                var.i = 0
                while (true) {
                    var.i += 1
                }
                """, ctx, sandbox);
        });
    }

    //  调用深度限制

    @Test
    void testCallDepthLimitStopsDeepRecursion() {
        Interpreter.resetCallDepth();
        Interpreter.clearSandbox();
        assertThrows(Exception.class, () -> {
            Context ctx = Aria.createContext();
            SandboxConfig sandbox = SandboxConfig.builder()
                    .maxCallDepth(10)
                    .build();
            Aria.eval("""
                var.sandboxTestRecurse = -> {
                    return sandboxTestRecurse()
                }
                sandboxTestRecurse()
                """, ctx, sandbox);
        });
    }

    //  命名空间白名单

    @Test
    void testNamespaceWhitelistBlocksUnauthorized() {
        SandboxConfig sandbox = SandboxConfig.builder()
                .allowedNamespaces("math", "type")
                .build();
        // "fs" 不在白名单中
        assertFalse(sandbox.isNamespaceAllowed("fs"));
        // "math" 在白名单中
        assertTrue(sandbox.isNamespaceAllowed("math"));
        // "type" 在白名单中
        assertTrue(sandbox.isNamespaceAllowed("type"));
    }

    //  Builder 配置

    @Test
    void testSandboxConfigBuilder() {
        SandboxConfig config = SandboxConfig.builder()
                .maxInstructions(5000)
                .maxCallDepth(64)
                .maxExecutionTime(10000)
                .allowFileSystem(false)
                .allowNetwork(false)
                .allowJavaInterop(false)
                .build();

        assertEquals(5000, config.getMaxInstructions());
        assertEquals(64, config.getMaxCallDepth());
        assertEquals(10000, config.getMaxExecutionTimeMs());
        assertFalse(config.isAllowFileSystem());
        assertFalse(config.isAllowNetwork());
        assertFalse(config.isAllowJavaInterop());
    }

    //  无沙箱时正常执行

    @Test
    void testNoSandboxDoesNotAffectNormalExecution() throws AriaException {
        Context ctx = Aria.createContext();
        IValue<?> result = Aria.eval("""
            var.sum = 0
            for (var.i = 0; var.i < 1000; var.i += 1) {
                var.sum += var.i
            }
            return var.sum
            """, ctx);
        assertEquals(499500.0, result.numberValue());
    }

    //  多个沙箱配置可顺序使用

    @Test
    void testMultipleSandboxConfigsSequentially() throws AriaException {
        // 第一个沙箱：宽松限制
        Context ctx1 = Aria.createContext();
        SandboxConfig relaxed = SandboxConfig.builder()
                .maxInstructions(100000)
                .build();
        IValue<?> result1 = Aria.eval("return 1 + 2\n", ctx1, relaxed);
        assertEquals(3.0, result1.numberValue());

        // 第二个沙箱：严格限制
        Context ctx2 = Aria.createContext();
        SandboxConfig strict = SandboxConfig.builder()
                .maxInstructions(50)
                .build();
        assertThrows(Exception.class, () -> {
            Aria.eval("""
                var.i = 0
                while (true) {
                    var.i += 1
                }
                """, ctx2, strict);
        });

        // 沙箱清除后正常执行
        Context ctx3 = Aria.createContext();
        IValue<?> result3 = Aria.eval("return 42\n", ctx3);
        assertEquals(42.0, result3.numberValue());
    }
}
