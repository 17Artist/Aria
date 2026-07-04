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

/**
 * 生产前语法收口审计:系统性验证每个语法构造的正确性,含正确但奇怪的边缘写法。
 * 每个 @Test 聚焦一类构造,断言结果值。失败即语法不可用/行为错误的待修项。
 */
// Shimmer 对齐(A2, variables-7/8/9/controlflow-13)：裸名与 var./val. 隔离、val 脚本写静默忽略——
// 本文件脚本已改为命名空间一致写法。
public class SyntaxAuditTest {

    @BeforeAll
    static void setup() { Aria.getEngine().initialize(); }

    private IValue<?> eval(String code) throws AriaException {
        Context ctx = Aria.createContext();
        return Aria.eval(code, ctx);
    }
    private double num(String code) throws AriaException { return eval(code).numberValue(); }
    private String str(String code) throws AriaException { return eval(code).stringValue(); }
    private boolean bool(String code) throws AriaException { return eval(code).booleanValue(); }

    // ---------- 数字字面量 ----------
    @Test void numInt() throws Exception { assertEquals(42, num("return 42")); }
    @Test void numFloat() throws Exception { assertEquals(3.14, num("return 3.14"), 1e-9); }
    @Test void numHex() throws Exception { assertEquals(255, num("return 0xFF")); }
    @Test void numBinary() throws Exception { assertEquals(10, num("return 0b1010")); }
    @Test void numOctal() throws Exception { assertEquals(63, num("return 0o77")); }
    @Test void numScientific() throws Exception { assertEquals(1500.0, num("return 1.5e3"), 1e-9); }

    // ---------- 字符串 ----------
    @Test void strSingle() throws Exception { assertEquals("hi", str("return 'hi'")); }
    @Test void strDoubleInterp() throws Exception { assertEquals("v=3.0", str("return \"v={1+2}\"")); } // Shimmer 对齐：数字恒 double 格式
    @Test void strTriple() throws Exception { assertEquals("a\nb", str("return \"\"\"a\nb\"\"\"")); }
    @Test void strEscapes() throws Exception { assertEquals("a\\tb\\nc", str("return 'a\\tb\\nc'")); } // syntax-05: 转义不展开,值保留原文
    @Test void strConcat() throws Exception { assertEquals("ab", str("return 'a' + 'b'")); }

    // ---------- 集合 ----------
    @Test void listIndex() throws Exception { assertEquals(2, num("l = [1,2,3]\nreturn l[1]")); }
    @Test void listLen() throws Exception { assertEquals(3, num("l = [1,2,3]\nreturn l.length")); }
    @Test void mapGet() throws Exception { assertEquals(7, num("m = {'a': 7}\nreturn m['a']")); }
    @Test void nestedColl() throws Exception { assertEquals(9, num("x = [{'k':[0,9]}]\nreturn x[0]['k'][1]")); }
    @Test void listAppendEmptyIndex() throws Exception { assertEquals(5, num("l = [1]\nl[] = 5\nreturn l[1]")); }

