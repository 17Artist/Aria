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
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;

import static org.junit.jupiter.api.Assertions.*;

class JavaScriptModeTest {

    private IValue<?> evalJS(String code) throws AriaException {
        Context ctx = Aria.createContext();
        var unit = Aria.compile("test.js", ctx, code, Aria.Mode.JAVASCRIPT);
        return unit.execute();
    }


    @Test
    void testVarDeclaration() throws AriaException {
        IValue<?> result = evalJS("var x = 42; return x;");
        assertEquals(42.0, result.numberValue());
    }

    @Test
    void testLetDeclaration() throws AriaException {
        IValue<?> result = evalJS("let x = 10; return x;");
        assertEquals(10.0, result.numberValue());
    }

    @Test
    void testConstDeclaration() throws AriaException {
        IValue<?> result = evalJS("const PI = 3.14; return PI;");
        assertEquals(3.14, result.numberValue(), 0.001);
    }

    @Test
    void testMultipleVarDecl() throws AriaException {
        IValue<?> result = evalJS("var x = 1; var y = 2; return x + y;");
        assertEquals(3.0, result.numberValue());
    }


    @Test
    void testFunctionDeclaration() throws AriaException {
        IValue<?> result = evalJS("""
            function add(a, b) {
                return a + b;
            }
            return add(3, 4);
            """);
        assertEquals(7.0, result.numberValue());
    }

    @Test
    void testFunctionNoParams() throws AriaException {
        IValue<?> result = evalJS("""
            function hello() {
                return 'hello';
            }
            return hello();
            """);
        assertEquals("hello", result.stringValue());
    }

    @Test
    void testFunctionMultipleParams() throws AriaException {
        IValue<?> result = evalJS("""
            function sum(a, b, c) {
                return a + b + c;
            }
            return sum(1, 2, 3);
            """);
        assertEquals(6.0, result.numberValue());
    }


    @Test
    void testArrowFunctionExpression() throws AriaException {
        IValue<?> result = evalJS("""
            const double = (x) => x * 2;
            return double(5);
            """);
        assertEquals(10.0, result.numberValue());
    }

    @Test
    void testArrowFunctionBlock() throws AriaException {
        IValue<?> result = evalJS("""
            const add = (a, b) => {
                return a + b;
            };
            return add(10, 20);
            """);
        assertEquals(30.0, result.numberValue());
    }

    @Test
    void testArrowFunctionSingleParam() throws AriaException {
        IValue<?> result = evalJS("""
            const square = x => x * x;
            return square(6);
            """);
        assertEquals(36.0, result.numberValue());
    }


    @Test
    void testIfElse() throws AriaException {
        IValue<?> result = evalJS("""
            var x = 10;
            if (x > 5) {
                return 'big';
            } else {
                return 'small';
            }
            """);
        assertEquals("big", result.stringValue());
    }

    @Test
    void testIfElseIf() throws AriaException {
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
    }

    @Test
    void testWhileLoop() throws AriaException {
        IValue<?> result = evalJS("""
            var sum = 0;
            var i = 1;
            while (i <= 10) {
                sum = sum + i;
                i = i + 1;
            }
            return sum;
            """);
        assertEquals(55.0, result.numberValue());
    }

    @Test
    void testForLoop() throws AriaException {
        IValue<?> result = evalJS("""
            var sum = 0;
            for (var i = 0; i < 5; i = i + 1) {
                sum = sum + i;
            }
            return sum;
            """);
        assertEquals(10.0, result.numberValue());
    }

    @Test
    void testDoWhile() throws AriaException {
        IValue<?> result = evalJS("""
            var count = 0;
            do {
                count = count + 1;
            } while (count < 5);
            return count;
            """);
        assertEquals(5.0, result.numberValue());
    }

    @Test
    void testForOf() throws AriaException {
        IValue<?> result = evalJS("""
            var sum = 0;
            for (let x of [1, 2, 3, 4, 5]) {
                sum = sum + x;
            }
            return sum;
            """);
        assertEquals(15.0, result.numberValue());
    }


