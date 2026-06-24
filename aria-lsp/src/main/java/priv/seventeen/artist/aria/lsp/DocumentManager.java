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

import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DocumentManager {

    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, DocumentAnalysis> analysisCache = new ConcurrentHashMap<>();

    public void open(String uri, String content) {
        documents.put(uri, content);
        analysisCache.remove(uri);
    }

    public void update(String uri, String content) {
        documents.put(uri, content);
        analysisCache.remove(uri);
    }

    public void close(String uri) {
        documents.remove(uri);
        analysisCache.remove(uri);
    }

    public String getContent(String uri) {
        return documents.get(uri);
    }

    public boolean isOpen(String uri) {
        return documents.containsKey(uri);
    }

    public DocumentAnalysis getAnalysis(String uri) {
        return analysisCache.computeIfAbsent(uri, u -> {
            String content = documents.get(u);
            if (content == null) return null;
            return DocumentAnalysis.analyze(content);
        });
    }

    public void invalidateAnalysis(String uri) {
        analysisCache.remove(uri);
    }
}