    // ---------- 命名空间 ----------
    @Test void varNs() throws Exception { assertEquals(1, num("var.x = 1\nreturn var.x")); }
    // Shimmer 对齐: variables-8 —— 脚本写 val 静默忽略(ValueReference.setValue no-op),读回 none(0)
    @Test void valNs() throws Exception { assertEquals(0, num("val.x = 2\nreturn val.x")); }
    // Shimmer 对齐: variables-8/9 —— val 写被忽略,裸读也不回落 val
    @Test void valBareRead() throws Exception { assertEquals(0, num("val.x = 5\nreturn x")); }
    @Test void valImmutableReassignThrows() throws Exception {
        // Shimmer 对齐: variables-8 —— 脚本写 val(含重赋)一律静默忽略,不再抛 Cannot reassign
        assertEquals(0, num("val.x = 1\nval.x = 2\nreturn val.x"));
    }
    @Test void valHostForceSetThenImmutable() throws Exception {
        // Java 宿主可 forceSet 注入 val,脚本读到;脚本再赋报错
        Context ctx = Aria.createContext();
        ctx.forceSetLocalValue(priv.seventeen.artist.aria.context.VariableKey.of("HP"),
                new priv.seventeen.artist.aria.value.NumberValue(100));
        assertEquals(100, Aria.eval("return val.HP", ctx).numberValue());
        Context ctx2 = Aria.createContext();
        ctx2.forceSetLocalValue(priv.seventeen.artist.aria.context.VariableKey.of("HP"),
                new priv.seventeen.artist.aria.value.NumberValue(100));
        // Shimmer 对齐: variables-8 —— 脚本写 val 静默忽略,宿主 forceSet 值保持不变
        assertEquals(100, Aria.eval("val.HP = 1\nreturn val.HP", ctx2).numberValue());
    }
    @Test void globalNs() throws Exception { assertEquals(3, num("global.x = 3\nreturn global.x")); }
    @Test void bareScope() throws Exception { assertEquals(4, num("x = 4\nreturn x")); }

    // ---------- 解构 ----------
    @Test void destructureList() throws Exception { assertEquals(3, num("var.[a, b] = [1, 2]\nreturn var.a + var.b")); }
    @Test void destructureRest() throws Exception { assertEquals(2, num("var.[h, ...t] = [9, 1, 1]\nreturn var.t.length")); }

    // ---------- ~= 初始化或取 ----------
    @Test void initOrGet() throws Exception { assertEquals(5, num("var.x ~= 5\nreturn var.x")); }
    @Test void initOrGetExisting() throws Exception { assertEquals(1, num("var.x = 1\nvar.x ~= 99\nreturn var.x")); }

    // ---------- 算术 ----------
    @Test void arithmetic() throws Exception { assertEquals(7, num("return 1 + 2 * 3")); }
    @Test void modulo() throws Exception { assertEquals(1, num("return 7 % 3")); }
    @Test void unaryNeg() throws Exception { assertEquals(-5, num("return -(2 + 3)")); }

    // ---------- 自增自减 ----------
    @Test void prefixInc() throws Exception { assertEquals(2, num("i = 1\nreturn ++i")); }
    @Test void postfixInc() throws Exception { assertEquals(1, num("i = 1\nj = i++\nreturn j")); }
    @Test void prefixDec() throws Exception { assertEquals(0, num("i = 1\nreturn --i")); }

    // ---------- 比较 ----------
    @Test void comparisons() throws Exception {
        assertTrue(bool("return 1 < 2")); assertTrue(bool("return 2 >= 2"));
        assertTrue(bool("return 1 != 2")); assertTrue(bool("return 2 == 2"));
    }

    // ---------- 逻辑 ----------
    @Test void logical() throws Exception {
        assertTrue(bool("return true && true")); assertFalse(bool("return false || false"));
        assertTrue(bool("return !false"));
    }

    // ---------- 位运算 ----------
    @Test void bitwise() throws Exception {
        assertEquals(4, num("return 6 & 5")); assertEquals(7, num("return 6 | 1"));
        assertEquals(3, num("return 6 ^ 5"));
    }
    @Test void shifts() throws Exception {
        assertEquals(8, num("return 2 << 2")); assertEquals(2, num("return 8 >> 2"));
    }

    // ---------- 复合赋值 ----------
    @Test void compoundAssign() throws Exception {
        assertEquals(15, num("x = 10\nx += 5\nreturn x"));
        assertEquals(20, num("x = 10\nx *= 2\nreturn x"));
        assertEquals(2, num("x = 10\nx /= 5\nreturn x"));
        assertEquals(0xF0, num("x = 0xFF\nx &= 0xF0\nreturn x"));
    }

    // ---------- 三元 / Elvis / 空合并 ----------
    @Test void ternary() throws Exception { assertEquals(1, num("return true ? 1 : 2")); }
    @Test void elvis() throws Exception { assertEquals(9, num("var.x = none\nreturn x ?: 9")); }
    @Test void nullish() throws Exception { assertEquals(9, num("var.x = none\nreturn x ?? 9")); }

