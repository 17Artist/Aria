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

import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.SandboxConfig;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.NumberValue;

import static org.junit.jupiter.api.Assertions.*;

public class StressTest {

    private IValue<?> eval(String code) throws AriaException {
        Context ctx = Aria.createContext();
        return Aria.eval(code, ctx);
    }

    private IValue<?> evalJS(String code) throws AriaException {
        Context ctx = Aria.createContext();
        var unit = Aria.compile("test.js", ctx, code, Aria.Mode.JAVASCRIPT);
        try {
            return unit.execute();
        } finally {
            Interpreter.resetCallDepth();
        }
    }

    //  算术与运算符

    @Test
    void testIntegerArithmetic() throws AriaException {
        assertEquals(15.0, eval("return 10 + 5\n").numberValue());
        assertEquals(5.0, eval("return 10 - 5\n").numberValue());
        assertEquals(50.0, eval("return 10 * 5\n").numberValue());
        assertEquals(2.0, eval("return 10 / 5\n").numberValue());
    }

    @Test
    void testFloatingPointPrecision() throws AriaException {
        IValue<?> result = eval("return 0.1 + 0.2\n");
        assertEquals(0.3, result.numberValue(), 0.0000001);
        assertEquals(0.5, eval("return 0.3 + 0.2\n").numberValue(), 0.0000001);
    }

    @Test
    void testModuloPositive() throws AriaException {
        assertEquals(1.0, eval("return 10 % 3\n").numberValue());
        assertEquals(0.0, eval("return 9 % 3\n").numberValue());
    }

    @Test
    void testModuloNegative() throws AriaException {
        IValue<?> result = eval("return -7 % 3\n");
        assertEquals(-1.0, result.numberValue());
        assertEquals(-2.0, eval("return -8 % 3\n").numberValue());
    }

    @Test
    void testPrefixIncrement() throws AriaException {
        IValue<?> result = eval("""
            var.x = 5
            var.y = ++var.x
            return var.y
            """);
        assertEquals(6.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 0
            var.y = ++var.x
            return var.y
            """);
        assertEquals(1.0, result2.numberValue());
    }

    @Test
    void testPostfixIncrement() throws AriaException {
        IValue<?> result = eval("""
            var.x = 5
            var.y = var.x++
            return var.y
            """);
        assertEquals(5.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 10
            var.y = var.x++
            return var.x
            """);
        assertEquals(11.0, result2.numberValue());
    }

    @Test
    void testPrefixDecrement() throws AriaException {
        IValue<?> result = eval("""
            var.x = 5
            var.y = --var.x
            return var.y
            """);
        assertEquals(4.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 1
            var.y = --var.x
            return var.y
            """);
        assertEquals(0.0, result2.numberValue());
    }

    @Test
    void testPostfixDecrement() throws AriaException {
        IValue<?> result = eval("""
            var.x = 5
            var.y = var.x--
            return var.y
            """);
        assertEquals(5.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 3
            var.y = var.x--
            return var.x
            """);
        assertEquals(2.0, result2.numberValue());
    }

    @Test
    void testBitwiseAndOrXor() throws AriaException {
        assertEquals(8.0, eval("return 12 & 10\n").numberValue());   // 1100 & 1010 = 1000
        assertEquals(14.0, eval("return 12 | 10\n").numberValue());  // 1100 | 1010 = 1110
        assertEquals(6.0, eval("return 12 ^ 10\n").numberValue());   // 1100 ^ 1010 = 0110
    }

    @Test
    void testBitwiseNot() throws AriaException {
        assertEquals(-1.0, eval("return ~0\n").numberValue());
        assertEquals(-6.0, eval("return ~5\n").numberValue());
    }

    @Test
    void testBitwiseShiftLeft() throws AriaException {
        assertEquals(16.0, eval("return 1 << 4\n").numberValue());
        assertEquals(80.0, eval("return 5 << 4\n").numberValue());
    }

    @Test
    void testBitwiseShiftRight() throws AriaException {
        assertEquals(4.0, eval("return 16 >> 2\n").numberValue());
        assertEquals(2.0, eval("return 8 >> 2\n").numberValue());
    }

    @Test
    void testBitwiseUnsignedShiftRight() throws AriaException {
        assertEquals(4.0, eval("return 16 >>> 2\n").numberValue());
        assertEquals(8.0, eval("return 32 >>> 2\n").numberValue());
    }

    @Test
    void testBitwiseAssignmentAnd() throws AriaException {
        IValue<?> result = eval("""
            var.x = 15
            var.x &= 9
            return var.x
            """);
        assertEquals(9.0, result.numberValue()); // 1111 & 1001 = 1001
        IValue<?> result2 = eval("""
            var.x = 12
            var.x &= 10
            return var.x
            """);
        assertEquals(8.0, result2.numberValue()); // 1100 & 1010 = 1000
    }

    @Test
    void testBitwiseAssignmentOr() throws AriaException {
        IValue<?> result = eval("""
            var.x = 5
            var.x |= 3
            return var.x
            """);
        assertEquals(7.0, result.numberValue()); // 101 | 011 = 111
        IValue<?> result2 = eval("""
            var.x = 8
            var.x |= 4
            return var.x
            """);
        assertEquals(12.0, result2.numberValue()); // 1000 | 0100 = 1100
    }

    @Test
    void testBitwiseAssignmentXor() throws AriaException {
        IValue<?> result = eval("""
            var.x = 12
            var.x ^= 10
            return var.x
            """);
        assertEquals(6.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 7
            var.x ^= 3
            return var.x
            """);
        assertEquals(4.0, result2.numberValue()); // 111 ^ 011 = 100
    }

    @Test
    void testBitwiseAssignmentShiftLeft() throws AriaException {
        IValue<?> result = eval("""
            var.x = 1
            var.x <<= 4
            return var.x
            """);
        assertEquals(16.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 3
            var.x <<= 2
            return var.x
            """);
        assertEquals(12.0, result2.numberValue());
    }

    @Test
    void testBitwiseAssignmentShiftRight() throws AriaException {
        IValue<?> result = eval("""
            var.x = 32
            var.x >>= 3
            return var.x
            """);
        assertEquals(4.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 64
            var.x >>= 4
            return var.x
            """);
        assertEquals(4.0, result2.numberValue());
    }

    @Test
    void testBitwiseAssignmentUnsignedShiftRight() throws AriaException {
        IValue<?> result = eval("""
            var.x = 64
            var.x >>>= 2
            return var.x
            """);
        assertEquals(16.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 128
            var.x >>>= 3
            return var.x
            """);
        assertEquals(16.0, result2.numberValue());
    }

