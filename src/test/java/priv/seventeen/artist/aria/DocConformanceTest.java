/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.SandboxConfig;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档↔实现一致性验证：对着 docs/src/content/docs/*.md 的每条具体承诺逐一断言
 */
public class DocConformanceTest {

    @BeforeAll
    static void setup() { Aria.getEngine().initialize(); }

    @BeforeEach
    void reset() { Interpreter.resetCallDepth(); Interpreter.clearSandbox(); }

    private IValue<?> eval(String code) throws AriaException { return Aria.eval(code, Aria.createContext()); }
    private double num(String code) throws AriaException { return eval(code).numberValue(); }
    private String str(String code) throws AriaException { return eval(code).stringValue(); }
    private boolean bool(String code) throws AriaException { return eval(code).booleanValue(); }
    private boolean isNone(String code) throws AriaException { return eval(code) instanceof NoneValue; }

    // ===================== data-types.md =====================

    // 数字方法
    @Test void roundReturnsNumber() throws Exception { assertEquals(3.142, num("return (3.14159).round(3)"), 1e-9); }
    @Test void roundNoArg() throws Exception { assertEquals(4.0, num("return (3.5).round()"), 1e-9); }
    @Test void roundReturnsNumberType() throws Exception { assertEquals("number", str("return type.typeof((3.5).round())")); }
    @Test void roundChainable() throws Exception { assertEquals(6.96, num("return (3.14159).round(1) + 3.86"), 1e-9); }
    @Test void numToInt() throws Exception { assertEquals(3.0, num("return (3.9).toInt()"), 1e-9); }
    @Test void numToFixed() throws Exception { assertEquals("3.14", str("return (3.14159).toFixed(2)")); }
    @Test void numAbs() throws Exception { assertEquals(3.2, num("return (-3.2).abs()"), 1e-9); }
    @Test void numCeil() throws Exception { assertEquals(4.0, num("return (3.2).ceil()"), 1e-9); }
    @Test void numFloor() throws Exception { assertEquals(3.0, num("return (3.8).floor()"), 1e-9); }
    @Test void numIsNaN() throws Exception { assertFalse(bool("return (1.0).isNaN()")); }
    @Test void numIsInfinite() throws Exception { assertFalse(bool("return (1.0).isInfinite()")); }

    // 字符串方法（文档列出但多数未测）
    @Test void strReplaceAll() throws Exception { assertEquals("aXbX", str("return 'a1b2'.replaceAll('\\\\d','X')")); }
    @Test void strReplaceFirst() throws Exception { assertEquals("aXb2", str("return 'a1b2'.replaceFirst('\\\\d','X')")); }
    @Test void strCharAt() throws Exception { assertEquals("b", str("return 'abc'.charAt(1)")); }
    @Test void strRepeat() throws Exception { assertEquals("abab", str("return 'ab'.repeat(2)")); }
    @Test void strStartsWith() throws Exception { assertTrue(bool("return 'abc'.startsWith('a')")); }
    @Test void strEndsWith() throws Exception { assertTrue(bool("return 'abc'.endsWith('c')")); }
    @Test void strContains() throws Exception { assertTrue(bool("return 'abc'.contains('b')")); }
    @Test void strIndexOf() throws Exception { assertEquals(0.0, num("return 'abca'.indexOf('a')"), 1e-9); }
    @Test void strLastIndexOf() throws Exception { assertEquals(3.0, num("return 'abca'.lastIndexOf('a')"), 1e-9); }
    @Test void strUpper() throws Exception { assertEquals("AB", str("return 'ab'.toUpperCase()")); }
    @Test void strLower() throws Exception { assertEquals("ab", str("return 'AB'.toLowerCase()")); }
    @Test void strEqualsIgnoreCase() throws Exception { assertTrue(bool("return 'A'.equalsIgnoreCase('a')")); }
    @Test void strIsEmpty() throws Exception { assertTrue(bool("return ''.isEmpty()")); }

    // 字符串插值差异：单引号不插值
    @Test void singleQuoteNoInterp() throws Exception { assertEquals("Hi {n}", str("var.n='W'\nreturn 'Hi {n}'")); }

    // boolean 数学：true=1 false=0
    @Test void booleanMath() throws Exception { assertEquals(2.0, num("return true + true"), 1e-9); }

