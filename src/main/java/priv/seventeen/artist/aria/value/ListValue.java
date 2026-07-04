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
    // Shimmer 对齐(operators-14):手工拼 "[a, b]",元素用 stringValue()(none→空串、数字→N.0),不走 Java toString。
    @Override public String stringValue() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (IValue<?> iValue : value) {
            builder.append(iValue.stringValue()).append(", ");
        }
        if (!value.isEmpty()) {
            builder.delete(builder.length() - 2, builder.length());
        }
        builder.append("]");
        return builder.toString();
    }
    @Override public boolean booleanValue() { return !value.isEmpty(); }
    @Override public int typeID() { return 11; }
    @Override public boolean canMath() { return false; }
    @Override public boolean isBaseType() { return false; }

    @Override
    protected IValue<?> addValue(IValue<?> other) {
        // Shimmer 对齐：原地修改并返回 this（其它持有同一 list 引用处能读到新元素）。
        if (other instanceof ListValue lv) {
            this.value.addAll(lv.jvmValue());
        } else {
            this.value.add(other);
        }
        return this;
    }

    @Override
    protected IValue<?> subValue(IValue<?> other) {
        // Shimmer 对齐(operators-15):原地删除并返回 this;非 list 一律按 Math.abs(intValue()) 当索引删
        // (字符串/none/bool 同样折算索引;越界/空 list 抛异常,bug-for-bug)。
        if (other instanceof ListValue lv) {
            this.value.removeAll(lv.jvmValue());
        } else {
            this.value.remove(Math.abs(other.intValue()));
        }
        return this;
    }

    @Override
    public String typeName() { return "list"; }

    @Override
    public String toString() { return stringValue(); }
}