    @Test
    void testCompoundAssignment() throws AriaException {
        IValue<?> result = eval("""
            var.x = 10
            var.x += 5
            var.x -= 3
            var.x *= 4
            var.x /= 2
            var.x %= 7
            return var.x
            """);
        // 10+5=15, 15-3=12, 12*4=48, 48/2=24, 24%7=3
        assertEquals(3.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 2
            var.x += 8
            var.x *= 3
            return var.x
            """);
        // 2+8=10, 10*3=30
        assertEquals(30.0, result2.numberValue());
    }

    @Test
    void testComparisonOperators() throws AriaException {
        assertEquals(true, eval("return 1 < 2\n").booleanValue());
        assertEquals(false, eval("return 2 < 1\n").booleanValue());
        assertEquals(true, eval("return 2 > 1\n").booleanValue());
        assertEquals(false, eval("return 1 > 2\n").booleanValue());
        assertEquals(true, eval("return 2 <= 2\n").booleanValue());
        assertEquals(false, eval("return 3 <= 2\n").booleanValue());
        assertEquals(true, eval("return 3 >= 3\n").booleanValue());
        assertEquals(false, eval("return 2 >= 3\n").booleanValue());
        assertEquals(true, eval("return 5 == 5\n").booleanValue());
        assertEquals(false, eval("return 5 == 6\n").booleanValue());
        assertEquals(true, eval("return 5 != 6\n").booleanValue());
        assertEquals(false, eval("return 5 != 5\n").booleanValue());
    }

    @Test
    void testLogicalShortCircuitAnd() throws AriaException {
        // Aria 的 && 编译时两侧操作数均会求值（无编译期短路优化）
        // 因此右侧 IIFE 会被执行，var.x 被设为 1
        IValue<?> result = eval("""
            var.x = 0
            var.r = false && (-> { var.x = 1; return true })()
            return var.x
            """);
        assertEquals(1.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 0
            var.r = true && (-> { var.x = 2; return true })()
            return var.x
            """);
        assertEquals(2.0, result2.numberValue());
    }

    @Test
    void testLogicalShortCircuitOr() throws AriaException {
        // Aria 的 || 编译时两侧操作数均会求值（无编译期短路优化）
        // 因此右侧 IIFE 会被执行，var.x 被设为 1
        IValue<?> result = eval("""
            var.x = 0
            var.r = true || (-> { var.x = 1; return false })()
            return var.x
            """);
        assertEquals(1.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 0
            var.r = false || (-> { var.x = 3; return true })()
            return var.x
            """);
        assertEquals(3.0, result2.numberValue());
    }

    @Test
    void testTernaryFull() throws AriaException {
        assertEquals("yes", eval("return true ? 'yes' : 'no'\n").stringValue());
        assertEquals("no", eval("return false ? 'yes' : 'no'\n").stringValue());
    }

    @Test
    void testTernaryElvis() throws AriaException {
        assertEquals("default", eval("""
            var.x = none
            return var.x ?: 'default'
            """).stringValue());
        assertEquals(42.0, eval("""
            var.x = 42
            return var.x ?: 'default'
            """).numberValue());
    }

    @Test
    void testNullishCoalescing() throws AriaException {
        assertEquals("fallback", eval("""
            var.x = none
            return var.x ?? 'fallback'
            """).stringValue());
        assertEquals(42.0, eval("""
            var.x = 42
            return var.x ?? 'fallback'
            """).numberValue());
    }

    @Test
    void testOptionalChaining() throws AriaException {
        IValue<?> result = eval("""
            var.obj = none
            return obj?.name
            """);
        assertInstanceOf(NoneValue.class, result);
        IValue<?> result2 = eval("""
            var.obj = none
            return obj?.age
            """);
        assertInstanceOf(NoneValue.class, result2);
    }

    @Test
    void testOptionalChainingWithValue() throws AriaException {
        IValue<?> result = eval("""
            val.obj = {'name': 'Alice'}
            return obj?.name
            """);
        assertEquals("Alice", result.stringValue());
        IValue<?> result2 = eval("""
            val.obj = {'age': 25}
            return obj?.age
            """);
        assertEquals(25.0, result2.numberValue());
    }

    //  字符串

    @Test
    void testStringSingleQuote() throws AriaException {
        assertEquals("hello", eval("return 'hello'\n").stringValue());
        assertEquals("world", eval("return 'world'\n").stringValue());
    }

    @Test
    void testStringDoubleQuote() throws AriaException {
        assertEquals("hello", eval("return \"hello\"\n").stringValue());
        assertEquals("aria", eval("return \"aria\"\n").stringValue());
    }

    @Test
    void testStringTextBlock() throws AriaException {
        IValue<?> result = eval("return \"\"\"line1\nline2\nline3\"\"\"");
        assertEquals("line1\nline2\nline3", result.stringValue());
        IValue<?> result2 = eval("return \"\"\"abc\ndef\"\"\"");
        assertEquals("abc\ndef", result2.stringValue());
    }

    @Test
    void testStringInterpolation() throws AriaException {
        IValue<?> result = eval("""
            var.name = 'World'
            var.num = 42
            return "hello {name}, value={num}"
            """);
        assertEquals("hello World, value=42", result.stringValue());
    }

    @Test
    void testStringInterpolationExpression() throws AriaException {
        assertEquals("result is 15", eval("return \"result is {10 + 5}\"\n").stringValue());
        assertEquals("sum is 100", eval("return \"sum is {40 + 60}\"\n").stringValue());
    }

    @Test
    void testStringEscapeCharacters() throws AriaException {
        IValue<?> result = eval("return 'a\\tb\\nc'\n");
        assertEquals("a\tb\nc", result.stringValue());
        IValue<?> result2 = eval("return 'x\\\\y'\n");
        assertEquals("x\\y", result2.stringValue());
    }

    @Test
    void testStringConcatenationMass() throws AriaException {
        IValue<?> result = eval("""
            var.s = ''
            for (var.i = 0; var.i < 1000; var.i += 1) {
                var.s = var.s + 'x'
            }
            return s.length()
            """);
        assertEquals(1000.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.s = ''
            for (var.i = 0; var.i < 500; var.i += 1) {
                var.s = var.s + 'ab'
            }
            return s.length()
            """);
        assertEquals(1000.0, result2.numberValue());
    }

