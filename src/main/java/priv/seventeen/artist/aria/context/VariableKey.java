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

package priv.seventeen.artist.aria.context;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class VariableKey {

    private static final ConcurrentHashMap<String, VariableKey> CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    private final String name;
    private final int id;

    private VariableKey(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public static VariableKey of(String name) {
        return CACHE.computeIfAbsent(name, k -> new VariableKey(k, ID_GENERATOR.incrementAndGet()));
    }

    public String getName() { return name; }
    public int getId() { return id; }

    @Override public int hashCode() { return id; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariableKey vk)) return false;
        return this.id == vk.id;
    }
    @Override public String toString() { return name; }
}
