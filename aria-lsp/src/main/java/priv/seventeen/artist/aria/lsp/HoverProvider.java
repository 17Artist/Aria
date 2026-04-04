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

package priv.seventeen.artist.aria.lsp;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis.SymbolInfo;

import java.util.LinkedHashMap;
import java.util.Map;

public class HoverProvider {

    // 内置函数文档：namespace.name → 文档
    private static final Map<String, String> BUILTIN_DOCS = new LinkedHashMap<>();

    // 关键字文档
    private static final Map<String, String> KEYWORD_DOCS = new LinkedHashMap<>();


    private static void addDoc(String key, String doc) {
        BUILTIN_DOCS.put(key, doc);
    }

        public Hover hover(DocumentAnalysis analysis, String content, Position position) {
        String lineText = getLineText(content, position.getLine());
        int col = position.getCharacter();

        String word = extractWordAt(lineText, col);
        if (word == null || word.isEmpty()) return null;

        String qualifiedName = extractQualifiedName(lineText, col);
        if (qualifiedName != null) {
            String doc = BUILTIN_DOCS.get(qualifiedName);
            if (doc != null) {
                return new Hover(new MarkupContent(MarkupKind.MARKDOWN, doc));
            }
        }

        // 尝试匹配关键字
        String kwDoc = KEYWORD_DOCS.get(word);
        if (kwDoc != null) {
            return new Hover(new MarkupContent(MarkupKind.MARKDOWN, kwDoc));
        }

        // 尝试匹配文档中的符号
        if (analysis != null) {
            SymbolInfo symbol = analysis.findSymbolByName(word);
            if (symbol != null) {
                String md = "```\n" + symbol.detail() + " " + symbol.name() + "\n```";
                return new Hover(new MarkupContent(MarkupKind.MARKDOWN, md));
            }
        }

        return null;
    }

    private String getLineText(String content, int line) {
        String[] lines = content.split("\n", -1);
        if (line >= 0 && line < lines.length) return lines[line];
        return "";
    }

        private String extractWordAt(String line, int col) {
        if (col < 0 || col > line.length()) return null;
        int start = col, end = col;
        while (start > 0 && isIdentChar(line.charAt(start - 1))) start--;
        while (end < line.length() && isIdentChar(line.charAt(end))) end++;
        if (start == end) return null;
        return line.substring(start, end);
    }

        private String extractQualifiedName(String line, int col) {
        if (col < 0 || col > line.length()) return null;
        int start = col, end = col;
        // 向右扩展
        while (end < line.length() && isIdentChar(line.charAt(end))) end++;
        // 向左扩展，包含 '.'
        while (start > 0 && (isIdentChar(line.charAt(start - 1)) || line.charAt(start - 1) == '.')) start--;
        if (start == end) return null;
        String text = line.substring(start, end);
        if (text.contains(".")) return text;
        return null;
    }

    private boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