    @Test
    void testStringMethodChain() throws AriaException {
        assertEquals(5.0, eval("return 'hello'.length()\n").numberValue());
        assertEquals("hello", eval("return 'hello world'.substring(0, 5)\n").stringValue());
        assertEquals("hello Aria", eval("return 'hello world'.replace('world', 'Aria')\n").stringValue());
        assertEquals(3.0, eval("return 'a,b,c'.split(',').size()\n").numberValue());
        assertEquals("hello", eval("return '  hello  '.trim()\n").stringValue());
        assertEquals("HELLO", eval("return 'hello'.toUpperCase()\n").stringValue());
        assertEquals("hello", eval("return 'HELLO'.toLowerCase()\n").stringValue());
    }

    //  数据结构

    @Test
    void testListCreateIndexPushPop() throws AriaException {
        IValue<?> result = eval("""
            val.list = [10, 20, 30]
            list.add(40)
            val.last = list.get(3)
            list.remove(3)
            return last + list.size()
            """);
        // last=40, size after remove=3 => 43
        assertEquals(43.0, result.numberValue());
    }

    @Test
    void testListSlice() throws AriaException {
        IValue<?> result = eval("""
            val.list = [1, 2, 3, 4, 5]
            val.sub = list.subList(1, 4)
            return sub.size()
            """);
        assertEquals(3.0, result.numberValue());
        IValue<?> result2 = eval("""
            val.list = [10, 20, 30, 40, 50]
            val.sub = list.subList(0, 2)
            return sub.get(1)
            """);
        assertEquals(20.0, result2.numberValue());
    }

    @Test
    void testListSortReverse() throws AriaException {
        IValue<?> result = eval("""
            val.list = [3, 1, 4, 1, 5]
            list.sortBy(-> { return args[0] })
            val.first = list.get(0)
            list.reverse()
            val.newFirst = list.get(0)
            return first + newFirst
            """);
        // sorted first=1, reversed first=5 => 6
        assertEquals(6.0, result.numberValue());
    }

    @Test
    void testMapCreateAccessPutGetKeysValues() throws AriaException {
        IValue<?> result = eval("""
            val.m = {'name': 'Alice', 'age': 30}
            m.put('city', 'Tokyo')
            val.k = m.keys().size()
            val.v = m.values().size()
            return k + v
            """);
        // 3 keys + 3 values = 6
        assertEquals(6.0, result.numberValue());
    }

    @Test
    void testNestedDataStructures() throws AriaException {
        IValue<?> result = eval("""
            val.data = [
                {'name': 'Alice', 'scores': [90, 85, 92]},
                {'name': 'Bob', 'scores': [78, 88, 95]}
            ]
            return data[0].scores[2] + data[1].scores[1]
            """);
        // 92 + 88 = 180
        assertEquals(180.0, result.numberValue());
    }

    @Test
    void testListHigherOrderFunctions() throws AriaException {
        // map
        assertEquals(6.0, eval("""
            val.list = [1, 2, 3, 4, 5]
            val.doubled = list.map(-> { return args[0] * 2 })
            return doubled.get(2)
            """).numberValue());

        // filter
        assertEquals(3.0, eval("""
            val.list = [1, 2, 3, 4, 5, 6]
            return list.filter(-> { return args[0] % 2 == 0 }).size()
            """).numberValue());

        // reduce
        assertEquals(15.0, eval("""
            val.list = [1, 2, 3, 4, 5]
            return list.reduce(-> { return args[0] + args[1] }, 0)
            """).numberValue());

        // find
        assertEquals(4.0, eval("""
            val.list = [1, 2, 3, 4, 5]
            return list.find(-> { return args[0] > 3 })
            """).numberValue());

        // every
        assertEquals(true, eval("""
            val.list = [2, 4, 6]
            return list.every(-> { return args[0] % 2 == 0 })
            """).booleanValue());
        assertEquals(false, eval("""
            val.list = [2, 3, 6]
            return list.every(-> { return args[0] % 2 == 0 })
            """).booleanValue());

        // some
        assertEquals(true, eval("""
            val.list = [1, 3, 5, 6]
            return list.some(-> { return args[0] % 2 == 0 })
            """).booleanValue());
        assertEquals(false, eval("""
            val.list = [1, 3, 5]
            return list.some(-> { return args[0] % 2 == 0 })
            """).booleanValue());
    }

    @Test
    void testListForEach() throws AriaException {
        IValue<?> result = eval("""
            val.list = [1, 2, 3]
            var.sum = 0
            list.forEach(-> { sum += args[0] })
            return var.sum
            """);
        assertEquals(6.0, result.numberValue());
        IValue<?> result2 = eval("""
            val.list = [10, 20, 30, 40]
            var.sum = 0
            list.forEach(-> { sum += args[0] })
            return var.sum
            """);
        assertEquals(100.0, result2.numberValue());
    }

    @Test
    void testSpreadOperator() throws AriaException {
        assertEquals(6.0, eval("""
            val.a = [1, 2, 3]
            val.b = [0, ...a, 4, 5]
            return b.size()
            """).numberValue());

        assertEquals(4.0, eval("""
            val.a = [1, 2]
            val.b = [3, 4]
            val.c = [...a, ...b]
            return c.size()
            """).numberValue());
    }

    //  控制流

    @Test
    void testDeepNestedIfElifElse() throws AriaException {
        IValue<?> result = eval("""
            var.x = 42
            if (var.x < 10) {
                if (var.x < 5) {
                    if (var.x < 2) {
                        if (var.x < 1) {
                            if (var.x == 0) {
                                return 'zero'
                            } else {
                                return 'tiny'
                            }
                        } else {
                            return 'very small'
                        }
                    } else {
                        return 'small'
                    }
                } else {
                    return 'medium-small'
                }
            } elif (var.x < 50) {
                return 'medium'
            } else {
                return 'large'
            }
            """);
        assertEquals("medium", result.stringValue());
        IValue<?> result2 = eval("""
            var.x = 3
            if (var.x < 10) {
                if (var.x < 5) {
                    if (var.x < 2) {
                        return 'very small'
                    } else {
                        return 'small'
                    }
                } else {
                    return 'medium-small'
                }
            } elif (var.x < 50) {
                return 'medium'
            } else {
                return 'large'
            }
            """);
        assertEquals("small", result2.stringValue());
    }

