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
import org.junit.jupiter.api.io.TempDir;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.ListValue;
import priv.seventeen.artist.aria.value.StringValue;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceTest {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    private IValue<?> eval(String code) throws AriaException {
        Context ctx = Aria.createContext();
        return Aria.eval(code, ctx);
    }

    //  json.parse / json.stringify

    @Test
    void testJsonParseObject() throws AriaException {
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(
            VariableKey.of("jsonStr")
        ).setValue(new StringValue("{\"name\":\"Alice\",\"age\":30}"));
        IValue<?> result = Aria.eval("""
            var.obj = json.parse(global.jsonStr)
            return obj.name
            """, ctx);
        assertEquals("Alice", result.stringValue());
    }

    @Test
    void testJsonParseArray() throws AriaException {
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(
            VariableKey.of("jsonStr")
        ).setValue(new StringValue("[1, 2, 3]"));
        IValue<?> result = Aria.eval("""
            var.arr = json.parse(global.jsonStr)
            return arr.size()
            """, ctx);
        assertEquals(3.0, result.numberValue());
    }

    @Test
    void testJsonStringifyMap() throws AriaException {
        IValue<?> result = eval("""
            val.m = {'key': 'value'}
            val.str = json.stringify(m)
            return type.isString(str)
            """);
        assertTrue(result.booleanValue());
    }

    @Test
    void testJsonParseUnicodeEscape() throws AriaException {
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(
            VariableKey.of("jsonStr")
        ).setValue(new StringValue("{\"letter\":\"\\u0041\"}"));
        IValue<?> result = Aria.eval("""
            var.obj = json.parse(global.jsonStr)
            return obj.letter
            """, ctx);
        assertEquals("A", result.stringValue());
    }

    //  event.on / event.emit

    @Test
    void testEventOnAndEmit() throws AriaException {
        IValue<?> result = eval("""
            var.received = 'none'
            event.on('svc.test2', -> {
                var.received = args[0]
            })
            event.emit('svc.test2', 'hello')
            return var.received
            """);
        assertEquals("hello", result.stringValue());
    }

    //  fs — 文件系统服务

    @TempDir
    Path tempDir;

    @Test
    void testFsWriteAndRead() throws AriaException {
        String filePath = tempDir.resolve("test.txt").toString().replace("\\", "/");
        Context ctx = Aria.createContext();
        Aria.eval("fs.write('" + filePath + "', 'hello aria')\n", ctx);
        IValue<?> result = Aria.eval("return fs.read('" + filePath + "')\n", ctx);
        assertEquals("hello aria", result.stringValue());
    }

    @Test
    void testFsAppendAndRead() throws AriaException {
        String filePath = tempDir.resolve("append.txt").toString().replace("\\", "/");
        Context ctx = Aria.createContext();
        Aria.eval("fs.write('" + filePath + "', 'line1')\n", ctx);
        Aria.eval("fs.append('" + filePath + "', '\\nline2')\n", ctx);
        IValue<?> result = Aria.eval("return fs.read('" + filePath + "')\n", ctx);
        assertEquals("line1\nline2", result.stringValue());
    }

    @Test
    void testFsReadLines() throws AriaException {
        String filePath = tempDir.resolve("lines.txt").toString().replace("\\", "/");
        Context ctx = Aria.createContext();
        Aria.eval("fs.write('" + filePath + "', 'a\\nb\\nc')\n", ctx);
        IValue<?> result = Aria.eval("return fs.readLines('" + filePath + "')\n", ctx);
        assertInstanceOf(ListValue.class, result);
        assertEquals(3.0, eval("return " + ((ListValue) result).jvmValue().size() + "\n").numberValue());
    }

    @Test
    void testFsExists() throws AriaException {
        String filePath = tempDir.resolve("exists.txt").toString().replace("\\", "/");
        Context ctx = Aria.createContext();
        IValue<?> before = Aria.eval("return fs.exists('" + filePath + "')\n", ctx);
        assertFalse(before.booleanValue());
        Aria.eval("fs.write('" + filePath + "', 'data')\n", ctx);
        IValue<?> after = Aria.eval("return fs.exists('" + filePath + "')\n", ctx);
        assertTrue(after.booleanValue());
    }

    @Test
    void testFsDelete() throws AriaException {
        String filePath = tempDir.resolve("delete.txt").toString().replace("\\", "/");
        Context ctx = Aria.createContext();
        Aria.eval("fs.write('" + filePath + "', 'temp')\n", ctx);
        IValue<?> existsBefore = Aria.eval("return fs.exists('" + filePath + "')\n", ctx);
        assertTrue(existsBefore.booleanValue());
        Aria.eval("fs.delete('" + filePath + "')\n", ctx);
        IValue<?> existsAfter = Aria.eval("return fs.exists('" + filePath + "')\n", ctx);
        assertFalse(existsAfter.booleanValue());
    }

    //  crypto — 哈希

    @Test
    void testCryptoSha256() throws AriaException {
        IValue<?> result = eval("return crypto.sha256('hello')\n");
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                result.stringValue());
    }

    @Test
    void testCryptoMd5() throws AriaException {
        IValue<?> result = eval("return crypto.md5('hello')\n");
        assertEquals("5d41402abc4b2a76b9719d911017c592", result.stringValue());
    }

    //  datetime

    @Test
    void testDatetimeNowReturnsNumber() throws AriaException {
        IValue<?> result = eval("return datetime.now()\n");
        assertTrue(result.numberValue() > 0);
    }

    //  template.render

    @Test
    void testTemplateRenderWithVariables() throws AriaException {
        // Aria 字符串中 { 会触发插值，用 Java 端传入模板
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(
            VariableKey.of("tpl")
        ).setValue(new StringValue("Hello {name} v{version}"));
        IValue<?> result = Aria.eval("""
            return template.render(global.tpl, {'name': 'Aria', 'version': '1.0'})
            """, ctx);
        assertEquals("Hello Aria v1.0", result.stringValue());
    }

    //  serial.encode / serial.decode

    @Test
    void testSerialEncodeDecodeRoundtrip() throws AriaException {
        IValue<?> result = eval("""
            val.data = {'key': 'value', 'num': 42}
            val.encoded = serial.encode(data)
            var.decoded = serial.decode(encoded)
            return decoded.key
            """);
        assertEquals("value", result.stringValue());
    }

    //  UUID 构造器

    @Test
    void testUUIDConstructorCreatesValidUUID() throws AriaException {
        IValue<?> result = eval("""
            val.id = UUID()
            val.str = type.toString(id)
            return str.length()
            """);
        // UUID 格式: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx = 36 字符
        assertEquals(36.0, result.numberValue());
    }

    @Test
    void testUUIDConstructorParsesString() throws AriaException {
        IValue<?> result = eval("""
            val.id = UUID('550e8400-e29b-41d4-a716-446655440000')
            return type.toString(id)
            """);
        assertEquals("550e8400-e29b-41d4-a716-446655440000", result.stringValue());
    }

    //  string.repeat

    @Test
    void testStringRepeat() throws AriaException {
        IValue<?> result = eval("""
            val.s = 'ha'
            return s.repeat(3)
            """);
        assertEquals("hahaha", result.stringValue());
    }
}
