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
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

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

    // 是否定义了任何 __get_xxx / __set_xxx 访问器（含父类继承）— GET_PROP/SET_PROP 据此短路
    private boolean hasAnyAccessor;

    // 静态字段：name → mutable
    private final Map<String, Boolean> staticFieldMeta = new LinkedHashMap<>();
    // 静态字段值：name → IValue
    private final Map<String, IValue<?>> staticFields = new LinkedHashMap<>();
    // 静态方法：name → IRProgram
    private final Map<String, IRProgram> staticMethods = new LinkedHashMap<>();
    // 静态字段初始化子程序（self = ObjectValue<ClassDefinition>，运行一次）
    private IRProgram staticInitProgram;

    private List<AriaAnnotation> classAnnotations = Collections.emptyList();
    private final Map<String, List<AriaAnnotation>> methodAnnotations = new LinkedHashMap<>();
    private final Map<String, List<AriaAnnotation>> fieldAnnotations = new LinkedHashMap<>();

    public ClassDefinition(String name) {
        this.name = name;
    }

    public void setParent(ClassDefinition parent) { this.parent = parent; }
    public void addField(String name, boolean mutable) { fieldMeta.put(name, mutable); }
    public void setFieldInitProgram(IRProgram program) { this.fieldInitProgram = program; }
    public void addMethod(String name, IRProgram body) {
        methods.put(name, body);
        if (name.startsWith("__get_") || name.startsWith("__set_")) {
            hasAnyAccessor = true;
        }
    }
    public void setConstructorProgram(IRProgram program) { this.constructorProgram = program; }

    /** true iff 本类或任意父类定义了 __get_xxx / __set_xxx。GET_PROP / SET_PROP 据此短路 getter 查找。 */
    public boolean hasAnyAccessor() {
        if (hasAnyAccessor) return true;
        return parent != null && parent.hasAnyAccessor();
    }

    public void addStaticField(String name, boolean mutable) {
        staticFieldMeta.put(name, mutable);
        staticFields.put(name, NoneValue.NONE);
    }
    public void addStaticMethod(String name, IRProgram body) { staticMethods.put(name, body); }
    public void setStaticInitProgram(IRProgram program) { this.staticInitProgram = program; }
    public IRProgram getStaticInitProgram() { return staticInitProgram; }
    public Map<String, Boolean> getStaticFieldMeta() { return staticFieldMeta; }
    public Map<String, IValue<?>> getStaticFieldsRaw() { return staticFields; }
    public Map<String, IRProgram> getStaticMethods() { return staticMethods; }

    /** 沿父类链查找静态字段是否存在（包括 mutable 信息）。 */
    public boolean hasStaticField(String name) {
        if (staticFieldMeta.containsKey(name)) return true;
        return parent != null && parent.hasStaticField(name);
    }

    public IValue<?> getStaticField(String name) {
        if (staticFieldMeta.containsKey(name)) {
            IValue<?> v = staticFields.get(name);
            return v != null ? v : NoneValue.NONE;
        }
        if (parent != null) return parent.getStaticField(name);
        return NoneValue.NONE;
    }

    /** 写入静态字段；若本类未定义则向父类查找。返回 true 表示成功写入。 */
    public boolean setStaticField(String name, IValue<?> value) {
        if (staticFieldMeta.containsKey(name)) {
            staticFields.put(name, value);
            return true;
        }
        if (parent != null) return parent.setStaticField(name, value);
        return false;
    }

    public IRProgram findStaticMethod(String methodName) {
        IRProgram m = staticMethods.get(methodName);
        if (m != null) return m;
        if (parent != null) return parent.findStaticMethod(methodName);
        return null;
    }

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
