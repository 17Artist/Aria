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
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

import static org.junit.jupiter.api.Assertions.*;

public class EdgeCaseTest {

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
        Context ctx = Aria.createContext();
        return Aria.eval(code, ctx);
    }


    @Test
    void testListMap() throws AriaException {
        IValue<?> result = eval("""
            val.list = [1, 2, 3, 4, 5]
            val.doubled = list.map(-> { return args[0] * 2 })
            return doubled.get(2)
            """);
        assertEquals(6.0, result.numberValue());
    }

    @Test
    void testListFilter() throws AriaException {
        IValue<?> result = eval("""
            val.list = [1, 2, 3, 4, 5, 6]
            val.evens = list.filter(-> { return args[0] % 2 == 0 })
            return evens.size()
            """);
        assertEquals(3.0, result.numberValue());
    }

    @Test
    void testListReduce() throws AriaException {
        IValue<?> result = eval("""
            val.list = [1, 2, 3, 4, 5]
            val.sum = list.reduce(-> { return args[0] + args[1] }, 0)
            return sum
            """);
        assertEquals(15.0, result.numberValue());
    }

    @Test
    void testListFind() throws AriaException {
        IValue<?> result = eval("""
            val.list = [1, 2, 3, 4, 5]
            val.found = list.find(-> { return args[0] > 3 })
            return found
            """);
        assertEquals(4.0, result.numberValue());
    }

    @Test
    void testListEvery() throws AriaException {
        IValue<?> result = eval("""
            val.list = [2, 4, 6, 8]
            return list.every(-> { return args[0] % 2 == 0 })
            """);
        assertTrue(result.booleanValue());
    }

    @Test
    void testListSome() throws AriaException {
        IValue<?> result = eval("""
            val.list = [1, 3, 5, 6]
            return list.some(-> { return args[0] % 2 == 0 })
            """);
        assertTrue(result.booleanValue());
    }

    @Test
    void testListJoin() throws AriaException {
        IValue<?> result = eval("""
            val.list = ['a', 'b', 'c']
            return list.join('-')
            """);
        assertEquals("a-b-c", result.stringValue());
    }

    @Test
    void testListSortBy() throws AriaException {
        IValue<?> result = eval("""
            val.list = [3, 1, 4, 1, 5]
            list.sortBy(-> { return args[0] })
            return list.get(0)
            """);
        assertEquals(1.0, result.numberValue());
    }

    @Test
    void testMapForEach() throws AriaException {
        // 不抛异常就算通过
        eval("""
            val.m = {'a': 1, 'b': 2}
            var.count = 0
            m.forEach(-> { count = count + 1 })
            """);
    }

    @Test
    void testMapEntries() throws AriaException {
        IValue<?> result = eval("""
            val.m = {'x': 10, 'y': 20}
            val.entries = m.entries()
            return entries.size()
            """);
        assertEquals(2.0, result.numberValue());
    }


    @Test
    void testClosureSharedState() throws AriaException {
        IValue<?> result = eval("""
            var.x = 10
            var.f = -> { return x }
            var.x = 20
            return f()
            """);
        assertEquals(20.0, result.numberValue());
    }

    @Test
    void testFunctionAsReturnValue() throws AriaException {
        IValue<?> result = eval("""
            var.makeAdder = -> {
                val.n = args[0]
                return -> { return n + args[0] }
            }
            val.add5 = makeAdder(5)
            return add5(3)
            """);
        assertEquals(8.0, result.numberValue());
    }


    @Test
    void testRecursion() throws AriaException {
        IValue<?> result = eval("""
            var.fib = -> {
                if (args[0] <= 1) { return args[0] }
                return fib(args[0] - 1) + fib(args[0] - 2)
            }
            return fib(10)
            """);
        assertEquals(55.0, result.numberValue());
    }

    @Test
    void testStackOverflowProtection() {
        assertThrows(Throwable.class, () -> eval("""
            var.infinite = -> { return infinite() }
            infinite()
            """));
    }


    @Test
    void testChainedStringMethods() throws AriaException {
        IValue<?> result = eval("""
            return 'hello world'.toUpperCase().substring(0, 5)
            """);
        assertEquals("HELLO", result.stringValue());
    }

    @Test
    void testChainedListMethods() throws AriaException {
        IValue<?> result = eval("""
            val.list = [3, 1, 4, 1, 5, 9]
            return list.filter(-> { return args[0] > 3 }).size()
            """);
        assertEquals(3.0, result.numberValue());
    }


    @Test
    void testTryCatchRecovery() throws AriaException {
        IValue<?> result = eval("""
            var.result = 'ok'
            try {
                throw 'test error'
            } catch (e) {
                var.result = e
            }
            return var.result
            """);
        assertEquals("test error", result.stringValue());
    }

    @Test
    void testDivisionByZeroInfinity() throws AriaException {
        IValue<?> result = eval("""
            return 10 / 0
            """);
        assertTrue(Double.isInfinite(result.numberValue()));
    }


    @Test
    void testOptionalChainNone() throws AriaException {
        IValue<?> result = eval("""
            var.obj = none
            return obj?.name
            """);
        assertInstanceOf(NoneValue.class, result);
    }


    @Test
    void testMultilineExpression() throws AriaException {
        IValue<?> result = eval("""
            var.x = 1 +
                2 +
                3
            return x
            """);
        assertEquals(6.0, result.numberValue());
    }


    @Test
    void testListAddImmutable() throws AriaException {
        IValue<?> result = eval("""
            val.a = [1, 2]
            val.b = [3, 4]
            val.c = a + b
            return a.size()
            """);
        assertEquals(2.0, result.numberValue());
    }


    @Test
    void testSpreadInList() throws AriaException {
        IValue<?> result = eval("""
            val.a = [1, 2, 3]
            val.b = [0, ...a, 4, 5]
            return b.size()
            """);
        assertEquals(6.0, result.numberValue());
    }

    @Test
    void testSpreadMultiple() throws AriaException {
        IValue<?> result = eval("""
            val.a = [1, 2]
            val.b = [3, 4]
            val.c = [...a, ...b]
            return c.size()
            """);
        assertEquals(4.0, result.numberValue());
    }


    @Test
    void testRegexTest() throws AriaException {
        IValue<?> result = eval("""
            return regex.test('[0-9]+', 'hello123world')
            """);
        assertTrue(result.booleanValue());
    }

    @Test
    void testRegexMatch() throws AriaException {
        IValue<?> result = eval("""
            val.m = regex.match('([0-9]+)', 'price: 42 dollars')
            return m.get(1)
            """);
        assertEquals("42", result.stringValue());
    }

    @Test
    void testRegexReplace() throws AriaException {
        IValue<?> result = eval("""
            return regex.replace('[0-9]+', 'a1b2c3', 'X')
            """);
        assertEquals("aXbXcX", result.stringValue());
    }

    @Test
    void testRegexSplit() throws AriaException {
        IValue<?> result = eval("""
            val.parts = regex.split('[,;]', 'a,b;c,d')
            return parts.size()
            """);
        assertEquals(4.0, result.numberValue());
    }


    @Test
    void testDestructureBasic() throws AriaException {
        IValue<?> result = eval("""
            var.[a, b, c] = [10, 20, 30]
            return a + b + c
            """);
        assertEquals(60.0, result.numberValue());
    }

    @Test
    void testDestructureWithRest() throws AriaException {
        IValue<?> result = eval("""
            var.[first, ...rest] = [1, 2, 3, 4, 5]
            return rest.size()
            """);
        assertEquals(4.0, result.numberValue());
    }

    @Test
    void testDestructureVal() throws AriaException {
        IValue<?> result = eval("""
            val.[x, y] = [100, 200]
            return x * y
            """);
        assertEquals(20000.0, result.numberValue());
    }
}
