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
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.value.BooleanValue;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.StoreOnlyValue;
import priv.seventeen.artist.aria.value.StringValue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A5 内建函数矩阵对齐回归：逐函数对照 Shimmer 的
 * ListObjectFunction / TextObjectFunction / MapObjectFunction / MathFunctions。
 * 每个用例冷(第 1 次=解释器)+热(WARM 次=JIT)双跑断言一致；预期值=Shimmer 行为(bug-for-bug)。
 * 另含第 6 项(解释器内部一致性)：executeInlineInternal 内联表移除 math.round、
 * 数值递归快路径选路收紧到 A4 白名单同级的守卫用例。
 */
public class ShimmerAlignmentA5Test {

    @BeforeAll static void s() { Aria.getEngine().initialize(); }
    @BeforeEach void r() { Interpreter.resetCallDepth(); Interpreter.clearSandbox(); }

    private static final int WARM = 5;

    private Context ctx() { return Aria.createContext(); }

    /** 冷(解释)+热(JIT)双跑：全部执行结果类型与字符串一致，返回首个结果。 */
    private IValue<?> parity(String code) throws AriaException {
        AriaCompiledRoutine r = Aria.compile("a5", code.endsWith("\n") ? code : code + "\n");
        IValue<?> first = r.execute(ctx());
        for (int i = 0; i < WARM; i++) {
            IValue<?> v = r.execute(ctx());
            assertEquals(first.getClass(), v.getClass(), "第" + (i + 2) + "次执行结果类型漂移: " + code);
            assertEquals(first.stringValue(), v.stringValue(), "第" + (i + 2) + "次执行结果值漂移: " + code);
        }
        return first;
    }

    private void parityNum(String code, double expected) throws AriaException {
        IValue<?> v = parity(code);
        assertEquals(expected, v.numberValue(), 1e-12, code);
    }

    private void parityStr(String code, String expected) throws AriaException {
        assertEquals(expected, parity(code).stringValue(), code);
    }

    private void parityBool(String code, boolean expected) throws AriaException {
        IValue<?> v = parity(code);
        assertInstanceOf(BooleanValue.class, v, code + " 应返回 boolean");
        assertEquals(expected, v.booleanValue(), code);
    }

    /** 抛错脚本：冷/热每次都抛且消息含指定片段。 */
    private void parityThrows(String code, String expectMsgPart) throws AriaException {
        AriaCompiledRoutine r = Aria.compile("a5e", code.endsWith("\n") ? code : code + "\n");
        for (int i = 0; i <= WARM; i++) {
            final int round = i;
            AriaException ex = assertThrows(AriaException.class, () -> r.execute(ctx()),
                    "第" + (round + 1) + "次执行应抛错: " + code);
            assertTrue(ex.getMessage() != null && ex.getMessage().contains(expectMsgPart),
                    "第" + (round + 1) + "次异常消息 [" + ex.getMessage() + "] 应含 [" + expectMsgPart + "]");
        }
    }

    // ================= 1. ListFunctions(对照 ListObjectFunction) =================

    // builtins-object-1/interop-5：remove(value) 按值删除(eq 语义)返回 boolean
    @Test void listRemoveByValue() throws Exception {
        parityBool("var.l = ['a','b','c']\nreturn var.l.remove('b')", true);
        parityStr("var.l = ['a','b','c']\nvar.l.remove('b')\nreturn var.l[0] + '|' + var.l[1]", "a|c");
        parityNum("var.l = ['a','b','c']\nvar.l.remove('b')\nreturn var.l.size()", 2);
        // interop-5 snippet：值 2 不存在(元素是 9/8/7) → 不删、返回 false
        parityBool("var.l = [9,8,7]\nreturn var.l.remove(2)", false);
        parityNum("var.l = [9,8,7]\nvar.l.remove(2)\nreturn var.l.size()", 3);
        // 跨类型 eq：字符串 "2" 删除数字 2
        parityNum("var.l = [1,2,3]\nvar.l.remove('2')\nreturn var.l.size()", 2);
    }

