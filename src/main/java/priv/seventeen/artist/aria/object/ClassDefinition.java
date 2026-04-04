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

import priv.seventeen.artist.aria.annotation.AriaAnnotation;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClassDefinition implements IAriaObject {

    private final String name;
    private ClassDefinition parent;  // nullable，继承

    // 字段元数据：name → mutable
    private final Map<String, Boolean> fieldMeta = new LinkedHashMap<>();

    // 字段初始化子程序（执行时 self 已设置，会 SET_PROP 各字段默认值）
    private IRProgram fieldInitProgram;

    // 方法：name → IRProgram
    private final Map<String, IRProgram> methods = new LinkedHashMap<>();

    // 构造器
    private IRProgram constructorProgram;  // nullable

    private List<AriaAnnotation> classAnnotations = Collections.emptyList();
    private final Map<String, List<AriaAnnotation>> methodAnnotations = new LinkedHashMap<>();
    private final Map<String, List<AriaAnnotation>> fieldAnnotations = new LinkedHashMap<>();

    public ClassDefinition(String name) {
        this.name = name;
    }

    public void setParent(ClassDefinition parent) { this.parent = parent; }
    public void addField(String name, boolean mutable) { fieldMeta.put(name, mutable); }
    public void setFieldInitProgram(IRProgram program) { this.fieldInitProgram = program; }
    public void addMethod(String name, IRProgram body) { methods.put(name, body); }
    public void setConstructorProgram(IRProgram program) { this.constructorProgram = program; }

    public void setClassAnnotations(List<AriaAnnotation> annotations) { this.classAnnotations = annotations; }
    public void setMethodAnnotations(String methodName, List<AriaAnnotation> annotations) {
        if (annotations != null && !annotations.isEmpty()) methodAnnotations.put(methodName, annotations);
    }
    public void setFieldAnnotations(String fieldName, List<AriaAnnotation> annotations) {
        if (annotations != null && !annotations.isEmpty()) fieldAnnotations.put(fieldName, annotations);
    }

    public String getName() { return name; }
    public ClassDefinition getParent() { return parent; }
    public Map<String, Boolean> getFieldMeta() { return fieldMeta; }
    public IRProgram getFieldInitProgram() { return fieldInitProgram; }
    public Map<String, IRProgram> getMethods() { return methods; }
    public IRProgram getConstructorProgram() { return constructorProgram; }

    public List<AriaAnnotation> getClassAnnotations() { return classAnnotations; }

    public List<AriaAnnotation> getMethodAnnotations(String methodName) {
        return methodAnnotations.getOrDefault(methodName, Collections.emptyList());
    }

    public List<AriaAnnotation> getFieldAnnotations(String fieldName) {
        return fieldAnnotations.getOrDefault(fieldName, Collections.emptyList());
    }

    public boolean hasAnnotation(String name) {
        return classAnnotations.stream().anyMatch(a -> a.name().equals(name));
    }

    public AriaAnnotation getAnnotation(String name) {
        return classAnnotations.stream().filter(a -> a.name().equals(name)).findFirst().orElse(null);
    }

    public IRProgram findMethod(String methodName) {
        IRProgram m = methods.get(methodName);
        if (m != null) return m;
        if (parent != null) return parent.findMethod(methodName);
        return null;
    }

    public Map<String, Boolean> collectAllFieldMeta() {
        Map<String, Boolean> all = new LinkedHashMap<>();
        if (parent != null) {
            all.putAll(parent.collectAllFieldMeta());
        }
        all.putAll(fieldMeta);
        return all;
    }

    @Override
    public String getTypeName() { return "ClassDef:" + name; }

    @Override
    public String stringValue() { return "class " + name; }
}
