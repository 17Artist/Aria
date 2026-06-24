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

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis.SymbolInfo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LspTest {

    private DocumentManager documentManager;
    private CompletionProvider completionProvider;
    private HoverProvider hoverProvider;
    private DefinitionProvider definitionProvider;

    @BeforeEach
    void setUp() {
        documentManager = new DocumentManager();
        completionProvider = new CompletionProvider();
        hoverProvider = new HoverProvider();
        definitionProvider = new DefinitionProvider();
    }


    @Test
    void testDocumentOpenAndClose() {
        String uri = "file:///test.aria";
        documentManager.open(uri, "var.x = 1");
        assertTrue(documentManager.isOpen(uri));
        assertEquals("var.x = 1", documentManager.getContent(uri));

        documentManager.close(uri);
        assertFalse(documentManager.isOpen(uri));
    }

    @Test
    void testDocumentUpdate() {
        String uri = "file:///test.aria";
        documentManager.open(uri, "var.x = 1");
        documentManager.update(uri, "var.x = 2");
        assertEquals("var.x = 2", documentManager.getContent(uri));
    }


    @Test
    void testValidCodeNoDiagnostics() {
        DocumentAnalysis analysis = DocumentAnalysis.analyze("var.x = 1\nvar.y = 2");
        assertTrue(analysis.hasAst());
        assertTrue(analysis.getDiagnostics().isEmpty());
    }

    @Test
    void testSyntaxErrorDiagnostic() {
        DocumentAnalysis analysis = DocumentAnalysis.analyze("if { }");
        assertFalse(analysis.getDiagnostics().isEmpty());
        DocumentAnalysis.DiagnosticInfo diag = analysis.getDiagnostics().get(0);
        assertEquals(DocumentAnalysis.DiagnosticInfo.Severity.ERROR, diag.severity());
    }

    @Test
    void testUnterminatedStringDiagnostic() {
        DocumentAnalysis analysis = DocumentAnalysis.analyze("var.x = 'hello");
        assertFalse(analysis.getDiagnostics().isEmpty());
    }


    @Test
    void testVariableSymbol() {
        DocumentAnalysis analysis = DocumentAnalysis.analyze("var.x = 42");
        assertTrue(analysis.hasAst());
        List<SymbolInfo> symbols = analysis.getSymbols();
        assertEquals(1, symbols.size());
        assertEquals("x", symbols.get(0).name());
        assertEquals(SymbolInfo.SymbolKind.VARIABLE, symbols.get(0).kind());
    }

    @Test
    void testValSymbol() {
        DocumentAnalysis analysis = DocumentAnalysis.analyze("val.name = 'hello'");
        assertTrue(analysis.hasAst());
        List<SymbolInfo> symbols = analysis.getSymbols();
        assertEquals(1, symbols.size());
        assertEquals("name", symbols.get(0).name());
        assertTrue(symbols.get(0).detail().contains("val"));
    }

    @Test
    void testFunctionSymbol() {
        DocumentAnalysis analysis = DocumentAnalysis.analyze("var.add = -> {\n  return 1\n}");
        assertTrue(analysis.hasAst());
        List<SymbolInfo> symbols = analysis.getSymbols();
        assertEquals(1, symbols.size());
        assertEquals("add", symbols.get(0).name());
        assertEquals(SymbolInfo.SymbolKind.FUNCTION, symbols.get(0).kind());
    }

    @Test
    void testClassSymbol() {
        String code = "class Animal {\n  val.name = 'unknown'\n  speak = -> {\n    return val.name\n  }\n}";
        DocumentAnalysis analysis = DocumentAnalysis.analyze(code);
        assertTrue(analysis.hasAst());
        List<SymbolInfo> symbols = analysis.getSymbols();
        // class + field + method
        assertTrue(symbols.size() >= 1);
        assertEquals("Animal", symbols.get(0).name());
        assertEquals(SymbolInfo.SymbolKind.CLASS, symbols.get(0).kind());
    }

    @Test
    void testMultipleSymbols() {
        String code = "var.x = 1\nval.y = 'hello'\nvar.fn = -> { return 0 }";
        DocumentAnalysis analysis = DocumentAnalysis.analyze(code);
        assertTrue(analysis.hasAst());
        assertEquals(3, analysis.getSymbols().size());
    }

    @Test
    void testFindSymbolByName() {
        DocumentAnalysis analysis = DocumentAnalysis.analyze("var.x = 1\nvar.y = 2");
        SymbolInfo found = analysis.findSymbolByName("y");
        assertNotNull(found);
        assertEquals("y", found.name());
    }


    @Test
    void testKeywordCompletion() {
        DocumentAnalysis analysis = DocumentAnalysis.analyze("");
        List<CompletionItem> items = completionProvider.complete(analysis, "", new Position(0, 0));
        assertFalse(items.isEmpty());
        // 应包含关键字
        assertTrue(items.stream().anyMatch(i -> "if".equals(i.getLabel())));
        assertTrue(items.stream().anyMatch(i -> "for".equals(i.getLabel())));
        assertTrue(items.stream().anyMatch(i -> "class".equals(i.getLabel())));
    }

    @Test
    void testNamespaceCompletion() {
        DocumentAnalysis analysis = DocumentAnalysis.analyze("");
        List<CompletionItem> items = completionProvider.complete(analysis, "", new Position(0, 0));
        assertTrue(items.stream().anyMatch(i -> "math".equals(i.getLabel())));
        assertTrue(items.stream().anyMatch(i -> "console".equals(i.getLabel())));
        assertTrue(items.stream().anyMatch(i -> "var".equals(i.getLabel())));
    }

    @Test
    void testMathMemberCompletion() {
        String content = "math.";
        DocumentAnalysis analysis = DocumentAnalysis.analyze(content);
        List<CompletionItem> items = completionProvider.complete(analysis, content, new Position(0, 5));
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> "sin".equals(i.getLabel())));
        assertTrue(items.stream().anyMatch(i -> "cos".equals(i.getLabel())));
        assertTrue(items.stream().anyMatch(i -> "PI".equals(i.getLabel())));
    }

    @Test
    void testSymbolCompletion() {
        String content = "var.myVar = 42\n";
        DocumentAnalysis analysis = DocumentAnalysis.analyze(content);
        List<CompletionItem> items = completionProvider.complete(analysis, content, new Position(1, 0));
        assertTrue(items.stream().anyMatch(i -> "myVar".equals(i.getLabel())));
    }


    @Test
    void testHoverBuiltinFunction() {
        String content = "val.x = math.sin(3.14)";
        DocumentAnalysis analysis = DocumentAnalysis.analyze(content);
        // 悬停在 "sin" 上（位于 math.sin 中）
        Hover hover = hoverProvider.hover(analysis, content, new Position(0, 15));
        assertNotNull(hover);
        assertTrue(hover.getContents().getRight().getValue().contains("sin"));
    }

    @Test
    void testHoverKeyword() {
        String content = "if true { }";
        DocumentAnalysis analysis = DocumentAnalysis.analyze(content);
        Hover hover = hoverProvider.hover(analysis, content, new Position(0, 1));
        assertNotNull(hover);
        assertTrue(hover.getContents().getRight().getValue().contains("条件语句"));
    }

    @Test
    void testHoverVariable() {
        String content = "var.myVar = 42";
        DocumentAnalysis analysis = DocumentAnalysis.analyze(content);
        Hover hover = hoverProvider.hover(analysis, content, new Position(0, 6));
        assertNotNull(hover);
        assertTrue(hover.getContents().getRight().getValue().contains("myVar"));
    }

    @Test
    void testHoverNoResult() {
        String content = "var.x = 1";
        DocumentAnalysis analysis = DocumentAnalysis.analyze(content);
        // 悬停在空白处
        Hover hover = hoverProvider.hover(analysis, content, new Position(0, 7));
        assertNull(hover);
    }


    @Test
    void testDefinitionFound() {
        String content = "var.x = 1\nvar.y = var.x + 1";
        DocumentAnalysis analysis = DocumentAnalysis.analyze(content);
        // 在第二行的 "x" 上跳转 (col 12)
        List<LocationLink> links = definitionProvider.findDefinition(
                "file:///test.aria", analysis, content, new Position(1, 12));
        assertFalse(links.isEmpty());
    }

    @Test
    void testDefinitionNotFound() {
        String content = "var.x = 1";
        DocumentAnalysis analysis = DocumentAnalysis.analyze(content);
        // 在未知标识符上跳转
        List<LocationLink> links = definitionProvider.findDefinition(
                "file:///test.aria", analysis, content, new Position(0, 7));
        assertTrue(links.isEmpty());
    }


    @Test
    void testAnalysisCaching() {
        String uri = "file:///test.aria";
        documentManager.open(uri, "var.x = 1");
        DocumentAnalysis a1 = documentManager.getAnalysis(uri);
        DocumentAnalysis a2 = documentManager.getAnalysis(uri);
        assertSame(a1, a2, "分析结果应被缓存");

        documentManager.update(uri, "var.x = 2");
        DocumentAnalysis a3 = documentManager.getAnalysis(uri);
        assertNotSame(a1, a3, "更新后应重新分析");
    }


    @Test
    void testComplexCodeAnalysis() {
        String code = """
                import math
                
                class Calculator {
                    val.result = 0
                    
                    add = -> {
                        return args[0] + args[1]
                    }
                }
                
                var.calc = new Calculator()
                var.sum = calc.add(1, 2)
                
                for (var.i = 0; var.i < 10; var.i += 1) {
                    print(var.i)
                }
                
                if (var.sum > 0) {
                    print('positive')
                } else {
                    print('non-positive')
                }
                """;
        DocumentAnalysis analysis = DocumentAnalysis.analyze(code);
        assertTrue(analysis.hasAst());
        assertTrue(analysis.getDiagnostics().isEmpty());
        assertFalse(analysis.getSymbols().isEmpty());
    }

    @Test
    void testEmptyDocument() {
        DocumentAnalysis analysis = DocumentAnalysis.analyze("");
        assertTrue(analysis.hasAst());
        assertTrue(analysis.getDiagnostics().isEmpty());
        assertTrue(analysis.getSymbols().isEmpty());
    }
}
