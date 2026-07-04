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
 * Shimmer 对齐(A2, variables-7/8/9/controlflow-13)：裸名与 var./val. 命名空间完全隔离，
 * 本文件脚本已改为命名空间一致写法(混用写法在 Shimmer 语义下读不到值)。
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
    @Test void strReplaceAll() throws Exception { assertEquals("aXbX", str("return 'a1b2'.replaceAll('\\d','X')")); } // syntax-05: 转义原文,正则直接写 \d
    @Test void strReplaceFirst() throws Exception { assertEquals("aXb2", str("return 'a1b2'.replaceFirst('\\d','X')")); } // syntax-05
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
    // Shimmer 对齐: operators-7 —— bool+bool 走字符串拼接(Shimmer BooleanValue.addValue 漏 return bug)
    @Test void booleanMath() throws Exception { assertEquals("truetrue", str("return true + true")); }

    // list 运算
    @Test void listConcat() throws Exception { assertEquals(4.0, num("return ([1,2]+[3,4]).size()"), 1e-9); }
    @Test void listAppendElement() throws Exception { assertEquals(4.0, num("return ([1,2,3]+4).size()"), 1e-9); }
    @Test void listRemoveByIndex() throws Exception { assertEquals(2.0, num("l=[1,2,3]-0\nreturn l[0]"), 1e-9); }
    // Shimmer 对齐：显式索引越界抛异常（而非静默 none）
    @Test void listOutOfBoundsReadThrows() { assertThrows(AriaException.class, () -> eval("l=[1]\nreturn l[5]")); }
    @Test void listOutOfBoundsWriteFills() throws Exception { assertEquals(6.0, num("l=[1]\nl[5]=9\nreturn l.size()"), 1e-9); }

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
    @Test void orBitAssign() throws Exception { assertEquals(15.0, num("b=0b1100\nb|=0b0011\nreturn b"), 1e-9); }
    @Test void bitNot() throws Exception { assertEquals(-1.0, num("return ~0"), 1e-9); }

    // ~~ Range 双端闭 [start,end]（Shimmer 对齐）
    @Test void rangeMatchInside() throws Exception { assertTrue(bool("return 5 ~~ Range(1,10)")); }
    @Test void rangeMatchStartInclusive() throws Exception { assertTrue(bool("return 1 ~~ Range(1,10)")); }
    // Shimmer 对齐: operators-6 —— range 双端闭,end 含入
    @Test void rangeMatchEndInclusive() throws Exception { assertTrue(bool("return 10 ~~ Range(1,10)")); }
    // Shimmer 对齐: operators-6/syntax-03 —— 非 Range 右值一律 false(删相等回退)
    @Test void rangeMatchEqualityFallback() throws Exception { assertFalse(bool("return 5 ~~ 5")); }

    // in：map 键 / list 索引有效性
    @Test void inMapKeyTrue() throws Exception { assertTrue(bool("return 'name' in {'name':1}")); }
    @Test void inMapKeyFalse() throws Exception { assertFalse(bool("return 'x' in {'name':1}")); }
    @Test void inListIndexValid() throws Exception { assertTrue(bool("return 0 in [10,20,30]")); }
    @Test void inListIndexInvalid() throws Exception { assertFalse(bool("return 3 in [10,20,30]")); }

    // 三元 / Elvis / 空合并
    @Test void elvis() throws Exception { assertEquals(9.0, num("var.x=none\nreturn x ?: 9"), 1e-9); }
    @Test void nullish() throws Exception { assertEquals(8.0, num("var.x=none\nreturn x ?? 8"), 1e-9); }

    // 展开
    @Test void spreadList() throws Exception { assertEquals(5.0, num("a=[1,2,3]\nreturn [0,...a,4].size()"), 1e-9); }
    @Test void spreadMap() throws Exception { assertEquals(2.0, num("b={'x':1}\nreturn {...b,'y':2}.size()"), 1e-9); }
    @Test void spreadMapEmpty() throws Exception { assertEquals(0.0, num("return {...{}}.size()"), 1e-9); }
    @Test void spreadMapMultiple() throws Exception { assertEquals(2.0, num("return {...{'a':1}, ...{'b':2}}.size()"), 1e-9); }
    @Test void spreadMapOverride() throws Exception { assertEquals(9.0, num("return {'x':1, ...{'x':9}}['x']"), 1e-9); }
    @Test void spreadMapNewInstance() throws Exception { assertEquals(1.0, num("m={'a':1}\nc={...m}\nc['a']=9\nreturn m['a']"), 1e-9); }
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

    @Test void varReassign() throws Exception { assertEquals(20.0, num("var.x=10\nvar.x=20\nreturn var.x"), 1e-9); }
    @Test void initOrGetFirst() throws Exception { assertEquals(5.0, num("var.x~=5\nreturn var.x"), 1e-9); }
    @Test void initOrGetExisting() throws Exception { assertEquals(1.0, num("var.x=1\nvar.x~=99\nreturn var.x"), 1e-9); }
    @Test void argsIteration() throws Exception {
        assertEquals(6.0, num("var.f=-> {\nt=0\nfor (i in Range(0, args.length)) { t += args[i] }\nreturn t\n}\nreturn f(1,2,3)"), 1e-9);
    }
    @Test void closureSeesOuterMutation() throws Exception {
        // Shimmer 对齐(R2)：lambda 体与外层 scope 完全隔离(全新 ScopeStack)——体内 x 从 none 起步，
        // none + 1 = 1.0，外层 x 的变动不可见(Shimmer 实测 probes4 X15/X20)。文档同步更新。
        assertEquals(1.0, num("x=10\nvar.f=-> { return x + 1 }\nr1=f()\nx=20\nreturn f()"), 1e-9);
    }
    @Test void globalNamespace() throws Exception { assertEquals(10.0, num("global.s=0\nglobal.s+=10\nreturn global.s"), 1e-9); }
    @Test void undefinedBareReadsNone() throws Exception { assertTrue(isNone("return someUndefinedVar")); }

    // ===================== control-flow.md =====================

    @Test void elifChainThird() throws Exception {
        assertEquals(3.0, num("var.x=-5\nif (x > 0) { return 1 } elif (x == 0) { return 2 } elif (x > -10) { return 3 } else { return 4 }"), 1e-9);
    }
    @Test void forCStyleIncrement() throws Exception {
        assertEquals(45.0, num("s=0\nfor (i=0; i < 10; i++) { s += i }\nreturn s"), 1e-9);
    }
    @Test void switchFallthrough() throws Exception {
        // Shimmer 对齐(controlflow-03)：非穿透,case 1 执行后比对值被块结果 10.0 替换 => 10
        assertEquals(10.0, num("r=0\nswitch (1) { case 1 { r += 10 } case 2 { r += 20 } }\nreturn r"), 1e-9);
    }
    @Test void switchBreakStopsFallthrough() throws Exception {
        assertEquals(10.0, num("r=0\nswitch (1) { case 1 { r += 10\n break } case 2 { r += 20 } }\nreturn r"), 1e-9);
    }
    @Test void matchNoFallthrough() throws Exception {
        assertEquals(10.0, num("r=0\nmatch (1) { case 1 { r += 10 } case 2 { r += 20 } }\nreturn r"), 1e-9);
    }
    @Test void breakInForLoop() throws Exception {
        assertEquals(11.0, num("r=0\nfor (i in Range(0,100)) { if (i > 10) { break }\n r += 1 }\nreturn r"), 1e-9);
    }
    @Test void nextSkipsIteration() throws Exception {
        assertEquals(5.0, num("c=0\nfor (i in Range(0,10)) { if (i % 2 == 0) { next }\n c += 1 }\nreturn c"), 1e-9);
    }
    @Test void bareReturnYieldsNone() throws Exception {
        assertTrue(isNone("var.f=-> { if (args[0] < 0) { return }\n return args[0] }\nreturn f(-5)"));
    }
    @Test void forInMapKeyValue() throws Exception {
        assertEquals("a=1.0;b=2.0;", str("m={'a':1,'b':2}\ns=''\nfor (k, v in m) { s += k + '=' + v + ';' }\nreturn s")); // Shimmer 对齐
    }

    // ===================== functions.md =====================

    @Test void closureMutableCounter() throws Exception {
        // Shimmer 对齐(R2)：闭包不捕获外层 scope——count 每次调用从 none 起步，n() 恒 1(none++ → 1)，
        // a+b = 2。持久计数需用 var. 存储。
        assertEquals(2.0, num("var.counter=-> { count=0\n return -> { count++\n return count } }\nn=counter()\na=n()\nb=n()\nreturn a + b"), 1e-9);
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
        assertEquals("fig", str("l=['banana','apple','fig']\nl.sortBy(-> { return args[0].length() })\nreturn l[0]"));
    }
    @Test void listJoinDefaultSep() throws Exception {
        assertEquals("1.0,2.0,3.0", str("return [1,2,3].join()")); // Shimmer 对齐：数字恒 double 格式
    }
    @Test void listForEachIndex() throws Exception {
        // Shimmer 对齐: operators-3/gui-chain-10 —— 拼接结果 canBeNumber 重算:
        // ''+0.0="0.0"(可数) → "0.0"+1.0=1.0(数值加) → 1.0+2.0=3.0
        // Shimmer 对齐(R2)：lambda 隔离后裸名累加器写不透外层——累加器改用 var. 存储。
        assertEquals("3.0", str("var.s=''\n['a','b','c'].forEach(-> { var.s += args[1] })\nreturn var.s"));
        // 隔离本身：裸名 s 在 lambda 体内的写入对外层不可见
        assertEquals("", str("s=''\n['a','b','c'].forEach(-> { s += args[1] })\nreturn s"));
    }
    @Test void mapFilterByValue() throws Exception {
        assertEquals(2.0, num("s={'math':90,'english':55,'science':80}\np=s.filter(-> { return args[1] >= 60 })\nreturn p.size()"), 1e-9);
    }
    @Test void mapMapValues() throws Exception {
        assertEquals(10.0, num("p={'apple':5}\nd=p.mapValues(-> { return args[0]*2 })\nreturn d['apple']"), 1e-9);
    }
    @Test void mapPutIfAbsent() throws Exception {
        assertEquals(1.0, num("m={'a':1}\nm.putIfAbsent('a',9)\nreturn m['a']"), 1e-9);
    }
    @Test void mapGetOrDefault() throws Exception {
        assertEquals(5.0, num("return {'a':1}.getOrDefault('x',5)"), 1e-9);
    }

    // ===================== error-handling.md =====================

    @Test void tryFinallyOnly() throws Exception {
        assertEquals(2.0, num("r=0\ntry { r=1 } finally { r=2 }\nreturn r"), 1e-9);
    }
    @Test void diagFinallyRunsEmptyTry() throws Exception {
        assertEquals(9.0, num("r=0\ntry { } finally { r=9 }\nreturn r"), 1e-9);
    }
    @Test void diagTryBodyRunsWithEmptyCatch() throws Exception {
        assertEquals(5.0, num("r=0\ntry { r=5 } catch { }\nreturn r"), 1e-9);
    }
    @Test void diagTryCatchFinallyNoException() throws Exception {
        assertEquals(7.0, num("r=0\ntry { r=3 } catch (e) { r=99 } finally { r=7 }\nreturn r"), 1e-9);
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
        assertEquals("1.0", str("class C { val.version='1.0' }\nc=C()\nreturn c.version"));
    }
    @Test void superMultiArg() throws Exception {
        assertEquals("Rex", str(
            "class A { var.name='?'\nvar.age=0\nnew=-> { self.name=args[0]\n self.age=args[1] } }\n" +
            "class D extends A { var.breed='?'\nnew=-> { super(args[0],args[1])\n self.breed=args[2] } }\n" +
            "d=D('Rex',3,'Lab')\nreturn d.name"));
    }
    @Test void instanceofInheritance() throws Exception {
        assertTrue(bool("class A {}\nclass B extends A {}\nb=B()\nreturn b instanceof A"));
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
    @Test void regexMatchAll() throws Exception { assertEquals(2.0, num("return regex.matchAll('\\d','a1b2').size()"), 1e-9); } // syntax-05
    @Test void regexReplaceFirst() throws Exception { assertEquals("aXb2", str("return regex.replaceFirst('\\d','a1b2','X')")); } // syntax-05
    @Test void datetimeDiffDays() throws Exception { assertEquals(1.0, num("return datetime.diff(0, 86400000, 'days')"), 1e-9); }
    @Test void datetimeFormatLen() throws Exception { assertEquals(19.0, num("return datetime.format(0).length()"), 1e-9); }
    @Test void listAddSize() throws Exception { assertEquals(1.0, num("l=[]\nl.add(1)\nreturn l.size()"), 1e-9); }
    @Test void listSort() throws Exception { assertEquals(1.0, num("l=[3,1,2]\nl.sort()\nreturn l.get(0)"), 1e-9); }
    @Test void listSubList() throws Exception { assertEquals(2.0, num("return [1,2,3].subList(0,2).size()"), 1e-9); }
    @Test void mapPutGet() throws Exception { assertEquals(1.0, num("m={}\nm.put('a',1)\nreturn m.get('a')"), 1e-9); }
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

    // ===================== Shimmer 全量对齐后的文档新承诺（2026-07 docs 同步新增） =====================

    // variables.md：脚本写 val 静默 no-op（val 仅宿主 forceSetValue 可写）
    @Test void valScriptWriteIsNoOp() throws Exception { assertTrue(isNone("val.x = 5\nreturn val.x")); }
    // variables.md：裸名与 var. 完全隔离，读写互不回退
    @Test void bareDoesNotReadVar() throws Exception { assertTrue(isNone("var.x = 10\nreturn x")); }
    @Test void varDoesNotReadBare() throws Exception { assertTrue(isNone("x = 10\nreturn var.x")); }
    // variables.md：宿主注入 val 后脚本可读、脚本写不掉
    @Test void hostInjectedValReadableAndProtected() throws Exception {
        priv.seventeen.artist.aria.context.Context ctx = Aria.createContext();
        ctx.forceSetLocalValue(priv.seventeen.artist.aria.context.VariableKey.of("PI"),
                new priv.seventeen.artist.aria.value.NumberValue(3.5));
        assertEquals(3.5, Aria.eval("val.PI = 0\nreturn val.PI", ctx).numberValue(), 1e-9);
    }

    // control-flow.md / stdlib.md：Range 双端闭；start>end 无步长为空；显式负步长可倒序；<2 参 →(0,0)
    @Test void rangeLiteralClosedIteration() throws Exception {
        assertEquals("0.0;1.0;2.0;3.0;", str("r=''\nfor (i in 0..3) { r += i + ';' }\nreturn r"));
    }
    @Test void rangeReversedIsEmptyWithoutStep() throws Exception {
        assertEquals(0.0, num("c=0\nfor (i in Range(3,1)) { c += 1 }\nreturn c"), 1e-9);
    }
    @Test void rangeExplicitNegativeStep() throws Exception {
        assertEquals("3.0;2.0;1.0;", str("r=''\nfor (i in Range(3,1,-1)) { r += i + ';' }\nreturn r"));
    }
    @Test void rangeUnderTwoArgsIsZeroZero() throws Exception {
        assertTrue(bool("return 0 ~~ Range(5)"));
        assertFalse(bool("return 5 ~~ Range(5)"));
    }
    // control-flow.md：循环变量循环后可见；含 none 列表完整遍历
    @Test void loopVarVisibleAfterLoop() throws Exception { assertEquals(3.0, num("for (i in 1..3) { }\nreturn i"), 1e-9); }
    @Test void listWithNoneTraversesFully() throws Exception {
        assertEquals(3.0, num("c=0\nfor (x in [1,none,3]) { c += 1 }\nreturn c"), 1e-9);
    }
    // control-flow.md：while 内 break 无外层 for 时整脚本终止（返回 none）
    @Test void breakInWhileTerminatesScript() throws Exception {
        assertTrue(isNone("i=0\nwhile (true) { if (i >= 5) { break }\ni++ }\nreturn i"));
    }

    // operators.md：数字/字符串/布尔特殊运算（bug-for-bug 兼容 Shimmer）
    @Test void stringPlusStringNumeric() throws Exception { assertEquals(5.0, num("return '2' + '3'"), 1e-9); }
    @Test void stringPlusNumberConcatDoubleFormat() throws Exception { assertEquals("slot1.0", str("return 'slot' + 1")); }
    @Test void eqStringFallback() throws Exception { assertTrue(bool("return true == 'true'")); }
    @Test void boolMinusNumberIsAdd() throws Exception { assertEquals(2.0, num("return true - 1"), 1e-9); }
    @Test void divisionByZeroYieldsZero() throws Exception { assertEquals(0.0, num("return 1/0"), 1e-9); }
    @Test void numberPlusListThrows() { assertThrows(AriaException.class, () -> eval("return 1 + [1]")); }
    @Test void listPlusIsInPlace() throws Exception {
        assertEquals(3.0, num("a=[1,2]\nb=a+3\nreturn a.size()"), 1e-9); // 原地追加并返回同一列表
    }
    @Test void numericStringIsFalsy() throws Exception { assertEquals(2.0, num("return '2' ? 1 : 2"), 1e-9); }

    // functions.md：赋值 RHS 裸名读取的零参自动调用；空括号吞括号；非函数带参调用抛错
    @Test void assignmentRhsBareCallableAutoInvokes() throws Exception {
        assertEquals(9.0, num("f = -> { return 9 }\ng = f\nreturn g"), 1e-9);
    }
    @Test void emptyParensOnNonCallableSwallowed() throws Exception { assertEquals(5.0, num("x = 5\nreturn x()"), 1e-9); }
    @Test void argsCallOnNonCallableThrows() { assertThrows(AriaException.class, () -> eval("x = 5\nreturn x(1)")); }
    // functions.md：裸名 lambda 自递归抛错（作用域隔离解析不到）；var.f 自递归可用
    @Test void bareLambdaSelfRecursionThrows() {
        assertThrows(AriaException.class, () -> eval("f = -> { if (args[0] <= 1) { return 1 }\nreturn args[0] * f(args[0] - 1) }\nreturn f(5)"));
    }
    @Test void varLambdaSelfRecursionWorks() throws Exception {
        assertEquals(120.0, num("var.fact = -> { if (args[0] <= 1) { return 1 }\nreturn args[0] * fact(args[0] - 1) }\nreturn fact(5)"), 1e-9);
    }
    // variables.md：args 越界返回 none
    @Test void argsOutOfBoundsIsNone() throws Exception { assertTrue(isNone("var.f = -> { return args[5] }\nreturn f(1)")); }

    // stdlib.md：list 函数矩阵
    @Test void listRemoveByValueReturnsBoolean() throws Exception { assertTrue(bool("l=[1,2,3]\nreturn l.remove(2)")); }
    @Test void listRemoveIndexReturnsElement() throws Exception { assertEquals(20.0, num("l=[10,20,30]\nreturn l.removeIndex(1)"), 1e-9); }
    @Test void listRemoveIndexOobIsNone() throws Exception { assertTrue(isNone("l=[1,2]\nreturn l.removeIndex(9)")); }
    @Test void listAddAtIndex() throws Exception { assertEquals(9.0, num("l=[1,2]\nl.add(0,9)\nreturn l[0]"), 1e-9); }
    @Test void listSetReturnsOldElement() throws Exception { assertEquals(1.0, num("l=[1,2]\nreturn l.set(0,9)"), 1e-9); }
    @Test void listContainsCrossTypeEq() throws Exception { assertTrue(bool("return [1,2,3].contains('2')")); }
    @Test void listIndexOfCrossTypeEq() throws Exception { assertEquals(1.0, num("return [1,2,3].indexOf('2')"), 1e-9); }
    @Test void listContainsAll() throws Exception { assertTrue(bool("return [1,2,3].containsAll([1,2])")); }
    @Test void listRetainAll() throws Exception { assertEquals(1.0, num("l=[1,2,3]\nl.retainAll([2])\nreturn l.size()"), 1e-9); }
    @Test void listAddAllReturnsBoolean() throws Exception { assertTrue(bool("l=[1]\nreturn l.addAll([2,3])")); }
    @Test void listSubListFullRangeIsNone() throws Exception { assertTrue(isNone("return [1,2,3].subList(0,3)")); }
    @Test void listGetOobIsNone() throws Exception { assertTrue(isNone("return [1,2].get(5)")); }

    // stdlib.md：string 函数
    @Test void splitDropsTrailingEmpties() throws Exception { assertEquals(2.0, num("return 'a,b,,'.split(',').size()"), 1e-9); }
    @Test void splitWithLimit() throws Exception { assertEquals("b,c", str("return 'a,b,c'.split(',', 2)[1]")); }
    @Test void startsWithOffset() throws Exception { assertTrue(bool("return 'abcabc'.startsWith('b', 1)")); }
    @Test void indexOfFrom() throws Exception { assertEquals(3.0, num("return 'abcabc'.indexOf('a', 1)"), 1e-9); }
    @Test void replaceSingleArgReturnsOriginal() throws Exception { assertEquals("hello", str("return 'hello'.replace('l')")); }

    // stdlib.md：map 函数
    @Test void mapPutReturnsOldValue() throws Exception { assertEquals(1.0, num("m={'a':1}\nreturn m.put('a',2)"), 1e-9); }
    @Test void mapPutAllNonMapSilentlyIgnored() throws Exception { assertEquals(1.0, num("m={'a':1}\nm.putAll(5)\nreturn m.size()"), 1e-9); }

    // stdlib.md：math 新增
    @Test void mathPiLowercase() throws Exception { assertEquals(Math.PI, num("return math.pi()"), 1e-12); }
    @Test void mathNextAfter() throws Exception { assertEquals(Math.nextAfter(1.0, 2.0), num("return math.nextAfter(1, 2)"), 0.0); }

    // stdlib.md：UUID 回退与新方法
    @Test void uuidInvalidStringFallsBackToRandom() throws Exception {
        assertEquals(36.0, num("u = UUID('not-a-uuid')\nreturn ('' + u).length()"), 1e-9);
    }
    @Test void uuidVersionMethod() throws Exception {
        assertEquals(4.0, num("u = UUID('550e8400-e29b-41d4-a716-446655440000')\nreturn u.version()"), 1e-9);
    }

    // error-handling.md：运行时错误消息格式与常见错误
    @Test void runtimeErrorMessageShimmerFormat() {
        AriaException ex = assertThrows(AriaException.class, () -> {
            var unit = Aria.compile("demo", Aria.createContext(), "return [1,2][5]");
            unit.execute();
        });
        assertTrue(ex.getMessage().contains("单元: [demo] 运行时错误"), ex.getMessage());
        assertTrue(ex.getMessage().contains("请检查: return [1,2][5]"), ex.getMessage());
        assertTrue(ex.getMessage().contains("列表索引越界"), ex.getMessage());
    }
    @Test void unknownNamespaceFunctionMessage() {
        AriaException ex = assertThrows(AriaException.class, () -> eval("return math.nope(1)"));
        assertTrue(ex.getMessage().contains("点运算解析工具集函数不存在"), ex.getMessage());
    }
    @Test void callNonCallableMessage() {
        AriaException ex = assertThrows(AriaException.class, () -> eval("x = 5\nreturn x(1)"));
        assertTrue(ex.getMessage().contains("不支持的后缀运算"), ex.getMessage());
    }

    // embedding.md：lenient 编译截断保留前缀 + 警告；严格模式 fail-fast
    @Test void lenientCompileTruncatesAndWarns() throws Exception {
        priv.seventeen.artist.aria.context.Context ctx = Aria.createContext();
        var unit = Aria.compile("lenient-doc", ctx, "var.a = 1\nvar.b = {bad\nvar.a = 99\nreturn var.a\n", true);
        assertEquals(1.0, unit.execute().numberValue(), 1e-9);
        assertFalse(unit.getWarnings().isEmpty());
        assertTrue(unit.getWarnings().get(0).contains("[lenient]"), unit.getWarnings().get(0));
    }

    // embedding.md：createSharedCallContext + 宿主常驻层 pushScope 契约
    @Test void sharedCallContextSharesBareNames() throws Exception {
        priv.seventeen.artist.aria.context.Context caller = Aria.createContext();
        caller.pushScope(); // 宿主常驻层
        caller.getScopeStack().get(priv.seventeen.artist.aria.context.VariableKey.of("flag"))
                .setValue(new priv.seventeen.artist.aria.value.NumberValue(1));
        priv.seventeen.artist.aria.context.Context shared = caller.createSharedCallContext(null, new IValue<?>[0]);
        Aria.eval("flag = 9", shared);
        assertEquals(9.0, Aria.eval("return flag", caller).numberValue(), 1e-9);
    }
}