    // builtins-object-2：removeIndex(i) 按索引删；上越界 none、负索引抛、返回被删元素
    @Test void listRemoveIndex() throws Exception {
        parityNum("var.l = [10,20,30]\nreturn var.l.removeIndex(1)", 20);
        parityNum("var.l = [10,20,30]\nvar.l.removeIndex(0)\nreturn var.l.size()", 2);
        IValue<?> oob = parity("var.l = [1,2,3]\nreturn var.l.removeIndex(9)");
        assertInstanceOf(NoneValue.class, oob, "removeIndex 上越界应返回 none");
        parityNum("var.l = [1,2,3]\nvar.l.removeIndex(9)\nreturn var.l.size()", 3);
        parityThrows("var.i = 0 - 1\nreturn [1,2].removeIndex(var.i)", "对象函数调用失败");
    }

    // builtins-object-3：add(e) 追加 / add(i,e) 在 i 处插入
    @Test void listAddTwoArgInsert() throws Exception {
        parityStr("var.l = ['a','c']\nvar.l.add(1, 'b')\nreturn var.l[1]", "b");
        parityNum("var.l = ['a','c']\nvar.l.add(1, 'b')\nreturn var.l.size()", 3);
        parityNum("var.l = [1]\nvar.l.add(2)\nreturn var.l.size()", 2);
    }

    // builtins-object-4：contains/indexOf/lastIndexOf 走 eq 语义(跨类型数值相等)
    @Test void listContainsEqSemantics() throws Exception {
        parityBool("return [1,2,3].contains('2')", true);
        parityBool("return [1,2,3].contains(2)", true);
        parityBool("return [1,2,3].contains('x')", false);
        parityNum("return [1,2,3].indexOf('2')", 1);
        parityNum("return [1,2,3].indexOf(9)", -1);
        parityNum("return [2,1,2].lastIndexOf('2')", 2);
    }

    // builtins-object-9：set(i,v) 返回旧元素；i>=size 返回 none；负索引抛
    @Test void listSetReturnsOldElement() throws Exception {
        parityStr("var.l = ['a','b']\nreturn var.l.set(0, 'x')", "a");
        parityStr("var.l = ['a','b']\nvar.l.set(0, 'x')\nreturn var.l[0]", "x");
        IValue<?> oob = parity("var.l = ['a','b']\nreturn var.l.set(5, 'x')");
        assertInstanceOf(NoneValue.class, oob, "set 上越界应返回 none");
        parityThrows("var.i = 0 - 1\nvar.l = ['a']\nreturn var.l.set(var.i, 'x')", "对象函数调用失败");
    }

    // builtins-object-9：get 上越界 none、负索引抛
    @Test void listGetBounds() throws Exception {
        IValue<?> oob = parity("return [1,2].get(9)");
        assertInstanceOf(NoneValue.class, oob, "get 上越界应返回 none");
        parityThrows("var.i = 0 - 1\nreturn [1,2].get(var.i)", "对象函数调用失败");
    }

    // builtins-object-9：subList(a,b) 需 a<size 且 b<size，否则 none(subList(0,size)=none, bug-for-bug)；单参删除
    @Test void listSubListShimmerBounds() throws Exception {
        parityNum("return [1,2,3].subList(0, 2).size()", 2);
        IValue<?> full = parity("return [1,2,3].subList(0, 3)");
        assertInstanceOf(NoneValue.class, full, "subList(0,size) 在 Shimmer 也是 none(bug-for-bug)");
        IValue<?> single = parity("return [1,2,3].subList(1)");
        assertInstanceOf(NoneValue.class, single, "单参 subList 已删除(Shimmer 无) → none");
    }

