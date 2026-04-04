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

package priv.seventeen.artist.aria.classystem;

import priv.seventeen.artist.aria.annotation.AriaAnnotation;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.object.ClassInstance;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.value.AriaClassValue;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.reference.ValueReference;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AriaClass {
    private final String name;
    private AriaClass parent;  // nullable
    private final Map<String, AriaField> fields;
    private final Map<String, AriaMethod> methods;
    private AriaConstructor constructor;  // nullable
    private final List<AriaAnnotation> annotations;

    public AriaClass(String name) {
        this.name = name;
        this.fields = new LinkedHashMap<>();
        this.methods = new LinkedHashMap<>();
        this.annotations = new ArrayList<>();
    }

    public void setParent(AriaClass parent) { this.parent = parent; }
    public void addField(AriaField field) { fields.put(field.getName(), field); }
    public void addMethod(AriaMethod method) { methods.put(method.getName(), method); }
    public void setConstructor(AriaConstructor constructor) { this.constructor = constructor; }
    public void addAnnotation(AriaAnnotation annotation) { annotations.add(annotation); }

    public String getName() { return name; }
    public AriaClass getParent() { return parent; }
    public Map<String, AriaField> getFields() { return fields; }
    public Map<String, AriaMethod> getMethods() { return methods; }
    public AriaConstructor getConstructor() { return constructor; }
    public List<AriaAnnotation> getAnnotations() { return annotations; }

        public AriaMethod findMethod(String methodName) {
        AriaMethod m = methods.get(methodName);
        if (m != null) return m;
        if (parent != null) return parent.findMethod(methodName);
        return null;
    }

        public Map<String, AriaField> collectAllFields() {
        Map<String, AriaField> all = new LinkedHashMap<>();
        if (parent != null) {
            all.putAll(parent.collectAllFields());
        }
        all.putAll(fields);
        return all;
    }

        public ClassInstance instantiate(Context context, IValue<?>[] args) throws AriaException {
        ClassInstance instance = new ClassInstance(name);

        // 初始化所有字段（含继承的）为默认值
        Map<String, AriaField> allFields = collectAllFields();
        for (Map.Entry<String, AriaField> entry : allFields.entrySet()) {
            AriaField field = entry.getValue();
            IValue<?> defaultVal = NoneValue.NONE;
            if (field.isMutable()) {
                instance.getFields().put(entry.getKey(), new VariableReference(defaultVal));
            } else {
                instance.getFields().put(entry.getKey(), new ValueReference(defaultVal));
            }
        }

        // 调用构造器
        if (constructor != null && constructor.getBody() != null) {
            Context callCtx = context.createCallContext(new AriaClassValue(instance), args);
            Interpreter interpreter = new Interpreter();
            interpreter.execute(constructor.getBody(), callCtx);
        }

        return instance;
    }
}