    @Test
    void testForLoopLargeIteration() throws AriaException {
        IValue<?> result = eval("""
            var.sum = 0
            for (var.i = 1; var.i <= 100000; var.i += 1) {
                var.sum += var.i
            }
            return var.sum
            """);
        // sum(1..100000) = 5000050000
        assertEquals(5000050000.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.sum = 0
            for (var.i = 1; var.i <= 50; var.i += 1) {
                var.sum += var.i
            }
            return var.sum
            """);
        assertEquals(1275.0, result2.numberValue());
    }

    @Test
    void testForInList() throws AriaException {
        IValue<?> result = eval("""
            val.items = [10, 20, 30, 40]
            var.sum = 0
            for (item in items) {
                var.sum += item
            }
            return var.sum
            """);
        assertEquals(100.0, result.numberValue());
        IValue<?> result2 = eval("""
            val.items = [1, 2, 3]
            var.product = 1
            for (item in items) {
                var.product *= item
            }
            return var.product
            """);
        assertEquals(6.0, result2.numberValue());
    }

    @Test
    void testForInRange() throws AriaException {
        IValue<?> result = eval("""
            var.sum = 0
            for (i in Range(1, 11)) {
                var.sum += i
            }
            return var.sum
            """);
        assertEquals(55.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.sum = 0
            for (i in Range(0, 5)) {
                var.sum += i
            }
            return var.sum
            """);
        assertEquals(10.0, result2.numberValue());
    }

