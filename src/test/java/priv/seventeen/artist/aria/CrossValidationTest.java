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
import priv.seventeen.artist.aria.value.IValue;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CrossValidationTest {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    private IValue<?> eval(String code) throws AriaException {
        return Aria.eval(code, Aria.createContext());
    }

    //  1. 算术交叉验证

    @Test
    void testArithmeticTwoWays() throws AriaException {
        // Way 1: 直接表达式
        IValue<?> r1 = eval("return 2 + 3 * 4 - 1");
        // Way 2: 分步变量计算
        IValue<?> r2 = eval("var.a = 3 * 4\nvar.b = 2 + a\nreturn b - 1");
        assertEquals(r1.numberValue(), r2.numberValue());
        assertEquals(13.0, r1.numberValue());
    }

    //  2. 斐波那契交叉验证

    @Test
    void testFibonacciTwoWays() throws AriaException {
        // Way 1: 递归
        IValue<?> r1 = eval("""
            var.fib = -> { if (args[0] <= 1) { return args[0] } return fib(args[0]-1) + fib(args[0]-2) }
            return fib(20)
            """);
        // Way 2: 迭代
        IValue<?> r2 = eval("""
            var.a = 0
            var.b = 1
            for (var.i = 0; var.i < 20; var.i += 1) {
                var.temp = b
                var.b = a + b
                var.a = temp
            }
            return a
            """);
        assertEquals(r1.numberValue(), r2.numberValue());
        assertEquals(6765.0, r1.numberValue());
    }

    //  3. 字符串操作交叉验证

    @Test
    void testStringConcatTwoWays() throws AriaException {
        // Way 1: + 运算符
        IValue<?> r1 = eval("return 'hello' + ' ' + 'world'");
        // Way 2: 字符串插值
        IValue<?> r2 = eval("var.w = 'world'\nreturn \"hello {w}\"");
        // Way 3: 变量累加
        IValue<?> r3 = eval("var.s = 'hello'\nvar.s = s + ' '\nvar.s = s + 'world'\nreturn s");
        assertEquals(r1.stringValue(), r2.stringValue());
        assertEquals(r1.stringValue(), r3.stringValue());
        assertEquals("hello world", r1.stringValue());
    }

    //  4. 列表操作交叉验证

    @Test
    void testListSumTwoWays() throws AriaException {
        // Way 1: for-in 循环
        IValue<?> r1 = eval("""
            var.list = [10, 20, 30, 40, 50]
            var.sum = 0
            for (item in list) { var.sum += item }
            return sum
            """);
        // Way 2: reduce
        IValue<?> r2 = eval("""
            val.list = [10, 20, 30, 40, 50]
            return list.reduce(-> { return args[0] + args[1] }, 0)
            """);
        assertEquals(r1.numberValue(), r2.numberValue());
        assertEquals(150.0, r1.numberValue());
    }

    //  5. 类系统交叉验证

    @Test
    void testClassTwoWays() throws AriaException {
        // Way 1: class + 构造器
        IValue<?> r1 = eval("""
            class Point {
                var.x = 0
                var.y = 0
                new = -> { self.x = args[0]; self.y = args[1] }
                sum = -> { return self.x + self.y }
            }
            val.p = Point(3, 4)
            return p.sum()
            """);
        // Way 2: map 作为对象
        IValue<?> r2 = eval("""
            val.p = {'x': 3, 'y': 4}
            return p['x'] + p['y']
            """);
        assertEquals(r1.numberValue(), r2.numberValue());
        assertEquals(7.0, r1.numberValue());
    }

    //  6. 闭包交叉验证

    @Test
    void testClosureTwoWays() throws AriaException {
        // Way 1: 闭包捕获变量
        IValue<?> r1 = eval("""
            var.makeCounter = -> {
                var.count = 0
                return -> { count = count + 1; return count }
            }
            val.c = makeCounter()
            c()
            c()
            return c()
            """);
        // Way 2: 基于类的计数器
        IValue<?> r2 = eval("""
            class Counter {
                var.count = 0
                inc = -> { self.count = self.count + 1; return self.count }
            }
            val.c = Counter()
            c.inc()
            c.inc()
            return c.inc()
            """);
        assertEquals(r1.numberValue(), r2.numberValue());
        assertEquals(3.0, r1.numberValue());
    }

    //  7. 控制流交叉验证

    @Test
    void testBranchIntensiveTwoWays() throws AriaException {
        // Way 1: if/elif/else
        IValue<?> r1 = eval("""
            var.count = 0
            for (var.i = 0; var.i < 100; var.i += 1) {
                if (var.i % 3 == 0) { var.count += 1 }
                elif (var.i % 3 == 1) { var.count += 2 }
                else { var.count += 3 }
            }
            return count
            """);
        // Way 2: match（不穿透）
        IValue<?> r2 = eval("""
            var.count = 0
            for (var.i = 0; var.i < 100; var.i += 1) {
                match (var.i % 3) {
                    case 0 { var.count += 1 }
                    case 1 { var.count += 2 }
                    case 2 { var.count += 3 }
                }
            }
            return count
            """);
        assertEquals(r1.numberValue(), r2.numberValue());
        // 0..99: 34 个 mod=0, 33 个 mod=1, 33 个 mod=2 → 34*1 + 33*2 + 33*3 = 199
        assertEquals(199.0, r1.numberValue());
    }

    //  8. Java 互操作交叉验证

    @Test
    void testJavaInteropTwoWays() throws AriaException {
        // Way 1: use() + Java 类
        IValue<?> r1 = eval("""
            val.Math = use('java.lang.Math')
            return Math.abs(-42)
            """);
        // Way 2: 内置 math.abs
        IValue<?> r2 = eval("return math.abs(-42)");
        assertEquals(r1.numberValue(), r2.numberValue());
        assertEquals(42.0, r1.numberValue());
    }

    //  9. JS 模式 vs Aria 模式交叉验证

    @Test
    void testJsVsAriaSameResult() throws AriaException {
        // Aria 模式
        IValue<?> r1 = eval("""
            var.sum = 0
            for (var.i = 1; var.i <= 100; var.i += 1) { var.sum += i }
            return sum
            """);
        // JS 模式
        Context ctx = Aria.createContext();
        var unit = Aria.compile("test.js", ctx, """
            var sum = 0;
            for (var i = 1; i <= 100; i++) { sum = sum + i; }
            return sum;
            """, Aria.Mode.JAVASCRIPT);
        IValue<?> r2 = unit.execute();
        Interpreter.resetCallDepth();
        assertEquals(r1.numberValue(), r2.numberValue());
        assertEquals(5050.0, r1.numberValue());
    }

    //  10. 自增交叉验证

    @Test
    void testIncrementTwoWays() throws AriaException {
        // Way 1: 前缀 ++
        IValue<?> r1 = eval("var.x = 5\n++var.x\n++var.x\nreturn x");
        // Way 2: += 1
        IValue<?> r2 = eval("var.x = 5\nvar.x += 1\nvar.x += 1\nreturn x");
        assertEquals(r1.numberValue(), r2.numberValue());
        assertEquals(7.0, r1.numberValue());
    }

    //  11. Map 操作交叉验证

    @Test
    void testMapTwoWays() throws AriaException {
        // Way 1: 字面量 + 索引访问
        IValue<?> r1 = eval("val.m = {'a': 1, 'b': 2, 'c': 3}\nreturn m['a'] + m['b'] + m['c']");
        // Way 2: 逐步构建 map + dot 访问
        IValue<?> r2 = eval("""
            val.m = {'a': 0, 'b': 0, 'c': 0}
            m['a'] = 1
            m['b'] = 2
            m['c'] = 3
            return m.a + m.b + m.c
            """);
        assertEquals(6.0, r1.numberValue());
        assertEquals(r1.numberValue(), r2.numberValue());
    }

    //  12. 错误处理交叉验证

    @Test
    void testTryCatchTwoWays() throws AriaException {
        // Way 1: catch 带变量
        IValue<?> r1 = eval("""
            var.result = 'none'
            try {
                throw 'error'
            } catch (e) {
                var.result = e
            }
            return result
            """);
        // Way 2: catch 不带变量
        IValue<?> r2 = eval("""
            var.result = 'none'
            try {
                throw 'error'
            } catch {
                var.result = 'caught'
            }
            return result
            """);
        assertEquals("error", r1.stringValue());
        assertEquals("caught", r2.stringValue());
    }
}
