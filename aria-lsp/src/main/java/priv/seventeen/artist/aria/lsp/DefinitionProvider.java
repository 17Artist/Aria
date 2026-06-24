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

import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis.SymbolInfo;
import priv.seventeen.artist.aria.parser.SourceLocation;

import java.util.Collections;
import java.util.List;

public class DefinitionProvider {

        public List<LocationLink> findDefinition(String uri, DocumentAnalysis analysis, String content, Position position) {
        if (analysis == null || !analysis.hasAst()) return Collections.emptyList();

        String lineText = getLineText(content, position.getLine());
        String word = extractWordAt(lineText, position.getCharacter());
        if (word == null || word.isEmpty()) return Collections.emptyList();

        // 在符号表中查找
        SymbolInfo symbol = analysis.findSymbolByName(word);
        if (symbol == null) return Collections.emptyList();

        SourceLocation loc = symbol.location();
        if (loc == null || loc.startLine() < 0) return Collections.emptyList();

        // 转为 LSP 位置（0-based）
        int startLine = Math.max(0, loc.startLine() - 1);
        int startCol = Math.max(0, loc.startColumn() - 1);
        int endLine = Math.max(0, loc.endLine() - 1);
        int endCol = Math.max(0, loc.endColumn() - 1);

        Range targetRange = new Range(
                new Position(startLine, startCol),
                new Position(endLine, endCol)
        );

        // 计算光标处标识符的范围作为 originSelectionRange
        int wordStart = position.getCharacter();
        while (wordStart > 0 && isIdentChar(lineText.charAt(wordStart - 1))) wordStart--;
        int wordEnd = position.getCharacter();
        while (wordEnd < lineText.length() && isIdentChar(lineText.charAt(wordEnd))) wordEnd++;

        Range originRange = new Range(
                new Position(position.getLine(), wordStart),
                new Position(position.getLine(), wordEnd)
        );

        LocationLink link = new LocationLink(uri, targetRange, targetRange, originRange);
        return List.of(link);
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

    private boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
