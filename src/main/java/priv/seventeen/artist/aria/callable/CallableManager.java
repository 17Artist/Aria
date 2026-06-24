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

package priv.seventeen.artist.aria.callable;

import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler;
import priv.seventeen.artist.aria.annotation.java.AriaNamespace;
import priv.seventeen.artist.aria.annotation.java.AriaObjectConstructor;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.value.*;
import priv.seventeen.artist.aria.value.MutableStringValue;
import priv.seventeen.artist.aria.value.RopeString;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CallableManager {
    public static final CallableManager INSTANCE = new CallableManager();

    private final Map<String, Map<String, ICallable>> staticFunctions = new ConcurrentHashMap<>();
    private final Map<String, IObjectConstructor<?>> constructors = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<String, ICallable>> objectFunctions = new ConcurrentHashMap<>();
    private final Map<String, ICallable> objectFunctionCache = new ConcurrentHashMap<>();

    private CallableManager() {}


    public void registerStaticFunction(String namespace, String name, ICallable callable) {
        staticFunctions.computeIfAbsent(namespace, k -> new ConcurrentHashMap<>()).put(name, callable);
    }

    public void aliasNamespace(String existing, String alias) {
        Map<String, ICallable> ns = staticFunctions.get(existing);
        if (ns != null) staticFunctions.put(alias, ns);
    }

    public void registerStaticFunction(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(AriaNamespace.class)) return;
        String[] namespaces = clazz.getAnnotation(AriaNamespace.class).value();
        if (namespaces.length == 0) return;
        String primary = namespaces[0];
        for (Method method : clazz.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) continue;
            AriaInvokeHandler ann = method.getAnnotation(AriaInvokeHandler.class);
            if (ann == null) continue;
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != InvocationData.class) continue;
            method.setAccessible(true);
            MethodHandle handle;
            try {
                handle = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup()).unreflect(method);
            } catch (IllegalAccessException e) {
                continue;
            }
            handle = handle.asType(handle.type().changeReturnType(Object.class));
            String functionName = ann.value();
            MethodHandle finalHandle = handle;
            staticFunctions.computeIfAbsent(primary, k -> new ConcurrentHashMap<>())
                .put(functionName, data -> {
                    try {
                        Object result = finalHandle.invokeExact(data);
                        if (result instanceof IValue<?> iv) return iv;
                        if (result instanceof Double d) return new NumberValue(d);
                        if (result instanceof Number n) return new NumberValue(n.doubleValue());
                        if (result instanceof String s) return new StringValue(s);
                        if (result instanceof Boolean b) return BooleanValue.of(b);
                        if (result == null) return NoneValue.NONE;
                        return NoneValue.NONE;
                    } catch (AriaRuntimeException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new AriaRuntimeException("Static function call failed: " + functionName + " — " + e.getMessage());
                    }
                });
        }
        for (int i = 1; i < namespaces.length; i++) {
            aliasNamespace(primary, namespaces[i]);
        }
    }

    public void registerConstructor(String name, IObjectConstructor<?> constructor) {
        constructors.put(name, constructor);
    }

    public void registerObjectFunction(Class<?> clazz, String name, ICallable callable) {
        objectFunctions.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>()).put(name, callable);
    }

    public void registerObjectFunction(Class<?> clazz) {
        for (Method method : getAllMethods(clazz)) {
            if (!Modifier.isStatic(method.getModifiers())) continue;
            AriaInvokeHandler ann = method.getAnnotation(AriaInvokeHandler.class);
            if (ann == null) continue;
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != InvocationData.class) continue;
            Class<?> targetClass = ann.target();
            if (targetClass == Void.class) continue;
            method.setAccessible(true);
            String functionName = ann.value();
            Method finalMethod = method;
            objectFunctions.computeIfAbsent(targetClass, k -> new ConcurrentHashMap<>())
                .put(functionName, data -> {
                    try {
                        Object result = finalMethod.invoke(null, data);
                        if (result instanceof IValue<?> iv) return iv;
                        if (result instanceof Double d) return new NumberValue(d);
                        if (result instanceof Number n) return new NumberValue(n.doubleValue());
                        if (result instanceof String s) return new StringValue(s);
                        if (result instanceof Boolean b) return BooleanValue.of(b);
                        if (result == null) return NoneValue.NONE;
                        return NoneValue.NONE;
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof AriaRuntimeException are) throw are;
                        throw new AriaRuntimeException("Object function call failed: " + functionName + " — " + cause.getMessage());
                    } catch (Exception e) {
                        throw new AriaRuntimeException("Object function call failed: " + functionName + " — " + e.getMessage());
                    }
                });
        }
    }

    private static List<Method> getAllMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        while (clazz != null) {
            methods.addAll(Arrays.asList(clazz.getDeclaredMethods()));
            clazz = clazz.getSuperclass();
        }
        return methods;
    }

        @SuppressWarnings("unchecked")
    public void registerObject(Class<? extends IAriaObject> clazz) {
        // 扫描构造器
        for (Constructor<?> ctor : clazz.getConstructors()) {
            AriaObjectConstructor ann = ctor.getAnnotation(AriaObjectConstructor.class);
            if (ann != null) {
                String name = ann.value();
                Constructor<?> finalCtor = ctor;
                registerConstructor(name, data -> {
                    try {
                        return (IAriaObject) finalCtor.newInstance(data);
                    } catch (Exception e) {
                        throw new AriaRuntimeException("Failed to construct " + name + ": " + e.getMessage());
                    }
                });
            }
        }

        // 扫描实例方法
        for (Method method : clazz.getMethods()) {
            AriaInvokeHandler ann = method.getAnnotation(AriaInvokeHandler.class);
            if (ann != null) {
                String handlerName = ann.value();
                registerObjectFunction(clazz, handlerName, data -> {
                    try {
                        Object target = null;
                        // data.get(0) 是 self（ObjectValue 包装的对象）
                        IValue<?> selfVal = data.get(0);
                        if (selfVal instanceof ObjectValue<?> ov) {
                            target = ov.jvmValue();
                        }
                        if (target == null) return NoneValue.NONE;

                        Object result = method.invoke(target, data);
                        if (result instanceof IValue<?> iv) return iv;
                        if (result instanceof Double d) return new NumberValue(d);
                        if (result instanceof Number n) return new NumberValue(n.doubleValue());
                        if (result instanceof String s) return new StringValue(s);
                        if (result instanceof Boolean b) return BooleanValue.of(b);
                        if (result == null) return NoneValue.NONE;
                        return NoneValue.NONE;
                    } catch (Exception e) {
                        throw new AriaRuntimeException("Failed to invoke " + handlerName + ": " + e.getMessage());
                    }
                });
            }
        }
    }


    public boolean hasStaticNamespace(String namespace) {
        return staticFunctions.containsKey(namespace);
    }

    public ICallable getStaticFunction(String namespace, String name) {
        Map<String, ICallable> ns = staticFunctions.get(namespace);
        return ns != null ? ns.get(name) : null;
    }

    public IObjectConstructor<?> getConstructor(String name) {
        return constructors.get(name);
    }

    public java.util.Set<String> getNamespaces() {
        return staticFunctions.keySet();
    }

    public java.util.Set<String> getFunctionNames(String namespace) {
        Map<String, ICallable> ns = staticFunctions.get(namespace);
        return ns != null ? ns.keySet() : java.util.Collections.emptySet();
    }

    public java.util.Set<String> getConstructorNames() {
        return constructors.keySet();
    }

    public ICallable getObjectFunction(Class<?> clazz, String name) {
        // MutableStringValue / RopeString → 复用 StringValue 的方法
        if (clazz == MutableStringValue.class
                || clazz == RopeString.class) {
            return getObjectFunction(StringValue.class, name);
        }
        // 先查直接注册
        String cacheKey = clazz.getName() + "#" + name;
        ICallable cached = objectFunctionCache.get(cacheKey);
        if (cached != null) return cached;

        // 查当前类
        Map<String, ICallable> funcs = objectFunctions.get(clazz);
        if (funcs != null) {
            ICallable callable = funcs.get(name);
            if (callable != null) {
                objectFunctionCache.put(cacheKey, callable);
                return callable;
            }
        }

        // 查父类和接口
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            ICallable result = getObjectFunction(superClass, name);
            if (result != null) {
                objectFunctionCache.put(cacheKey, result);
                return result;
            }
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            ICallable result = getObjectFunction(iface, name);
            if (result != null) {
                objectFunctionCache.put(cacheKey, result);
                return result;
            }
        }

        return null;
    }

    public void clear() {
        staticFunctions.clear();
        constructors.clear();
        objectFunctions.clear();
        objectFunctionCache.clear();
    }
}
