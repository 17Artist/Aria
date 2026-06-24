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

package priv.seventeen.artist.aria.callable.builtin;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.value.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MapFunctions {

    public static void register(CallableManager manager) {
        manager.registerObjectFunction(MapValue.class, "put", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            map.put(d.get(1), d.get(2));
            return NoneValue.NONE;
        });
        manager.registerObjectFunction(MapValue.class, "get", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            IValue<?> val = map.get(d.get(1));
            return val != null ? val : NoneValue.NONE;
        });
        manager.registerObjectFunction(MapValue.class, "remove", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            IValue<?> removed = map.remove(d.get(1));
            return removed != null ? removed : NoneValue.NONE;
        });
        manager.registerObjectFunction(MapValue.class, "size", d ->
                new NumberValue(((MapValue) d.get(0)).jvmValue().size()));
        manager.registerObjectFunction(MapValue.class, "keys", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            return new ListValue(new ArrayList<>(map.keySet()));
        });
        manager.registerObjectFunction(MapValue.class, "values", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            return new ListValue(new ArrayList<>(map.values()));
        });
        manager.registerObjectFunction(MapValue.class, "containsKey", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            return BooleanValue.of(map.containsKey(d.get(1)));
        });
        manager.registerObjectFunction(MapValue.class, "clear", d -> {
            ((MapValue) d.get(0)).jvmValue().clear();
            return NoneValue.NONE;
        });
        manager.registerObjectFunction(MapValue.class, "isEmpty", d ->
                BooleanValue.of(((MapValue) d.get(0)).jvmValue().isEmpty()));
        manager.registerObjectFunction(MapValue.class, "putAll", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            Map<IValue<?>, IValue<?>> other = ((MapValue) d.get(1)).jvmValue();
            map.putAll(other);
            return NoneValue.NONE;
        });
        manager.registerObjectFunction(MapValue.class, "putIfAbsent", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            IValue<?> existing = map.putIfAbsent(d.get(1), d.get(2));
            return existing != null ? existing : NoneValue.NONE;
        });
        manager.registerObjectFunction(MapValue.class, "getOrDefault", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            IValue<?> val = map.get(d.get(1));
            return val != null ? val : d.get(2);
        });
        manager.registerObjectFunction(MapValue.class, "containsValue", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            return BooleanValue.of(map.containsValue(d.get(1)));
        });


        // map.forEach(fn) → fn(key, value) 对每个键值对调用
        manager.registerObjectFunction(MapValue.class, "forEach", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            for (Map.Entry<IValue<?>, IValue<?>> entry : map.entrySet()) {
                fn.getCallable().invoke(new InvocationData(null, null,
                    new IValue<?>[]{ entry.getKey(), entry.getValue() }));
            }
            return NoneValue.NONE;
        });

        // map.filter(fn) → 保留 fn(key, value) 返回 true 的键值对
        manager.registerObjectFunction(MapValue.class, "filter", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            LinkedHashMap<IValue<?>, IValue<?>> result = new LinkedHashMap<>();
            for (Map.Entry<IValue<?>, IValue<?>> entry : map.entrySet()) {
                IValue<?> keep = fn.getCallable().invoke(new InvocationData(null, null,
                    new IValue<?>[]{ entry.getKey(), entry.getValue() }));
                if (keep.booleanValue()) result.put(entry.getKey(), entry.getValue());
            }
            return new MapValue(result);
        });

        // map.mapValues(fn) → 对每个 value 调用 fn，返回新 map
        manager.registerObjectFunction(MapValue.class, "mapValues", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            LinkedHashMap<IValue<?>, IValue<?>> result = new LinkedHashMap<>();
            for (Map.Entry<IValue<?>, IValue<?>> entry : map.entrySet()) {
                IValue<?> newVal = fn.getCallable().invoke(new InvocationData(null, null,
                    new IValue<?>[]{ entry.getValue(), entry.getKey() }));
                result.put(entry.getKey(), newVal);
            }
            return new MapValue(result);
        });

        // map.entries() → 返回 [[key, value], ...] 列表
        manager.registerObjectFunction(MapValue.class, "entries", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            List<IValue<?>> result = new ArrayList<>();
            for (Map.Entry<IValue<?>, IValue<?>> entry : map.entrySet()) {
                List<IValue<?>> pair = new ArrayList<>(2);
                pair.add(entry.getKey());
                pair.add(entry.getValue());
                result.add(new ListValue(pair));
            }
            return new ListValue(result);
        });
    }
}