    // list 运算
    @Test void listConcat() throws Exception { assertEquals(4.0, num("return ([1,2]+[3,4]).size()"), 1e-9); }
    @Test void listAppendElement() throws Exception { assertEquals(4.0, num("return ([1,2,3]+4).size()"), 1e-9); }
    @Test void listRemoveByIndex() throws Exception { assertEquals(2.0, num("var.l=[1,2,3]-0\nreturn l[0]"), 1e-9); }
    // Shimmer 对齐：显式索引越界抛异常（而非静默 none）
    @Test void listOutOfBoundsReadThrows() { assertThrows(AriaException.class, () -> eval("var.l=[1]\nreturn l[5]")); }
    @Test void listOutOfBoundsWriteFills() throws Exception { assertEquals(6.0, num("var.l=[1]\nl[5]=9\nreturn l.size()"), 1e-9); }

    // map 运算
    @Test void mapMerge() throws Exception { assertEquals(2.0, num("var.m={'x':1}+{'y':2}\nreturn m.size()"), 1e-9); }
    @Test void mapMissingKeyNone() throws Exception { assertTrue(isNone("var.m={'a':1}\nreturn m['z']")); }

    // 类型转换
    @Test void typeToString() throws Exception { assertEquals("42.0", str("return type.toString(42)")); } // Shimmer 对齐：数字恒 double 格式
    @Test void typeToBooleanZero() throws Exception { assertFalse(bool("return type.toBoolean(0)")); }
    @Test void typeToBooleanOne() throws Exception { assertTrue(bool("return type.toBoolean(1)")); }
    @Test void typeIsString() throws Exception { assertTrue(bool("return type.isString('h')")); }
    @Test void typeIsNone() throws Exception { assertTrue(bool("return type.isNone(none)")); }

    // ===================== operators.md =====================

    @Test void unsignedRightShift() throws Exception { assertEquals(15.0, num("return -1 >>> 28"), 1e-9); }
    @Test void modAssign() throws Exception { assertEquals(0.0, num("var.x=10\nx%=5\nreturn x"), 1e-9); }
    @Test void orBitAssign() throws Exception { assertEquals(15.0, num("var.b=0b1100\nb|=0b0011\nreturn b"), 1e-9); }
    @Test void bitNot() throws Exception { assertEquals(-1.0, num("return ~0"), 1e-9); }

    // ~~ Range 半开 [start,end)
    @Test void rangeMatchInside() throws Exception { assertTrue(bool("return 5 ~~ Range(1,10)")); }
    @Test void rangeMatchStartInclusive() throws Exception { assertTrue(bool("return 1 ~~ Range(1,10)")); }
    @Test void rangeMatchEndExclusive() throws Exception { assertFalse(bool("return 10 ~~ Range(1,10)")); }
    @Test void rangeMatchEqualityFallback() throws Exception { assertTrue(bool("return 5 ~~ 5")); }

    // in：map 键 / list 索引有效性
    @Test void inMapKeyTrue() throws Exception { assertTrue(bool("return 'name' in {'name':1}")); }
    @Test void inMapKeyFalse() throws Exception { assertFalse(bool("return 'x' in {'name':1}")); }
    @Test void inListIndexValid() throws Exception { assertTrue(bool("return 0 in [10,20,30]")); }
    @Test void inListIndexInvalid() throws Exception { assertFalse(bool("return 3 in [10,20,30]")); }

    // 三元 / Elvis / 空合并
    @Test void elvis() throws Exception { assertEquals(9.0, num("var.x=none\nreturn x ?: 9"), 1e-9); }
    @Test void nullish() throws Exception { assertEquals(8.0, num("var.x=none\nreturn x ?? 8"), 1e-9); }

