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

public final class MapValue extends IValue<Map<IValue<?>, IValue<?>>> {

    private final Map<IValue<?>, IValue<?>> value;

    public MapValue() {
        this.value = new LinkedHashMap<>();
    }

    public MapValue(Map<IValue<?>, IValue<?>> value) {
        this.value = value;
    }

    @Override public Map<IValue<?>, IValue<?>> jvmValue() { return value; }
    @Override public double numberValue() { return value.size(); }
    @Override public String stringValue() { return value.toString(); }
    @Override public boolean booleanValue() { return !value.isEmpty(); }
    @Override public int typeID() { return 12; }
    @Override public boolean canMath() { return false; }
    @Override public boolean isBaseType() { return false; }

    @Override
    protected IValue<?> addValue(IValue<?> other) {
        if (other instanceof MapValue mv) {
            Map<IValue<?>, IValue<?>> newMap = new LinkedHashMap<>(this.value);
            newMap.putAll(mv.jvmValue());
            return new MapValue(newMap);
        }
        return this;
    }

    @Override
    protected IValue<?> subValue(IValue<?> other) {
        Map<IValue<?>, IValue<?>> newMap = new LinkedHashMap<>(this.value);
        if (other instanceof MapValue mv) {
            for (IValue<?> key : mv.jvmValue().keySet()) {
                newMap.remove(key);
            }
        } else if (other instanceof ListValue lv) {
            for (IValue<?> key : lv.jvmValue()) {
                newMap.remove(key);
            }
        } else {
            newMap.remove(other);
        }
        return new MapValue(newMap);
    }

    @Override
    public String toString() { return stringValue(); }
}
