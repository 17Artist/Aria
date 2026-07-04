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

package priv.seventeen.artist.aria.api;

import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.parser.ContentTracker;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.Result;
import priv.seventeen.artist.aria.value.IValue;

public class AriaCompiledRoutine {
    private final String name;
    private final IRProgram program;
    private final ContentTracker tracker;

    public AriaCompiledRoutine(String name, IRProgram program, ContentTracker tracker) {
        this.name = name;
        this.program = program;
        this.tracker = tracker;
    }

    public IValue<?> execute(Context context) throws AriaException {
        try {
            Interpreter interpreter = new Interpreter();
            Result result = interpreter.execute(program, context);
            return result.getValue();
        } catch (AriaRuntimeException e) {
            throw enrichRuntimeError(name, tracker, e);
        } catch (AriaException e) {
            throw e;
        } catch (Exception e) {
            throw new AriaRuntimeException(
                    runtimeErrorMessage(name, tracker, -1, -1, originalMessage(e)), e);
        }
    }


    public static AriaRuntimeException enrichRuntimeError(String name, ContentTracker tracker, AriaRuntimeException e) {
        String original = originalMessage(e);
        if (original.startsWith("单元: [")) {
            return e; // 已由内层执行入口包装过(如嵌套 eval)，不再重复嵌套
        }
        return new AriaRuntimeException(
                runtimeErrorMessage(name, tracker, e.getStartLine(), e.getEndLine(), original),
                e.getStartLine(), e.getStartColumn(), e.getEndLine(), e);
    }

    /** 拼装 "单元: [name] 运行时错误, 位于第 X 行, 到第 Y 行\n请检查: <源码行>\n错误信息: <原始错误>"。 */
    public static String runtimeErrorMessage(String name, ContentTracker tracker, int startLine, int endLine, String original) {
        StringBuilder sb = new StringBuilder("单元: [").append(name).append("] 运行时错误");
        String snippet;
        if (startLine > 0) {
            int end = Math.max(endLine, startLine);
            sb.append(", 位于第 ").append(startLine).append(" 行, 到第 ").append(end).append(" 行");
            snippet = tracker != null ? tracker.getContent(startLine, end) : "";
        } else {
            // 行号拿不到(如 JIT 生成码抛出的无行号异常)：尽量回显源码片段(至多 3 行)
            snippet = tracker != null ? sourceSnippet(tracker) : "";
        }
        return sb.append("\n请检查: ").append(snippet)
                 .append("\n错误信息: ").append(original).toString();
    }

    private static String sourceSnippet(ContentTracker tracker) {
        int lines = Math.min(3, tracker.lineCount());
        String snippet = tracker.getContent(1, lines);
        return tracker.lineCount() > lines ? snippet + "\n..." : snippet;
    }

    private static String originalMessage(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    public String getName() { return name; }
    public IRProgram getProgram() { return program; }
    public ContentTracker getTracker() { return tracker; }

    private java.util.List<String> warnings = java.util.Collections.emptyList();

    public void setWarnings(java.util.List<String> warnings) {
        this.warnings = warnings != null ? warnings : java.util.Collections.emptyList();
    }

    public java.util.List<String> getWarnings() { return warnings; }
}