    // 展开
    @Test void spreadList() throws Exception { assertEquals(5.0, num("var.a=[1,2,3]\nreturn [0,...a,4].size()"), 1e-9); }
    @Test void spreadMap() throws Exception { assertEquals(2.0, num("var.b={'x':1}\nreturn {...b,'y':2}.size()"), 1e-9); }
    @Test void spreadMapEmpty() throws Exception { assertEquals(0.0, num("return {...{}}.size()"), 1e-9); }
    @Test void spreadMapMultiple() throws Exception { assertEquals(2.0, num("return {...{'a':1}, ...{'b':2}}.size()"), 1e-9); }
    @Test void spreadMapOverride() throws Exception { assertEquals(9.0, num("return {'x':1, ...{'x':9}}['x']"), 1e-9); }
    @Test void spreadMapNewInstance() throws Exception { assertEquals(1.0, num("var.m={'a':1}\nvar.c={...m}\nc['a']=9\nreturn m['a']"), 1e-9); }
    // 展开非 map：必须报错（不静默吞噬）
    @Test void spreadNonMapListThrows() { assertThrows(AriaException.class, () -> eval("return {...[1,2]}")); }
    @Test void spreadNonMapNumberThrows() { assertThrows(AriaException.class, () -> eval("return {...5}")); }
    @Test void spreadNonMapNoneThrows() { assertThrows(AriaException.class, () -> eval("return {...none}")); }
    // 简写键已移除：{n} 无冒号应解析报错（不再静默变成 {n求值: n求值}）
    @Test void mapShorthandKeyRemoved() { assertThrows(AriaException.class, () -> eval("return {n}")); }
    // fail-fast：坏语句即便夹在好语句之间，也应报错（不再静默丢弃）
    @Test void parseErrorFailFastMidStatement() { assertThrows(AriaException.class, () -> eval("var.x=1\nreturn {n}\n")); }
    @Test void parseErrorFailFastDoesNotDropStatement() { assertThrows(AriaException.class, () -> eval("var.x=1\nvar.y={bad\nreturn x\n")); }

    // number+string 强制转换规则
    @Test void numberPlusConvertibleString() throws Exception { assertEquals(3.0, num("return 1 + '2'"), 1e-9); }
    @Test void numberPlusNonNumericString() throws Exception { assertEquals("1.0a", str("return 1 + 'a'")); } // Shimmer 对齐
    @Test void stringMinusSubstring() throws Exception { assertEquals("abc", str("return 'aXbXc' - 'X'")); }

    // 短路求值（右侧不应触发除零）
    @Test void shortCircuitAnd() throws Exception { assertFalse(bool("return false && (1/0 > 0)")); }

    // ===================== variables.md =====================

    @Test void varReassign() throws Exception { assertEquals(20.0, num("var.x=10\nvar.x=20\nreturn x"), 1e-9); }
    @Test void initOrGetFirst() throws Exception { assertEquals(5.0, num("var.x~=5\nreturn x"), 1e-9); }
    @Test void initOrGetExisting() throws Exception { assertEquals(1.0, num("var.x=1\nvar.x~=99\nreturn x"), 1e-9); }
    @Test void argsIteration() throws Exception {
        assertEquals(6.0, num("var.f=-> {\nvar.t=0\nfor (i in Range(0, args.length)) { t += args[i] }\nreturn t\n}\nreturn f(1,2,3)"), 1e-9);
    }
    @Test void closureSeesOuterMutation() throws Exception {
        // 文档承诺：闭包共享引用，外层改值可见（variables.md）
        assertEquals(21.0, num("var.x=10\nvar.f=-> { return x + 1 }\nval.r1=f()\nx=20\nreturn f()"), 1e-9);
    }
    @Test void globalNamespace() throws Exception { assertEquals(10.0, num("global.s=0\nglobal.s+=10\nreturn global.s"), 1e-9); }
    @Test void undefinedBareReadsNone() throws Exception { assertTrue(isNone("return someUndefinedVar")); }

    // ===================== control-flow.md =====================

    @Test void elifChainThird() throws Exception {
        assertEquals(3.0, num("var.x=-5\nif (x > 0) { return 1 } elif (x == 0) { return 2 } elif (x > -10) { return 3 } else { return 4 }"), 1e-9);
    }
    @Test void forCStyleIncrement() throws Exception {
        assertEquals(45.0, num("var.s=0\nfor (var.i=0; i < 10; i++) { s += i }\nreturn s"), 1e-9);
    }
    @Test void switchFallthrough() throws Exception {
        assertEquals(30.0, num("var.r=0\nswitch (1) { case 1 { r += 10 } case 2 { r += 20 } }\nreturn r"), 1e-9);
    }
    @Test void switchBreakStopsFallthrough() throws Exception {
        assertEquals(10.0, num("var.r=0\nswitch (1) { case 1 { r += 10\n break } case 2 { r += 20 } }\nreturn r"), 1e-9);
    }
    @Test void matchNoFallthrough() throws Exception {
        assertEquals(10.0, num("var.r=0\nmatch (1) { case 1 { r += 10 } case 2 { r += 20 } }\nreturn r"), 1e-9);
    }
    @Test void breakInForLoop() throws Exception {
        assertEquals(11.0, num("var.r=0\nfor (i in Range(0,100)) { if (i > 10) { break }\n r += 1 }\nreturn r"), 1e-9);
    }
    @Test void nextSkipsIteration() throws Exception {
        assertEquals(5.0, num("var.c=0\nfor (i in Range(0,10)) { if (i % 2 == 0) { next }\n c += 1 }\nreturn c"), 1e-9);
    }
    @Test void bareReturnYieldsNone() throws Exception {
        assertTrue(isNone("var.f=-> { if (args[0] < 0) { return }\n return args[0] }\nreturn f(-5)"));
    }
    @Test void forInMapKeyValue() throws Exception {
        assertEquals("a=1.0;b=2.0;", str("val.m={'a':1,'b':2}\nvar.s=''\nfor (k, v in m) { s += k + '=' + v + ';' }\nreturn s")); // Shimmer 对齐
    }

