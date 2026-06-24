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

import priv.seventeen.artist.aria.value.Variable;
import priv.seventeen.artist.aria.value.reference.IReference;

import java.util.HashMap;
import java.util.Map;

public class ClassInstance implements IAriaObject {

    private final ClassDefinition classDefinition;  // nullable for legacy
    private final String typeName;
    private final Map<String, IReference> fields;

    public ClassInstance(ClassDefinition classDefinition) {
        this.classDefinition = classDefinition;
        this.typeName = classDefinition != null ? classDefinition.getName() : "Object";
        this.fields = new HashMap<>();
    }

    // 旧的兼容构造器
    public ClassInstance(String typeName) {
        this.classDefinition = null;
        this.typeName = typeName;
        this.fields = new HashMap<>();
    }

    public ClassDefinition getClassDefinition() { return classDefinition; }
    public Map<String, IReference> getFields() { return fields; }

    @Override
    public String getTypeName() { return typeName; }

    @Override
    public Variable getVariable(String name) {
        IReference ref = fields.get(name);
        if (ref != null) {
            return new Variable.Normal(ref);
        }
        return Variable.Normal.NONE;
    }

    @Override
    public Variable getElement(String name) {
        return getVariable(name);
    }

    @Override
    public String stringValue() {
        return typeName + "@" + Integer.toHexString(hashCode());
    }

    @Override
    public String toString() { return stringValue(); }
}
