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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.api.AriaCompilationUnit;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.parser.ContentTracker;
import priv.seventeen.artist.aria.runtime.Interpreter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A7 / gui-chain-9：执行入口运行时错误消息增强为 Shimmer 风格(对照 ShimmerCompilationUnit.execute)——
 * "单元: [name] 运行时错误, 位于第 X 行, 到第 Y 行\n请检查: <源码行>\n错误信息: <原始错误>"。
 * 只改消息文本：异常类型仍为 AriaRuntimeException、结构化行列信息保留、原异常挂 cause、传播语义不变。
 * 行号拿不到(如 JIT 生成码抛出的无行号异常——JIT 编译为异步调度，端到端不可确定性触发，
 * 故该路径经包内静态拼装函数直接固定)时回退为源码片段回显(至多 3 行)。
 */
public class ShimmerAlignmentA7Test {

    @BeforeEach
    void reset() {
        Interpreter.resetCallDepth();
        Interpreter.clearSandbox();
    }

    private static final String CODE = "x = 1\nreturn nosuch(1)\n";
    private static final String EXPECT_MSG =
            "单元: [%s] 运行时错误, 位于第 2 行, 到第 2 行\n"
                    + "请检查: return nosuch(1)\n"
                    + "错误信息: 不支持的后缀运算: (line 2:8)";

    @Test
    void unitRuntimeErrorIsShimmerStyleWithLineAndSourceEcho() throws Exception {
        AriaCompilationUnit unit = Aria.compile("t", Aria.createContext(), CODE, true);
        AriaRuntimeException ex = assertThrows(AriaRuntimeException.class, unit::execute);
        assertEquals(String.format(EXPECT_MSG, "t"), ex.getMessage());
        assertEquals(2, ex.getStartLine(), "包装后结构化行号应保留");
        assertNotNull(ex.getCause(), "原始异常应挂为 cause(传播语义不变)");
        assertTrue(ex.getCause() instanceof AriaRuntimeException);
        assertEquals("不支持的后缀运算: (line 2:8)", ex.getCause().getMessage());
    }

    @Test
    void routineRuntimeErrorIsShimmerStyleWithLineAndSourceEcho() throws Exception {
        AriaCompiledRoutine rt = Aria.compile("r9", CODE, true);
        AriaRuntimeException ex = assertThrows(AriaRuntimeException.class, () -> rt.execute(Aria.createContext()));
        assertEquals(String.format(EXPECT_MSG, "r9"), ex.getMessage());
        assertEquals(2, ex.getStartLine());
    }

    /** 冷(解释)热(JIT)每轮都为 Shimmer 风格包装,且原始错误核心恒在(行号 JIT 轮可丢,由片段回退兜底)。 */
    @Test
    void everyRoundWrapsShimmerStyle() throws Exception {
        AriaCompilationUnit unit = Aria.compile("tw", Aria.createContext(), CODE, true);
        for (int r = 1; r <= 5; r++) {
            AriaRuntimeException ex = assertThrows(AriaRuntimeException.class, unit::execute, "第" + r + "轮应抛错");
            String msg = ex.getMessage();
            assertTrue(msg.startsWith("单元: [tw] 运行时错误"), "第" + r + "轮应含单元名: " + msg);
            assertTrue(msg.contains("\n请检查: "), "第" + r + "轮应含源码回显: " + msg);
            assertTrue(msg.contains("return nosuch(1)"), "第" + r + "轮回显应含出错源码: " + msg);
            assertTrue(msg.contains("\n错误信息: 不支持的后缀运算:"), "第" + r + "轮应含原始错误: " + msg);
        }
    }

    /** 行号拿不到的回退：源码片段回显(至多 3 行 + "...")。直接固定包内拼装函数(JIT 异步无法确定性触发)。 */
    @Test
    void noLineFallbackEchoesSourceSnippet() {
        ContentTracker tracker = new ContentTracker("a = 1\nb = 2\nc = 3\nd = 4\nreturn x");
        assertEquals("单元: [tt] 运行时错误\n请检查: a = 1\nb = 2\nc = 3\n...\n错误信息: boom",
                AriaCompiledRoutine.runtimeErrorMessage("tt", tracker, -1, -1, "boom"));
        // 短脚本不加省略号
        assertEquals("单元: [ts] 运行时错误\n请检查: a = 1\n错误信息: boom",
                AriaCompiledRoutine.runtimeErrorMessage("ts", new ContentTracker("a = 1"), -1, -1, "boom"));
    }

    /** 已被内层单元包装过的消息不再嵌套包装(原实例原样上抛)。 */
    @Test
    void alreadyWrappedMessageIsNotNestedAgain() {
        AriaRuntimeException inner = new AriaRuntimeException("单元: [in] 运行时错误\n请检查: x\n错误信息: boom");
        assertSame(inner, AriaCompiledRoutine.enrichRuntimeError("out", new ContentTracker("x"), inner));
    }

    /** 编译错误(CompileException)不受运行时包装影响,原样上抛。 */
    @Test
    void compileErrorsAreUntouched() {
        Exception ex = assertThrows(Exception.class, () -> Aria.compile("tc", Aria.createContext(), "# bad\n", true));
        assertFalse(String.valueOf(ex.getMessage()).contains("运行时错误"));
    }
}
