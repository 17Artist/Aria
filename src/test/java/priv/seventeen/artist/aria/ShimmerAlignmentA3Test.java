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
import org.junit.jupiter.api.Timeout;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.CompileException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A3 编译器/解析器语义对齐回归守卫(Shimmer 基准, bug-for-bug)。
 * 覆盖: syntax-01(插值寄存器窗口)、syntax-04(行首运算符续行)、syntax-10(关键字与括号间换行)、
 * syntax-07(保留字上下文回退)、syntax-05(转义原文保留)、syntax-06/controlflow-04/05
 * (break/next 泄漏与循环外优雅终止)、controlflow-08(隐式返回边界)、controlflow-03
 * (switch 逐条重比对语义)、variables-13(语句级 var.x++)、syntax-08(lenient 截断编译)、
 * jit-16(ConstantFolding 跨跳转失效)、async-6(语句形 async 隐式返回 none)。
 */
@Timeout(30)
public class ShimmerAlignmentA3Test {
    @BeforeAll static void s() { Aria.getEngine().initialize(); }
    @BeforeEach void r() { Interpreter.resetCallDepth(); Interpreter.clearSandbox(); }
    private IValue<?> eval(String c) throws AriaException { return Aria.eval(c, Aria.createContext()); }
    private double num(String c) throws AriaException { return eval(c).numberValue(); }
    private String str(String c) throws AriaException { return eval(c).stringValue(); }
    private boolean bool(String c) throws AriaException { return eval(c).booleanValue(); }
    private boolean isNone(String c) throws AriaException { return eval(c) instanceof NoneValue; }

    // ===== 1. syntax-01：插值寄存器窗口 =====
    @Test void interpMultiBinarySegments() throws Exception {
        assertEquals("a2.0b4.0c", str("return \"a{1+1}b{2+2}c\""));
    }
    @Test void interpIndexExprSegment() throws Exception {
        assertEquals("k=7.0;", str("m = {'k': 7}\nreturn \"k={m['k']};\""));
    }
    @Test void interpStringConcatSegment() throws Exception {
        assertEquals("xaby", str("return \"x{ 'a'+'b' }y\""));
    }
    @Test void interpDotCallSegment() throws Exception {
        assertEquals("n=2.0!", str("l = [1, 2]\nreturn \"n={l.size()}!\""));
    }

    // ===== 2. syntax-04：行首运算符续行(折叠集合: . ? : && || ( , < ==) =====
    @Test void foldLeadingDotCall() throws Exception {
        assertEquals(1.0, num("m = {'a': 1}\nreturn m\n.size()"), 1e-9);
    }
    @Test void foldLeadingAnd() throws Exception {
        assertFalse(bool("return true\n&& false"));
    }
    @Test void foldLeadingOr() throws Exception {
        assertTrue(bool("return false\n|| true"));
    }
    @Test void foldTernaryAcrossLines() throws Exception {
        assertEquals(1.0, num("x = true\n? 1\n: 2\nreturn x"), 1e-9);
    }
    @Test void foldLeadingLparenCall() throws Exception {
        // Shimmer 对齐(R6, A9-P2 探针 N01-N12 实测校正)：`\n(` 不折叠成调用后缀——
        // `x = f` 与 `(3)` 是两条语句；`x = f`(裸变量读 RHS)按 Shimmer Assignment 语义零参
        // 自动调用 f → args[0]=none → none*2 = 0.0；`(3)` 为独立死语句。(原断言 6.0 编码旧折叠)
        assertEquals(0.0, num("f = -> { return args[0] * 2 }\nx = f\n(3)\nreturn x"), 1e-9);
        // return 语境同样不折叠(cases#77)：return (x) 完结,行首 (3) 是死代码
        assertEquals(5.0, num("x = 5\nreturn (x)\n(3)"), 1e-9);
    }
    @Test void foldLeadingCommaInList() throws Exception {
        assertEquals(2.0, num("x = [1\n,2]\nreturn x.size()"), 1e-9);
    }
    @Test void foldLeadingLt() throws Exception {
        assertTrue(bool("x = 1\n< 2\nreturn x"));
    }
    @Test void foldLeadingEq() throws Exception {
        assertTrue(bool("x = 1\n== 1\nreturn x"));
    }
    @Test void noFoldLeadingMinus() throws Exception {
        // `-` 不折叠：`- 2` 自成一条(被丢弃值的)语句,y 保持 1
        assertEquals(1.0, num("y = 1\n- 2\nreturn y"), 1e-9);
    }
    @Test void noFoldLeadingPlusIsError() {
        // `+` 不折叠：行首 + 无法开启语句 → 严格模式编译错(Shimmer 为静默截断,见 lenient)
        assertThrows(CompileException.class, () -> eval("a = 1\n+ 2\nreturn a"));
    }
    @Test void noFoldLeadingBracket() throws Exception {
        // `[` 不折叠：`[1,2]` 自成语句
        assertEquals(5.0, num("y = 5\n[1,2]\nreturn y"), 1e-9);
    }
    @Test void noFoldAcrossBlankLine() {
        // 仅折叠紧邻单换行；空行(连续两个 NEWLINE)保持语句结束
        assertThrows(CompileException.class, () -> eval("m = {'a': 1}\nreturn m\n\n.size()"));
    }

