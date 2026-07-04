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

import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.object.IAriaObject;

public final class ObjectValue<T extends IAriaObject> extends IValue<T> {

    private final T value;

    public ObjectValue(T value) {
        this.value = value;
    }

    @Override public T jvmValue() { return value; }
    @Override public double numberValue() { return value.numberValue(); }
    @Override public String stringValue() { return value.stringValue(); }
    @Override public boolean booleanValue() { return value.booleanValue(); }
    @Override public int typeID() { return 4; }
    @Override public boolean canMath() { return value.canMath(); }
    @Override public boolean isBaseType() { return false; }

    // Shimmer 对齐(ObjectValue.addValue 逐行):双方 canMath 才可运算,否则抛异常(operators-8)。
    @Override
    protected IValue<?> addValue(IValue<?> other) throws AriaRuntimeException {
        if (this.value.canMath() && other.canMath()) {
            if (other instanceof StringValue sv && !sv.canBeNumber()) {
                return new StringValue(this.value.stringValue() + other.stringValue());
            }
            return new NumberValue(this.value.numberValue() + other.numberValue());
        }
        throw new AriaRuntimeException(other.typeName() + "不支持加法操作");
    }

    @Override
    protected IValue<?> subValue(IValue<?> other) throws AriaRuntimeException {
        if (this.value.canMath() && other.canMath()) {
            if (other instanceof StringValue sv && !sv.canBeNumber()) {
                return new StringValue(this.value.stringValue().replace(other.stringValue(), ""));
            }
            // Shimmer bug-for-bug:ObjectValue.subValue 数值分支实际是加法(源码第 66 行 +)。
            return new NumberValue(this.value.numberValue() + other.numberValue());
        }
        throw new AriaRuntimeException(other.typeName() + "不支持减法操作");
    }

    @Override
    public String typeName() { return value.getTypeName(); }

    @Override
    public String toString() { return stringValue(); }
}
