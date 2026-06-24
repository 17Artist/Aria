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

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis.SymbolInfo;
import priv.seventeen.artist.aria.parser.SourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AriaTextDocumentService implements TextDocumentService {

    private final AriaLanguageServer server;
    private final DocumentManager documentManager;
    private final CompletionProvider completionProvider;
    private final HoverProvider hoverProvider;
    private final DefinitionProvider definitionProvider;
    private DiagnosticsProvider diagnosticsProvider;

    public AriaTextDocumentService(AriaLanguageServer server) {
        this.server = server;
        this.documentManager = new DocumentManager();
        this.completionProvider = new CompletionProvider();
        this.hoverProvider = new HoverProvider();
        this.definitionProvider = new DefinitionProvider();
    }

    public void connect(LanguageClient client) {
        this.diagnosticsProvider = new DiagnosticsProvider(client);
    }


    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String content = params.getTextDocument().getText();
        documentManager.open(uri, content);
        analyzeAndDiagnose(uri);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
        if (!changes.isEmpty()) {
            String content = changes.get(changes.size() - 1).getText();
            documentManager.update(uri, content);
            analyzeAndDiagnose(uri);
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documentManager.close(uri);
        if (diagnosticsProvider != null) {
            server.getClient().publishDiagnostics(
                    new PublishDiagnosticsParams(uri, List.of()));
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        analyzeAndDiagnose(uri);
    }


    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String content = documentManager.getContent(uri);
            if (content == null) return Either.forLeft(List.of());

            DocumentAnalysis analysis = documentManager.getAnalysis(uri);
            List<CompletionItem> items = completionProvider.complete(analysis, content, params.getPosition());
            return Either.forLeft(items);
        });
    }


    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String content = documentManager.getContent(uri);
            if (content == null) return null;

            DocumentAnalysis analysis = documentManager.getAnalysis(uri);
            return hoverProvider.hover(analysis, content, params.getPosition());
        });
    }


    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String content = documentManager.getContent(uri);
            if (content == null) return Either.forRight(List.of());

            DocumentAnalysis analysis = documentManager.getAnalysis(uri);
            List<LocationLink> links = definitionProvider.findDefinition(uri, analysis, content, params.getPosition());
            return Either.forRight(links);
        });
    }


    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            DocumentAnalysis analysis = documentManager.getAnalysis(uri);
            if (analysis == null) return List.of();

            List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
            for (SymbolInfo symbol : analysis.getSymbols()) {
                DocumentSymbol ds = new DocumentSymbol();
                ds.setName(symbol.name());
                ds.setKind(mapSymbolKind(symbol.kind()));
                ds.setDetail(symbol.detail());

                SourceLocation loc = symbol.location();
                int startLine = Math.max(0, loc.startLine() - 1);
                int startCol = Math.max(0, loc.startColumn() - 1);
                int endLine = Math.max(0, loc.endLine() - 1);
                int endCol = Math.max(0, loc.endColumn() - 1);
                Range range = new Range(new Position(startLine, startCol), new Position(endLine, endCol));
                ds.setRange(range);
                ds.setSelectionRange(range);

                result.add(Either.forRight(ds));
            }
            return result;
        });
    }


    private void analyzeAndDiagnose(String uri) {
        documentManager.invalidateAnalysis(uri);
        DocumentAnalysis analysis = documentManager.getAnalysis(uri);
        if (analysis != null && diagnosticsProvider != null) {
            diagnosticsProvider.diagnose(uri, analysis);
        }
    }

    private SymbolKind mapSymbolKind(SymbolInfo.SymbolKind kind) {
        return switch (kind) {
            case VARIABLE -> SymbolKind.Variable;
            case FUNCTION -> SymbolKind.Function;
            case CLASS -> SymbolKind.Class;
            case PARAMETER -> SymbolKind.Variable;
            case FIELD -> SymbolKind.Field;
            case METHOD -> SymbolKind.Method;
            case IMPORT -> SymbolKind.Module;
        };
    }
}
