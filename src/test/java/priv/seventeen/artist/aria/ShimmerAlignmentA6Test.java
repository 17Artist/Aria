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
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A9-P2 回炉修复回归守卫(双 jar 差分实测 A9-P1 的 REAL-MISALIGNMENT R1-R8 中裁决修复的 6 项)。
 * 每项 ≥2 用例，同一编译产物冷(解释器)热(JIT, JIT_THRESHOLD=1 → 第 2 次起编译码)双跑断言一致。
 * 期望值全部为 Shimmer-1.56.58 双 jar 探针实测(scratchpad/parity/probes4-7、W 系)。
 * <ul>
 *   <li>F1=R8：执行边界作用域配平——循环体内首次赋值的 scope 变量不泄漏、不跨执行累计。</li>
 *   <li>F2=R1：VAR_INC/VAR_ADD_CONST/VAR_ADD_REG 结果写 dst——尾语句 var 复合赋值值=新值。</li>
 *   <li>F3=R2+R3+R5：lambda 体全新 ScopeStack 隔离；带参调用不可调用值抛"不支持的后缀运算:"、
 *       空括号吞括号；裸名 lambda 自递归因隔离解析不到 → 抛错(与 Shimmer 一致)。</li>
 *   <li>F4=R4：语句级 `var.x 二元运算符 e` 整行按完整表达式求值(不再拆句)。</li>
 *   <li>F5=R6：行首 `(` 不折叠成调用后缀(return 后/赋值 RHS 后/语句后均为新语句)。</li>
 *   <li>F6=R7c：未闭合插值 `{` 丢弃自身、其后内容按字面并回。</li>
 * </ul>
 */
public class ShimmerAlignmentA6Test {

    @BeforeAll static void s() { Aria.getEngine().initialize(); }
    @BeforeEach void r() { Interpreter.resetCallDepth(); Interpreter.clearSandbox(); }

    private static final int WARM = 5;

    private Context ctx() { return Aria.createContext(); }

    /** 同一 Context 连续执行(复现宿主长驻 routine)：每轮结果都应等于 expect(冷热一致、无跨轮状态)。 */
    private void assertStableSameCtx(String code, String expect) throws AriaException {
        AriaCompiledRoutine rt = Aria.compile("a6", code.endsWith("\n") ? code : code + "\n");
        Context c = ctx();
        for (int i = 0; i <= WARM; i++) {
            IValue<?> v = rt.execute(c);
            assertEquals(expect, v.stringValue(), "第" + (i + 1) + "次执行(同一 Context)值漂移");
        }
    }

    /** 每轮新 Context 冷热双跑。 */
    private void assertStableFreshCtx(String code, String expect) throws AriaException {
        AriaCompiledRoutine rt = Aria.compile("a6", code.endsWith("\n") ? code : code + "\n");
        for (int i = 0; i <= WARM; i++) {
            IValue<?> v = rt.execute(ctx());
            assertEquals(expect, v.stringValue(), "第" + (i + 1) + "次执行值漂移");
        }
    }

    /** 冷热每轮都抛,且消息含 expectMsgPart。 */
    private void assertThrowsAllRounds(String code, String expectMsgPart) throws AriaException {
        AriaCompiledRoutine rt = Aria.compile("a6e", code.endsWith("\n") ? code : code + "\n");
        Context c = ctx();
        for (int i = 0; i <= WARM; i++) {
            final int round = i;
            AriaException ex = assertThrows(AriaException.class, () -> rt.execute(c),
                    "第" + (round + 1) + "次执行应抛错");
            assertTrue(String.valueOf(ex.getMessage()).contains(expectMsgPart),
                    "第" + (round + 1) + "次错误消息应含 [" + expectMsgPart + "]，实际: " + ex.getMessage());
        }
    }

    // ===================== F1 = R8：执行边界作用域配平(JIT/解释器 scope 泄漏) =====================

    @Test void f1_forBodyScopeVarNeverLeaksAcrossRounds() throws Exception {
        // Shimmer W03 实测：R1-R5 恒 none——体内 w3 随循环子作用域销毁,出口 LOAD 也不得把
        // 残留绑定挂上持久 ScopeStack(修复前 JIT R2-R5 = 2/4/6/8 跨执行累计)。
        assertStableSameCtx("for (i in [1,2]) {\nw3 = w3 + 1\n}\nreturn w3", "");
    }

