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

import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class NativeCallable implements ICallable {
    private final MethodHandle handle;
    private final Function<Object, IValue<?>> converter;

    public NativeCallable(MethodHandle handle, Class<?> returnType) {
        this.handle = handle;
        this.converter = createConverter(returnType);
    }

    @Override
    public IValue<?> invoke(InvocationData data) throws AriaException {
        try {
            Object result = handle.invoke(data);
            return converter.apply(result);
        } catch (AriaException e) {
            throw e;
        } catch (Throwable e) {
            throw new AriaRuntimeException("Native function error: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Function<Object, IValue<?>> createConverter(Class<?> returnType) {
        if (returnType == void.class || returnType == Void.class) {
            return o -> NoneValue.NONE;
        }
        if (IValue.class.isAssignableFrom(returnType)) {
            return o -> o != null ? (IValue<?>) o : NoneValue.NONE;
        }
        if (returnType == String.class) {
            return o -> o != null ? new StringValue((String) o) : NoneValue.NONE;
        }
        if (Number.class.isAssignableFrom(returnType) || returnType == double.class || returnType == int.class || returnType == long.class || returnType == float.class) {
            return o -> o != null ? new NumberValue(((Number) o).doubleValue()) : NoneValue.NONE;
        }
        if (returnType == boolean.class || returnType == Boolean.class) {
            return o -> BooleanValue.of(o != null && (Boolean) o);
        }
        if (List.class.isAssignableFrom(returnType)) {
            return o -> {
                if (o == null) return NoneValue.NONE;
                List<?> list = (List<?>) o;
                java.util.ArrayList<IValue<?>> values = new java.util.ArrayList<>();
                for (Object item : list) {
                    values.add(wrapObject(item));
                }
                return new ListValue(values);
            };
        }
        if (Map.class.isAssignableFrom(returnType)) {
            return o -> {
                if (o == null) return NoneValue.NONE;
                Map<?, ?> map = (Map<?, ?>) o;
                java.util.LinkedHashMap<IValue<?>, IValue<?>> values = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    values.put(wrapObject(entry.getKey()), wrapObject(entry.getValue()));
                }
                return new MapValue(values);
            };
        }
        return o -> o != null ? new StoreOnlyValue<>(o) : NoneValue.NONE;
    }

    public static IValue<?> wrapObject(Object o) {
        if (o == null) return NoneValue.NONE;
        if (o instanceof IValue<?> v) return v;
        if (o instanceof String s) return new StringValue(s);
        if (o instanceof Number n) return new NumberValue(n.doubleValue());
        if (o instanceof Boolean b) return BooleanValue.of(b);
        return new StoreOnlyValue<>(o);
    }
}