    // ===== 3. syntax-10：控制结构关键字与 ( 之间允许换行 =====
    @Test void newlineBetweenIfAndParen() throws Exception {
        assertEquals(8.0, num("if\n(true){\nreturn 8\n}"), 1e-9);
    }
    @Test void newlineBetweenWhileAndParen() throws Exception {
        assertEquals(3.0, num("while\n(false){ }\nreturn 3"), 1e-9);
    }
    @Test void newlineBetweenForAndParen() throws Exception {
        assertEquals(2.0, num("for\n(i in [1,2]) { }\nreturn i"), 1e-9);
    }
    @Test void newlineBetweenSwitchAndParen() throws Exception {
        assertEquals(9.0, num("switch\n(1){ case 1 { } }\nreturn 9"), 1e-9);
    }
    @Test void newlineBetweenElifAndParen() throws Exception {
        assertEquals(2.0, num("if (false) { return 1 } elif\n(true) { return 2 }"), 1e-9);
    }

    // ===== 4. syntax-07：保留字上下文回退为标识符 =====
    @Test void matchAsVariable() throws Exception {
        assertEquals(5.0, num("match = 5\nreturn match"), 1e-9);
    }
    @Test void fromAndAsAsVariables() throws Exception {
        assertEquals(3.0, num("from = 1\nas = 2\nreturn from + as"), 1e-9);
    }
    @Test void classAsVariableWithIncrement() throws Exception {
        assertEquals(4.0, num("class = 3\nclass++\nreturn class"), 1e-9);
    }
    @Test void tryThrowAwaitAsVariables() throws Exception {
        assertEquals(7.0, num("try = 7\nreturn try"), 1e-9);
        assertEquals(9.0, num("throw = 9\nreturn throw"), 1e-9);
        assertEquals(6.0, num("await = 5\nreturn await + 1"), 1e-9);
    }
    @Test void catchFinallyExtendsAsVariables() throws Exception {
        assertEquals(6.0, num("catch = 1\nfinally = 2\nextends = 3\nreturn catch + finally + extends"), 1e-9);
    }
    @Test void importExportInstanceofAsVariables() throws Exception {
        assertEquals(5.0, num("import = 5\nreturn import"), 1e-9);
        assertEquals(4.0, num("export = 4\nreturn export"), 1e-9);
        assertEquals(3.0, num("instanceof = 2\nreturn instanceof + 1"), 1e-9);
    }
    @Test void keywordInOperandPositions() throws Exception {
        assertEquals(2.0, num("as = 2\nl = [as]\nreturn l[0]"), 1e-9);          // 列表元素
        assertEquals(3.0, num("f = -> { return args[0] }\nfrom = 3\nreturn f(from)"), 1e-9); // 实参
        assertEquals(6.0, num("match = 5\nreturn match + 1"), 1e-9);            // 二元操作数
    }
    @Test void keywordAsForInLoopVariable() throws Exception {
        assertEquals(6.0, num("s = 0\nfor (as in [1,2,3]) { s = s + as }\nreturn s"), 1e-9);
    }
    @Test void keywordAsDotPropertyStillWorks() throws Exception {
        // (d) 点成员位已放行的保持
        assertEquals(5.0, num("var.match = 5\nreturn var.match"), 1e-9);
        assertEquals(3.0, num("var.from = 3\nreturn var.from"), 1e-9);
    }
    @Test void ariaConstructsNotRegressed() throws Exception {
        // match(x){...}、try{}catch、class X{} 构造位置不受回退影响
        assertEquals(10.0, num("r = 0\nmatch (1) { case 1 { r += 10 } case 2 { r += 20 } }\nreturn r"), 1e-9);
        assertEquals(1.0, num("r = 0\ntry { throw 'x' } catch (e) { r = 1 }\nreturn r"), 1e-9);
        assertEquals(3.0, num("class P { var.v = 3 }\np = P()\nreturn p.v"), 1e-9);
    }

