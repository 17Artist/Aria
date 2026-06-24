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

package priv.seventeen.artist.aria.object;

import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.Variable;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 模块命名空间对象。
 */
public final class ModuleObject implements IAriaObject {

    private final String name;
    private final Map<String, IValue<?>> exports;

    public ModuleObject(String name, Map<String, IValue<?>> exports) {
        this.name = name;
        // 拷贝并保持插入顺序，避免外部改动；只读对外暴露
        this.exports = Collections.unmodifiableMap(new LinkedHashMap<>(exports));
    }

    public String getName() { return name; }

    public Map<String, IValue<?>> getExports() { return exports; }

    public Set<String> getExportNames() { return exports.keySet(); }

    public boolean has(String key) { return exports.containsKey(key); }

    @Override public String getTypeName() { return "Module"; }

    @Override public String stringValue() {
        return "Module(" + name + ") {" + String.join(", ", exports.keySet()) + "}";
    }

    @Override public boolean booleanValue() { return true; }

    /** dot 访问：mod.fn —— 由 GET_PROP 调用。 */
    @Override public Variable getVariable(String key) {
        IValue<?> v = exports.get(key);
        return v != null ? new Variable.Normal(new VariableReference(v)) : Variable.Normal.NONE;
    }

    /** index 访问：mod['fn'] —— 与 dot 访问语义一致。 */
    @Override public Variable getElement(String key) {
        return getVariable(key);
    }
}