    // builtins-object-7：containsAll/retainAll 新增返回 boolean；addAll/removeAll 返回 boolean + listValue 单元素包装
    @Test void listContainsAll() throws Exception {
        parityStr("if([1,2,3].containsAll([1,2])){ var.out = 'yes' } else { var.out = 'no' }\nreturn var.out", "yes");
        parityBool("return [1,2,3].containsAll([1,9])", false);
        // bug-for-bug：Shimmer containsAll 走 HashSet(hash 门)——跨类型 eq 相等但 hash 不同 → 查不到
        parityBool("return [1,2].containsAll(['1'])", false);
        // 非 list 参数按 listValue() 单元素包装
        parityBool("return [1,2].containsAll(2)", true);
    }

    @Test void listRetainAll() throws Exception {
        parityBool("var.l = [1,2,3]\nreturn var.l.retainAll([2])", true);
        parityNum("var.l = [1,2,3]\nvar.l.retainAll([2])\nreturn var.l.size()", 1);
        // retainAll 是 List.contains 纯 eq(无 hash 门)：'2' 与 2 跨类型相等
        parityNum("var.l = [1,2,3]\nvar.l.retainAll(['2'])\nreturn var.l[0]", 2);
        parityBool("var.l = [2]\nreturn var.l.retainAll([2])", false);
    }

    @Test void listAddAllRemoveAll() throws Exception {
        parityBool("var.l = [1]\nreturn var.l.addAll([2,3])", true);
        parityBool("var.l = [1]\nreturn var.l.addAll([])", false);
        // 非 list 参数 → IData.listValue() 单元素包装(不强转 CCE)
        parityNum("var.l = [1]\nvar.l.addAll(5)\nreturn var.l.size()", 2);
        parityBool("var.l = [1,2,1]\nreturn var.l.removeAll(1)", true);
        parityNum("var.l = [1,2,1]\nvar.l.removeAll(1)\nreturn var.l.size()", 1);
        parityBool("var.l = [1,2]\nreturn var.l.removeAll([9])", false);
    }

    // ================= 2. StringFunctions(对照 TextObjectFunction) =================

    // builtins-object-5：split 默认 limit=0(丢尾部空串)、双参 limit
    @Test void stringSplitLimit() throws Exception {
        parityNum("return 'a,b,,'.split(',').size()", 2);
        parityNum("return 'a,b,c'.split(',', 2).size()", 2);
        parityStr("return 'a,b,c'.split(',', 2)[1]", "b,c");
        parityNum("return 'a,b,c'.split(',').size()", 3);
    }

    // builtins-object-8：startsWith(prefix,toffset)/indexOf(s,from)/lastIndexOf(s,from) 双参
    @Test void stringTwoArgOverloads() throws Exception {
        parityBool("return 'abcabc'.startsWith('b', 1)", true);
        parityBool("return 'abcabc'.startsWith('a', 1)", false);
        parityNum("return 'abcabc'.indexOf('a', 1)", 3);
        parityNum("return 'abcabc'.lastIndexOf('a', 4)", 3);
        parityNum("return 'abcabc'.lastIndexOf('a', 2)", 0);
    }

    // builtins-object-8：replace 单参返回原串(不是删除)；replaceAll/replaceFirst 同模式
    @Test void stringReplaceSingleArgReturnsOriginal() throws Exception {
        parityStr("return 'hello'.replace('l')", "hello");
        parityStr("return 'a1b2'.replaceAll('[0-9]')", "a1b2");
        parityStr("return 'a1b2'.replaceFirst('[0-9]')", "a1b2");
        parityStr("return 'hello'.replace('l', 'L')", "heLLo");
    }

    // builtins-object-8：toUpper/LowerCase 用 Locale.ROOT(行为固化)
    @Test void stringCaseLocaleRoot() throws Exception {
        parityStr("return 'imi'.toUpperCase()", "IMI");
        parityStr("return 'IMI'.toLowerCase()", "imi");
    }

    // ================= 3. MapFunctions(对照 MapObjectFunction) =================

