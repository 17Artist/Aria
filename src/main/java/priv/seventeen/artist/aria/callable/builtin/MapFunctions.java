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
import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A5(内建函数矩阵)：逐函数对照 Shimmer MapObjectFunction 对齐(名字/参数个数/返回/边界)。
 * 约定：d.get(0)=self(MapValue,前插)，用户参数从 d.get(1) 起；
 * Shimmer 的 data.size()==N 对应这里 d.argCount()==N+1。
 * 键相等语义说明：Shimmer 的 map 键是 IValue(equals→eq + 各自 jvm hash)，HashMap 查找先比 hash——
 * 跨类型 eq 相等的键(1 与 "1")hash 不同查不到；Aria 键 equals 是同型比值 + 同源 hash，
 * 对基础类型键的观测行为与 Shimmer 的「hash 门 + eq」一致，无需改 MapValue。
 */
public class MapFunctions {

    /** Shimmer 对齐：handler 内 Java 异常 → 脚本级运行时错误(与 List/StringFunctions 同模式)。 */
    private static void reg(CallableManager manager, String name, ICallable body) {
        manager.registerObjectFunction(MapValue.class, name, d -> {
            try {
                return body.invoke(d);
            } catch (AriaException e) {
                throw e;
            } catch (Exception e) {
                throw new AriaRuntimeException("对象函数调用失败: map." + name + " — " + e.getMessage());
            }
        });
    }

    public static void register(CallableManager manager) {
        // Shimmer 对齐：put(k,v) 返回被替换的旧值(无则 none)；其它元数 none。
        reg(manager, "put", d -> {
            if (d.argCount() != 3) return NoneValue.NONE;
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            IValue<?> old = map.put(d.get(1), d.get(2));
            return old != null ? old : NoneValue.NONE;
        });
        reg(manager, "get", d -> {
            if (d.argCount() != 2) return NoneValue.NONE;
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            IValue<?> val = map.get(d.get(1));
            return val != null ? val : NoneValue.NONE;
        });
        reg(manager, "remove", d -> {
            if (d.argCount() != 2) return NoneValue.NONE;
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            IValue<?> removed = map.remove(d.get(1));
            return removed != null ? removed : NoneValue.NONE;
        });
        reg(manager, "size", d ->
                new NumberValue(((MapValue) d.get(0)).jvmValue().size()));
        reg(manager, "keys", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            return new ListValue(new ArrayList<>(map.keySet()));
        });
        reg(manager, "values", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            return new ListValue(new ArrayList<>(map.values()));
        });
        reg(manager, "containsKey", d -> {
            if (d.argCount() != 2) return BooleanValue.FALSE;
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            return BooleanValue.of(map.containsKey(d.get(1)));
        });
        reg(manager, "clear", d -> {
            ((MapValue) d.get(0)).jvmValue().clear();
            return NoneValue.NONE;
        });
        reg(manager, "isEmpty", d ->
                BooleanValue.of(((MapValue) d.get(0)).jvmValue().isEmpty()));
        // Shimmer 对齐(builtins-object-10)：putAll 鸭子类型——接受任何 jvmValue() instanceof Map 的值
        // (含遗留 SmallMapValue)，不强转 MapValue；非 map 参数静默 no-op。
        reg(manager, "putAll", d -> {
            if (d.argCount() == 2 && d.get(1).jvmValue() instanceof Map<?, ?> other) {
                Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
                @SuppressWarnings("unchecked")
                Map<IValue<?>, IValue<?>> typed = (Map<IValue<?>, IValue<?>>) other;
                map.putAll(typed);
            }
            return NoneValue.NONE;
        });
        // Shimmer 对齐(bug-for-bug)：putIfAbsent 声明返回 Object → CachedCallable 按 StoreOnlyValue 包装
        // ——键已存在时返回 StoreOnlyValue(旧值)而非旧值本身；键不存在(null)→none；元数不符 → StoreOnlyValue(0)。
        reg(manager, "putIfAbsent", d -> {
            if (d.argCount() != 3) return new StoreOnlyValue<>(0);
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            IValue<?> existing = map.putIfAbsent(d.get(1), d.get(2));
            return existing != null ? new StoreOnlyValue<>(existing) : NoneValue.NONE;
        });
        reg(manager, "getOrDefault", d -> {
            if (d.argCount() != 3) return NoneValue.NONE;
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            IValue<?> val = map.get(d.get(1));
            return val != null ? val : d.get(2);
        });

        // ---- 以下为 Aria 自由区扩展(Shimmer 无对应方法) ----
        reg(manager, "containsValue", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            return BooleanValue.of(map.containsValue(d.get(1)));
        });


        // map.forEach(fn) → fn(key, value) 对每个键值对调用
        reg(manager, "forEach", d -> {
            Map<IValue<?>, IValue<?>> map = ((MapValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            for (Map.Entry<IValue<?>, IValue<?>> entry : map.entrySet()) {
                fn.getCallable().invoke(new InvocationData(null, null,
                    new IValue<?>[]{ entry.getKey(), entry.getValue() }));
            }
            return NoneValue.NONE;
        });

        // map.filter(fn) → 保留 fn(key, value) 返回 true 的键值对
        reg(manager, "filter", d -> {
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
        reg(manager, "mapValues", d -> {
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
        reg(manager, "entries", d -> {
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
