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

package priv.seventeen.artist.aria.module;

import priv.seventeen.artist.aria.compiler.ir.IRProgram;

import java.util.concurrent.ConcurrentHashMap;

public class ModuleCache {
    private final ConcurrentHashMap<String, IRProgram> cache = new ConcurrentHashMap<>();

    public IRProgram get(String path) { return cache.get(path); }
    public void put(String path, IRProgram program) { cache.put(path, program); }
    public boolean contains(String path) { return cache.containsKey(path); }
    public void clear() { cache.clear(); }
}
