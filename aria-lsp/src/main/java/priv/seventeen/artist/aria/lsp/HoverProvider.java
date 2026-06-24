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

/**
 * 悬停文档提供器。
 */
public class HoverProvider {

    public Hover hover(DocumentAnalysis analysis, String content, Position position) {
        String lineText = getLineText(content, position.getLine());
        int col = position.getCharacter();

        String word = extractWordAt(lineText, col);
        if (word == null || word.isEmpty()) return null;

        // 1. 限定名（如 math.sin / fs.read / Promise.all）：优先匹配内置函数文档
        String qualified = extractQualifiedName(lineText, col);
        if (qualified != null) {
            String doc = LanguageDocs.function(qualified);
            if (doc != null) return markdown(doc);

            int lastDot = qualified.lastIndexOf('.');
            if (lastDot >= 0) {
                String func = qualified.substring(lastDot + 1);
                String before = qualified.substring(0, lastDot);
                String segment = before.substring(before.lastIndexOf('.') + 1);
                if (!segment.isEmpty()) {
                    doc = LanguageDocs.function(segment + "." + func);
                    if (doc != null) return markdown(doc);
                }
            }
            // 未命中则继续按裸词处理（光标可能落在命名空间段上）
        }

        // 2. 关键字 / 字面量 / 命名空间前缀 / 语境标识符（保留字优先于一切）
        String doc = LanguageDocs.keyword(word);
        if (doc != null) return markdown(doc);

        // 3. 用户在本文档中声明的符号，优先于内置命名空间概览
        if (analysis != null) {
            SymbolInfo symbol = analysis.findSymbolByName(word);
            if (symbol != null) {
                String md = "```aria\n" + symbol.detail() + " " + symbol.name() + "\n```";
                return markdown(md);
            }
        }

        // 4. 构造器（Range / UUID / 动画构造器等）
        doc = LanguageDocs.constructor(word);
        if (doc != null) return markdown(doc);

        // 5. 命名空间概览（math / string / fs ...，含别名）
        doc = LanguageDocs.namespace(word);
        if (doc != null) return markdown(doc);

        // 6. 全局内置函数（print / println / use）
        doc = LanguageDocs.global(word);
        if (doc != null) return markdown(doc);

        return null;
    }

    private Hover markdown(String value) {
        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, value));
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