    // ===== 5. syntax-05：字符串转义不展开(值保留原文) =====
    @Test void escapeNotExpandedSingleQuote() throws Exception {
        assertEquals("a\\nb", str("return 'a\\nb'"));      // 4 字符 a \ n b
        assertEquals("C:\\new\\tab", str("return 'C:\\new\\tab'"));
    }
    @Test void escapeNotExpandedDoubleQuote() throws Exception {
        assertEquals("x\\ty", str("return \"x\\ty\""));
    }
    @Test void escapedQuoteKeepsBackslashButNotTerminate() throws Exception {
        // 'a\'b' → 值为 a \ ' b 四个字符(\' 不终止字符串,值保留原文)
        assertEquals("a\\'b", str("return 'a\\'b'"));
    }
    @Test void escapeBackslashKeptRaw() throws Exception {
        assertEquals("x\\\\y", str("return 'x\\\\y'")); // \\ 也原样保留(两字符)
    }
    @Test void escapedBraceStillBlocksInterpolation() throws Exception {
        // \{ 现行为不变：拦截插值、值保留原文
        assertEquals("a\\{b}c", str("n = 1\nreturn \"a\\{b}c\""));
    }
    @Test void textBlockKeepsRawBackslash() throws Exception {
        assertEquals("a\\nb", str("return \"\"\"a\\nb\"\"\""));
    }