    @Test
    void testSwitch() throws AriaException {
        IValue<?> result = evalJS("""
            var x = 2;
            var result = '';
            switch (x) {
                case 1:
                    result = 'one';
                    break;
                case 2:
                    result = 'two';
                    break;
                default:
                    result = 'other';
            }
            return result;
            """);
        assertEquals("two", result.stringValue());
    }


    @Test
    void testTryCatch() throws AriaException {
        IValue<?> result = evalJS("""
            var msg = '';
            try {
                throw 'oops';
            } catch (e) {
                msg = 'caught: ' + e;
            }
            return msg;
            """);
        assertEquals("caught: oops", result.stringValue());
    }

    @Test
    void testTryFinally() throws AriaException {
        IValue<?> result = evalJS("""
            var x = 0;
            try {
                x = 1;
            } finally {
                x = x + 10;
            }
            return x;
            """);
        assertEquals(11.0, result.numberValue());
    }


    @Test
    void testNullUndefined() throws AriaException {
        IValue<?> r1 = evalJS("return null;");
        assertTrue(r1.stringValue().equals("none") || r1.jvmValue() == null);

        IValue<?> r2 = evalJS("return undefined;");
        assertTrue(r2.stringValue().equals("none") || r2.jvmValue() == null);
    }


    @Test
    void testStrictEquality() throws AriaException {
        IValue<?> result = evalJS("return 1 === 1;");
        assertTrue(result.booleanValue());
    }

    @Test
    void testStrictInequality() throws AriaException {
        IValue<?> result = evalJS("return 1 !== 2;");
        assertTrue(result.booleanValue());
    }


    @Test
    void testTernary() throws AriaException {
        IValue<?> result = evalJS("var x = 10; return x > 5 ? 'yes' : 'no';");
        assertEquals("yes", result.stringValue());
    }


    @Test
    void testObjectLiteral() throws AriaException {
        IValue<?> result = evalJS("""
            var obj = { 'name': 'Alice', 'age': 30 };
            return obj['name'];
            """);
        assertEquals("Alice", result.stringValue());
    }


    @Test
    void testArrayLiteral() throws AriaException {
        IValue<?> result = evalJS("""
            var arr = [10, 20, 30];
            return arr[1];
            """);
        assertEquals(20.0, result.numberValue());
    }


    @Test
    void testTypeof() throws AriaException {
        IValue<?> result = evalJS("return typeof 42;");
        assertEquals("number", result.stringValue());
    }


    @Test
    void testLogicalOperators() throws AriaException {
        IValue<?> r1 = evalJS("return true && false;");
        assertFalse(r1.booleanValue());

        IValue<?> r2 = evalJS("return true || false;");
        assertTrue(r2.booleanValue());

        IValue<?> r3 = evalJS("return !true;");
        assertFalse(r3.booleanValue());
    }


    @Test
    void testStringConcatenation() throws AriaException {
        IValue<?> result = evalJS("return 'hello' + ' ' + 'world';");
        assertEquals("hello world", result.stringValue());
    }


    @Test
    void testNestedFunction() throws AriaException {
        IValue<?> result = evalJS("""
            function outer(x) {
                function inner(y) {
                    return x + y;
                }
                return inner(10);
            }
            return outer(5);
            """);
        assertEquals(15.0, result.numberValue());
    }


    @Test
    void testRecursion() throws AriaException {
        IValue<?> result = evalJS("""
            function factorial(n) {
                if (n <= 1) return 1;
                return n * factorial(n - 1);
            }
            return factorial(5);
            """);
        assertEquals(120.0, result.numberValue());
    }


    @Test
    void testBreakInLoop() throws AriaException {
        IValue<?> result = evalJS("""
            var sum = 0;
            for (var i = 0; i < 100; i = i + 1) {
                if (i >= 5) break;
                sum = sum + i;
            }
            return sum;
            """);
        assertEquals(10.0, result.numberValue());
    }

    @Test
    void testContinueInLoop() throws AriaException {
        IValue<?> result = evalJS("""
            var sum = 0;
            for (var i = 0; i < 10; i = i + 1) {
                if (i % 2 !== 0) continue;
                sum = sum + i;
            }
            return sum;
            """);
        // 0 + 2 + 4 + 6 + 8 = 20
        assertEquals(20.0, result.numberValue());
    }


