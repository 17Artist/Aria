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

package priv.seventeen.artist.aria.value;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小型 map 优化：固定容量数组 + 线性扫描，适用于属性 ≤ 8 的对象字面量。
 * key 强制为 String（常见 JS 对象字面量场景）。读路径无 hash 计算无装箱，
 * O(N) 但 N 小常数极小，比 HashMap 快数倍。
 *
 * put 在容量内直接原地修改；超容量返回 promote 后的 MapValue，调用方需替换。
 */
public final class SmallMapValue extends IValue<java.util.Map<IValue<?>, IValue<?>>> {

    private final String[] keys;
    private final IValue<?>[] values;
    private int size;

    public SmallMapValue(String[] keys, IValue<?>[] values, int size) {
        this.keys = keys;
        this.values = values;
        this.size = size;
    }


    public IValue<?> get(String key) {
        for (int i = 0; i < size; i++) {
            if (key.equals(keys[i])) return values[i];
        }
        return NoneValue.NONE;
    }


    public IValue<?> get(IValue<?> key) {
        return get(key.stringValue());
    }

    /**
     * 写入 key=value。
     * - 命中现有 key：原地更新，返回 this
     * - 容量内新增：原地添加，返回 this
     * - 容量外：升级为 MapValue 返回（调用方需用此结果替换原引用）
     */
    public IValue<?> put(String key, IValue<?> val) {
        for (int i = 0; i < size; i++) {
            if (key.equals(keys[i])) { values[i] = val; return this; }
        }
        if (size < keys.length) {
            keys[size] = key;
            values[size] = val;
            size++;
            return this;
        }
        // 容量耗尽：升级到 MapValue
        Map<IValue<?>, IValue<?>> map = new LinkedHashMap<>(size * 2 + 2);
        for (int i = 0; i < size; i++) map.put(new StringValue(keys[i], true), values[i]);
        map.put(new StringValue(key, true), val);
        return new MapValue(map);
    }

    public int size() { return size; }

    @Override
    public Map<IValue<?>, IValue<?>> jvmValue() {
        Map<IValue<?>, IValue<?>> map = new LinkedHashMap<>(size * 2);
        for (int i = 0; i < size; i++) map.put(new StringValue(keys[i], true), values[i]);
        return map;
    }

    @Override public double numberValue() { return size; }
    @Override public String stringValue() { return "map(" + size + ")"; }
    @Override public boolean booleanValue() { return size > 0; }
    @Override public int typeID() { return 5; }
    @Override public boolean canMath() { return false; }
    @Override public boolean isBaseType() { return false; }

    @Override
    protected IValue<?> addValue(IValue<?> other) {
        if (other instanceof SmallMapValue sm) {
            java.util.Map<IValue<?>, IValue<?>> merged = jvmValue();
            for (int i = 0; i < sm.size; i++) merged.put(new StringValue(sm.keys[i], true), sm.values[i]);
            return new MapValue(merged);
        }
        if (other instanceof MapValue mv) {
            java.util.Map<IValue<?>, IValue<?>> merged = jvmValue();
            merged.putAll(mv.jvmValue());
            return new MapValue(merged);
        }
        return NoneValue.NONE;
    }

    @Override
    protected IValue<?> subValue(IValue<?> other) {
        return NoneValue.NONE;
    }

    @Override public String toString() { return stringValue(); }
}