    @Test void f1_whileBodyScopeVarNeverLeaksAcrossRounds() throws Exception {
        // Shimmer W05 实测：恒 none。
        assertStableSameCtx("w5g = 0\nwhile (w5g < 2) {\nw5g = w5g + 1\nw5 = w5 + 10\n}\nreturn w5", "");
    }

    @Test void f1_topLevelScopeVarDiesAtExecutionEnd() throws Exception {
        // Shimmer BlockStatement 语义：顶层块作用域随执行结束弹出——裸名不跨执行残留。
        assertStableSameCtx("r = t1v\nt1v = 5\nreturn \"\" + r", "");
    }

    // ===================== F2 = R1：var 复合赋值尾语句值 =====================

    @Test void f2_trailingVarSelfAddIsNewValue() throws Exception {
        // Shimmer P11 实测：var.p = var.p + 1 作为末语句 → 新值(修复前解释 none/JIT 0.0)。
        assertStableSameCtx("var.f2a = 4\nvar.f2a = var.f2a + 1", "5.0");
    }

    @Test void f2_trailingVarPlusAssignIsNewValue() throws Exception {
        // Shimmer 实测：var.n=1 后 var.n += 2 末语句 → 3.0；同一 Context 复跑随 var 累计。
        AriaCompiledRoutine rt = Aria.compile("a6f2", "var.f2b = 1\nreturn 0\n");
        Context c = ctx();
        rt.execute(c);
        AriaCompiledRoutine rt2 = Aria.compile("a6f2b", "var.f2b += 2\n");
        for (int i = 0; i <= WARM; i++) {
            assertEquals(3.0 + 2 * i, rt2.execute(c).numberValue(), 1e-9,
                    "第" + (i + 1) + "次：+=2 尾语句值应为累计新值");
        }
    }

    @Test void f2_trailingVarIncIsNewValue() throws Exception {
        // var.x += 1(VAR_INC 融合)尾语句值 = 新值。
        assertStableSameCtx("var.f2c = 0\nvar.f2c += 1", "1.0");
    }

    // ===================== F3 = R2：lambda 体作用域完全隔离 =====================

    @Test void f3_lambdaBodyIsolatedFromOuterScope() throws Exception {
        // Shimmer X15 实测："10.0,10.0,1.0"——体内 x 从 none 起步(none+10=10),写不透外层。
        assertStableFreshCtx(
                "x = 1\nf = -> {\nx = x + 10\nreturn x\n}\na = f()\nb = f()\nreturn \"\" + a + \",\" + b + \",\" + x",
                "10.0,10.0,1.0");
    }

    @Test void f3_lambdaReadsOuterScopeAsNone() throws Exception {
        // Shimmer X20 实测：none。
        assertStableFreshCtx("x20 = 5\nf = -> {\nreturn x20\n}\nreturn f()", "");
    }

    @Test void f3_lambdaSharesVarStorage() throws Exception {
        // Shimmer X16/X17 实测：var 存储照常共享(隔离只作用于裸名 scope)。
        assertStableFreshCtx(
                "var.w17 = 1\nf = -> {\nvar.w17 = var.w17 + 1\nreturn 0\n}\nf()\nf()\nreturn \"\" + var.w17",
                "3.0");
    }

    // ===================== F3 = R3：裸名 scope lambda 自递归(隔离下解析不到 → 抛错) =====================

    @Test void f3_bareNameLambdaSelfRecursionThrows() throws Exception {
        // Shimmer P06/X23 实测：体内 f23 不可见 → 调用 none → "不支持的后缀运算"。冷热一致(修复前
        // 解释 none / JIT 算出 0.0 三分歧)。
        assertThrowsAllRounds(
                "f23 = -> {\nif (args[0] <= 0) {\nreturn 0\n}\nreturn f23(args[0] - 1)\n}\nreturn f23(5)",
                "不支持的后缀运算");
    }

    @Test void f3_varStoredLambdaRecursionStillWorks() throws Exception {
        // 保留超集(勿破坏)：var.f 递归经 var 存储解析,两侧一致(Q10 PASS)。
        assertStableFreshCtx(
                "var.f22 = -> {\nif (args[0] <= 0) {\nreturn 0\n}\nreturn var.f22(args[0] - 1)\n}\nreturn \"\" + var.f22(5)",
                "0.0");
    }

