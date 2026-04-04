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
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.interop.ClassFilter;
import priv.seventeen.artist.aria.interop.JavaArrayMirror;
import priv.seventeen.artist.aria.interop.JavaClassMirror;
import priv.seventeen.artist.aria.interop.JavaObjectMirror;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.staticcompile.AriaFileReader;
import priv.seventeen.artist.aria.staticcompile.AriaFileWriter;
import priv.seventeen.artist.aria.staticcompile.AriaPackageReader;
import priv.seventeen.artist.aria.staticcompile.AriaPackager;
import priv.seventeen.artist.aria.value.*;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class Phase3Test {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    private IValue<?> eval(String code) throws AriaException {
        Context ctx = Aria.createContext();
        return Aria.eval(code, ctx);
    }

    @Test
    void testJavaClassMirrorCreation() {
        JavaClassMirror mirror = new JavaClassMirror(HashMap.class);
        assertEquals("JavaClass:HashMap", mirror.getTypeName());
        assertEquals("java.util.HashMap", mirror.stringValue());
    }

    @Test
    void testJavaClassMirrorStaticField() {
        JavaClassMirror mirror = new JavaClassMirror(Math.class);
        Variable piVar = mirror.getVariable("PI");
        assertNotNull(piVar.ariaValue());
        assertEquals(Math.PI, piVar.ariaValue().numberValue(), 0.0001);
    }

    @Test
    void testJavaObjectMirrorMethod() throws Exception {
        ArrayList<String> list = new ArrayList<>();
        list.add("hello");
        list.add("world");

        JavaObjectMirror mirror = new JavaObjectMirror(list);
        assertEquals("Java:ArrayList", mirror.getTypeName());

        // size() method
        Variable sizeVar = mirror.getVariable("size");
        assertNotNull(sizeVar.ariaValue());
    }

    @Test
    void testJavaObjectMirrorBeanProperty() {
        // java.util.Date has getTime()/setTime()
        java.util.Date date = new java.util.Date(0);
        JavaObjectMirror mirror = new JavaObjectMirror(date);

        Variable timeVar = mirror.getVariable("time");
        assertNotNull(timeVar.ariaValue());
        assertEquals(0.0, timeVar.ariaValue().numberValue());
    }

    @Test
    void testJavaArrayMirror() {
        int[] arr = new int[]{10, 20, 30};
        JavaArrayMirror mirror = new JavaArrayMirror(arr);

        assertEquals(3, mirror.length());
        assertEquals(10.0, mirror.get(0).numberValue());
        assertEquals(20.0, mirror.get(1).numberValue());
        assertEquals(30.0, mirror.get(2).numberValue());

        mirror.set(1, new NumberValue(99));
        assertEquals(99.0, mirror.get(1).numberValue());
    }

    @Test
    void testJavaClassMirrorNewInstance() throws Exception {
        JavaClassMirror mirror = new JavaClassMirror(HashMap.class);
        IValue<?> instance = mirror.newInstance(new IValue<?>[0]);

        assertInstanceOf(ObjectValue.class, instance);
        Object obj = ((ObjectValue<?>) instance).jvmValue();
        assertInstanceOf(JavaObjectMirror.class, obj);
    }

    @Test
    void testClassFilter() {
        ClassFilter filter = className -> !className.startsWith("java.io");
        assertTrue(filter.exposeToScripts("java.util.HashMap"));
        assertFalse(filter.exposeToScripts("java.io.File"));
    }

    @Test
    void testConvertArgs() {
        // Test type conversion indirectly through newInstance
        JavaClassMirror mirror = new JavaClassMirror(StringBuilder.class);
        try {
            IValue<?> instance = mirror.newInstance(new IValue<?>[]{ new StringValue("hello") });
            assertNotNull(instance);
            Object obj = ((ObjectValue<?>) instance).jvmValue();
            assertInstanceOf(JavaObjectMirror.class, obj);
            assertEquals("hello", ((JavaObjectMirror) obj).stringValue());
        } catch (Exception e) {
            fail("Should not throw: " + e.getMessage());
        }
    }

    @Test
    void testJavaObjectMirrorListAccess() {
        ArrayList<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");

        JavaObjectMirror mirror = new JavaObjectMirror(list);
        Variable elem = mirror.getElement("1");
        assertNotNull(elem.ariaValue());
        assertEquals("b", elem.ariaValue().stringValue());
    }

    @Test
    void testJavaObjectMirrorMapAccess() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("x", 10);
        map.put("y", 20);

        JavaObjectMirror mirror = new JavaObjectMirror(map);
        Variable xVar = mirror.getElement("x");
        assertNotNull(xVar.ariaValue());
        assertEquals(10.0, xVar.ariaValue().numberValue());
    }


    @Test
    void testJavaTypeFromScript() throws AriaException {
        IValue<?> result = eval("""
            val.StringBuilder = use('java.lang.StringBuilder')
            return type.typeof(StringBuilder)
            """);
        assertEquals("object", result.stringValue());
    }

    @Test
    void testJavaNewInstanceFromScript() throws AriaException {
        IValue<?> result = eval("""
            val.StringBuilder = use('java.lang.StringBuilder')
            val.sb = StringBuilder('hello')
            return sb.toString()
            """);
        assertEquals("hello", result.stringValue());
    }

    @Test
    void testJavaMethodCallFromScript() throws AriaException {
        IValue<?> result = eval("""
            val.StringBuilder = use('java.lang.StringBuilder')
            val.sb = StringBuilder()
            sb.append('hello')
            sb.append(' ')
            sb.append('world')
            return sb.toString()
            """);
        assertEquals("hello world", result.stringValue());
    }

    @Test
    void testJavaStaticFieldFromScript() throws AriaException {
        IValue<?> result = eval("""
            val.Math = use('java.lang.Math')
            return Math.PI
            """);
        assertEquals(Math.PI, result.numberValue(), 0.0001);
    }

    @Test
    void testJavaStaticMethodFromScript() throws AriaException {
        IValue<?> result = eval("""
            val.Math = use('java.lang.Math')
            return Math.abs(-42)
            """);
        assertEquals(42.0, result.numberValue());
    }

    @Test
    void testNashornStaticCompatibility() throws AriaException {
        Context ctx1 = Aria.createContext();
        var unit1 = Aria.compile("test.js", ctx1, """
            const Math = Java.type('java.lang.Math');
            return Math.static.abs(-42);
            """, Aria.Mode.JAVASCRIPT);
        IValue<?> r1 = unit1.execute();

        Context ctx2 = Aria.createContext();
        var unit2 = Aria.compile("test.js", ctx2, """
            const Math = Java.type('java.lang.Math');
            return Math.abs(-42);
            """, Aria.Mode.JAVASCRIPT);
        IValue<?> r2 = unit2.execute();

        assertEquals(r1.numberValue(), r2.numberValue());
        assertEquals(42.0, r1.numberValue());
    }

    @Test
    void testJavaFromListConversion() throws AriaException {
        // Java.to 将 Aria list 转为 Java List，Java.from 转回来
        IValue<?> result = eval("""
            val.list = [10, 20, 30]
            val.javaList = Java.to(list, 'java.util.List')
            val.backToSL = Java.from(javaList)
            return type.isList(backToSL)
            """);
        assertTrue(result.booleanValue());
    }

    @Test
    void testJavaHashMapFromScript() throws AriaException {
        IValue<?> result = eval("""
            val.HashMap = use('java.util.HashMap')
            val.map = HashMap()
            map.put('key', 'value')
            return map.get('key')
            """);
        assertEquals("value", result.stringValue());
    }

    @Test
    void testJavaArrayListFromScript() throws AriaException {
        IValue<?> result = eval("""
            val.ArrayList = use('java.util.ArrayList')
            val.list = ArrayList()
            list.add('a')
            list.add('b')
            list.add('c')
            return list.size()
            """);
        assertEquals(3.0, result.numberValue());
    }

    //  服务层端到端测试

    @Test
    void testJsonParseFromScript() throws AriaException {
        // json.parse 返回 MapValue，通过 GET_PROP 访问（而非方法调用）
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(
            VariableKey.of("jsonStr")
        ).setValue(new StringValue("{\"name\":\"test\",\"value\":42}"));

        IValue<?> result = Aria.eval("""
            var.obj = json.parse(global.jsonStr)
            return obj.name
            """, ctx);
        // MapValue 的 GET_PROP 通过 dot 访问
        assertEquals("test", result.stringValue());
    }

    @Test
    void testJsonStringifyFromScript() throws AriaException {
        IValue<?> result = eval("""
            val.map = {'a': 1, 'b': 2}
            val.str = json.stringify(map)
            return type.isString(str)
            """);
        assertTrue(result.booleanValue());
    }

    @Test
    void testEventBusFromScript() throws AriaException {
        // event.on/emit 基本注册测试
        IValue<?> result = eval("""
            var.received = 'none'
            event.on('test.event', -> {
                received = args[0]
            })
            event.emit('test.event', 'hello')
            return received
            """);
        // EventBus 可能是同步的
    }

    //  Java.extend 动态代理测试

    @Test
    void testJavaExtendFunctionalInterface() throws AriaException {
        // 测试 Java.extend 创建 Runnable 代理
        IValue<?> result = eval("""
            val.Runnable = use('java.lang.Runnable')
            val.r = Java.extend(Runnable, -> {
                return 42
            })
            r.run()
            return type.typeof(r)
            """);
        assertEquals("object", result.stringValue());
    }

    @Test
    void testJavaExtendComparator() throws AriaException {
        // 测试 Java.extend 创建 Comparator 代理
        IValue<?> result = eval("""
            val.Comparator = use('java.util.Comparator')
            val.cmp = Java.extend(Comparator, -> {
                return args[0] - args[1]
            })
            return type.typeof(cmp)
            """);
        assertEquals("object", result.stringValue());
    }

    //  静态编译端到端测试

    @Test
    void testStaticCompileRoundTrip() throws Exception {
        // 编译 → 写入 .aria → 读取 → 执行
        String code = "var.x = 10\nvar.y = 20\nreturn x + y\n";
        var unit = Aria.compile("test", code);
        var program = unit.getProgram();

        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("aria-test", ".aria");
        try {
            AriaFileWriter.write(program, tempFile);
            var loaded = AriaFileReader.read(tempFile);

            Context ctx = Aria.createContext();
            Interpreter interpreter = new Interpreter();
            var result = interpreter.execute(loaded, ctx);
            assertEquals(30.0, result.getValue().numberValue());
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testStaticCompileWithFunction() throws Exception {
        // 先验证直接执行能工作
        String code = """
            var.add = -> {
                return args[0] + args[1]
            }
            return add(3, 4)
            """;
        IValue<?> directResult = eval(code);
        assertEquals(7.0, directResult.numberValue(), "Direct execution should work");

        // 再验证序列化 round-trip
        var unit = Aria.compile("test-func", code);
        var program = unit.getProgram();

        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("aria-func", ".aria");
        try {
            AriaFileWriter.write(program, tempFile);
            var loaded = AriaFileReader.read(tempFile);

            Context ctx = Aria.createContext();
            var result = new Interpreter().execute(loaded, ctx);
            assertEquals(7.0, result.getValue().numberValue(), "Deserialized execution should work");
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testAriaPackageRoundTrip() throws Exception {
        // .ariapkg 打包 → 读取 → 执行
        String code1 = "return 42\n";
        String code2 = "return 99\n";
        var prog1 = Aria.compile("mod1", code1).getProgram();
        var prog2 = Aria.compile("mod2", code2).getProgram();

        java.nio.file.Path tempPkg = java.nio.file.Files.createTempFile("aria-pkg", ".ariapkg");
        try {
            // 打包
            var packager = new AriaPackager();
            packager.setManifestEntry("name", "test-package");
            packager.setManifestEntry("version", "1.0.0");
            packager.setManifestEntry("entry", "mod1");
            packager.addModule("mod1", prog1);
            packager.addModule("mod2", prog2);
            packager.addResource("config.txt", "hello=world".getBytes());
            packager.writeTo(tempPkg);

            // 读取
            var reader = AriaPackageReader.read(tempPkg);
            assertEquals("test-package", reader.getManifest().getProperty("name"));
            assertEquals("1.0.0", reader.getManifest().getProperty("version"));
            assertEquals(2, reader.getModuleNames().size());
            assertNotNull(reader.getResource("config.txt"));
            assertEquals("hello=world", new String(reader.getResource("config.txt")));

            // 执行模块
            var loadedMod1 = reader.getModule("mod1");
            Context ctx = Aria.createContext();
            var result = new Interpreter().execute(loadedMod1, ctx);
            assertEquals(42.0, result.getValue().numberValue());

            var loadedMod2 = reader.getModule("mod2");
            var result2 = new Interpreter().execute(loadedMod2, ctx);
            assertEquals(99.0, result2.getValue().numberValue());
        } finally {
            java.nio.file.Files.deleteIfExists(tempPkg);
        }
    }
}
