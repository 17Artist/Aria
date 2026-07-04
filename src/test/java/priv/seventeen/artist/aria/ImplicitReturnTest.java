package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.exception.AriaException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 顶层程序的<b>隐式返回</b>：末尾表达式的值即程序结果（与 lambda 一致、Shimmer 兼容、Ruby/Kotlin 式）。
 * 回归守护 —— 曾缺失导致 ArcartX UI 所有裸表达式属性求值成 none（值全 0）。
 */
public class ImplicitReturnTest {

    @BeforeAll
    static void setup() { Aria.getEngine().initialize(); }

    private Context ctx() { return new Context(new GlobalStorage()); }

    @Test
    void bareExpressionReturnsValue() throws AriaException {
        assertEquals(15.0, Aria.eval("10 + 5\n", ctx()).numberValue(), 1e-9);
        assertEquals(15.0, Aria.eval("return 10 + 5\n", ctx()).numberValue(), 1e-9);
        assertEquals(15.0, Aria.compile("x", "10 + 5\n").execute(ctx()).numberValue(), 1e-9);
    }

    @Test
    void lastStatementOfMultiIsReturned() throws AriaException {
        assertEquals(6.0, Aria.eval("a = 3\na * 2\n", ctx()).numberValue(), 1e-9);
    }

    @Test
    void explicitReturnStillWins() throws AriaException {
        assertEquals(1.0, Aria.eval("return 1\n2 + 2\n", ctx()).numberValue(), 1e-9);
    }
}