    // ===================== functions.md =====================

    @Test void closureMutableCounter() throws Exception {
        assertEquals(3.0, num("var.counter=-> { var.count=0\n return -> { count++\n return count } }\nval.n=counter()\nvar.a=n()\nvar.b=n()\nreturn a + b"), 1e-9);
    }
    @Test void reduceNoInitial() throws Exception {
        assertEquals(15.0, num("return [1,2,3,4,5].reduce(-> { return args[0] + args[1] })"), 1e-9);
    }
    @Test void listFindIndex() throws Exception {
        assertEquals(2.0, num("return [10,20,30,40].findIndex(-> { return args[0] > 25 })"), 1e-9);
    }
    @Test void listFlatMap() throws Exception {
        assertEquals(6.0, num("return [1,2,3].flatMap(-> { return [args[0], args[0]*10] }).size()"), 1e-9);
    }
    @Test void listSortByKey() throws Exception {
        assertEquals("fig", str("val.l=['banana','apple','fig']\nl.sortBy(-> { return args[0].length() })\nreturn l[0]"));
    }
    @Test void listJoinDefaultSep() throws Exception {
        assertEquals("1.0,2.0,3.0", str("return [1,2,3].join()")); // Shimmer 对齐：数字恒 double 格式
    }
    @Test void listForEachIndex() throws Exception {
        assertEquals("0.01.02.0", str("var.s=''\n['a','b','c'].forEach(-> { s += args[1] })\nreturn s")); // Shimmer 对齐：索引是数字→N.0
    }
    @Test void mapFilterByValue() throws Exception {
        assertEquals(2.0, num("val.s={'math':90,'english':55,'science':80}\nval.p=s.filter(-> { return args[1] >= 60 })\nreturn p.size()"), 1e-9);
    }
    @Test void mapMapValues() throws Exception {
        assertEquals(10.0, num("val.p={'apple':5}\nval.d=p.mapValues(-> { return args[0]*2 })\nreturn d['apple']"), 1e-9);
    }
    @Test void mapPutIfAbsent() throws Exception {
        assertEquals(1.0, num("val.m={'a':1}\nm.putIfAbsent('a',9)\nreturn m['a']"), 1e-9);
    }
    @Test void mapGetOrDefault() throws Exception {
        assertEquals(5.0, num("return {'a':1}.getOrDefault('x',5)"), 1e-9);
    }

    // ===================== error-handling.md =====================

    @Test void tryFinallyOnly() throws Exception {
        assertEquals(2.0, num("var.r=0\ntry { r=1 } finally { r=2 }\nreturn r"), 1e-9);
    }
    @Test void diagFinallyRunsEmptyTry() throws Exception {
        assertEquals(9.0, num("var.r=0\ntry { } finally { r=9 }\nreturn r"), 1e-9);
    }
    @Test void diagTryBodyRunsWithEmptyCatch() throws Exception {
        assertEquals(5.0, num("var.r=0\ntry { r=5 } catch { }\nreturn r"), 1e-9);
    }
    @Test void diagTryCatchFinallyNoException() throws Exception {
        assertEquals(7.0, num("var.r=0\ntry { r=3 } catch (e) { r=99 } finally { r=7 }\nreturn r"), 1e-9);
    }
    @Test void catchNoVariable() throws Exception {
        assertEquals(1.0, num("try { throw 'e' } catch { return 1 }\nreturn 0"), 1e-9);
    }
    @Test void nestedTryRethrow() throws Exception {
        assertEquals("I:inner;O:rethrown: inner", str(
            "var.r=''\ntry { try { throw 'inner' } catch (e) { r += 'I:'+e+';'\n throw 'rethrown: '+e } } catch (e) { r += 'O:'+e }\nreturn r"));
    }
    @Test void uncaughtThrowPropagates() {
        assertThrows(AriaException.class, () -> eval("throw 'boom'"));
    }

