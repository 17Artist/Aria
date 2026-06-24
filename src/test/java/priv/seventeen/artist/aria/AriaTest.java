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
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.parser.Lexer;
import priv.seventeen.artist.aria.parser.Token;
import priv.seventeen.artist.aria.parser.TokenType;
import priv.seventeen.artist.aria.value.*;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class AriaTest {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    @Test
    void testBasicArithmetic() throws AriaException {
        Context ctx = Aria.createContext();
        IValue<?> result = Aria.eval("var.x = 10\nvar.y = 20\nreturn x + y\n", ctx);
        assertNotNull(result);
    }

    @Test
    void testStringValue() {
        StringValue sv = new StringValue("hello");
        assertEquals("hello", sv.stringValue());
        assertFalse(sv.canBeNumber());

        StringValue num = new StringValue("42");
        assertTrue(num.canBeNumber());
        assertEquals(42.0, num.numberValue());
    }

    @Test
    void testNumberValue() {
        NumberValue nv = new NumberValue(3.14);
        assertEquals(3.14, nv.numberValue());
        assertTrue(nv.canMath());
        assertEquals(1, nv.typeID());
    }

    @Test
    void testBooleanValue() {
        assertSame(BooleanValue.TRUE, BooleanValue.of(true));
        assertSame(BooleanValue.FALSE, BooleanValue.of(false));
        assertSame(BooleanValue.FALSE, BooleanValue.TRUE.not());
    }

    @Test
    void testNoneValue() {
        assertSame(NoneValue.NONE, NoneValue.NONE);
        assertEquals(0, NoneValue.NONE.typeID());
        assertFalse(NoneValue.NONE.booleanValue());
    }

    @Test
    void testListValue() {
        ListValue list = new ListValue();
        list.jvmValue().add(new NumberValue(1));
        list.jvmValue().add(new NumberValue(2));
        assertEquals(2, list.jvmValue().size());
        assertEquals(11, list.typeID());
    }

    @Test
    void testMapValue() {
        MapValue map = new MapValue();
        map.jvmValue().put(new StringValue("key"), new NumberValue(42));
        assertEquals(1, map.jvmValue().size());
        assertEquals(12, map.typeID());
    }

    @Test
    void testIDataArithmetic() {
        NumberValue a = new NumberValue(10);
        NumberValue b = new NumberValue(3);

        IValue<?> sum = a.add(b);
        assertInstanceOf(NumberValue.class, sum);
        assertEquals(13.0, sum.numberValue());

        IValue<?> diff = a.sub(b);
        assertEquals(7.0, diff.numberValue());

        IValue<?> prod = a.mul(b);
        assertEquals(30.0, prod.numberValue());

        IValue<?> quot = a.div(b);
        assertEquals(10.0 / 3.0, quot.numberValue(), 0.0001);

        IValue<?> mod = a.mod(b);
        assertEquals(1.0, mod.numberValue());
    }

    @Test
    void testIDataComparison() {
        NumberValue a = new NumberValue(10);
        NumberValue b = new NumberValue(20);

        assertTrue(a.lt(b).booleanValue());
        assertFalse(a.gt(b).booleanValue());
        assertTrue(a.le(a).booleanValue());
        assertTrue(a.eq(new NumberValue(10)).booleanValue());
        assertTrue(a.ne(b).booleanValue());
    }

    @Test
    void testStringAddition() {
        StringValue hello = new StringValue("hello ");
        StringValue world = new StringValue("world");
        IValue<?> result = hello.add(world);
        assertInstanceOf(StringValue.class, result);
        assertEquals("hello world", result.stringValue());
    }

    @Test
    void testStringNumberAdd() {
        StringValue numStr = new StringValue("10");
        NumberValue num = new NumberValue(5);
        IValue<?> result = numStr.add(num);
        // 两者都能转数字，应该做数学加法
        assertInstanceOf(NumberValue.class, result);
        assertEquals(15.0, result.numberValue());
    }

    @Test
    void testVariableKey() {
        VariableKey k1 = VariableKey.of("test");
        VariableKey k2 = VariableKey.of("test");
        assertSame(k1, k2);
        assertEquals(k1.hashCode(), k2.hashCode());
    }

    @Test
    void testContextVariables() {
        GlobalStorage gs = new GlobalStorage();
        Context ctx = new Context(gs);

        // 设置全局变量
        ctx.getGlobalVariable(VariableKey.of("score")).setValue(new NumberValue(100));
        IValue<?> val = ctx.getGlobalVariable(VariableKey.of("score")).ariaValue();
        assertEquals(100.0, val.numberValue());

        // 设置局部变量
        ctx.getLocalVariable(VariableKey.of("x")).setValue(new NumberValue(42));
        assertEquals(42.0, ctx.getLocalVariable(VariableKey.of("x")).ariaValue().numberValue());
    }

    @Test
    void testScopeStack() {
        Context ctx = new Context(new GlobalStorage());
        ctx.pushScope();
        ctx.getScopeVariable(VariableKey.of("a")).setValue(new NumberValue(1));
        assertEquals(1.0, ctx.getScopeVariable(VariableKey.of("a")).ariaValue().numberValue());

        ctx.pushScope();
        ctx.getScopeVariable(VariableKey.of("b")).setValue(new NumberValue(2));
        // 内层能看到外层
        assertEquals(1.0, ctx.getScopeVariable(VariableKey.of("a")).ariaValue().numberValue());

        ctx.popScope();
        // 外层看不到内层
        // b 在外层不存在，会创建新的
        assertEquals(0.0, ctx.getScopeVariable(VariableKey.of("b")).ariaValue().numberValue());
    }

    @Test
    void testLexer() throws Exception {
        var lexer = new Lexer("var.x = 10 + 20\n");
        var tokens = new ArrayList<Token>();
        Token t;
        while ((t = lexer.nextToken()).getType() != TokenType.EOF) {
            tokens.add(t);
        }
        // var . x = 10 + 20 NEWLINE
        assertTrue(tokens.size() >= 7);
        assertEquals(TokenType.IDENTIFIER, tokens.get(0).getType());
        assertEquals("var", tokens.get(0).getValue());
        assertEquals(TokenType.DOT, tokens.get(1).getType());
    }

    @Test
    void testDivisionByZero() {
        NumberValue a = new NumberValue(10);
        NumberValue zero = new NumberValue(0);
        IValue<?> result = a.div(zero);
        assertTrue(Double.isInfinite(result.numberValue())); // 10/0 = Infinity

        NumberValue neg = new NumberValue(-5);
        IValue<?> negResult = neg.div(zero);
        assertTrue(Double.isInfinite(negResult.numberValue()) && negResult.numberValue() < 0); // -5/0 = -Infinity

        IValue<?> zeroZero = zero.div(zero);
        assertTrue(Double.isNaN(zeroZero.numberValue())); // 0/0 = NaN
    }

    @Test
    void testNoneArithmetic() {
        IValue<?> result = NoneValue.NONE.add(new NumberValue(5));
        assertEquals(5.0, result.numberValue()); // None + x = x
    }


    @Test
    void testAriaREPLClassExists() {
        // Verify REPL class is loadable (JLine dependency is present)
        assertDoesNotThrow(() -> Class.forName("priv.seventeen.artist.aria.AriaREPL"));
    }
}