    // ---------- 可选链 ----------
    @Test void optionalChain() throws Exception {
        IValue<?> r = eval("var.x = none\nreturn x?.foo");
        assertTrue(r == priv.seventeen.artist.aria.value.NoneValue.NONE || "none".equals(r.toString()));
    }

    // ---------- 范围匹配 ~~ ----------
    @Test void rangeMatch() throws Exception { assertTrue(bool("return 5 ~~ Range(1, 10)")); }

    // ---------- 控制流 ----------
    @Test void ifElifElse() throws Exception {
        assertEquals(2, num("x = 0\nif (x > 0) { return 1 } elif (x == 0) { return 2 } else { return 3 }"));
    }
    @Test void whileLoop() throws Exception { assertEquals(10, num("var.i = 0\nwhile (i < 10) { i += 1 }\nreturn i")); }
    @Test void forCStyle() throws Exception { assertEquals(45, num("var.s = 0\nfor (var.i = 0; var.i < 10; var.i += 1) { var.s += var.i }\nreturn var.s")); }
    @Test void forInList() throws Exception { assertEquals(6, num("var.s = 0\nfor (item in [1,2,3]) { var.s += item }\nreturn var.s")); }
    @Test void forInRange() throws Exception { assertEquals(21, num("var.s = 0\nfor (i in Range(1, 6)) { var.s += i }\nreturn var.s")); } // Shimmer 对齐: controlflow-01/02 双端闭
    @Test void forInMap() throws Exception { assertEquals(3, num("var.s = 0\nfor (k, v in {'a':1,'b':2}) { var.s += v }\nreturn var.s")); }
    // Shimmer 对齐(syntax-06/controlflow-04)：while 内 break 泄漏终止脚本 => none(0)
    @Test void breakStmt() throws Exception { assertEquals(0, num("i = 0\nwhile (true) { if (i == 5) { break }\n i += 1 }\nreturn i")); }
    @Test void nextStmt() throws Exception { assertEquals(4, num("var.c = 0\nfor (i in Range(0,8)) { if (i % 2 == 0) { next }\n var.c += 1 }\nreturn var.c")); }

    // ---------- switch / match ----------
    @Test void switchFallthrough() throws Exception {
        // Shimmer 对齐(controlflow-03)：非穿透,比对值被块结果替换 => 10
        assertEquals(10, num("var.r = 0\nswitch (1) { case 1 { var.r += 10 } case 2 { var.r += 20 } }\nreturn var.r"));
    }
    @Test void matchNoFallthrough() throws Exception {
        assertEquals(10, num("var.r = 0\nmatch (1) { case 1 { var.r += 10 } case 2 { var.r += 20 } }\nreturn var.r"));
    }

    // ---------- 异常 ----------
    @Test void tryCatch() throws Exception { assertEquals(1, num("try { throw 'boom' } catch (e) { return 1 }\nreturn 0")); }
    @Test void tryCatchFinally() throws Exception {
        assertEquals(3, num("var.r = 0\ntry { throw 'x' } catch (e) { var.r += 1 } finally { var.r += 2 }\nreturn var.r"));
    }
    @Test void catchNoVar() throws Exception { assertEquals(1, num("try { throw 'x' } catch { return 1 }\nreturn 0")); }

    // ---------- 函数 ----------
    @Test void lambdaArgs() throws Exception { assertEquals(3, num("var.f = -> { return args[0] + args[1] }\nreturn f(1, 2)")); }
    // Shimmer 对齐(R2)：lambda 体隔离——体内 x 不可见(none)，5 + none = 5。
    @Test void closure() throws Exception { assertEquals(5, num("x = 10\nvar.f = -> { return args[0] + x }\nreturn f(5)")); }
    @Test void recursion() throws Exception { assertEquals(120, num("var.fact = -> { if (args[0] <= 1) { return 1 }\n return args[0] * fact(args[0]-1) }\nreturn fact(5)")); }
    @Test void iife() throws Exception { assertEquals(42, num("return (-> { return 42 })()")); }
    @Test void higherOrder() throws Exception {
        assertEquals(11, num("var.apply = -> { return args[0](args[1]) }\nvar.inc = -> { return args[0] + 1 }\nreturn apply(var.inc, 10)"));
    }
    @Test void argsLength() throws Exception { assertEquals(3, num("var.f = -> { return args.length }\nreturn f(1,2,3)")); }

