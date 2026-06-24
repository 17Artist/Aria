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
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.parser.Lexer;
import priv.seventeen.artist.aria.parser.aria.AriaParser;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.SandboxConfig;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.StringValue;

import static org.junit.jupiter.api.Assertions.*;

public class ModulesTest {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    @BeforeEach
    void resetState() {
        Interpreter.resetCallDepth();
        Interpreter.clearSandbox();
    }

    private IValue<?> eval(String code) throws AriaException {
        return Aria.eval(code, Aria.createContext());
    }


    @Test
    void testCryptoMd5() throws AriaException {
        IValue<?> result = eval("return crypto.md5('hello')\n");
        assertEquals("5d41402abc4b2a76b9719d911017c592", result.stringValue());
    }

    @Test
    void testCryptoSha256() throws AriaException {
        IValue<?> result = eval("return crypto.sha256('hello')\n");
        assertEquals(64, result.stringValue().length()); // SHA-256 = 64 hex chars
    }

    @Test
    void testCryptoBase64() throws AriaException {
        IValue<?> result = eval("""
            val.encoded = crypto.base64Encode('hello world')
            return crypto.base64Decode(encoded)
            """);
        assertEquals("hello world", result.stringValue());
    }

    @Test
    void testCryptoUuid() throws AriaException {
        IValue<?> result = eval("return crypto.uuid()\n");
        assertEquals(36, result.stringValue().length()); // UUID format
    }


    @Test
    void testDateTimeNow() throws AriaException {
        IValue<?> result = eval("return datetime.now()\n");
        assertTrue(result.numberValue() > 0);
    }

    @Test
    void testDateTimeFormat() throws AriaException {
        IValue<?> result = eval("""
            val.ts = datetime.now()
            return datetime.format(ts, 'yyyy')
            """);
        assertTrue(result.stringValue().length() == 4); // year
    }

    @Test
    void testDateTimeFields() throws AriaException {
        IValue<?> result = eval("""
            val.ts = datetime.now()
            val.year = datetime.year(ts)
            return year > 2020
            """);
        assertTrue(result.booleanValue());
    }

    @Test
    void testDateTimeDiff() throws AriaException {
        IValue<?> result = eval("""
            val.a = datetime.now()
            val.b = datetime.addDays(a, 1)
            return datetime.diff(a, b, 'days')
            """);
        assertEquals(1.0, result.numberValue(), 0.01);
    }


    @Test
    void testTemplateRender() throws AriaException {
        // 注意：Aria 字符串中 { 会触发插值，用 Java 端传入模板
        Context ctx = Aria.createContext();

        IValue<?> result = Aria.eval("""
            return template.render('Hello {name}, you are {age} years old', {'name': 'Alice', 'age': '25'})
            """, ctx);
        assertEquals("Hello Alice, you are 25 years old", result.stringValue());
    }


    @Test
    void testSandboxInstructionLimit() {
        SandboxConfig config = SandboxConfig.builder()
            .maxInstructions(100)
            .build();
        assertThrows(AriaException.class, () ->
            Aria.eval("""
                var.i = 0
                while (i < 10000) {
                    i = i + 1
                }
                """, Aria.createContext(), config));
    }

    @Test
    void testSandboxTimeLimit() {
        SandboxConfig config = SandboxConfig.builder()
            .maxExecutionTime(50) // 50ms
            .build();
        assertThrows(AriaException.class, () ->
            Aria.eval("""
                var.i = 0
                while (true) {
                    i = i + 1
                }
                """, Aria.createContext(), config));
    }

    @Test
    void testSandboxCallDepth() {
        Interpreter.resetCallDepth();
        Interpreter.clearSandbox();
        SandboxConfig config = SandboxConfig.builder()
            .maxCallDepth(10)
            .build();
        assertThrows(AriaException.class, () ->
            Aria.eval("""
                var.sandboxModRecurse = -> { return sandboxModRecurse() }
                sandboxModRecurse()
                """, Aria.createContext(), config));
    }

    @Test
    void testSandboxNormalExecution() throws AriaException {
        SandboxConfig config = SandboxConfig.builder()
            .maxExecutionTime(5000)
            .maxInstructions(100000)
            .build();
        IValue<?> result = Aria.eval("return 1 + 2\n", Aria.createContext(), config);
        assertEquals(3.0, result.numberValue());
    }


    @Test
    void testSandboxBlockNamespace() {
        SandboxConfig config = SandboxConfig.builder()
            .allowedNamespaces("math", "type")
            .build();
        // fs 不在白名单中，应该被阻止
        assertThrows(AriaException.class, () ->
            Aria.eval("fs.read('test.txt')\n", Aria.createContext(), config));
    }

    @Test
    void testSandboxAllowNamespace() throws AriaException {
        SandboxConfig config = SandboxConfig.builder()
            .allowedNamespaces("math", "type")
            .maxInstructions(100000)
            .build();
        // math 在白名单中，应该正常执行
        IValue<?> result = Aria.eval("return math.abs(-42)\n", Aria.createContext(), config);
        assertEquals(42.0, result.numberValue());
    }

    @Test
    void testSandboxBlockJavaInterop() {
        SandboxConfig config = SandboxConfig.builder()
            .allowJavaInterop(false)
            .allowedNamespaces("math", "type")
            .build();
        assertThrows(AriaException.class, () ->
            Aria.eval("use('java.lang.System')\n", Aria.createContext(), config));
    }


    @Test
    void testNamedImportParsing() throws AriaException {
        var parser = new AriaParser(
            new Lexer("import { parse, stringify } from 'json'\n"));
        var ast = parser.parse();
        assertNotNull(ast);
        assertFalse(parser.hasErrors());
    }
}
