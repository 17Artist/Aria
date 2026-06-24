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
import org.eclipse.lsp4j.services.*;

import java.util.concurrent.CompletableFuture;

public class AriaLanguageServer implements LanguageServer, LanguageClientAware {

    private LanguageClient client;
    private final AriaTextDocumentService textDocumentService;
    private final AriaWorkspaceService workspaceService;
    private int shutdownStatus = 1;

    public AriaLanguageServer() {
        this.textDocumentService = new AriaTextDocumentService(this);
        this.workspaceService = new AriaWorkspaceService();
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        this.textDocumentService.connect(client);
    }

    public LanguageClient getClient() {
        return client;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();

        TextDocumentSyncOptions syncOptions = new TextDocumentSyncOptions();
        syncOptions.setOpenClose(true);
        syncOptions.setChange(TextDocumentSyncKind.Full);
        capabilities.setTextDocumentSync(syncOptions);

        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(java.util.List.of(".", ":", "@"));
        completionOptions.setResolveProvider(false);
        capabilities.setCompletionProvider(completionOptions);

        capabilities.setHoverProvider(true);

        capabilities.setDefinitionProvider(true);

        capabilities.setDocumentSymbolProvider(true);

        ServerInfo serverInfo = new ServerInfo("Aria Language Server", "1.0.0");
        return CompletableFuture.completedFuture(new InitializeResult(capabilities, serverInfo));
    }

    @Override
    public void initialized(InitializedParams params) {
        // 服务器初始化完成
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        shutdownStatus = 0;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(shutdownStatus);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }
}
