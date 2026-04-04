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

import java.util.ArrayList;
import java.util.List;

public final class ListValue extends IValue<List<IValue<?>>> {

    private final List<IValue<?>> value;

    public ListValue() {
        this.value = new ArrayList<>();
    }

    public ListValue(List<IValue<?>> value) {
        this.value = value;
    }

    @Override public List<IValue<?>> jvmValue() { return value; }
    @Override public double numberValue() { return value.size(); }
    @Override public String stringValue() { return value.toString(); }
    @Override public boolean booleanValue() { return !value.isEmpty(); }
    @Override public int typeID() { return 11; }
    @Override public boolean canMath() { return false; }
    @Override public boolean isBaseType() { return false; }

    @Override
    protected IValue<?> addValue(IValue<?> other) {
        List<IValue<?>> newList = new ArrayList<>(this.value);
        if (other instanceof ListValue lv) {
            newList.addAll(lv.jvmValue());
        } else {
            newList.add(other);
        }
        return new ListValue(newList);
    }

    @Override
    protected IValue<?> subValue(IValue<?> other) {
        List<IValue<?>> newList = new ArrayList<>(this.value);
        if (other instanceof ListValue lv) {
            newList.removeAll(lv.jvmValue());
        } else if (other instanceof NumberValue nv) {
            int idx = (int) nv.numberValue();
            if (idx >= 0 && idx < newList.size()) {
                newList.remove(idx);
            }
        }
        return new ListValue(newList);
    }

    @Override
    public String toString() { return stringValue(); }
}
