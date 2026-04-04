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
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Position;
import priv.seventeen.artist.aria.Aria;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis.SymbolInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CompletionProvider {

    private static final List<String> KEYWORDS = List.of(
            "if", "elif", "else", "while", "for", "in", "switch", "case", "match",
            "break", "return", "next", "async",
            "class", "extends", "super",
            "try", "catch", "finally", "throw",
            "import", "from", "as", "export",
            "true", "false", "none", "use"
    );

    private static final List<String> VAR_PREFIXES = List.of(
            "var", "val", "global", "server", "client"
    );

    private static boolean engineInitialized = false;

    private static void ensureEngine() {
        if (!engineInitialized) {
            Aria.getEngine().initialize();
            engineInitialized = true;
        }
    }

    public List<CompletionItem> complete(DocumentAnalysis analysis, String content, Position position) {
        ensureEngine();
        List<CompletionItem> items = new ArrayList<>();
        CallableManager mgr = CallableManager.INSTANCE;

        String lineText = getLineText(content, position.getLine());
        String prefix = lineText.substring(0, Math.min(position.getCharacter(), lineText.length()));
        String trimmed = prefix.stripLeading();

        int lastDot = trimmed.lastIndexOf('.');
        if (lastDot >= 0) {
            String ns = trimmed.substring(0, lastDot).stripTrailing();
            int nsStart = Math.max(ns.lastIndexOf(' '), Math.max(ns.lastIndexOf('('), ns.lastIndexOf('=')));
            if (nsStart >= 0) ns = ns.substring(nsStart + 1).strip();

            Set<String> funcs = mgr.getFunctionNames(ns);
            if (!funcs.isEmpty()) {
                for (String fn : funcs) {
                    CompletionItem ci = new CompletionItem(fn);
                    ci.setKind(CompletionItemKind.Function);
                    items.add(ci);
                }
                return items;
            }
        }

        for (String kw : KEYWORDS) {
            CompletionItem ci = new CompletionItem(kw);
            ci.setKind(CompletionItemKind.Keyword);
            items.add(ci);
        }

        for (String vp : VAR_PREFIXES) {
            CompletionItem ci = new CompletionItem(vp);
            ci.setKind(CompletionItemKind.Module);
            items.add(ci);
        }

        for (String ns : mgr.getNamespaces()) {
            if (ns.isEmpty()) continue;
            CompletionItem ci = new CompletionItem(ns);
            ci.setKind(CompletionItemKind.Module);
            items.add(ci);
        }

        for (String ctor : mgr.getConstructorNames()) {
            CompletionItem ci = new CompletionItem(ctor);
            ci.setKind(CompletionItemKind.Constructor);
            items.add(ci);
        }

        if (analysis != null) {
            for (SymbolInfo symbol : analysis.getSymbols()) {
                CompletionItem ci = new CompletionItem(symbol.name());
                ci.setKind(mapSymbolKind(symbol.kind()));
                items.add(ci);
            }
        }

        return items;
    }

    private CompletionItemKind mapSymbolKind(SymbolInfo.SymbolKind kind) {
        return switch (kind) {
            case VARIABLE -> CompletionItemKind.Variable;
            case FUNCTION -> CompletionItemKind.Function;
            case CLASS -> CompletionItemKind.Class;
            case PARAMETER -> CompletionItemKind.Variable;
            case FIELD -> CompletionItemKind.Field;
            case METHOD -> CompletionItemKind.Method;
            case IMPORT -> CompletionItemKind.Module;
        };
    }

    private String getLineText(String content, int line) {
        String[] lines = content.split("\n", -1);
        if (line >= 0 && line < lines.length) {
            return lines[line];
        }
        return "";
    }
}
