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

public final class SmallMapValue extends IValue<java.util.Map<IValue<?>, IValue<?>>> {

    private final String[] keys;
    private final IValue<?>[] values;
    private final int size;

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
        String k = key.stringValue();
        return get(k);
    }

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