    // ===================== classes.md =====================

    @Test void classValFieldDefaultRead() throws Exception {
        assertEquals("1.0", str("class C { val.version='1.0' }\nval.c=C()\nreturn c.version"));
    }
    @Test void superMultiArg() throws Exception {
        assertEquals("Rex", str(
            "class A { var.name='?'\nvar.age=0\nnew=-> { self.name=args[0]\n self.age=args[1] } }\n" +
            "class D extends A { var.breed='?'\nnew=-> { super(args[0],args[1])\n self.breed=args[2] } }\n" +
            "val.d=D('Rex',3,'Lab')\nreturn d.name"));
    }
    @Test void instanceofInheritance() throws Exception {
        assertTrue(bool("class A {}\nclass B extends A {}\nval.b=B()\nreturn b instanceof A"));
    }

    // ===================== stdlib.md（代表性未测函数）=====================

    @Test void mathTrig() throws Exception { assertEquals(0.0, num("return math.sin(0)"), 1e-9); }
    @Test void mathLog10() throws Exception { assertEquals(2.0, num("return math.log10(100)"), 1e-9); }
    @Test void mathExp() throws Exception { assertEquals(1.0, num("return math.exp(0)"), 1e-9); }
    @Test void mathHypot() throws Exception { assertEquals(5.0, num("return math.hypot(3,4)"), 1e-9); }
    @Test void mathCbrt() throws Exception { assertEquals(3.0, num("return math.cbrt(27)"), 1e-9); }
    @Test void mathSignum() throws Exception { assertEquals(-1.0, num("return math.signum(-4)"), 1e-9); }
    @Test void cryptoSha1Len() throws Exception { assertEquals(40.0, num("return crypto.sha1('abc').length()"), 1e-9); }
    @Test void cryptoSha512Len() throws Exception { assertEquals(128.0, num("return crypto.sha512('abc').length()"), 1e-9); }
    @Test void regexMatchAll() throws Exception { assertEquals(2.0, num("return regex.matchAll('\\\\d','a1b2').size()"), 1e-9); }
    @Test void regexReplaceFirst() throws Exception { assertEquals("aXb2", str("return regex.replaceFirst('\\\\d','a1b2','X')")); }
    @Test void datetimeDiffDays() throws Exception { assertEquals(1.0, num("return datetime.diff(0, 86400000, 'days')"), 1e-9); }
    @Test void datetimeFormatLen() throws Exception { assertEquals(19.0, num("return datetime.format(0).length()"), 1e-9); }
    @Test void listAddSize() throws Exception { assertEquals(1.0, num("val.l=[]\nl.add(1)\nreturn l.size()"), 1e-9); }
    @Test void listSort() throws Exception { assertEquals(1.0, num("val.l=[3,1,2]\nl.sort()\nreturn l.get(0)"), 1e-9); }
    @Test void listSubList() throws Exception { assertEquals(2.0, num("return [1,2,3].subList(0,2).size()"), 1e-9); }
    @Test void mapPutGet() throws Exception { assertEquals(1.0, num("val.m={}\nm.put('a',1)\nreturn m.get('a')"), 1e-9); }
    @Test void mapContainsValue() throws Exception { assertTrue(bool("return {'a':1}.containsValue(1)")); }
    @Test void jsonRoundTrip() throws Exception { assertEquals(1.0, num("return json.parse(json.stringify({'a':1}))['a']"), 1e-9); }

    // ===================== embedding.md：沙箱能力开关 =====================

    @Test void sandboxFsBlockedByCapabilityFlagAlone() {
        // .allowFileSystem(false) 单独（无白名单）即应阻止 fs（修复项）
        SandboxConfig config = SandboxConfig.builder().allowFileSystem(false).maxInstructions(100000).build();
        assertThrows(AriaException.class, () ->
            Aria.eval("return fs.read('nope.txt')", Aria.createContext(), config));
    }
    @Test void sandboxMathAllowedWithoutWhitelistWhenCapabilityOn() throws Exception {
        // 仅禁 fs，未设白名单：math 仍可用
        SandboxConfig config = SandboxConfig.builder().allowFileSystem(false).maxInstructions(100000).build();
        assertEquals(42.0, Aria.eval("return math.abs(-42)", Aria.createContext(), config).numberValue(), 1e-9);
    }
}