    // put(k,v) 返回被替换的旧值(无则 none)
    @Test void mapPutReturnsOldValue() throws Exception {
        parityNum("var.m = {'a': 1}\nreturn var.m.put('a', 2)", 1);
        IValue<?> fresh = parity("var.m = {'a': 1}\nreturn var.m.put('b', 2)");
        assertInstanceOf(NoneValue.class, fresh, "put 新键应返回 none");
        parityNum("var.m = {'a': 1}\nvar.m.put('a', 2)\nreturn var.m.get('a')", 2);
    }

    // builtins-object-10：putAll 鸭子类型(任意 jvmValue instanceof Map)；非 map 参数静默 no-op
    @Test void mapPutAllDuckTyped() throws Exception {
        parityNum("var.m = {'a':1}\nvar.m.putAll({'b':2,'c':3})\nreturn var.m.size()", 3);
        parityNum("var.m = {'a':1}\nvar.m.putAll(5)\nreturn var.m.size()", 1);
    }

    // bug-for-bug：putIfAbsent 键已存在 → StoreOnlyValue(旧值)(Shimmer CachedCallable 对 Object 返回类型的包装)；
    // 键不存在 → none 且写入。
    @Test void mapPutIfAbsentStoreOnlyWrap() throws Exception {
        IValue<?> existing = parity("var.m = {'a': 1}\nreturn var.m.putIfAbsent('a', 9)");
        assertInstanceOf(StoreOnlyValue.class, existing, "已存在键的 putIfAbsent 返回 StoreOnlyValue 包装(bug-for-bug)");
        IValue<?> absent = parity("var.m = {}\nreturn var.m.putIfAbsent('a', 9)");
        assertInstanceOf(NoneValue.class, absent, "不存在键的 putIfAbsent 返回 none");
        parityNum("var.m = {}\nvar.m.putIfAbsent('a', 9)\nreturn var.m.get('a')", 9);
        parityNum("var.m = {'a': 1}\nvar.m.putIfAbsent('a', 9)\nreturn var.m.get('a')", 1);
    }

    // ================= 4. MathFunctions(对照 Shimmer MathFunctions) =================

    // builtins-static-3：pi(小写,零参)
    @Test void mathPiLowercase() throws Exception {
        parityNum("return Math.pi()", Math.PI);
        parityNum("return math.pi()", Math.PI);
        parityNum("return Math.pi() * 2", Math.PI * 2);
    }

    // builtins-static-4：nextAfter(a,b)
    @Test void mathNextAfter() throws Exception {
        parityNum("return Math.nextAfter(1, 2)", Math.nextAfter(1.0, 2.0));
        parityNum("return math.nextAfter(1, 0)", Math.nextAfter(1.0, 0.0));
    }

    // ================= 5. 解释器内部一致性(第 6 项) =================

    // 6a：executeInlineInternal 的 CALL_STATIC 内联表移除 math.round → 覆盖注册必须在
    // 函数体(inline 循环)与主循环、JIT 三处一致生效。
    @Test void mathRoundOverrideRespectedInInlineLoop() throws Exception {
        ICallable original = CallableManager.INSTANCE.getStaticFunction("math", "round");
        assertNotNull(original);
        try {
            CallableManager.INSTANCE.registerStaticFunction("math", "round",
                    d -> new StringValue("R" + (int) d.get(0).numberValue()));
            // 函数体经 executeInline/executeInlineInternal 执行——修复前 inline 表硬编码 Math.round 绕过覆盖
            String code = "var.f = -> { return math.round(1.4) }\nreturn var.f()\n";
            AriaCompiledRoutine r = Aria.compile("a5round", code);
            for (int i = 0; i <= WARM; i++) {
                IValue<?> v = r.execute(ctx());
                assertEquals("R1", v.stringValue(),
                        "第" + (i + 1) + "次: math.round 覆盖必须在两个解释循环与 JIT 一致生效");
            }
        } finally {
            CallableManager.INSTANCE.registerStaticFunction("math", "round", original);
            CallableManager.INSTANCE.markStaticDefault("math", "round");
        }
    }