    // ===== 6. syntax-06/controlflow-04/05：break/next 泄漏 bug-for-bug =====
    @Test void whileBreakLeaksTerminatesScript() throws Exception {
        // Shimmer: while 内 break 泄漏,循环后语句全部跳过,脚本值 none
        assertTrue(isNone("s = 0\nwhile (s < 5) {\ns = s + 2\nbreak\n}\nreturn s"));
    }
    @Test void whileBreakLeakSkipsFollowingAssignments() throws Exception {
        assertTrue(isNone("var.r = 'start'\nwhile (true) { break }\nvar.r = 'after'\nreturn var.r"));
    }
    @Test void whileBreakConsumedByOuterFor() throws Exception {
        // 泄漏的 BREAK 被最近外层 for 当作自己的 break 消耗(跳到 for 出口,跳过本轮剩余语句)
        assertEquals(0.0, num("r = 0\nfor (i in [1,2,3]) { while (true) { break }\n r = r + 1 }\nreturn r"), 1e-9);
    }
    @Test void whileBreakLeaksThroughNestedWhile() throws Exception {
        // 内层 while 的 break 连外层 while 一起 break,直到被 for 消耗
        assertEquals(0.0, num(
                "r = 0\nfor (i in [1]) { while (true) { while (true) { break }\n r = r + 100 }\n r = r + 1 }\nreturn r"), 1e-9);
    }
    @Test void whileBreakConsumedBySwitch() throws Exception {
        // switch 消耗泄漏的 BREAK：跳过 else,switch 之后语句正常执行
        assertEquals(0.0, num(
                "r = 0\nswitch (1) { case 1 { while (true) { break } } else { r = 5 } }\nreturn r"), 1e-9);
    }
    @Test void forBreakNormal() throws Exception {
        // for-in 内 break 正常(仅最内层 for,循环后语句执行)
        assertEquals(11.0, num("r = 0\nfor (i in range(0, 100)) { if (i > 10) { break }\n r = r + 1 }\nreturn r"), 1e-9);
    }
    @Test void whileNextLeaksToOuterFor() throws Exception {
        // 末轮 next 后条件为假退出 → 残留 NEXT 泄漏,充当外层 for 的 continue
        assertEquals(2.0, num(
                "c = 0\nfor (k in [1,2]) { c = c + 1\n j = 0\n while (j < 1) { j = j + 1\n next }\n c = c + 10 }\nreturn c"), 1e-9);
    }
    @Test void whileNextLeakAtTopTerminatesScript() throws Exception {
        assertTrue(isNone("j = 0\nwhile (j < 1) { j = j + 1\n next }\nreturn j"));
    }
    @Test void whileNextNoLeakWhenBodyCompletesNormally() throws Exception {
        // 末轮正常完成(next 不是末轮最后动作) → 不泄漏
        assertEquals(3.0, num("j = 0\nwhile (j < 3) { j = j + 1\n if (j == 1) { next }\n x = 0 }\nreturn j"), 1e-9);
    }
    @Test void breakOutsideLoopGracefulNone() throws Exception {
        // 循环外 break：Shimmer 优雅终止(Aria 旧行为 JUMP 0,0 挂死)
        assertTrue(isNone("var.r = 'before'\nif (true) { break }\nvar.r = 'after'\nreturn var.r"));
        assertTrue(isNone("break\nreturn 5"));
    }
    @Test void nextOutsideLoopGracefulNone() throws Exception {
        assertTrue(isNone("next\nreturn 5"));
        assertTrue(isNone("if (true) { next }\nreturn 5"));
    }
    @Test void breakInLambdaBottomsOutNone() throws Exception {
        // lambda 子编译器同样兜底 RETURN none(不挂死);调用后脚本继续
        assertEquals(7.0, num("f = -> { break\n return 1 }\nx = f()\nreturn 7"), 1e-9);
    }
    @Test void nextPassesThroughSwitchToFor() throws Exception {
        // switch 不消耗 NEXT(源码 line 43 上抛)：case 内 next 跳过 else 与本轮剩余语句,充当 for 的 continue
        assertEquals(22.0, num(
                "r = 0\nfor (i in [1,2,3]) { switch (i) { case 1 { next } else { r = r + 1 } }\n r = r + 10 }\nreturn r"), 1e-9);
    }
    @Test void nextInSwitchWithoutLoopTerminates() throws Exception {
        assertTrue(isNone("r = 0\nswitch (1) { case 1 { next } else { r = 5 } }\nreturn r"));
    }
    @Test void whileNextLeakChainsThroughNestedWhile() throws Exception {
        // 内层 while 末轮 next 泄漏 → 外层 while consume 后恰好退出再泄漏 → for continue
        assertEquals(2.0, num(
                "c = 0\nfor (k in [1,2]) { c = c + 1\n a = 0\n while (a < 1) { a = a + 1\n b = 0\n"
                + " while (b < 1) { b = b + 1\n next }\n c = c + 100 }\n c = c + 10 }\nreturn c"), 1e-9);
    }

    // ===== 7. controlflow-08：隐式返回边界 =====
    @Test void trailingIfReturnsChosenBranch() throws Exception {
        assertEquals("yes", str("if (true) { 'yes' } else { 'no' }"));
        assertEquals("no", str("if (false) { 'yes' } else { 'no' }"));
    }
    @Test void trailingIfNoBranchIsNone() throws Exception {
        assertTrue(isNone("if (false) { 1 }"));
    }
    @Test void trailingElifBranch() throws Exception {
        assertEquals("pos", str("x = 5\nif (x < 0) { 'neg' } elif (x > 0) { 'pos' } else { 'zero' }"));
    }
    @Test void trailingNestedIfRecursive() throws Exception {
        assertEquals(2.0, num("if (true) { if (false) { 1 } else { 2 } }"), 1e-9);
    }
    @Test void trailingWhileReturnsLastBodyValue() throws Exception {
        assertEquals(3.0, num("x = 0\nwhile (x < 3) { x = x + 1 }"), 1e-9);
    }
    @Test void trailingWhileZeroIterationsIsNone() throws Exception {
        assertTrue(isNone("while (false) { 5 }"));
    }
    @Test void trailingForInIsNone() throws Exception {
        assertTrue(isNone("for (i in [1,2]) { i }"));
    }
    @Test void trailingSwitchIsNone() throws Exception {
        // Shimmer SwitchStatement 恒返回 Result.NONE
        assertTrue(isNone("switch (1) { case 1 { 42 } }"));
    }