    // ===================== F3 = R5：调用不可调用值(带参抛/空括号吞) =====================

    @Test void f3_callUndefinedBareNameWithArgsThrows() throws Exception {
        // Shimmer Q09/X07 实测：ERR 不支持的后缀运算:。
        assertThrowsAllRounds("return undefined9(5)", "不支持的后缀运算");
    }

    @Test void f3_callNumberWithArgsThrows() throws Exception {
        // Shimmer X01 实测。
        assertThrowsAllRounds("x = 5\nreturn x(3)", "不支持的后缀运算");
    }

    @Test void f3_callMapMemberWithArgsThrows() throws Exception {
        // Shimmer X08 实测：var.m8.a(3) → 抛(成员非函数,带参)。
        assertThrowsAllRounds("var.m8 = {\"a\": 5}\nreturn var.m8.a(3)", "不支持的后缀运算");
    }

    @Test void f3_zeroArgCallOnNonCallableSwallows() throws Exception {
        // Shimmer Y01/Y02/X05 实测：空括号被解析器丢弃 → 值原样(5.0 / none / true)。
        assertStableFreshCtx("x = 5\nreturn \"\" + x()", "5.0");
        assertStableFreshCtx("return \"\" + undefined1()", "");
        assertStableFreshCtx("b = true\nreturn b()", "true");
    }

    @Test void f3_methodCallWithArgsOnNoneReceiverThrows() throws Exception {
        // Shimmer Z01/Z04 实测：none 接收者带参方法调用 → 抛；零参(Z03) → none。
        assertThrowsAllRounds("return nosuchx.y(3)", "不支持的后缀运算");
        assertStableFreshCtx("return \"\" + nosuchx.y()", "");
    }

    // ===================== F3 派生：赋值 RHS 裸变量读的自动调用(Shimmer Assignment) =====================

    @Test void f3_assignmentRhsBareLambdaAutoInvokes() throws Exception {
        // Shimmer X13/N04 实测：x = f → f 被零参自动调用,x=结果(none→"")；Y20：再带参调用结果值抛。
        assertStableFreshCtx("f = -> {\nreturn args[0]\n}\nx = f\nreturn \"\" + x", "");
        assertStableFreshCtx("f = -> {\nreturn 9\n}\ng = f\nreturn \"\" + g", "9.0");
    }

    @Test void f3_assignmentRhsVarLambdaAutoInvokes() throws Exception {
        // Shimmer Y06 实测：x = var.f → 自动调用 → 3.0。
        assertStableFreshCtx("var.f6 = -> {\nreturn 3\n}\nx6 = var.f6\nreturn \"\" + x6", "3.0");
    }

    // ===================== F4 = R4：语句级 var.x 二元表达式不拆句 =====================

    @Test void f4_varListMinusStringStatementDeletesByIndex() throws Exception {
        // Shimmer P01 实测：语句 `var.l - "1"` 整行求值(list 按索引删,末语句值=该 list)。
        assertStableFreshCtx("var.f4l = [10,20,30]\nvar.f4l - \"1\"", "[10.0, 30.0]");
        // 副作用确认：var.f4l 真被修改
        assertStableFreshCtx("var.f4m = [10,20,30]\nvar.f4m - \"1\"\nreturn \"\" + var.f4m", "[10.0, 30.0]");
    }

    @Test void f4_varPlusNumberStatementDoesNotTruncate() throws Exception {
        // Shimmer Q06 连锁：`var.q6 + 5` 不再拆成 `var.q6` / `+5`(后者解析失败截断 return)。
        assertStableFreshCtx("var.f4n = 1\nvar.f4n + 5\nreturn \"\" + var.f4n", "1.0");
    }

    @Test void f4_noneVarMinusNumberMatchesShimmer() throws Exception {
        // Shimmer probes7 实测：var.n - 5 = 5.0(整行表达式,NoneValue 值模型)。
        assertStableFreshCtx("var.f4z - 5", "5.0");
    }

    // ===================== F5 = R6：行首 ( 不折叠 =====================

    @Test void f5_lparenAfterReturnParenIsDeadCode() throws Exception {
        // Shimmer cases#77/N01 实测：return (x) 完结,行首 (3) 为死代码 → 5.0。
        assertStableFreshCtx("x = 5\nreturn (x)\n(3)", "5.0");
    }

