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

import org.jline.reader.*;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

import java.io.IOException;
import java.nio.file.Paths;

public class AriaREPL {

    public static void main(String[] args) throws IOException {
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        // Tab 补全：关键字 + 命名空间
        Completer completer = new AggregateCompleter(
            new StringsCompleter(
                // 关键字
                "if", "elif", "else", "while", "for", "in", "switch", "match",
                "case", "break", "return", "next", "async", "true", "false", "none",
                "class", "extends", "super", "try", "catch", "finally", "throw",
                "import", "from", "as", "export",
                // 命名空间
                "var.", "val.", "global.", "server.", "client.",
                // 内置函数
                "print", "println", "use",
                // 标准库命名空间
                "math.", "console.", "type.", "string.", "regex.", "crypto.",
                "datetime.", "scheduler.", "template.", "fs.", "net.", "event.",
                "json.", "serial.", "db.",
                // 命令
                "exit", "quit", "reset"
            )
        );

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .variable(LineReader.HISTORY_FILE,
                    Paths.get(System.getProperty("user.home"), ".aria_history"))
                .option(LineReader.Option.CASE_INSENSITIVE, false)
                .build();

        Context ctx = Aria.createContext();

        while (true) {
            String line;
            try {
                line = reader.readLine(">>> ");
            } catch (UserInterruptException e) {
                continue; // Ctrl+C: 取消当前行
            } catch (EndOfFileException e) {
                break; // Ctrl+D: 退出
            }

            if (line == null || line.isBlank()) continue;
            String trimmed = line.trim();
            if ("exit".equals(trimmed) || "quit".equals(trimmed)) break;
            if ("reset".equals(trimmed)) {
                ctx = Aria.createContext();
                System.out.println("Context reset.");
                continue;
            }

            // 多行输入：花括号未闭合时继续读取
            StringBuilder code = new StringBuilder(line);
            int braceDepth = countBraces(line);
            while (braceDepth > 0) {
                String cont;
                try {
                    cont = reader.readLine("... ");
                } catch (UserInterruptException | EndOfFileException e) {
                    break;
                }
                if (cont == null) break;
                code.append('\n').append(cont);
                braceDepth += countBraces(cont);
            }

            try {
                IValue<?> result = Aria.eval(code.toString(), ctx);
                if (result != null && !(result instanceof NoneValue)) {
                    System.out.println("= " + result.stringValue());
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        System.out.println("Bye.");
    }

    private static int countBraces(String line) {
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inString) {
                if (c == stringChar && (i == 0 || line.charAt(i - 1) != '\\')) {
                    inString = false;
                }
            } else {
                if (c == '\'' || c == '"') {
                    inString = true;
                    stringChar = c;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            }
        }
        return depth;
    }
}
