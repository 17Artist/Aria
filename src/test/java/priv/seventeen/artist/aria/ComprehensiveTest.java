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
import priv.seventeen.artist.aria.value.IValue;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveTest {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    private IValue<?> eval(String code) throws AriaException {
        Context ctx = Aria.createContext();
        return Aria.eval(code, ctx);
    }

    //  1. 字符串插值

    @Test
    void testStringInterpolationVariable() throws AriaException {
        IValue<?> result = eval("""
            var.name = 'World'
            return "hello {name}"
            """);
        assertEquals("hello World", result.stringValue());
    }

    @Test
    void testStringInterpolationExpression() throws AriaException {
        IValue<?> result = eval("""
            return "value is {1 + 2}"
            """);
        assertEquals("value is 3", result.stringValue());
    }

    //  2. Switch 穿透

    @Test
    void testSwitchFallthrough() throws AriaException {
        IValue<?> result = eval("""
            var.x = 1
            var.result = 0
            switch (x) {
                case 1 {
                    var.result = var.result + 10
                }
                case 2 {
                    var.result = var.result + 20
                }
            }
            return var.result
            """);
        // switch 穿透：匹配 case 1 后继续执行 case 2
        assertEquals(30.0, result.numberValue());
    }

    @Test
    void testSwitchElse() throws AriaException {
        IValue<?> result = eval("""
            var.x = 99
            var.result = 0
            switch (x) {
                case 1 {
                    var.result = 10
                }
                else {
                    var.result = -1
                }
            }
            return var.result
            """);
        assertEquals(-1.0, result.numberValue());
    }

    //  3. Match 不穿透

    @Test
    void testMatchNoFallthrough() throws AriaException {
        IValue<?> result = eval("""
            var.x = 1
            var.result = 0
            match (x) {
                case 1 {
                    var.result = 10
                }
                case 2 {
                    var.result = 20
                }
            }
            return var.result
            """);
        // match 不穿透：只执行 case 1
        assertEquals(10.0, result.numberValue());
    }

    @Test
    void testMatchElse() throws AriaException {
        IValue<?> result = eval("""
            var.x = 5
            var.result = 0
            match (x) {
                case 1 {
                    var.result = 10
                }
                case 2 {
                    var.result = 20
                }
                else {
                    var.result = 99
                }
            }
            return var.result
            """);
        assertEquals(99.0, result.numberValue());
    }

    //  4. For-in Range

    @Test
    void testForInRange() throws AriaException {
        IValue<?> result = eval("""
            var.sum = 0
            for (i in Range(1, 6)) {
                var.sum += i
            }
            return var.sum
            """);
        // 1+2+3+4+5 = 15
        assertEquals(15.0, result.numberValue());
    }

    //  5. For-in List

    @Test
    void testForInList() throws AriaException {
        IValue<?> result = eval("""
            val.items = [10, 20, 30]
            var.sum = 0
            for (item in items) {
                var.sum += item
            }
            return var.sum
            """);
        assertEquals(60.0, result.numberValue());
    }

    //  6. C-style for 循环

    @Test
    void testCStyleForLoop() throws AriaException {
        IValue<?> result = eval("""
            var.sum = 0
            for (var.i = 0; var.i < 5; var.i += 1) {
                var.sum += var.i
            }
            return var.sum
            """);
        // 0+1+2+3+4 = 10
        assertEquals(10.0, result.numberValue());
    }

    //  7. While + break / next

    @Test
    void testWhileWithBreak() throws AriaException {
        IValue<?> result = eval("""
            var.i = 0
            while (true) {
                if (var.i >= 5) {
                    break
                }
                var.i += 1
            }
            return var.i
            """);
        assertEquals(5.0, result.numberValue());
    }

    @Test
    void testWhileWithNext() throws AriaException {
        IValue<?> result = eval("""
            var.sum = 0
            var.i = 0
            while (var.i < 10) {
                var.i += 1
                if (var.i % 2 == 0) {
                    next
                }
                var.sum += var.i
            }
            return var.sum
            """);
        // 奇数之和: 1+3+5+7+9 = 25
        assertEquals(25.0, result.numberValue());
    }

    //  8. 嵌套 if / elif / else

    @Test
    void testNestedIfElifElse() throws AriaException {
        IValue<?> result = eval("""
            var.x = 15
            if (var.x < 10) {
                var.result = 'small'
            } elif (var.x < 20) {
                var.result = 'medium'
            } else {
                var.result = 'large'
            }
            return var.result
            """);
        assertEquals("medium", result.stringValue());
    }

    @Test
    void testNestedIfElseChain() throws AriaException {
        IValue<?> result = eval("""
            var.x = 100
            if (var.x < 10) {
                var.result = 'small'
            } elif (var.x < 20) {
                var.result = 'medium'
            } else {
                var.result = 'large'
            }
            return var.result
            """);
        assertEquals("large", result.stringValue());
    }

    //  9. ~= init-or-get 运算符

    @Test
    void testInitOrGetOperator() throws AriaException {
        IValue<?> result = eval("""
            var.x ~= 42
            var.x ~= 99
            return var.x
            """);
        // 第一次 ~= 初始化为 42，第二次 x 已有值，保持 42
        assertEquals(42.0, result.numberValue());
    }

    //  10. ~~ 范围检查运算符

    @Test
    void testRangeCheckOperator() throws AriaException {
        IValue<?> result = eval("""
            var.x = 5
            return var.x ~~ [1, 10]
            """);
        assertTrue(result.booleanValue());
    }

    @Test
    void testRangeCheckOutOfRange() throws AriaException {
        IValue<?> result = eval("""
            var.x = 15
            return var.x ~~ [1, 10]
            """);
        assertFalse(result.booleanValue());
    }

    //  11. 三元运算符

    @Test
    void testTernaryFullForm() throws AriaException {
        IValue<?> result = eval("""
            var.x = 10
            return var.x > 5 ? 'big' : 'small'
            """);
        assertEquals("big", result.stringValue());
    }

    @Test
    void testTernaryElvisForm() throws AriaException {
        // a ?: c — 如果 a 为 truthy 返回 a，否则返回 c
        IValue<?> result = eval("""
            var.x = none
            return var.x ?: 'default'
            """);
        assertEquals("default", result.stringValue());
    }

    @Test
    void testTernaryTrueOnlyForm() throws AriaException {
        // a ? b : — 如果 a 为 true 返回 b，否则返回 none
        IValue<?> result = eval("""
            var.x = true
            return (var.x ? 'yes' :)
            """);
        assertEquals("yes", result.stringValue());
    }

    //  12. 空值合并 ??

    @Test
    void testNullishCoalescing() throws AriaException {
        IValue<?> result = eval("""
            var.x = none
            return var.x ?? 'fallback'
            """);
        assertEquals("fallback", result.stringValue());
    }

    @Test
    void testNullishCoalescingWithValue() throws AriaException {
        IValue<?> result = eval("""
            var.x = 42
            return var.x ?? 'fallback'
            """);
        assertEquals(42.0, result.numberValue());
    }

    //  13. 位运算

    @Test
    void testBitAnd() throws AriaException {
        IValue<?> result = eval("return 12 & 10\n");
        assertEquals(8.0, result.numberValue()); // 1100 & 1010 = 1000 = 8
    }

    @Test
    void testBitOr() throws AriaException {
        IValue<?> result = eval("return 12 | 10\n");
        assertEquals(14.0, result.numberValue()); // 1100 | 1010 = 1110 = 14
    }

    @Test
    void testBitXor() throws AriaException {
        IValue<?> result = eval("return 12 ^ 10\n");
        assertEquals(6.0, result.numberValue()); // 1100 ^ 1010 = 0110 = 6
    }

    @Test
    void testBitNot() throws AriaException {
        IValue<?> result = eval("return ~0\n");
        assertEquals(-1.0, result.numberValue());
    }

    @Test
    void testBitShiftLeft() throws AriaException {
        IValue<?> result = eval("return 1 << 4\n");
        assertEquals(16.0, result.numberValue());
    }

    @Test
    void testBitShiftRight() throws AriaException {
        IValue<?> result = eval("return 16 >> 2\n");
        assertEquals(4.0, result.numberValue());
    }

    //  14. Async 块（验证不崩溃）

    @Test
    void testAsyncBlockDoesNotCrash() throws AriaException {
        // async 块提交到线程池，主线程不等待结果
        IValue<?> result = eval("""
            var.x = 1
            async {
                var.y = 2
            }
            return var.x
            """);
        assertEquals(1.0, result.numberValue());
    }

    //  15. Class 多方法

    @Test
    void testClassMultipleMethods() throws AriaException {
        IValue<?> result = eval("""
            class Calc {
                var.value = 0
                new = -> {
                    self.value = args[0]
                }
                add = -> {
                    self.value = self.value + args[0]
                }
                mul = -> {
                    self.value = self.value * args[0]
                }
                getValue = -> {
                    return self.value
                }
            }
            val.c = Calc(10)
            c.add(5)
            c.mul(3)
            return c.getValue()
            """);
        // (10 + 5) * 3 = 45
        assertEquals(45.0, result.numberValue());
    }

    //  16. use 和静态字段访问

    @Test
    void testJavaTypeStaticField() throws AriaException {
        IValue<?> result = eval("""
            val.Integer = use('java.lang.Integer')
            return Integer.MAX_VALUE
            """);
        assertEquals((double) Integer.MAX_VALUE, result.numberValue());
    }

    @Test
    void testJavaTypeStaticMethod() throws AriaException {
        IValue<?> result = eval("""
            val.Math = use('java.lang.Math')
            return Math.max(10, 20)
            """);
        assertEquals(20.0, result.numberValue());
    }

    //  17. List 字面量 + 操作

    @Test
    void testListLiteralAndOperations() throws AriaException {
        IValue<?> result = eval("""
            val.list = [10, 20, 30]
            list.add(40)
            return list.size()
            """);
        assertEquals(4.0, result.numberValue());
    }

    @Test
    void testListConcatenation() throws AriaException {
        IValue<?> result = eval("""
            val.a = [1, 2]
            val.b = [3, 4]
            val.c = a + b
            return c.size()
            """);
        assertEquals(4.0, result.numberValue());
    }

    @Test
    void testListIndexAccess() throws AriaException {
        IValue<?> result = eval("""
            val.list = ['a', 'b', 'c']
            return list[1]
            """);
        assertEquals("b", result.stringValue());
    }

    //  18. Map 字面量 + 操作

    @Test
    void testMapLiteralAndOperations() throws AriaException {
        IValue<?> result = eval("""
            val.m = {'name': 'Alice', 'age': 30}
            return m.name
            """);
        assertEquals("Alice", result.stringValue());
    }

    @Test
    void testMapPutAndGet() throws AriaException {
        IValue<?> result = eval("""
            val.m = {}
            m.put('key', 'value')
            return m.get('key')
            """);
        assertEquals("value", result.stringValue());
    }

    @Test
    void testMapKeys() throws AriaException {
        IValue<?> result = eval("""
            val.m = {'a': 1, 'b': 2, 'c': 3}
            return m.keys().size()
            """);
        assertEquals(3.0, result.numberValue());
    }

    @Test
    void testMapContainsKey() throws AriaException {
        IValue<?> result = eval("""
            val.m = {'x': 10}
            return m.containsKey('x')
            """);
        assertTrue(result.booleanValue());
    }

    //  19. Number 方法

    @Test
    void testNumberToInt() throws AriaException {
        IValue<?> result = eval("""
            val.x = 3.7
            return x.toInt()
            """);
        assertEquals(3.0, result.numberValue());
    }

    @Test
    void testNumberToFixed() throws AriaException {
        IValue<?> result = eval("""
            val.x = 3.14159
            return x.toFixed(2)
            """);
        assertEquals("3.14", result.stringValue());
    }

    @Test
    void testNumberIsNaN() throws AriaException {
        IValue<?> result = eval("""
            val.x = 0 / 0
            return x.isNaN()
            """);
        assertTrue(result.booleanValue());
    }

    //  20. String 方法

    @Test
    void testStringLength() throws AriaException {
        IValue<?> result = eval("""
            return 'hello'.length()
            """);
        assertEquals(5.0, result.numberValue());
    }

    @Test
    void testStringSubstring() throws AriaException {
        IValue<?> result = eval("""
            return 'hello world'.substring(0, 5)
            """);
        assertEquals("hello", result.stringValue());
    }

    @Test
    void testStringReplace() throws AriaException {
        IValue<?> result = eval("""
            return 'hello world'.replace('world', 'Aria')
            """);
        assertEquals("hello Aria", result.stringValue());
    }

    @Test
    void testStringSplit() throws AriaException {
        IValue<?> result = eval("""
            val.parts = 'a,b,c'.split(',')
            return parts.size()
            """);
        assertEquals(3.0, result.numberValue());
    }

    @Test
    void testStringTrim() throws AriaException {
        IValue<?> result = eval("""
            return '  hello  '.trim()
            """);
        assertEquals("hello", result.stringValue());
    }

    @Test
    void testStringIndexOf() throws AriaException {
        IValue<?> result = eval("""
            return 'hello world'.indexOf('world')
            """);
        assertEquals(6.0, result.numberValue());
    }

    //  21. 类型检查

    @Test
    void testTypeOfNumber() throws AriaException {
        IValue<?> result = eval("return type.typeof(42)\n");
        assertEquals("number", result.stringValue());
    }

    @Test
    void testTypeOfString() throws AriaException {
        IValue<?> result = eval("return type.typeof('hello')\n");
        assertEquals("string", result.stringValue());
    }

    @Test
    void testTypeIsNumber() throws AriaException {
        IValue<?> result = eval("return type.isNumber(3.14)\n");
        assertTrue(result.booleanValue());
    }

    @Test
    void testTypeIsList() throws AriaException {
        IValue<?> result = eval("return type.isList([1, 2, 3])\n");
        assertTrue(result.booleanValue());
    }

    @Test
    void testTypeIsMap() throws AriaException {
        IValue<?> result = eval("return type.isMap({'a': 1})\n");
        assertTrue(result.booleanValue());
    }

    @Test
    void testTypeIsFunction() throws AriaException {
        IValue<?> result = eval("""
            var.f = -> { return 1 }
            return type.isFunction(f)
            """);
        assertTrue(result.booleanValue());
    }

    //  22. Math 函数

    @Test
    void testMathSinCos() throws AriaException {
        IValue<?> result = eval("return math.sin(0)\n");
        assertEquals(0.0, result.numberValue(), 0.0001);

        IValue<?> result2 = eval("return math.cos(0)\n");
        assertEquals(1.0, result2.numberValue(), 0.0001);
    }

    @Test
    void testMathAbs() throws AriaException {
        IValue<?> result = eval("return math.abs(-42)\n");
        assertEquals(42.0, result.numberValue());
    }

    @Test
    void testMathFloorCeil() throws AriaException {
        IValue<?> floor = eval("return math.floor(3.7)\n");
        assertEquals(3.0, floor.numberValue());

        IValue<?> ceil = eval("return math.ceil(3.2)\n");
        assertEquals(4.0, ceil.numberValue());
    }

    @Test
    void testMathSqrt() throws AriaException {
        IValue<?> result = eval("return math.sqrt(144)\n");
        assertEquals(12.0, result.numberValue());
    }

    @Test
    void testMathPow() throws AriaException {
        IValue<?> result = eval("return math.pow(2, 10)\n");
        assertEquals(1024.0, result.numberValue());
    }

    //  23. 链式操作

    @Test
    void testChainedFilterMapJoin() throws AriaException {
        IValue<?> result = eval("""
            val.list = [1, 2, 3, 4, 5, 6]
            val.r = list.filter(-> { return args[0] % 2 == 0 }).map(-> { return args[0] * 10 }).join(',')
            return r
            """);
        assertEquals("20,40,60", result.stringValue());
    }

    //  24. Try-catch-finally

    @Test
    void testTryCatchFinally() throws AriaException {
        IValue<?> result = eval("""
            var.result = ''
            try {
                throw 'boom'
            } catch (e) {
                var.result = e
            }
            return var.result
            """);
        assertEquals("boom", result.stringValue());
    }

    @Test
    void testTryCatchNoError() throws AriaException {
        IValue<?> result = eval("""
            var.result = 'ok'
            try {
                var.result = 'success'
            } catch (e) {
                var.result = 'error'
            }
            return var.result
            """);
        assertEquals("success", result.stringValue());
    }

    //  25. Throw 自定义错误

    @Test
    void testThrowCustomError() throws AriaException {
        IValue<?> result = eval("""
            var.msg = 'none'
            try {
                throw 'custom error: 404'
            } catch (e) {
                var.msg = e
            }
            return var.msg
            """);
        assertEquals("custom error: 404", result.stringValue());
    }

    @Test
    void testThrowUncaught() {
        assertThrows(Exception.class, () -> eval("""
            throw 'uncaught error'
            """));
    }

    //  额外边界测试

    @Test
    void testNestedForLoops() throws AriaException {
        IValue<?> result = eval("""
            var.sum = 0
            var.i = 1
            while (i <= 3) {
                var.j = 1
                while (j <= 3) {
                    var.sum += i * j
                    var.j += 1
                }
                var.i += 1
            }
            return var.sum
            """);
        // (1+2+3) * (1+2+3) = 36
        assertEquals(36.0, result.numberValue());
    }

    @Test
    void testMapValuesMethod() throws AriaException {
        IValue<?> result = eval("""
            val.m = {'a': 1, 'b': 2}
            val.v = m.values()
            return v.size()
            """);
        assertEquals(2.0, result.numberValue());
    }

    @Test
    void testListReverse() throws AriaException {
        IValue<?> result = eval("""
            val.list = [1, 2, 3]
            list.reverse()
            return list.get(0)
            """);
        assertEquals(3.0, result.numberValue());
    }

    @Test
    void testStringToUpperLower() throws AriaException {
        IValue<?> upper = eval("return 'hello'.toUpperCase()\n");
        assertEquals("HELLO", upper.stringValue());

        IValue<?> lower = eval("return 'HELLO'.toLowerCase()\n");
        assertEquals("hello", lower.stringValue());
    }

    @Test
    void testTypeConversion() throws AriaException {
        IValue<?> result = eval("return type.toNumber('42')\n");
        assertEquals(42.0, result.numberValue());
    }
}