    // ---------- 类 ----------
    @Test void classBasic() throws Exception {
        assertEquals(30, num("class Calc { new = -> { self.x = args[0] }\n add = -> { return self.x + args[0] } }\nc = Calc(10)\nreturn c.add(20)"));
    }
    @Test void classFieldDefault() throws Exception {
        assertEquals(7, num("class C { var.v = 7 }\no = C()\nreturn o.v"));
    }
    @Test void classInheritance() throws Exception {
        assertEquals(3, num("class A { f = -> { return 1 } }\nclass B extends A { g = -> { return 2 } }\nb = B()\nreturn b.f() + b.g()"));
    }
    @Test void classSuper() throws Exception {
        assertEquals(1, num("class A { new = -> { self.x = 1 } }\nclass B extends A { new = -> { super() } }\nb = B()\nreturn b.x"));
    }

    // ---------- Java 互操作 ----------
    @Test void javaUseStatic() throws Exception {
        assertEquals(9.0, num("M = use('java.lang.Math')\nreturn M.max(4, 9)"), 1e-9);
    }
    @Test void javaInstance() throws Exception {
        assertEquals(2, num("AL = use('java.util.ArrayList')\nl = AL()\nl.add(1)\nl.add(2)\nreturn l.size()"));
    }

    // ---------- async / await ----------
    @Test void asyncAwait() throws Exception {
        assertEquals(42, num("var.r = async { return 42 }\nreturn await var.r"));
    }

    @Test void spreadList() throws Exception { assertEquals(4, num("a = [1,2]\nb = [...a, 3, 4]\nreturn b.length")); }

    // ---------- instanceof(本次审计接通解析器) ----------
    @Test void instanceofBasic() throws Exception {
        assertTrue(bool("class A { }\na = A()\nreturn a instanceof A"));
    }
    @Test void instanceofInheritance() throws Exception {
        assertTrue(bool("class A { }\nclass B extends A { }\nb = B()\nreturn b instanceof A"));
    }
    @Test void instanceofFalse() throws Exception {
        assertFalse(bool("class A { }\nclass C { }\na = A()\nreturn a instanceof C"));
    }
    @Test void instanceofInFunctionBody() throws Exception { // 验证 executeInlineInternal 也接通
        // Shimmer 对齐(R2)：lambda 体隔离后类名 A(scope 绑定)在体内不可见——类经 args 传入。
        assertTrue(bool("class A { }\nvar.f = -> { return args[0] instanceof args[1] }\na = A()\nreturn f(a, A)"));
    }

    // ---------- in 成员运算符(本次审计接通,for-in 头部抑制以消歧义) ----------
    @Test void inMapTrue() throws Exception { assertTrue(bool("return 'a' in {'a': 1}")); }
    @Test void inMapFalse() throws Exception { assertFalse(bool("return 'z' in {'a': 1}")); }
    @Test void inListIndex() throws Exception { assertTrue(bool("return 1 in [10, 20, 30]")); }
    @Test void inInFunctionBody() throws Exception { // 验证 inline 路径
        assertTrue(bool("var.f = -> { return 'k' in args[0] }\nreturn f({'k': 9})"));
    }
    @Test void forInStillWorksWithInOperator() throws Exception { // 确认 for-in 未被 in 运算符破坏
        assertEquals(6, num("var.s = 0\nfor (item in [1,2,3]) { var.s += item }\nreturn var.s"));
    }

    // 注:逗号运算符与 map 简写已从文档移除(逗号仅保留参数/列表分隔符功能,运算符逻辑已删)。
}
