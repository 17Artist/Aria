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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 内置语言文档库。
 */
final class LanguageDocs {

    private static final String RESOURCE = "/docs/aria-language.json";

    // 关键字 / 字面量 / 命名空间前缀 / 语境标识符：键为词形
    private static final Map<String, String> KEYWORDS = new HashMap<>();
    // 命名空间概览（含别名）：键为命名空间名
    private static final Map<String, String> NAMESPACES = new HashMap<>();
    // 静态命名空间下的函数：键为 "namespace.function"（含别名前缀）
    private static final Map<String, String> FUNCTIONS = new HashMap<>();
    // 全局（无命名空间）内置函数：键为函数名
    private static final Map<String, String> GLOBALS = new HashMap<>();
    // 构造器：键为构造器名（如 Range / UUID / Lerp）
    private static final Map<String, String> CONSTRUCTORS = new HashMap<>();

    private static volatile boolean loaded = false;

    private LanguageDocs() {}

    static void ensureLoaded() {
        if (loaded) return;
        synchronized (LanguageDocs.class) {
            if (loaded) return;
            try {
                load();
            } catch (Exception ignored) {
                // 资源缺失或损坏时静默降级：悬停退化为仅符号表，不影响其它能力
            }
            loaded = true;
        }
    }

    private static void load() throws Exception {
        try (InputStream in = LanguageDocs.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return;
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            JsonArray keywords = root.getAsJsonArray("keywords");
            if (keywords != null) {
                for (JsonElement e : keywords) {
                    JsonObject k = e.getAsJsonObject();
                    String name = asString(k, "keyword");
                    String doc = asString(k, "docMarkdown");
                    if (name != null && doc != null) KEYWORDS.put(name, doc);
                }
            }

            JsonArray namespaces = root.getAsJsonArray("namespaces");
            if (namespaces != null) {
                for (JsonElement e : namespaces) {
                    loadNamespace(e.getAsJsonObject());
                }
            }
        }
    }

    private static void loadNamespace(JsonObject ns) {
        String name = asString(ns, "namespace");
        if (name == null) name = "";
        String callStyle = asString(ns, "callStyle");
        if (callStyle == null) callStyle = "static";
        String overview = asString(ns, "docMarkdown");
        JsonArray aliases = ns.has("aliases") && ns.get("aliases").isJsonArray()
                ? ns.getAsJsonArray("aliases") : null;

        // 命名空间概览（对象方法命名空间如 string/list 同样登记，便于悬停类型名）
        if (overview != null && !name.isEmpty()) {
            NAMESPACES.put(name, overview);
            if (aliases != null) {
                for (JsonElement a : aliases) NAMESPACES.put(a.getAsString(), overview);
            }
        }

        JsonArray functions = ns.has("functions") && ns.get("functions").isJsonArray()
                ? ns.getAsJsonArray("functions") : null;
        if (functions == null) return;

        for (JsonElement fe : functions) {
            JsonObject f = fe.getAsJsonObject();
            String fn = asString(f, "name");
            if (fn == null) continue;
            String md = renderFunction(f, fn);

            if ("constructor".equals(callStyle)) {
                CONSTRUCTORS.put(fn, md);
            } else if (name.isEmpty()) {
                GLOBALS.put(fn, md);
            } else {
                FUNCTIONS.put(name + "." + fn, md);
                if (aliases != null) {
                    for (JsonElement a : aliases) FUNCTIONS.put(a.getAsString() + "." + fn, md);
                }
            }
        }
    }

    private static String renderFunction(JsonObject f, String fn) {
        String signature = asString(f, "signature");
        String description = asString(f, "description");
        StringBuilder sb = new StringBuilder();
        sb.append("```aria\n").append(signature != null ? signature : fn).append("\n```");
        if (description != null && !description.isEmpty()) {
            sb.append("\n\n").append(description);
        }
        return sb.toString();
    }

    private static String asString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsString();
    }

    // ── 查询 ──────────────────────────────────────────────

    static String keyword(String word) { ensureLoaded(); return KEYWORDS.get(word); }

    static String namespace(String name) { ensureLoaded(); return NAMESPACES.get(name); }

    static String function(String qualified) { ensureLoaded(); return FUNCTIONS.get(qualified); }

    static String global(String name) { ensureLoaded(); return GLOBALS.get(name); }

    static String constructor(String name) { ensureLoaded(); return CONSTRUCTORS.get(name); }

    static Map<String, String> keywords() { ensureLoaded(); return Collections.unmodifiableMap(KEYWORDS); }
}
