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

package priv.seventeen.artist.aria.interop;

import priv.seventeen.artist.aria.callable.CallableWithInvoker;
import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.callable.NativeCallable;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.value.StoreOnlyValue;
import priv.seventeen.artist.aria.value.Variable;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class JavaObjectMirror implements IAriaObject {
    private final Object javaObject;
    private final Map<String, Variable> memberCache = new ConcurrentHashMap<>();

    public JavaObjectMirror(Object javaObject) {
        this.javaObject = javaObject;
    }

    public Object getJavaObject() { return javaObject; }

    @Override
    public String getTypeName() { return "Java:" + javaObject.getClass().getSimpleName(); }

    @Override
    public String stringValue() { return javaObject.toString(); }

    @Override
    public double numberValue() {
        if (javaObject instanceof Number n) return n.doubleValue();
        return 0;
    }

    @Override
    public boolean booleanValue() {
        if (javaObject instanceof Boolean b) return b;
        return javaObject != null;
    }

    @Override
    public Variable getVariable(String name) {
        return memberCache.computeIfAbsent(name, this::resolveMember);
    }

    private Variable resolveMember(String name) {
        Class<?> clazz = javaObject.getClass();

        try {
            Field field = clazz.getField(name);
            if (!Modifier.isStatic(field.getModifiers())) {
                Object value = field.get(javaObject);
                return new Variable.Normal(new VariableReference(NativeCallable.wrapObject(value)));
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}

        String capitalized = name.substring(0, 1).toUpperCase() + name.substring(1);
        try {
            Method getter = clazz.getMethod("get" + capitalized);
            if (!Modifier.isStatic(getter.getModifiers())) {
                Object value = getter.invoke(javaObject);
                return new Variable.Normal(new VariableReference(NativeCallable.wrapObject(value)));
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
        try {
            Method getter = clazz.getMethod("is" + capitalized);
            if (!Modifier.isStatic(getter.getModifiers())
                    && (getter.getReturnType() == boolean.class || getter.getReturnType() == Boolean.class)) {
                Object value = getter.invoke(javaObject);
                return new Variable.Normal(new VariableReference(NativeCallable.wrapObject(value)));
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}

        Method[] methods = Arrays.stream(clazz.getMethods())
            .filter(m -> m.getName().equals(name) && !Modifier.isStatic(m.getModifiers()))
            .toArray(Method[]::new);
        if (methods.length > 0) {
            ICallable callable = JavaClassMirror.createMethodCallable(javaObject, methods);
            return new Variable.Normal(new VariableReference(
                new StoreOnlyValue<>(new CallableWithInvoker(callable, javaObject))
            ));
        }

        return Variable.Normal.NONE;
    }

    @Override
    public Variable getElement(String name) {
        if (javaObject instanceof List<?> list) {
            try {
                int idx = Integer.parseInt(name);
                if (idx >= 0 && idx < list.size()) {
                    return new Variable.Normal(new VariableReference(NativeCallable.wrapObject(list.get(idx))));
                }
            } catch (NumberFormatException ignored) {}
        }
        if (javaObject instanceof Map<?, ?> map) {
            Object val = map.get(name);
            if (val != null) {
                return new Variable.Normal(new VariableReference(NativeCallable.wrapObject(val)));
            }
        }
        return getVariable(name);
    }
}
