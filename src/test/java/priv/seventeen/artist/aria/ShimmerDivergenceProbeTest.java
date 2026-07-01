package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.MutableStringValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.RopeString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shimmer 行为对齐回归守卫。锁定本轮对齐修复的 6 项行为，防止后续回退。
 * （对应差异审计：#1 range、#2 负数真值、#3 list 越界、#4 拼接串真值、#5 整数格式、#6 串+数字）
 */
public class ShimmerDivergenceProbeTest {

    @BeforeAll static void setup() { Aria.getEngine().initialize(); }

    private static IValue<?> eval(String code) throws AriaException {
        return Aria.eval(code.endsWith("\n") ? code : code + "\n", new Context(new GlobalStorage()));
    }
    private static String str(String code) throws AriaException { return eval(code).stringValue(); }
    private static double num(String code) throws AriaException { return eval(code).numberValue(); }
    private static boolean bool(String code) throws AriaException { return eval(code).booleanValue(); }

    // ===== #5 整数→字符串恒 double 格式（Shimmer 对齐）=====
    @Test void numberFormatIntegerHasDecimal() {
        assertEquals("5.0", new NumberValue(5).stringValue());
        assertEquals("5.5", new NumberValue(5.5).stringValue());
    }
    @Test void numberFormatInConcatAndInterp() throws AriaException {
        assertEquals("v=5.0", str("\"v=\" + 5"));
        assertEquals("5.0", str("\"{5}\""));
        assertEquals("n=5.0", str("\"n=\" + (10 / 2)"));
    }

    // ===== #6 字符串 + 数字（连带 #5）=====
    @Test void stringPlusNumberFormat() throws AriaException {
        assertEquals("abc3.0", str("\"abc\" + 3"));
        assertEquals("3.0abc", str("3 + \"abc\""));
        assertEquals(8.0, num("\"5\" + 3"), 1e-9); // 数字化仍生效
    }

    // ===== #2 负数真值：数值热路径 truthiness = value>0（负数假）=====
    @Test void negativeTruthinessInterpreterSpecialized() throws AriaException {
        // 自递归数值函数（递减）：>0 语义 => f(-3)=0；旧 !=0 bug 会负累加/爆栈
        String f = "var.f = -> { if (args[0]) { return args[0] + f(args[0] - 1) } else { return 0 } }\n";
        assertEquals(0.0, num(f + "f(-3)"), 1e-9);
        assertEquals(6.0, num(f + "f(3)"), 1e-9);
    }
    @Test void negativeTruthinessJitWarmed() throws AriaException {
        // 预热触发 JIT（fastDouble 递归路径）后再喂负数，仍须走 else 返回 0
        String f = "var.f = -> { if (args[0]) { return args[0] + f(args[0] - 1) } else { return 0 } }\n";
        assertEquals(0.0, num(f + "f(3)\nf(3)\nf(3)\nf(-3)"), 1e-9);
    }
    @Test void negativeTruthinessGenericIf() throws AriaException {
        assertEquals("no", str("if (0 - 5) { 'yes' } else { 'no' }"));
    }

    // ===== #1 range `..`：不再死循环，语义 = 半开 [start,end)（当前 Aria 设计）=====
    @Test void rangeOperatorNoLongerHangs() throws AriaException {
        assertFalse(bool("7 ~~ 3..7"));  // 右开
        assertTrue(bool("5 ~~ 3..7"));
        assertTrue(bool("3 ~~ 3..7"));   // 左闭
    }
    @Test void rangeLiteralEqualsConstructor() throws AriaException {
        // a..b 降解为 Range(a,b)，for-in 可遍历（半开 => 3,4,5,6 共 4 个）
        assertEquals(4.0, num("var.c = 0\nfor (x in 3..7) { c = c + 1 }\nreturn c"), 1e-9);
    }

    // ===== #3 list 越界：显式索引抛异常；for-in 仍正常遍历 =====
    @Test void listOutOfBoundsThrows() {
        assertThrows(AriaException.class, () -> eval("var.l = [1,2,3]\nl[10]"));
        assertThrows(AriaException.class, () -> eval("var.l = [1,2,3]\nl[0 - 1]"));
    }
    @Test void listForInStillWorksAfterOobFix() throws AriaException {
        assertEquals(6.0, num("var.s = 0\nfor (x in [1,2,3]) { s = s + x }\nreturn s"), 1e-9);
    }
    @Test void mapMissingKeyStillNone() throws AriaException {
        assertTrue(eval("var.m = {\"a\":1}\nreturn m[\"nope\"]") instanceof NoneValue);
    }

    // ===== #4 拼接串真值：MutableStringValue/RopeString 用 parseBoolean =====
    @Test void concatStringTruthinessUnit() {
        assertFalse(new MutableStringValue("yes").booleanValue());
        assertFalse(new MutableStringValue("false").booleanValue());
        assertTrue(new MutableStringValue("true").booleanValue());
        assertFalse(new RopeString("yes").booleanValue());
        assertTrue(new RopeString("true").booleanValue());
    }
    @Test void concatStringTruthinessEndToEnd() throws AriaException {
        assertFalse(bool("(\"yes\" + \"\") && true"));    // parseBoolean("yes")=false → 短路
        assertTrue(bool("(\"true\" + \"\") && true"));
    }

    // ===== 未改动项的守卫（确认这些保持 Aria 现状，未被误动）=====
    @Test void divModZeroStaysZero() throws AriaException {
        assertEquals(0.0, num("5 / 0"), 1e-9);
        assertEquals(0.0, num("5 % 0"), 1e-9);
    }
    @Test void noneSubtractionStaysAriaSemantics() throws AriaException {
        // 未按 #8 复刻 Shimmer 怪行为：none-5 = -5（none 当 0），5-none = 5
        assertEquals(-5.0, num("var.n - 5"), 1e-9);
        assertEquals(5.0, num("5 - var.n"), 1e-9);
    }
    @Test void boolPlusNumericStringStaysAriaSemantics() throws AriaException {
        // 未按 #9 复刻 Shimmer 漏 return bug：true + "123" = 124
        assertEquals(124.0, num("true + \"123\""), 1e-9);
    }
}
