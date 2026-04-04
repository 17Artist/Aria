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

public class AriaFormatter {

    private int indentSize = 4;
    private char indentChar = ' ';

    public AriaFormatter() {}

    public AriaFormatter indentSize(int size) { this.indentSize = size; return this; }
    public AriaFormatter useTabs() { this.indentChar = '\t'; this.indentSize = 1; return this; }

    public String format(String source) {
        String[] lines = source.split("\n", -1);
        StringBuilder result = new StringBuilder();
        int indent = 0;
        boolean prevBlank = false;

        for (String s : lines) {
            String line = s.trim();

            // 跳过连续空行（最多保留一个）
            if (line.isEmpty()) {
                if (!prevBlank && result.length() > 0) {
                    result.append("\n");
                    prevBlank = true;
                }
                continue;
            }
            prevBlank = false;

            // 闭合括号减少缩进
            if (line.startsWith("}") || line.startsWith("]") || line.startsWith(")")) {
                indent = Math.max(0, indent - 1);
            }
            // elif/else/catch/finally 减少再增加
            if (line.startsWith("elif") || line.startsWith("else") || line.startsWith("catch") || line.startsWith("finally")) {
                indent = Math.max(0, indent - 1);
            }

            // 写入缩进 + 格式化后的行
            String formatted = formatLine(line);
            appendIndent(result, indent);
            result.append(formatted).append("\n");

            // elif/else/catch/finally 恢复缩进
            if (line.startsWith("elif") || line.startsWith("else") || line.startsWith("catch") || line.startsWith("finally")) {
                indent++;
            }

            // 开括号增加缩进
            if (line.endsWith("{") || line.endsWith("[") || line.endsWith("(")) {
                indent++;
            }
        }

        // 移除末尾多余空行
        String out = result.toString();
        while (out.endsWith("\n\n")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private String formatLine(String line) {
        // 注释行不格式化
        if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
            return line;
        }

        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        char stringChar = 0;
        int i = 0;

        while (i < line.length()) {
            char c = line.charAt(i);

            // 字符串内不格式化
            if (inString) {
                sb.append(c);
                if (c == '\\' && i + 1 < line.length()) {
                    sb.append(line.charAt(++i));
                } else if (c == stringChar) {
                    inString = false;
                }
                i++;
                continue;
            }

            if (c == '\'' || c == '"') {
                inString = true;
                stringChar = c;
                sb.append(c);
                i++;
                continue;
            }

            // 运算符间距：确保 =, +=, -=, ==, != 等两侧有空格
            if (isAssignOrCompare(line, i)) {
                int opLen = operatorLength(line, i);
                // 确保左侧有空格
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                sb.append(line, i, i + opLen);
                sb.append(' ');
                i += opLen;
                // 跳过右侧已有的空格
                while (i < line.length() && line.charAt(i) == ' ') i++;
                continue;
            }

            // 逗号后加空格
            if (c == ',' && i + 1 < line.length() && line.charAt(i + 1) != ' ') {
                sb.append(", ");
                i++;
                continue;
            }

            sb.append(c);
            i++;
        }

        return sb.toString();
    }

    private boolean isAssignOrCompare(String line, int i) {
        char c = line.charAt(i);
        if (c == '=' && i + 1 < line.length() && line.charAt(i + 1) == '>') return false; // =>
        if (c == '-' && i + 1 < line.length() && line.charAt(i + 1) == '>') return false; // ->
        if (c == '=' || c == '!') {
            if (i + 1 < line.length() && line.charAt(i + 1) == '=') return true;
            if (c == '=') return true;
        }
        if ((c == '<' || c == '>') && i + 1 < line.length() && line.charAt(i + 1) == '=') return true;
        if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%') {
            if (i + 1 < line.length() && line.charAt(i + 1) == '=') return true;
        }
        if (c == '~' && i + 1 < line.length() && line.charAt(i + 1) == '=') return true;
        return false;
    }

    private int operatorLength(String line, int i) {
        if (i + 2 < line.length()) {
            String three = line.substring(i, i + 3);
            if (three.equals("===") || three.equals("!==") || three.equals(">>>=") || three.equals("<<=") || three.equals(">>=")) return 3;
        }
        if (i + 1 < line.length()) {
            String two = line.substring(i, i + 2);
            if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=")
                || two.equals("+=") || two.equals("-=") || two.equals("*=") || two.equals("/=")
                || two.equals("%=") || two.equals("~=") || two.equals("&=") || two.equals("|=")
                || two.equals("^=")) return 2;
        }
        return 1; // single =
    }

    private void appendIndent(StringBuilder sb, int level) {
        for (int i = 0; i < level * indentSize; i++) {
            sb.append(indentChar);
        }
    }
}
