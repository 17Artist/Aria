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

import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.reference.ValueReference;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.util.concurrent.ConcurrentHashMap;

public class LocalStorage {

    private final ConcurrentHashMap<VariableKey, VariableReference> varVariables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VariableKey, ValueReference> valVariables = new ConcurrentHashMap<>();

    public VariableReference getVarVariable(VariableKey key) {
        return varVariables.computeIfAbsent(key, k -> new VariableReference(NoneValue.NONE));
    }

    public ValueReference getValVariable(VariableKey key) {
        return valVariables.computeIfAbsent(key, k -> new ValueReference(NoneValue.NONE));
    }

    public VariableReference getVarVariableExisting(VariableKey key) {
        return varVariables.get(key);
    }

    public ValueReference getValVariableExisting(VariableKey key) {
        return valVariables.get(key);
    }
}