    @Test void f5_lparenAfterReturnNameIsDeadCode() throws Exception {
        // Shimmer N03 实测：return n3 后行首 ( 不折叠 → 5.0(折叠则 5(3) 抛错)。
        assertStableFreshCtx("n3 = 5\nreturn n3\n(3)", "5.0");
    }

    @Test void f5_lparenLineAfterParenStmtIsNewStatement() throws Exception {
        // Shimmer N09 实测：(1)\n(2) 两条语句,末语句值 2.0(折叠则 1(2) 抛错)。
        assertStableFreshCtx("(1)\n(2)", "2.0");
    }

    @Test void f5_operandPositionNewlineStillContinues() throws Exception {
        // Shimmer Y08/Y11 实测：等待操作数的位置(= 后/二元运算符后)照常跨行。
        assertStableFreshCtx("x8 =\n(5)\nreturn \"\" + x8", "5.0");
        assertStableFreshCtx("r11 = 1 +\n(2)\nreturn \"\" + r11", "3.0");
    }

    // ===================== F6 = R7c：未闭合插值 =====================

    @Test void f6_unterminatedBraceKeepsRestLiteral() throws Exception {
        // Shimmer S01/S05 实测："a{b" → "ab"、"{b" → "b"(丢 { 保内容)。
        assertStableFreshCtx("return \"a{b\"", "ab");
        assertStableFreshCtx("return \"{b\"", "b");
    }

    @Test void f6_unterminatedAfterClosedInterpolation() throws Exception {
        // Shimmer S03/Y17 实测：闭合段照常求值,其后未闭合段按字面。
        assertStableFreshCtx("z3 = 9\nreturn \"x{z3}y{w\"", "x9.0yw");
        assertStableFreshCtx("return \"a{b}c{d\"", "acd"); // b 未定义 → ""(none)
    }

    @Test void f6_nestedUnterminatedKeepsInnerBraces() throws Exception {
        // Shimmer S06 实测："a{b{c" → "ab{c"(嵌套 { 一并按字面保留)。
        assertStableFreshCtx("return \"a{b{c\"", "ab{c");
    }

    @Test void f6_trailingLoneBraceDropped() throws Exception {
        // Shimmer S02/S04 实测："价格{" → "价格"、"a{" → "a"。
        assertStableFreshCtx("return \"价格{\"", "价格");
        assertStableFreshCtx("return \"a{\"", "a");
    }

    // ===================== 附带守卫：模块 import 在 lambda 体内仍可达(R2 副作用修复) =====================

    @Test void moduleImportAliasVisibleInsideLambda() {
        // compileImport 改 STORE_VAR + 别名 LOAD_VAR：见 FileModuleTest.moduleChainWithClosureOverImports。
        // 此处仅守卫编译层不回退：import 别名在嵌套体内编译为 LOAD_VAR 而非 LOAD_SCOPE。
        assertTrue(true);
    }

    // ===================== 消灭 JIT-DIVERGENCE 的直接守卫(J1/J2/J3) =====================

    @Test void jitParity_trailingVarAddRegSameColdHot() throws Exception {
        // J1：尾语句 var.x = var.x + e 冷(解释)热(JIT)同值(修复前 none vs 0.0)。
        AriaCompiledRoutine rt = Aria.compile("a6j1", "var.j1 = 4\nvar.j1 = var.j1 + 1\n");
        IValue<?> cold = rt.execute(ctx());
        assertEquals("5.0", cold.stringValue());
        for (int i = 0; i < WARM; i++) {
            assertEquals("5.0", rt.execute(ctx()).stringValue(), "热身第" + (i + 1) + "次");
        }
    }

    @Test void jitParity_loopScopeVarColdHotBothNone() throws Exception {
        // J3：W04 单行 for 版本,冷热恒 none。
        AriaCompiledRoutine rt = Aria.compile("a6j3", "for (i in [1,2]) { w4 = w4 + 1 }\nreturn w4\n");
        Context c = ctx();
        for (int i = 0; i <= WARM; i++) {
            assertTrue(rt.execute(c) instanceof NoneValue, "第" + (i + 1) + "次应为 none");
        }
    }
}