    // 6b：数值递归快路径选路收紧(A4 白名单同级)守卫——以下形状修复前会进 double 模型
    // (true→1、none→0、比较结果→1.0、LOAD_VAR→跳过、第 3 参丢失)，现必须走通用路径且冷热一致。

    @Test void recursionGuardTrueBaseCase() throws Exception {
        IValue<?> v = parity(
                "var.f = -> { if (args[0] <= 0) { return true } return f(args[0]-1) }\nreturn f(3)");
        assertInstanceOf(BooleanValue.class, v, "布尔基例的自递归必须返回 true(而非 1.0)");
        assertTrue(v.booleanValue());
    }

    @Test void recursionGuardComparisonResultEscapes() throws Exception {
        IValue<?> v = parity(
                "var.f = -> { if (args[0] <= 0) { return args[0] == 0 } return f(args[0]-1) }\nreturn f(3)");
        assertInstanceOf(BooleanValue.class, v, "返回比较结果的自递归必须是 BooleanValue(而非 1.0)");
        assertTrue(v.booleanValue());
    }

    @Test void recursionGuardNoneBaseCase() throws Exception {
        parityStr("var.f = -> { if (args[0] <= 0) { return none } return f(args[0]-1) }\nreturn '' + f(2)", "");
    }

    @Test void recursionGuardThirdArgSurvives() throws Exception {
        // 快路径递归帧只存 2 个参数槽——LOAD_ARG≥2 的形状必须拒绝(否则第 3 参在递归后读成 0)
        parityNum("var.f = -> { if (args[0] <= 0) { return args[2] } return f(args[0]-1, args[1], args[2]) }\n"
                + "return f(2, 5, 7)", 7);
    }

    @Test void recursionGuardStrayLoadVar() throws Exception {
        // dot 形式(CALL 变体)：基例读无关 var——修复前 LOAD_VAR 被跳过读成 0
        parityNum("var.g = 5\nvar.f = -> { if (args[0] <= 0) { return var.g } return var.f(args[0]-1) }\n"
                + "return var.f(2)", 5);
    }

    @Test void recursionFastPathStillCorrect() throws Exception {
        // 纯数值自递归(白名单形状)仍必须正确——两种形式
        parityNum("var.fib = -> { if (args[0] <= 1) { return args[0] } return fib(args[0]-1) + fib(args[0]-2) }\n"
                + "return fib(10)", 55);
        parityNum("var.fib = -> { if (args[0] <= 1) { return args[0] } return var.fib(args[0]-1) + var.fib(args[0]-2) }\n"
                + "return var.fib(10)", 55);
    }

    // ================= 6. 矩阵中「核对一致无需改」的抽样固化(10 项) =================

    @Test void matrixSampledUnchanged() throws Exception {
        parityNum("return [3,1,2].size()", 3);                                   // list.size
        parityBool("var.l = [1]\nvar.l.clear()\nreturn var.l.isEmpty()", true);  // list.clear/isEmpty
        parityStr("return 'hello world'.substring(0, 5)", "hello");              // string.substring 双参
        parityNum("return 'hello'.length()", 5);                                 // string.length
        parityBool("return 'abc'.contains('b')", true);                          // string.contains
        parityBool("return 'abc'.endsWith('c')", true);                          // string.endsWith
        parityBool("return {'a':1}.containsKey('a')", true);                     // map.containsKey
        parityNum("return {'a':1,'b':2}.keys().size()", 2);                      // map.keys
        parityNum("return {'a':1}.getOrDefault('x', 5)", 5);                     // map.getOrDefault
        parityNum("return math.hypot(3, 4)", 5);                                 // math.hypot
        parityNum("return math.atan2(0, 1)", 0);                                 // math.atan2
    }
}
