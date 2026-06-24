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
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.value.*;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class JavaClassMirror implements IAriaObject {
    private final Class<?> javaClass;
    private final Map<String, Variable> memberCache = new ConcurrentHashMap<>();

    public JavaClassMirror(Class<?> javaClass) {
        this.javaClass = javaClass;
    }

    public Class<?> getJavaClass() { return javaClass; }

    @Override
    public String getTypeName() { return "JavaClass:" + javaClass.getSimpleName(); }

    @Override
    public String stringValue() { return javaClass.getName(); }

    @Override
    public Variable getVariable(String name) {
        return memberCache.computeIfAbsent(name, this::resolveMember);
    }

    private Variable resolveMember(String name) {
        try {
            Field field = javaClass.getField(name);
            if (Modifier.isStatic(field.getModifiers())) {
                Object value = field.get(null);
                IValue<?> wrapped = NativeCallable.wrapObject(value);
                return new Variable.Normal(new VariableReference(wrapped));
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}

        Method[] methods = Arrays.stream(javaClass.getMethods())
            .filter(m -> m.getName().equals(name) && Modifier.isStatic(m.getModifiers()))
            .toArray(Method[]::new);
        if (methods.length > 0) {
            ICallable callable = createMethodCallable(null, methods);
            return new Variable.Normal(new VariableReference(
                new StoreOnlyValue<>(new CallableWithInvoker(callable, null))
            ));
        }

        for (Class<?> inner : javaClass.getDeclaredClasses()) {
            if (inner.getSimpleName().equals(name)) {
                return new Variable.Normal(new VariableReference(
                    new ObjectValue<>(new JavaClassMirror(inner))
                ));
            }
        }

        return Variable.Normal.NONE;
    }

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Map<Method, MethodHandle> METHOD_HANDLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Constructor<?>, MethodHandle> CTOR_HANDLE_CACHE = new ConcurrentHashMap<>();

    private static MethodHandle getHandle(Method m) {
        return METHOD_HANDLE_CACHE.computeIfAbsent(m, method -> {
            try {
                method.setAccessible(true);
                return LOOKUP.unreflect(method);
            } catch (IllegalAccessException e) {
                return null;
            }
        });
    }

    private static MethodHandle getHandle(Constructor<?> c) {
        return CTOR_HANDLE_CACHE.computeIfAbsent(c, ctor -> {
            try {
                ctor.setAccessible(true);
                return LOOKUP.unreflectConstructor(ctor);
            } catch (IllegalAccessException e) {
                return null;
            }
        });
    }

        public IValue<?> newInstance(IValue<?>[] args) throws AriaException {
        Constructor<?>[] constructors = javaClass.getConstructors();
        Constructor<?> best = findBestConstructor(constructors, args);
        if (best == null) {
            throw new AriaRuntimeException("No matching constructor for " + javaClass.getName() + " with " + args.length + " args");
        }
        try {
            Object[] javaArgs = convertArgs(best.getParameterTypes(), args);
            MethodHandle handle = getHandle(best);
            Object instance;
            if (handle != null) {
                instance = handle.invokeWithArguments(javaArgs);
            } else {
                instance = best.newInstance(javaArgs);
            }
            return new ObjectValue<>(new JavaObjectMirror(instance));
        } catch (AriaRuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new AriaRuntimeException("Failed to create " + javaClass.getName() + ": " + e.getMessage());
        }
    }


    static ICallable createMethodCallable(Object target, Method[] methods) {
        if (methods.length == 1) {
            Method m = methods[0];
            MethodHandle handle = getHandle(m);
            if (handle != null) {
                final MethodHandle mh = target != null ? handle.bindTo(target) : handle;
                return data -> {
                    try {
                        Object[] javaArgs = convertArgs(m.getParameterTypes(), data.getArgs());
                        Object result;
                        if (target != null) {
                            result = mh.invokeWithArguments(javaArgs);
                        } else {
                            result = mh.invokeWithArguments(javaArgs);
                        }
                        return NativeCallable.wrapObject(result);
                    } catch (AriaRuntimeException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new AriaRuntimeException("Java method error: " + e.getMessage());
                    }
                };
            }
        }

        return data -> {
            IValue<?>[] args = data.getArgs();
            Method best = findBestMethod(methods, args);
            if (best == null) {
                throw new AriaRuntimeException("No matching method overload with " + args.length + " args");
            }
            try {
                Object[] javaArgs = convertArgs(best.getParameterTypes(), args);
                MethodHandle handle = getHandle(best);
                Object result;
                if (handle != null) {
                    if (target != null) {
                        Object[] fullArgs = new Object[javaArgs.length + 1];
                        fullArgs[0] = target;
                        System.arraycopy(javaArgs, 0, fullArgs, 1, javaArgs.length);
                        result = handle.invokeWithArguments(fullArgs);
                    } else {
                        result = handle.invokeWithArguments(javaArgs);
                    }
                } else {
                    result = best.invoke(target, javaArgs);
                }
                return NativeCallable.wrapObject(result);
            } catch (AriaRuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw new AriaRuntimeException("Java method error: " + e.getMessage());
            }
        };
    }


    static Method findBestMethod(Method[] methods, IValue<?>[] args) {
        Method bestExact = null;
        int bestScore = Integer.MAX_VALUE;
        for (Method m : methods) {
            if (m.getParameterCount() == args.length) {
                int score = scoreParameterMatch(m.getParameterTypes(), args);
                if (score < bestScore) {
                    bestScore = score;
                    bestExact = m;
                }
            }
        }
        if (bestExact != null) return bestExact;

        for (Method m : methods) {
            if (m.isVarArgs() && args.length >= m.getParameterCount() - 1) return m;
        }
        return methods.length > 0 ? methods[0] : null;
    }

    static Constructor<?> findBestConstructor(Constructor<?>[] constructors, IValue<?>[] args) {
        Constructor<?> bestExact = null;
        int bestScore = Integer.MAX_VALUE;
        for (Constructor<?> c : constructors) {
            if (c.getParameterCount() == args.length) {
                int score = scoreParameterMatch(c.getParameterTypes(), args);
                if (score < bestScore) {
                    bestScore = score;
                    bestExact = c;
                }
            }
        }
        if (bestExact != null) return bestExact;

        for (Constructor<?> c : constructors) {
            if (c.isVarArgs() && args.length >= c.getParameterCount() - 1) return c;
        }
        return constructors.length > 0 ? constructors[0] : null;
    }

        private static int scoreParameterMatch(Class<?>[] paramTypes, IValue<?>[] args) {
        int score = 0;
        for (int i = 0; i < paramTypes.length && i < args.length; i++) {
            score += scoreTypeMatch(paramTypes[i], args[i]);
        }
        return score;
    }

    private static int scoreTypeMatch(Class<?> target, IValue<?> value) {
        if (value instanceof NoneValue) return 1;
        if (value instanceof NumberValue) {
            if (target == double.class || target == Double.class) return 0;
            if (target == int.class || target == Integer.class) return 1;
            if (target == long.class || target == Long.class) return 2;
            if (target == float.class || target == Float.class) return 2;
            if (Number.class.isAssignableFrom(target)) return 3;
            if (target == Object.class) return 5;
            return 10;
        }
        if (value instanceof StringValue) {
            if (target == String.class) return 0;
            if (target == CharSequence.class) return 1;
            if (target == Object.class) return 5;
            return 10;
        }
        if (value instanceof BooleanValue) {
            if (target == boolean.class || target == Boolean.class) return 0;
            if (target == Object.class) return 5;
            return 10;
        }
        if (value instanceof ObjectValue<?> ov) {
            Object jvm = ov.jvmValue();
            if (jvm instanceof JavaObjectMirror jom) {
                if (target.isAssignableFrom(jom.getJavaObject().getClass())) return 0;
            }
            if (jvm instanceof JavaClassMirror jcm) {
                if (target == Class.class) return 0;
            }
            if (target == Object.class) return 5;
            return 8;
        }
        return 5;
    }


    static Object[] convertArgs(Class<?>[] paramTypes, IValue<?>[] args) {
        Object[] result = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length && i < args.length; i++) {
            result[i] = convertToJava(paramTypes[i], args[i]);
        }
        return result;
    }

    static Object convertToJava(Class<?> targetType, IValue<?> value) {
        if (value instanceof NoneValue) return null;

        // 基本类型
        if (targetType == String.class || targetType == CharSequence.class) return value.stringValue();
        if (targetType == int.class || targetType == Integer.class) return (int) value.numberValue();
        if (targetType == long.class || targetType == Long.class) return (long) value.numberValue();
        if (targetType == double.class || targetType == Double.class) return value.numberValue();
        if (targetType == float.class || targetType == Float.class) return (float) value.numberValue();
        if (targetType == boolean.class || targetType == Boolean.class) return value.booleanValue();
        if (targetType == byte.class || targetType == Byte.class) return (byte) value.numberValue();
        if (targetType == short.class || targetType == Short.class) return (short) value.numberValue();
        if (targetType == char.class || targetType == Character.class) {
            String s = value.stringValue();
            return s.isEmpty() ? '\0' : s.charAt(0);
        }

        if (value instanceof ObjectValue<?> ov) {
            Object jvm = ov.jvmValue();
            if (jvm instanceof JavaObjectMirror jom) return jom.getJavaObject();
            if (jvm instanceof JavaClassMirror jcm) return jcm.getJavaClass();
            if (jvm instanceof JavaArrayMirror jam) return jam.getArray();
            return jvm;
        }

        if (value instanceof ListValue lv) return lv.jvmValue();
        if (value instanceof MapValue mv) return mv.jvmValue();
        if (value instanceof StoreOnlyValue<?> sv) return sv.jvmValue();

        return value.jvmValue();
    }
}