    // ===== 8. controlflow-03：switch Shimmer 语义(bug-for-bug) =====
    @Test void switchAeNoBreak() throws Exception {
        // case1 匹配执行后,比对值被替换为块结果 "a";case2 条件 2 != "a";else 总执行
        assertEquals("ae", str(
                "x = 1\nr = ''\nswitch (x) { case 1 { r = r + 'a' } case 2 { r = r + 'b' } else { r = r + 'e' } }\nreturn r"));
    }
    @Test void switchBreakConsumesAndSkipsElse() throws Exception {
        assertEquals("a", str(
                "x = 1\nr = ''\nswitch (x) { case 1 { r = r + 'a'\n break } case 2 { r = r + 'b' } else { r = r + 'e' } }\nreturn r"));
    }
    @Test void switchCompareValueReplacedByBlockResult() throws Exception {
        // case1 块结果 2 成为新比对值 → case2 也匹配 → "abe"(Shimmer 怪癖)
        assertEquals("abe", str(
                "r = ''\nswitch (1) { case 1 { r = r + 'a'\n 2 } case 2 { r = r + 'b' } else { r = r + 'e' } }\nreturn r"));
    }
    @Test void switchAllCaseConditionsEvaluated() throws Exception {
        // 匹配与否,所有 case 条件按序求值(副作用可见)
        assertEquals(11.0, num(
                "x = 9\nn = 0\nswitch (x) { case (n = n + 1) { } case (n = n + 10) { } }\nreturn n"), 1e-9);
    }
    @Test void switchNoMatchElseRuns() throws Exception {
        assertEquals("e", str(
                "r = ''\nswitch (9) { case 1 { r = r + 'a' } else { r = r + 'e' } }\nreturn r"));
    }
    @Test void matchKeepsAriaSemantics() throws Exception {
        // match 关键字保持 Aria 现语义(匹配后跳过 else,不重比对)
        assertEquals(10.0, num("r = 0\nmatch (1) { case 1 { r += 10 } case 2 { r += 20 } else { r += 5 } }\nreturn r"), 1e-9);
    }

    // ===== 9. variables-13：语句级 var.x++ / var.x-- =====
    @Test void statementLevelVarDotIncrement() throws Exception {
        assertEquals(2.0, num("var.x = 1\nvar.x++\nreturn var.x"), 1e-9);
    }
    @Test void statementLevelVarDotDecrement() throws Exception {
        assertEquals(4.0, num("var.y = 5\nvar.y--\nreturn var.y"), 1e-9);
    }
    @Test void statementLevelVarDotIncrementFromNone() throws Exception {
        assertEquals(1.0, num("var.z++\nreturn var.z"), 1e-9);
    }

    // ===== 10. syntax-08：lenient 编译模式(静默截断) =====
    @Test void lenientTruncatesAtBadStatement() throws Exception {
        AriaCompiledRoutine r = Aria.compile("t", "a = 1\n+ 2\nc = 3\nreturn c", true);
        assertFalse(r.getWarnings().isEmpty());
        assertEquals(1.0, r.execute(Aria.createContext()).numberValue(), 1e-9); // 前缀 a=1 的值
    }
    @Test void lenientBareThenBadAssign() throws Exception {
        AriaCompiledRoutine r = Aria.compile("t", "a\n= 5\nreturn a", true);
        assertTrue(r.execute(Aria.createContext()) instanceof NoneValue);
    }
    @Test void lenientFirstStatementBadGivesEmptyProgram() throws Exception {
        AriaCompiledRoutine r = Aria.compile("t", "+ 2\nreturn 5", true);
        assertFalse(r.getWarnings().isEmpty());
        assertTrue(r.execute(Aria.createContext()) instanceof NoneValue);
    }
    @Test void strictModeStillFailFast() {
        assertThrows(CompileException.class, () -> Aria.compile("t", "a = 1\n+ 2\nreturn a", false));
        assertThrows(CompileException.class, () -> Aria.compile("t", "a = 1\n+ 2\nreturn a"));
    }

