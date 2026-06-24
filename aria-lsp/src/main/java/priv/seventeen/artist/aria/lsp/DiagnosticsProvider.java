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
import org.eclipse.lsp4j.services.LanguageClient;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticsProvider {

    private final LanguageClient client;

    public DiagnosticsProvider(LanguageClient client) {
        this.client = client;
    }

        public void diagnose(String uri, DocumentAnalysis analysis) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (DocumentAnalysis.DiagnosticInfo info : analysis.getDiagnostics()) {
            Diagnostic diagnostic = new Diagnostic();
            diagnostic.setRange(new Range(
                    new Position(info.startLine(), info.startColumn()),
                    new Position(info.endLine(), info.endColumn())
            ));
            diagnostic.setSeverity(mapSeverity(info.severity()));
            diagnostic.setSource("aria");
            diagnostic.setMessage(info.message());
            diagnostics.add(diagnostic);
        }

        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    private DiagnosticSeverity mapSeverity(DocumentAnalysis.DiagnosticInfo.Severity severity) {
        return switch (severity) {
            case ERROR -> DiagnosticSeverity.Error;
            case WARNING -> DiagnosticSeverity.Warning;
            case INFO -> DiagnosticSeverity.Information;
            case HINT -> DiagnosticSeverity.Hint;
        };
    }
}