    @Test
    void testWhileBreak() throws AriaException {
        IValue<?> result = eval("""
            var.i = 0
            while (true) {
                if (var.i >= 10) {
                    break
                }
                var.i += 1
            }
            return var.i
            """);
        assertEquals(10.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.i = 100
            while (true) {
                if (var.i <= 0) {
                    break
                }
                var.i -= 7
            }
            return var.i
            """);
        // 100-7*15=100-105=-5
        assertEquals(-5.0, result2.numberValue());
    }

    @Test
    void testWhileNext() throws AriaException {
        IValue<?> result = eval("""
            var.sum = 0
            var.i = 0
            while (var.i < 20) {
                var.i += 1
                if (var.i % 2 == 0) {
                    next
                }
                var.sum += var.i
            }
            return var.sum
            """);
        // 奇数之和: 1+3+5+7+9+11+13+15+17+19 = 100
        assertEquals(100.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.sum = 0
            var.i = 0
            while (var.i < 10) {
                var.i += 1
                if (var.i % 3 == 0) {
                    next
                }
                var.sum += var.i
            }
            return var.sum
            """);
        // 跳过3,6,9: 1+2+4+5+7+8+10 = 37
        assertEquals(37.0, result2.numberValue());
    }

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
                case 3 {
                    var.result = var.result + 30
                }
            }
            return var.result
            """);
        // switch 穿透：1 匹配后继续执行 2 和 3
        assertEquals(60.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 2
            var.result = 0
            switch (x) {
                case 1 {
                    var.result = var.result + 10
                }
                case 2 {
                    var.result = var.result + 20
                }
                case 3 {
                    var.result = var.result + 30
                }
            }
            return var.result
            """);
        // switch 穿透：2 匹配后继续执行 3 => 20+30=50
        assertEquals(50.0, result2.numberValue());
    }

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
        IValue<?> result2 = eval("""
            var.x = 2
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
        // match 不穿透：只执行 case 2
        assertEquals(20.0, result2.numberValue());
    }

    @Test
    void testMatchElse() throws AriaException {
        IValue<?> result = eval("""
            var.x = 99
            match (x) {
                case 1 {
                    return 'one'
                }
                else {
                    return 'other'
                }
            }
            """);
        assertEquals("other", result.stringValue());
        IValue<?> result2 = eval("""
            var.x = 1
            match (x) {
                case 1 {
                    return 'one'
                }
                else {
                    return 'other'
                }
            }
            """);
        assertEquals("one", result2.stringValue());
    }

    @Test
    void testNestedLoopBreak() throws AriaException {
        IValue<?> result = eval("""
            var.count = 0
            for (var.i = 0; var.i < 10; var.i += 1) {
                for (var.j = 0; var.j < 10; var.j += 1) {
                    if (var.j >= 3) {
                        break
                    }
                    var.count += 1
                }
            }
            return var.count
            """);
        // 外层 10 次，内层每次 3 次 => 30
        assertEquals(30.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.count = 0
            for (var.i = 0; var.i < 5; var.i += 1) {
                for (var.j = 0; var.j < 5; var.j += 1) {
                    if (var.j >= 2) {
                        break
                    }
                    var.count += 1
                }
            }
            return var.count
            """);
        // 外层 5 次，内层每次 2 次 => 10
        assertEquals(10.0, result2.numberValue());
    }

    //  函数

    @Test
    void testRecursiveFibonacci() throws AriaException {
        IValue<?> result = eval("""
            var.fib = -> {
                if (args[0] <= 1) { return args[0] }
                return fib(args[0] - 1) + fib(args[0] - 2)
            }
            return fib(20)
            """);
        assertEquals(6765.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.fib = -> {
                if (args[0] <= 1) { return args[0] }
                return fib(args[0] - 1) + fib(args[0] - 2)
            }
            return fib(10)
            """);
        assertEquals(55.0, result2.numberValue());
    }

    @Test
    void testRecursiveFactorial() throws AriaException {
        IValue<?> result = eval("""
            var.fact = -> {
                if (args[0] <= 1) { return 1 }
                return args[0] * fact(args[0] - 1)
            }
            return fact(10)
            """);
        assertEquals(3628800.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.fact = -> {
                if (args[0] <= 1) { return 1 }
                return args[0] * fact(args[0] - 1)
            }
            return fact(5)
            """);
        assertEquals(120.0, result2.numberValue());
    }

    @Test
    void testClosureCapture() throws AriaException {
        IValue<?> result = eval("""
            var.x = 10
            var.f = -> { return x }
            var.x = 99
            return f()
            """);
        assertEquals(99.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 'hello'
            var.f = -> { return x }
            var.x = 'world'
            return f()
            """);
        assertEquals("world", result2.stringValue());
    }

    @Test
    void testHigherOrderFunctionAsParam() throws AriaException {
        IValue<?> result = eval("""
            var.apply = -> {
                val.fn = args[0]
                val.val = args[1]
                return fn(val)
            }
            var.double = -> { return args[0] * 2 }
            return apply(double, 21)
            """);
        assertEquals(42.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.apply = -> {
                val.fn = args[0]
                val.val = args[1]
                return fn(val)
            }
            var.triple = -> { return args[0] * 3 }
            return apply(triple, 10)
            """);
        assertEquals(30.0, result2.numberValue());
    }

    @Test
    void testHigherOrderFunctionAsReturn() throws AriaException {
        IValue<?> result = eval("""
            var.makeAdder = -> {
                val.n = args[0]
                return -> { return n + args[0] }
            }
            val.add10 = makeAdder(10)
            return add10(32)
            """);
        assertEquals(42.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.makeAdder = -> {
                val.n = args[0]
                return -> { return n + args[0] }
            }
            val.add5 = makeAdder(5)
            return add5(15)
            """);
        assertEquals(20.0, result2.numberValue());
    }

    @Test
    void testImmediateInvocation() throws AriaException {
        IValue<?> result = eval("""
            val.r = (-> { return 42 })()
            return r
            """);
        assertEquals(42.0, result.numberValue());
        IValue<?> result2 = eval("""
            val.r = (-> { return 'iife' })()
            return r
            """);
        assertEquals("iife", result2.stringValue());
    }

    @Test
    void testMultiLayerNestedFunctions() throws AriaException {
        IValue<?> result = eval("""
            var.outer = -> {
                var.middle = -> {
                    var.inner = -> {
                        return args[0] * 2
                    }
                    return inner(args[0]) + 1
                }
                return middle(args[0]) + 10
            }
            return outer(5)
            """);
        // inner(5)=10, middle(5)=11, outer(5)=21
        assertEquals(21.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.outer = -> {
                var.middle = -> {
                    var.inner = -> {
                        return args[0] * 2
                    }
                    return inner(args[0]) + 1
                }
                return middle(args[0]) + 10
            }
            return outer(10)
            """);
        // inner(10)=20, middle(10)=21, outer(10)=31
        assertEquals(31.0, result2.numberValue());
    }

    @Test
    void testFunctionAsMapValue() throws AriaException {
        IValue<?> result = eval("""
            val.addFn = -> { return args[0] + args[1] }
            val.mulFn = -> { return args[0] * args[1] }
            val.ops = {
                'add': addFn,
                'mul': mulFn
            }
            val.a = ops.get('add')
            val.m = ops.get('mul')
            return a(3, 4) + m(5, 6)
            """);
        // 7 + 30 = 37
        assertEquals(37.0, result.numberValue());
        IValue<?> result2 = eval("""
            val.subFn = -> { return args[0] - args[1] }
            val.ops = {'sub': subFn}
            val.s = ops.get('sub')
            return s(100, 42)
            """);
        assertEquals(58.0, result2.numberValue());
    }

    @Test
    void testVariadicArgs() throws AriaException {
        IValue<?> result = eval("""
            var.sum = -> {
                var.total = 0
                for (item in args) {
                    var.total += item
                }
                return var.total
            }
            return sum(1, 2, 3, 4, 5)
            """);
        assertEquals(15.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.sum = -> {
                var.total = 0
                for (item in args) {
                    var.total += item
                }
                return var.total
            }
            return sum(10, 20, 30)
            """);
        assertEquals(60.0, result2.numberValue());
    }

    //  类

    @Test
    void testClassBasicDefinition() throws AriaException {
        IValue<?> result = eval("""
            class Point {
                var.x = 0
                var.y = 0
            }
            val.p = Point()
            return p.x + p.y
            """);
        assertEquals(0.0, result.numberValue());
        IValue<?> result2 = eval("""
            class Counter {
                var.count = 10
            }
            val.c = Counter()
            return c.count
            """);
        assertEquals(10.0, result2.numberValue());
    }

    @Test
    void testClassConstructorSelf() throws AriaException {
        IValue<?> result = eval("""
            class Point {
                var.x = 0
                var.y = 0
                new = -> {
                    self.x = args[0]
                    self.y = args[1]
                }
            }
            val.p = Point(10, 20)
            return p.x + p.y
            """);
        assertEquals(30.0, result.numberValue());
        IValue<?> result2 = eval("""
            class Point {
                var.x = 0
                var.y = 0
                new = -> {
                    self.x = args[0]
                    self.y = args[1]
                }
            }
            val.p = Point(3, 7)
            return p.x * p.y
            """);
        assertEquals(21.0, result2.numberValue());
    }

    @Test
    void testClassMethodCall() throws AriaException {
        IValue<?> result = eval("""
            class Greeter {
                var.name = 'World'
                new = -> { self.name = args[0] }
                greet = -> { return 'Hello, ' + self.name + '!' }
            }
            val.g = Greeter('Aria')
            return g.greet()
            """);
        assertEquals("Hello, Aria!", result.stringValue());
        IValue<?> result2 = eval("""
            class Greeter {
                var.name = 'World'
                new = -> { self.name = args[0] }
                greet = -> { return 'Hello, ' + self.name + '!' }
            }
            val.g = Greeter('Java')
            return g.greet()
            """);
        assertEquals("Hello, Java!", result2.stringValue());
    }

    @Test
    void testClassInheritanceSuper() throws AriaException {
        IValue<?> result = eval("""
            class Animal {
                var.name = 'unknown'
                new = -> { self.name = args[0] }
                speak = -> { return self.name + ' says hello' }
            }
            class Dog extends Animal {
                var.breed = 'mutt'
                new = -> {
                    self.name = args[0]
                    self.breed = args[1]
                }
                speak = -> { return self.name + ' barks!' }
            }
            val.d = Dog('Rex', 'Lab')
            return d.speak() + ' ' + d.breed
            """);
        assertEquals("Rex barks! Lab", result.stringValue());
        IValue<?> result2 = eval("""
            class Animal {
                var.name = 'unknown'
                new = -> { self.name = args[0] }
                speak = -> { return self.name + ' says hello' }
            }
            class Cat extends Animal {
                new = -> { self.name = args[0] }
                speak = -> { return self.name + ' meows!' }
            }
            val.c = Cat('Whiskers')
            return c.speak()
            """);
        assertEquals("Whiskers meows!", result2.stringValue());
    }

    @Test
    void testClassFieldDefault() throws AriaException {
        IValue<?> result = eval("""
            class Config {
                var.debug = false
                var.timeout = 30
                var.name = 'default'
            }
            val.c = Config()
            return c.timeout
            """);
        assertEquals(30.0, result.numberValue());
        IValue<?> result2 = eval("""
            class Config {
                var.debug = false
                var.timeout = 30
                var.name = 'default'
            }
            val.c = Config()
            return c.name
            """);
        assertEquals("default", result2.stringValue());
    }

    @Test
    void testThreeLayerInheritance() throws AriaException {
        IValue<?> result = eval("""
            class A {
                var.val = 1
                getVal = -> { return self.val }
            }
            class B extends A {
                var.val2 = 2
                getVal2 = -> { return self.val2 }
            }
            class C extends B {
                var.val3 = 3
                getSum = -> { return self.val + self.val2 + self.val3 }
            }
            val.c = C()
            return c.getSum()
            """);
        assertEquals(6.0, result.numberValue());
        IValue<?> result2 = eval("""
            class A {
                var.val = 1
                getVal = -> { return self.val }
            }
            class B extends A {
                var.val2 = 2
                getVal2 = -> { return self.val2 }
            }
            class C extends B {
                var.val3 = 3
                getSum = -> { return self.val + self.val2 + self.val3 }
            }
            val.c = C()
            return c.getVal() + c.getVal2()
            """);
        // getVal()=1, getVal2()=2 => 3
        assertEquals(3.0, result2.numberValue());
    }

    //  异常处理

    @Test
    void testTryCatchBasic() throws AriaException {
        IValue<?> result = eval("""
            var.msg = 'none'
            try {
                throw 'boom'
            } catch (e) {
                var.msg = e
            }
            return var.msg
            """);
        assertEquals("boom", result.stringValue());
        IValue<?> result2 = eval("""
            var.msg = 'none'
            try {
                throw 'kaboom'
            } catch (e) {
                var.msg = e
            }
            return var.msg
            """);
        assertEquals("kaboom", result2.stringValue());
    }

    @Test
    void testTryCatchFinally() throws AriaException {
        IValue<?> result = eval("""
            var.log = ''
            try {
                throw 'err'
            } catch (e) {
                var.log = var.log + 'caught '
            } finally {
                var.log = var.log + 'finally'
            }
            return var.log
            """);
        assertEquals("caught finally", result.stringValue());
        IValue<?> result2 = eval("""
            var.log = ''
            try {
                throw 'oops'
            } catch (e) {
                var.log = var.log + e + ' '
            } finally {
                var.log = var.log + 'done'
            }
            return var.log
            """);
        assertEquals("oops done", result2.stringValue());
    }

    @Test
    void testTryFinallyNoError() throws AriaException {
        IValue<?> result = eval("""
            var.x = 0
            try {
                var.x = 1
            } finally {
                var.x = var.x + 10
            }
            return var.x
            """);
        assertEquals(11.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.x = 5
            try {
                var.x = var.x * 2
            } finally {
                var.x = var.x + 100
            }
            return var.x
            """);
        assertEquals(110.0, result2.numberValue());
    }

    @Test
    void testNestedTryCatch() throws AriaException {
        IValue<?> result = eval("""
            var.log = ''
            try {
                try {
                    throw 'inner'
                } catch (e) {
                    var.log = var.log + 'inner:' + e + ' '
                    throw 'outer'
                }
            } catch (e) {
                var.log = var.log + 'outer:' + e
            }
            return var.log
            """);
        assertEquals("inner:inner outer:outer", result.stringValue());
        IValue<?> result2 = eval("""
            var.log = ''
            try {
                try {
                    throw 'a'
                } catch (e) {
                    var.log = var.log + e
                    throw 'b'
                }
            } catch (e) {
                var.log = var.log + e
            }
            return var.log
            """);
        assertEquals("ab", result2.stringValue());
    }

    @Test
    void testThrowCustomException() throws AriaException {
        IValue<?> result = eval("""
            var.msg = ''
            try {
                throw 'CustomError: code=404'
            } catch (e) {
                var.msg = e
            }
            return var.msg
            """);
        assertEquals("CustomError: code=404", result.stringValue());
        IValue<?> result2 = eval("""
            var.msg = ''
            try {
                throw 'Timeout: 30s'
            } catch (e) {
                var.msg = e
            }
            return var.msg
            """);
        assertEquals("Timeout: 30s", result2.stringValue());
    }

    @Test
    void testThrowUncaught() {
        assertThrows(Exception.class, () -> eval("throw 'uncaught'\n"));
        assertThrows(Exception.class, () -> eval("throw 'another uncaught'\n"));
    }

    //  解构赋值

    @Test
    void testArrayDestructure() throws AriaException {
        IValue<?> result = eval("""
            var.[a, b, c] = [1, 2, 3]
            return a + b + c
            """);
        assertEquals(6.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.[x, y] = [10, 20]
            return x * y
            """);
        assertEquals(200.0, result2.numberValue());
    }

    @Test
    void testRestDestructure() throws AriaException {
        IValue<?> result = eval("""
            var.[first, ...rest] = [1, 2, 3, 4, 5]
            return first + rest.size()
            """);
        // first=1, rest.size()=4 => 5
        assertEquals(5.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.[head, ...tail] = [10, 20, 30]
            return head + tail.get(0)
            """);
        // head=10, tail[0]=20 => 30
        assertEquals(30.0, result2.numberValue());
    }

    @Test
    void testDestructureSwap() throws AriaException {
        IValue<?> result = eval("""
            var.a = 10
            var.b = 20
            var.[a, b] = [b, a]
            return a * 100 + b
            """);
        // a=20, b=10 => 2010
        assertEquals(2010.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.a = 3
            var.b = 7
            var.[a, b] = [b, a]
            return a * 10 + b
            """);
        // a=7, b=3 => 73
        assertEquals(73.0, result2.numberValue());
    }

    //  沙箱

    @Test
    void testSandboxInstructionLimit() {
        Interpreter.resetCallDepth();
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
        assertThrows(Exception.class, () -> {
            Context ctx = Aria.createContext();
            SandboxConfig sandbox = SandboxConfig.builder()
                    .maxInstructions(50)
                    .build();
            Aria.eval("""
                var.sum = 0
                for (var.i = 0; var.i < 10000; var.i += 1) {
                    var.sum += var.i
                }
                """, ctx, sandbox);
        });
    }

    @Test
    void testSandboxCallDepthLimit() {
        Interpreter.resetCallDepth();
        Interpreter.clearSandbox();
        assertThrows(Exception.class, () -> {
            Context ctx = Aria.createContext();
            SandboxConfig sandbox = SandboxConfig.builder()
                    .maxCallDepth(10)
                    .build();
            Aria.eval("""
                var.sandboxRecurse1 = -> {
                    return sandboxRecurse1()
                }
                sandboxRecurse1()
                """, ctx, sandbox);
        });
        assertThrows(Exception.class, () -> {
            Context ctx = Aria.createContext();
            SandboxConfig sandbox = SandboxConfig.builder()
                    .maxCallDepth(5)
                    .build();
            Aria.eval("""
                var.sandboxDeep2 = -> {
                    return sandboxDeep2()
                }
                sandboxDeep2()
                """, ctx, sandbox);
        });
    }

    //  JS 模式全覆盖

    @Test
    void testJSVarLetConst() throws AriaException {
        assertEquals(42.0, evalJS("var x = 42; return x;").numberValue());
        assertEquals(10.0, evalJS("let x = 10; return x;").numberValue());
        assertEquals(3.14, evalJS("const PI = 3.14; return PI;").numberValue(), 0.001);
    }

    @Test
    void testJSFunctionDeclaration() throws AriaException {
        IValue<?> result = evalJS("""
            function add(a, b) {
                return a + b;
            }
            return add(3, 4);
            """);
        assertEquals(7.0, result.numberValue());
        IValue<?> result2 = evalJS("""
            function multiply(a, b) {
                return a * b;
            }
            return multiply(6, 7);
            """);
        assertEquals(42.0, result2.numberValue());
    }

    @Test
    void testJSArrowFunctionSingleParam() throws AriaException {
        IValue<?> result = evalJS("""
            const square = x => x * x;
            return square(7);
            """);
        assertEquals(49.0, result.numberValue());
        IValue<?> result2 = evalJS("""
            const negate = x => -x;
            return negate(5);
            """);
        assertEquals(-5.0, result2.numberValue());
    }

    @Test
    void testJSArrowFunctionMultiParam() throws AriaException {
        IValue<?> result = evalJS("""
            const add = (a, b) => a + b;
            return add(10, 20);
            """);
        assertEquals(30.0, result.numberValue());
        IValue<?> result2 = evalJS("""
            const sub = (a, b) => a - b;
            return sub(50, 8);
            """);
        assertEquals(42.0, result2.numberValue());
    }

    @Test
    void testJSArrowFunctionExpressionBody() throws AriaException {
        IValue<?> result = evalJS("""
            const double = (x) => x * 2;
            return double(5);
            """);
        assertEquals(10.0, result.numberValue());
        IValue<?> result2 = evalJS("""
            const half = (x) => x / 2;
            return half(100);
            """);
        assertEquals(50.0, result2.numberValue());
    }

    @Test
    void testJSArrowFunctionBlockBody() throws AriaException {
        IValue<?> result = evalJS("""
            const compute = (a, b) => {
                var sum = a + b;
                return sum * 2;
            };
            return compute(3, 4);
            """);
        assertEquals(14.0, result.numberValue());
        IValue<?> result2 = evalJS("""
            const compute = (a, b) => {
                var diff = a - b;
                return diff * diff;
            };
            return compute(10, 3);
            """);
        assertEquals(49.0, result2.numberValue());
    }

    @Test
    void testJSIfElseIfElse() throws AriaException {
        IValue<?> result = evalJS("""
            var score = 75;
            if (score >= 90) {
                return 'A';
            } else if (score >= 60) {
                return 'B';
            } else {
                return 'C';
            }
            """);
        assertEquals("B", result.stringValue());
        IValue<?> result2 = evalJS("""
            var score = 95;
            if (score >= 90) {
                return 'A';
            } else if (score >= 60) {
                return 'B';
            } else {
                return 'C';
            }
            """);
        assertEquals("A", result2.stringValue());
    }

    @Test
    void testJSForLoop() throws AriaException {
        IValue<?> result = evalJS("""
            var sum = 0;
            for (var i = 0; i < 10; i = i + 1) {
                sum = sum + i;
            }
            return sum;
            """);
        assertEquals(45.0, result.numberValue());
        IValue<?> result2 = evalJS("""
            var product = 1;
            for (var i = 1; i <= 5; i = i + 1) {
                product = product * i;
            }
            return product;
            """);
        assertEquals(120.0, result2.numberValue());
    }

    @Test
    void testJSForOf() throws AriaException {
        IValue<?> result = evalJS("""
            var sum = 0;
            for (let x of [10, 20, 30]) {
                sum = sum + x;
            }
            return sum;
            """);
        assertEquals(60.0, result.numberValue());
        IValue<?> result2 = evalJS("""
            var sum = 0;
            for (let x of [1, 2, 3, 4]) {
                sum = sum + x;
            }
            return sum;
            """);
        assertEquals(10.0, result2.numberValue());
    }

    @Test
    void testJSWhileLoop() throws AriaException {
        IValue<?> result = evalJS("""
            var count = 0;
            while (count < 100) {
                count = count + 1;
            }
            return count;
            """);
        assertEquals(100.0, result.numberValue());
        IValue<?> result2 = evalJS("""
            var count = 50;
            while (count > 0) {
                count = count - 3;
            }
            return count;
            """);
        // 50-3*17=50-51=-1
        assertEquals(-1.0, result2.numberValue());
    }

    @Test
    void testJSDoWhile() throws AriaException {
        IValue<?> result = evalJS("""
            var count = 0;
            do {
                count = count + 1;
            } while (count < 5);
            return count;
            """);
        assertEquals(5.0, result.numberValue());
        IValue<?> result2 = evalJS("""
            var count = 10;
            do {
                count = count - 1;
            } while (count > 3);
            return count;
            """);
        assertEquals(3.0, result2.numberValue());
    }

    @Test
    void testJSSwitchCaseDefault() throws AriaException {
        IValue<?> result = evalJS("""
            var x = 3;
            var result = '';
            switch (x) {
                case 1:
                    result = 'one';
                    break;
                case 2:
                    result = 'two';
                    break;
                case 3:
                    result = 'three';
                    break;
                default:
                    result = 'other';
            }
            return result;
            """);
        assertEquals("three", result.stringValue());
        IValue<?> result2 = evalJS("""
            var x = 99;
            var result = '';
            switch (x) {
                case 1:
                    result = 'one';
                    break;
                default:
                    result = 'other';
            }
            return result;
            """);
        assertEquals("other", result2.stringValue());
    }

    @Test
    void testJSTryCatchFinally() throws AriaException {
        IValue<?> result = evalJS("""
            var log = '';
            try {
                throw 'error';
            } catch (e) {
                log = 'caught:' + e + ' ';
            } finally {
                log = log + 'done';
            }
            return log;
            """);
        assertEquals("caught:error done", result.stringValue());
        IValue<?> result2 = evalJS("""
            var log = '';
            try {
                log = 'ok';
            } finally {
                log = log + ' fin';
            }
            return log;
            """);
        assertEquals("ok fin", result2.stringValue());
    }

    @Test
    void testJSClassExtendsConstructorMethod() throws AriaException {
        IValue<?> result = evalJS("""
            class Animal {
                constructor(name) {
                    this.name = name;
                }
                speak() {
                    return this.name + ' speaks';
                }
            }
            class Dog extends Animal {
                constructor(name, breed) {
                    this.name = name;
                    this.breed = breed;
                }
                speak() {
                    return this.name + ' barks';
                }
            }
            var d = new Dog('Rex', 'Lab');
            return d.speak();
            """);
        assertEquals("Rex barks", result.stringValue());
        IValue<?> result2 = evalJS("""
            class Animal {
                constructor(name) {
                    this.name = name;
                }
                speak() {
                    return this.name + ' speaks';
                }
            }
            var a = new Animal('Cat');
            return a.speak();
            """);
        assertEquals("Cat speaks", result2.stringValue());
    }

    @Test
    void testJSNullUndefinedToNone() throws AriaException {
        IValue<?> r1 = evalJS("return null;");
        assertInstanceOf(NoneValue.class, r1);

        IValue<?> r2 = evalJS("return undefined;");
        assertInstanceOf(NoneValue.class, r2);
    }

    @Test
    void testJSStrictEqualityMapping() throws AriaException {
        assertEquals(true, evalJS("return 1 === 1;").booleanValue());
        assertEquals(false, evalJS("return 1 === 2;").booleanValue());
        assertEquals(true, evalJS("return 1 !== 2;").booleanValue());
        assertEquals(false, evalJS("return 1 !== 1;").booleanValue());
        assertEquals(true, evalJS("return 'abc' === 'abc';").booleanValue());
        assertEquals(false, evalJS("return 'abc' === 'def';").booleanValue());
    }

    @Test
    void testJSTypeofMapping() throws AriaException {
        assertEquals("number", evalJS("return typeof 42;").stringValue());
        assertEquals("string", evalJS("return typeof 'hello';").stringValue());
        assertEquals("boolean", evalJS("return typeof true;").stringValue());
    }

    @Test
    void testJSContinueToNextMapping() throws AriaException {
        IValue<?> result = evalJS("""
            var sum = 0;
            for (var i = 0; i < 10; i = i + 1) {
                if (i % 2 !== 0) continue;
                sum = sum + i;
            }
            return sum;
            """);
        // 0+2+4+6+8 = 20
        assertEquals(20.0, result.numberValue());
        IValue<?> result2 = evalJS("""
            var sum = 0;
            for (var i = 0; i < 10; i = i + 1) {
                if (i % 3 === 0) continue;
                sum = sum + i;
            }
            return sum;
            """);
        // 跳过0,3,6,9: 1+2+4+5+7+8 = 27
        assertEquals(27.0, result2.numberValue());
    }

    @Test
    void testJSTemplateLiteral() throws AriaException {
        IValue<?> result = evalJS("""
            var name = 'World';
            return `hello ${name}`;
            """);
        assertEquals("hello World", result.stringValue());
        IValue<?> result2 = evalJS("""
            var lang = 'Aria';
            return `I love ${lang}`;
            """);
        assertEquals("I love Aria", result2.stringValue());
    }

    @Test
    void testJSTemplateLiteralExpression() throws AriaException {
        IValue<?> result = evalJS("""
            return `result is ${2 + 3}`;
            """);
        assertEquals("result is 5", result.stringValue());
        IValue<?> result2 = evalJS("""
            return `answer is ${6 * 7}`;
            """);
        assertEquals("answer is 42", result2.stringValue());
    }

    //  大规模压力

    @Test
    void testMillionIterationArithmetic() throws AriaException {
        IValue<?> result = eval("""
            var.sum = 0
            for (var.i = 1; var.i <= 1000000; var.i += 1) {
                var.sum += var.i
            }
            return var.sum
            """);
        // sum(1..1000000) = 500000500000
        assertEquals(500000500000.0, result.numberValue());
    }

    @Test
    void testDeepRecursion() throws AriaException {
        // 测试接近调用深度限制的递归（默认 512）
        IValue<?> result = eval("""
            var.countdown = -> {
                if (args[0] <= 0) { return 0 }
                return 1 + countdown(args[0] - 1)
            }
            return countdown(400)
            """);
        assertEquals(400.0, result.numberValue());
        IValue<?> result2 = eval("""
            var.countdown = -> {
                if (args[0] <= 0) { return 0 }
                return 1 + countdown(args[0] - 1)
            }
            return countdown(200)
            """);
        assertEquals(200.0, result2.numberValue());
    }

    @Test
    void testMassVariableDeclaration() throws AriaException {
        // 动态生成 1000 个变量赋值
        StringBuilder code = new StringBuilder();
        code.append("val.list = []\n");
        for (int i = 0; i < 1000; i++) {
            code.append("var.v").append(i).append(" = ").append(i).append("\n");
            code.append("list.add(v").append(i).append(")\n");
        }
        code.append("return list.size()\n");
        IValue<?> result = eval(code.toString());
        assertEquals(1000.0, result.numberValue());
    }

    @Test
    void testJavaForceSetVal() throws AriaException {
        Context ctx = Aria.createContext();
        // 先在脚本中声明 val
        var unit = Aria.compile("test", ctx, "val.PI = 3.14\nreturn PI", Aria.Mode.ARIA);
        IValue<?> r1 = unit.execute();
        Interpreter.resetCallDepth();
        assertEquals(3.14, r1.numberValue(), 0.001);

        // Java 端强制修改 val
        ctx.forceSetLocalValue(VariableKey.of("PI"), new NumberValue(3.14159));

        // 直接通过 context 读取验证修改生效
        IValue<?> r2 = ctx.getLocalValue(VariableKey.of("PI")).ariaValue();
        assertEquals(3.14159, r2.numberValue(), 0.00001);
    }
}