    // ===== 11. jit-16：ConstantFolding 跨跳转失效 =====
    @Test void ternaryPlusArithNotMisfolded() throws Exception {
        assertEquals(4.0, num("var.c = 1\nvar.y = (var.c > 0 ? 1 : 2) + 3\nreturn var.y"), 1e-9);
        assertEquals(5.0, num("var.c = 0\nvar.y = (var.c > 0 ? 1 : 2) + 3\nreturn var.y"), 1e-9);
    }
    @Test void registerReuseAcrossIfNotFolded() throws Exception {
        assertEquals(4.0, num("x = 0\nif (true) { x = 1 } else { x = 2 }\nreturn x + 3"), 1e-9);
    }
    @Test void constantsInLoopStillFolded() throws Exception {
        // 直线常量折叠不回退,循环内结果正确
        assertEquals(15.0, num("s = 0\nfor (i in [1,2,3]) { s = s + 2 + 3 }\nreturn s"), 1e-9);
    }

    // ===== JIT parity：新 IR 形状(插值窗口/switch 重写/while 泄漏/隐式返回)双路一致 =====
    /** 强制走 JIT：编译一次、执行 N 次越过阈值(=1)，断言每次结果与解释器首轮一致。 */
    private IValue<?> jitStable(String code) throws AriaException {
        AriaCompiledRoutine r = Aria.compile("jit", code.endsWith("\n") ? code : code + "\n");
        IValue<?> first = r.execute(Aria.createContext());
        IValue<?> last = first;
        for (int i = 0; i < 200; i++) {
            last = r.execute(Aria.createContext());
            assertEquals(first.stringValue(), last.stringValue(), "JIT/解释器结果漂移: " + code);
        }
        return last;
    }
    @Test void jitParityInterpolationWindow() throws Exception {
        assertEquals("a2.0b4.0c", jitStable("return \"a{1+1}b{2+2}c\"").stringValue());
    }
    @Test void jitParitySwitchRewrite() throws Exception {
        assertEquals("ae", jitStable(
                "x = 1\nr = ''\nswitch (x) { case 1 { r = r + 'a' } case 2 { r = r + 'b' } else { r = r + 'e' } }\nreturn r").stringValue());
    }
    @Test void jitParityWhileBreakLeak() throws Exception {
        assertTrue(jitStable("s = 0\nwhile (s < 5) { s = s + 2\n break }\nreturn s") instanceof NoneValue);
    }
    @Test void jitParityWhileNextLeakToFor() throws Exception {
        assertEquals(2.0, jitStable(
                "c = 0\nfor (k in [1,2]) { c = c + 1\n j = 0\n while (j < 1) { j = j + 1\n next }\n c = c + 10 }\nreturn c").numberValue(), 1e-9);
    }
    @Test void jitParityTrailingIfValue() throws Exception {
        assertEquals("yes", jitStable("if (true) { 'yes' } else { 'no' }").stringValue());
    }
    @Test void jitParityTernaryFolding() throws Exception {
        assertEquals(4.0, jitStable("var.c = 1\nvar.y = (var.c > 0 ? 1 : 2) + 3\nreturn var.y").numberValue(), 1e-9);
    }

    // ===== 12. async-6：语句形 async{} 的隐式返回为 none =====
    @Test void trailingStatementAsyncReturnsNone() throws Exception {
        IValue<?> v = eval("async { return 1 }");
        assertTrue(v instanceof NoneValue);
        assertFalse(v.booleanValue());
    }
    @Test void expressionFormAsyncKeepsPromise() throws Exception {
        assertEquals(1.0, num("var.p = async { return 1 }\nreturn await var.p"), 1e-9);
    }
    @Test void nonTrailingAsyncUnaffected() throws Exception {
        assertEquals(5.0, num("async { return 1 }\nreturn 5"), 1e-9);
    }
}
