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

public sealed abstract class IData permits IValue, Variable, Namespace {

    public abstract IValue<?> ariaValue();
    public abstract int typeID();
    public abstract boolean canMath();
    public abstract boolean isBaseType();

    /** Shimmer 对齐(IData.type()):运算异常消息里的类型名。 */
    public String typeName() { return "unknown"; }


    public final IValue<?> add(IData other) throws AriaRuntimeException {
        IValue<?> left = this.ariaValue();
        IValue<?> right = other.ariaValue();
        if (left == null || right == null) return NoneValue.NONE;
        return left.addValue(right);
    }

    public final IValue<?> sub(IData other) throws AriaRuntimeException {
        IValue<?> left = this.ariaValue();
        IValue<?> right = other.ariaValue();
        if (left == null || right == null) return NoneValue.NONE;
        return left.subValue(right);
    }

    public final IValue<?> mul(IData other) {
        IValue<?> left = this.ariaValue();
        IValue<?> right = other.ariaValue();
        if (left == null || right == null) return NoneValue.NONE;
        double result = left.numberValue() * right.numberValue();
        return new NumberValue(result);
    }

    public final IValue<?> div(IData other) {
        IValue<?> left = this.ariaValue();
        IValue<?> right = other.ariaValue();
        if (left == null || right == null) return NoneValue.NONE;
        double r = right.numberValue();
        if (r == 0) return new NumberValue(0); // Shimmer 对齐：除零 -> 0（而非 Infinity/NaN）
        return new NumberValue(left.numberValue() / r);
    }

    public final IValue<?> mod(IData other) {
        IValue<?> left = this.ariaValue();
        IValue<?> right = other.ariaValue();
        if (left == null || right == null) return NoneValue.NONE;
        double r = right.numberValue();
        if (r == 0) return new NumberValue(0); // Shimmer 对齐：取余零 -> 0（而非 NaN）
        return new NumberValue(left.numberValue() % r);
    }


    public final BooleanValue gt(IData other) {
        return BooleanValue.of(this.ariaValue().numberValue() > other.ariaValue().numberValue());
    }

    public final BooleanValue lt(IData other) {
        return BooleanValue.of(this.ariaValue().numberValue() < other.ariaValue().numberValue());
    }

    // Shimmer 对齐(operators-8):ge/le 仅基础类型(number/string/boolean)可比,否则抛异常(gt/lt 不检查,bug-for-bug)。
    public final BooleanValue ge(IData other) throws AriaRuntimeException {
        IValue<?> left = this.ariaValue();
        IValue<?> right = other.ariaValue();
        if (left.isBaseType() && right.isBaseType()) {
            return BooleanValue.of(left.numberValue() >= right.numberValue());
        }
        throw new AriaRuntimeException(typeName() + " 类型不支持比较运算");
    }

    public final BooleanValue le(IData other) throws AriaRuntimeException {
        IValue<?> left = this.ariaValue();
        IValue<?> right = other.ariaValue();
        if (left.isBaseType() && right.isBaseType()) {
            return BooleanValue.of(left.numberValue() <= right.numberValue());
        }
        throw new AriaRuntimeException(typeName() + " 类型不支持比较运算");
    }

    public final BooleanValue eq(IData other) {
        IValue<?> left = this.ariaValue();
        IValue<?> right = other.ariaValue();
        if (left == null || right == null) return BooleanValue.FALSE;
        if (left instanceof NoneValue || right instanceof NoneValue) {
            return BooleanValue.of(left instanceof NoneValue && right instanceof NoneValue);
        }
        if (left.typeID() == right.typeID()) {
            if (left instanceof NumberValue nl && right instanceof NumberValue nr) {
                return BooleanValue.of(nl.numberValue() == nr.numberValue());
            }
            return BooleanValue.of(left.jvmValue().equals(right.jvmValue()));
        }
        if (left.canMath() && right.canMath()) {
            // Shimmer 对齐(operators-5, IData.java:126-150):任一侧为不可数字符串 → 回退字符串比较
            if (left instanceof StringValue sl && !sl.canBeNumber()) {
                return BooleanValue.of(left.stringValue().equals(right.stringValue()));
            }
            if (right instanceof StringValue sr && !sr.canBeNumber()) {
                return BooleanValue.of(left.stringValue().equals(right.stringValue()));
            }
            return BooleanValue.of(left.numberValue() == right.numberValue());
        }
        return BooleanValue.FALSE;
    }

    public final BooleanValue ne(IData other) {
        return eq(other).not();
    }
}
