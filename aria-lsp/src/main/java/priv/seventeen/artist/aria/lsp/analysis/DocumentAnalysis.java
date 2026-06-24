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

package priv.seventeen.artist.aria.lsp.analysis;

import priv.seventeen.artist.aria.ast.ASTNode;
import priv.seventeen.artist.aria.exception.CompileException;
import priv.seventeen.artist.aria.parser.Lexer;
import priv.seventeen.artist.aria.parser.SourceLocation;
import priv.seventeen.artist.aria.parser.aria.AriaParser;

import java.util.ArrayList;
import java.util.List;

public class DocumentAnalysis {

    private final ASTNode ast;           // nullable: 解析失败时为 null
    private final List<DiagnosticInfo> diagnostics;
    private final List<SymbolInfo> symbols;

    public DocumentAnalysis(ASTNode ast, List<DiagnosticInfo> diagnostics, List<SymbolInfo> symbols) {
        this.ast = ast;
        this.diagnostics = diagnostics;
        this.symbols = symbols;
    }

    public static DocumentAnalysis analyze(String source) {
        List<DiagnosticInfo> diagnostics = new ArrayList<>();
        ASTNode ast = null;
        List<SymbolInfo> symbols = new ArrayList<>();

        try {
            Lexer lexer = new Lexer(source);
            AriaParser parser = new AriaParser(lexer);
            ast = parser.parse();
        } catch (CompileException e) {
            int line = Math.max(0, e.getLine() - 1);  // LSP 行号从 0 开始
            int col = Math.max(0, e.getColumn() - 1);
            diagnostics.add(new DiagnosticInfo(
                    e.getMessage(),
                    line, col,
                    line, col + 1,
                    DiagnosticInfo.Severity.ERROR
            ));
        }

        // 如果解析成功，收集符号
        if (ast != null) {
            SymbolCollector.collect(ast, symbols);
        }

        return new DocumentAnalysis(ast, diagnostics, symbols);
    }

    public ASTNode getAst() { return ast; }
    public List<DiagnosticInfo> getDiagnostics() { return diagnostics; }
    public List<SymbolInfo> getSymbols() { return symbols; }
    public boolean hasAst() { return ast != null; }

    public SymbolInfo findSymbolAt(int line, int column) {
        for (SymbolInfo symbol : symbols) {
            SourceLocation loc = symbol.location();
            if (loc.startLine() <= line && line <= loc.endLine()) {
                if (loc.startLine() == line && column < loc.startColumn()) continue;
                if (loc.endLine() == line && column > loc.endColumn()) continue;
                return symbol;
            }
        }
        return null;
    }

    public SymbolInfo findSymbolByName(String name) {
        for (SymbolInfo symbol : symbols) {
            if (symbol.name().equals(name)) return symbol;
        }
        return null;
    }

    public record DiagnosticInfo(
            String message,
            int startLine, int startColumn,
            int endLine, int endColumn,
            Severity severity
    ) {
        public enum Severity { ERROR, WARNING, INFO, HINT }
    }

    public record SymbolInfo(
            String name,
            SymbolKind kind,
            SourceLocation location,
            String detail       // 额外信息（如类型、参数列表）
    ) {
        public enum SymbolKind {
            VARIABLE, FUNCTION, CLASS, PARAMETER, FIELD, METHOD, IMPORT
        }
    }
}