    @Test
    void testFibonacci() throws AriaException {
        IValue<?> result = evalJS("""
            function fib(n) {
                if (n <= 0) { return 0; }
                if (n === 1) { return 1; }
                return fib(n - 1) + fib(n - 2);
            }
            return fib(10);
            """);
        assertEquals(55.0, result.numberValue());
    }

    @Test
    void testHigherOrderFunction() throws AriaException {
        IValue<?> result = evalJS("""
            function apply(fn, x) {
                return fn(x);
            }
            function double(n) {
                return n * 2;
            }
            return apply(double, 21);
            """);
        assertEquals(42.0, result.numberValue());
    }


    @Test
    void testTemplateString() throws AriaException {
        IValue<?> result = evalJS("""
            var name = 'World';
            return `Hello, ${name}!`;
            """);
        assertEquals("Hello, World!", result.stringValue());
    }

    @Test
    void testTemplateStringExpression() throws AriaException {
        IValue<?> result = evalJS("""
            var a = 10;
            var b = 20;
            return `${a} + ${b} = ${a + b}`;
            """);
        assertEquals("10 + 20 = 30", result.stringValue());
    }


    @Test
    void testIncrementDecrement() throws AriaException {
        IValue<?> result = evalJS("""
            var x = 5;
            x++;
            x++;
            x--;
            return x;
            """);
        assertEquals(6.0, result.numberValue());
    }

    @Test
    void testPrefixIncrement() throws AriaException {
        IValue<?> result = evalJS("""
            var x = 5;
            var y = ++x;
            return y;
            """);
        assertEquals(6.0, result.numberValue());
    }


    @Test
    void testModeDetection() {
        assertEquals(Aria.Mode.JAVASCRIPT, Aria.detectMode("test.js"));
        assertEquals(Aria.Mode.ARIA, Aria.detectMode("test.aria"));
        assertEquals(Aria.Mode.ARIA, Aria.detectMode("test"));
    }


    @Test
    void testCommaOperator() throws AriaException {
        IValue<?> result = evalJS("var x = (1, 2, 3); return x;");
        assertEquals(3.0, result.numberValue());
    }

    @Test
    void testInOperator() throws AriaException {
        IValue<?> result = evalJS("""
            var obj = {name: 'test', age: 25};
            return 'name' in obj;
            """);
        assertTrue(result.booleanValue());
    }

    @Test
    void testInOperatorMissing() throws AriaException {
        IValue<?> result = evalJS("""
            var obj = {name: 'test'};
            return 'missing' in obj;
            """);
        assertFalse(result.booleanValue());
    }

    @Test
    void testInstanceof() throws AriaException {
        IValue<?> result = evalJS("""
            class Animal {
                constructor(name) { this.name = name; }
            }
            var a = new Animal('Rex');
            return a instanceof Animal;
            """);
        assertTrue(result.booleanValue());
    }

    @Test
    void testParameterDefaultValues() throws AriaException {
        IValue<?> result = evalJS("""
            function greet(name = 'World') {
                return 'Hello, ' + name + '!';
            }
            return greet();
            """);
        assertEquals("Hello, World!", result.stringValue());
    }

    @Test
    void testParameterDefaultValuesOverridden() throws AriaException {
        IValue<?> result = evalJS("""
            function greet(name = 'World') {
                return 'Hello, ' + name + '!';
            }
            return greet('Aria');
            """);
        assertEquals("Hello, Aria!", result.stringValue());
    }

    @Test
    void testArgumentsObject() throws AriaException {
        IValue<?> result = evalJS("""
            function sum() {
                var total = 0;
                for (var i = 0; i < arguments.size(); i++) {
                    total = total + arguments[i];
                }
                return total;
            }
            return sum(1, 2, 3, 4, 5);
            """);
        assertEquals(15.0, result.numberValue());
    }

    @Test
    void testLabelStatement() throws AriaException {
        // Labels are parsed but ignored — break/continue work on innermost loop
        IValue<?> result = evalJS("""
            var sum = 0;
            outer: for (var i = 0; i < 3; i++) {
                for (var j = 0; j < 3; j++) {
                    sum = sum + 1;
                }
            }
            return sum;
            """);
        assertEquals(9.0, result.numberValue());
    }
}
