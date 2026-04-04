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

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.callable.NativeCallable;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class JavaInterop {

    private static ClassFilter classFilter = ClassFilter.ALLOW_ALL;

    public static void setClassFilter(ClassFilter filter) {
        classFilter = filter != null ? filter : ClassFilter.ALLOW_ALL;
    }

    public static ClassFilter getClassFilter() {
        return classFilter;
    }

        public static void register(CallableManager manager) {
        manager.registerStaticFunction("", "use", JavaInterop::javaType);

        manager.registerStaticFunction("Java", "type", JavaInterop::javaType);
        manager.registerStaticFunction("Java", "extend", JavaInterop::javaExtend);
        manager.registerStaticFunction("Java", "from", JavaInterop::javaFrom);
        manager.registerStaticFunction("Java", "to", JavaInterop::javaTo);
        manager.registerStaticFunction("Java", "super", JavaInterop::javaSuper);
    }

        public static IValue<?> javaType(InvocationData data) throws AriaException {
        String className = data.get(0).stringValue();
        if (!classFilter.exposeToScripts(className)) {
            throw new AriaRuntimeException("Access denied to class: " + className);
        }
        try {
            if (className.endsWith("[]")) {
                String baseType = className.substring(0, className.length() - 2);
                Class<?> componentType = resolveClass(baseType);
                Class<?> arrayType = java.lang.reflect.Array.newInstance(componentType, 0).getClass();
                return new ObjectValue<>(new JavaClassMirror(arrayType));
            }
            Class<?> clazz = resolveClass(className);
            return new ObjectValue<>(new JavaClassMirror(clazz));
        } catch (ClassNotFoundException e) {
            throw new AriaRuntimeException("Class not found: " + className);
        }
    }

        public static IValue<?> javaFrom(InvocationData data) throws AriaException {
        IValue<?> arg = data.get(0);
        if (!(arg instanceof ObjectValue<?> ov)) return arg;

        Object obj = ov.jvmValue();
        Object javaObj = obj;
        if (obj instanceof JavaObjectMirror jom) {
            javaObj = jom.getJavaObject();
        } else if (obj instanceof JavaArrayMirror jam) {
            javaObj = jam.getArray();
        }

        if (javaObj instanceof List<?> list) {
            List<IValue<?>> values = new ArrayList<>(list.size());
            for (Object item : list) {
                values.add(NativeCallable.wrapObject(item));
            }
            return new ListValue(values);
        }
        if (javaObj instanceof Map<?, ?> map) {
            LinkedHashMap<IValue<?>, IValue<?>> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                values.put(NativeCallable.wrapObject(entry.getKey()), NativeCallable.wrapObject(entry.getValue()));
            }
            return new MapValue(values);
        }
        if (javaObj.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(javaObj);
            List<IValue<?>> values = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                values.add(NativeCallable.wrapObject(java.lang.reflect.Array.get(javaObj, i)));
            }
            return new ListValue(values);
        }

        return arg;
    }

        public static IValue<?> javaTo(InvocationData data) throws AriaException {
        IValue<?> value = data.get(0);

        if (value instanceof ListValue lv) {
            List<Object> javaList = new ArrayList<>();
            for (IValue<?> item : lv.jvmValue()) {
                javaList.add(JavaClassMirror.convertToJava(Object.class, item));
            }
            return new ObjectValue<>(new JavaObjectMirror(javaList));
        }
        if (value instanceof MapValue mv) {
            Map<Object, Object> javaMap = new LinkedHashMap<>();
            for (Map.Entry<IValue<?>, IValue<?>> entry : mv.jvmValue().entrySet()) {
                javaMap.put(
                    JavaClassMirror.convertToJava(Object.class, entry.getKey()),
                    JavaClassMirror.convertToJava(Object.class, entry.getValue())
                );
            }
            return new ObjectValue<>(new JavaObjectMirror(javaMap));
        }
        return value;
    }


    public static IValue<?> javaExtend(InvocationData data) throws AriaException {
        IValue<?> typeArg = data.get(0);
        IValue<?> implArg = data.get(1);

        Class<?> javaClass = null;
        if (typeArg instanceof ObjectValue<?> ov && ov.jvmValue() instanceof JavaClassMirror jcm) {
            javaClass = jcm.getJavaClass();
        }
        if (javaClass == null) {
            throw new AriaRuntimeException("Java.extend: first argument must be a Java type");
        }

        if (!javaClass.isInterface()) {
            throw new AriaRuntimeException("Java.extend: only interfaces are supported, got " + javaClass.getName());
        }

        final Class<?> targetInterface = javaClass;

        if (implArg instanceof FunctionValue fv) {
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                targetInterface.getClassLoader(),
                new Class<?>[]{ targetInterface },
                (proxyObj, method, methodArgs) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(proxyObj, methodArgs);
                    }
                    IValue<?>[] slArgs = wrapArgs(methodArgs);
                    IValue<?> result = fv.getCallable().invoke(new InvocationData(proxyContext(), null, slArgs));
                    return JavaClassMirror.convertToJava(method.getReturnType(), result);
                }
            );
            return new ObjectValue<>(new JavaObjectMirror(proxy));
        }

        if (implArg instanceof MapValue mv) {
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                targetInterface.getClassLoader(),
                new Class<?>[]{ targetInterface },
                (proxyObj, method, methodArgs) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(proxyObj, methodArgs);
                    }
                    IValue<?> handler = mv.jvmValue().get(new StringValue(method.getName()));
                    if (handler instanceof FunctionValue handlerFv) {
                        IValue<?>[] slArgs = wrapArgs(methodArgs);
                        IValue<?> result = handlerFv.getCallable().invoke(new InvocationData(proxyContext(), null, slArgs));
                        return JavaClassMirror.convertToJava(method.getReturnType(), result);
                    }
                    return defaultValue(method.getReturnType());
                }
            );
            return new ObjectValue<>(new JavaObjectMirror(proxy));
        }

        throw new AriaRuntimeException("Java.extend: second argument must be a function or map");
    }


    public static IValue<?> javaSuper(InvocationData data) throws AriaException {
        IValue<?> arg = data.get(0);
        if (arg instanceof ObjectValue<?> ov && ov.jvmValue() instanceof JavaObjectMirror) {
            return arg;
        }
        return arg;
    }

    private static IValue<?>[] wrapArgs(Object[] args) {
        if (args == null || args.length == 0) return new IValue<?>[0];
        IValue<?>[] result = new IValue<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = NativeCallable.wrapObject(args[i]);
        }
        return result;
    }

    private static Context proxyContext() {
        return new Context(new GlobalStorage());
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class || type == Void.class) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }

    private static Class<?> resolveClass(String name) throws ClassNotFoundException {
        return switch (name) {
            case "int" -> int.class;
            case "long" -> long.class;
            case "double" -> double.class;
            case "float" -> float.class;
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "char" -> char.class;
            case "void" -> void.class;
            default -> Class.forName(name);
        };
    }
}
