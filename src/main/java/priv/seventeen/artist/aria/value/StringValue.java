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

public final class StringValue extends IValue<String> {

    private final String value;
    private final boolean canBeNumber;
    private final double numericValue;

    public StringValue(String value) {
        // Shimmer 对齐(operators-3/11, gui-chain-10):无条件 try{Double.parseDouble}catch 判 canBeNumber
        // (无长度≤30/首字符门控;容空白/Infinity/NaN;拼接结果也重算)。
        this.value = value;
        boolean canParse;
        double parsed = 0;
        try {
            parsed = Double.parseDouble(value);
            canParse = true;
        } catch (Exception ignored) {
            canParse = false;
        }
        this.canBeNumber = canParse;
        this.numericValue = parsed;
    }

    public boolean canBeNumber() { return canBeNumber; }

    /** Shimmer 对齐(StringValue.nc()):一元负号——不可数抛异常,可数返回取负数字。 */
    public NumberValue nc() throws AriaRuntimeException {
        if (!canBeNumber) {
            throw new AriaRuntimeException("字符串内容非数字: " + value + " 无法转换为数字进行运算");
        }
        return new NumberValue(-numericValue);
    }

    @Override public String jvmValue() { return value; }
    @Override public double numberValue() { return numericValue; }
    @Override public String stringValue() { return value; }
    // Shimmer 对齐：仅字面量 "true"(忽略大小写) 为真，"1"/"0"/"false"/"abc" 均为 false。
    @Override public boolean booleanValue() { return Boolean.parseBoolean(value); }
    @Override public int typeID() { return 3; }
    @Override public boolean canMath() { return true; }
    @Override public boolean isBaseType() { return true; }

    // Shimmer 对齐(StringValue.addValue 逐行):
    //  串+数字:自身可数 → 数值相加;不可数 → value + numberValue()(即 "slot"+1="slot1.0")
    //  串+串:双方可数 → 数值相加返回 NumberValue;否则拼接
    //  串+其它:拼 stringValue()
    @Override
    protected IValue<?> addValue(IValue<?> other) {
        if (other instanceof NumberValue nv) {
            if (this.canBeNumber) {
                return new NumberValue(this.numericValue + nv.numberValue());
            }
            return new StringValue(this.value + nv.numberValue());
        }
        if (other instanceof StringValue sv) {
            if (this.canBeNumber && sv.canBeNumber()) {
                return new NumberValue(this.numericValue + sv.numberValue());
            }
            return new StringValue(this.value + sv.jvmValue());
        }
        return new StringValue(this.value + other.stringValue());
    }

    // Shimmer 对齐(StringValue.subValue 逐行):仅 Number/String 有数值分支;
    // 其它类型(含 boolean/none)一律走 replace(bug-for-bug:"5"-true="5" 而非 4.0)。
    @Override
    protected IValue<?> subValue(IValue<?> other) {
        if (other instanceof NumberValue nv) {
            if (this.canBeNumber) {
                return new NumberValue(this.numericValue - nv.numberValue());
            }
            return new StringValue(this.value.replace(nv.stringValue(), ""));
        }
        if (other instanceof StringValue sv) {
            if (this.canBeNumber && sv.canBeNumber()) {
                return new NumberValue(this.numericValue - sv.numberValue());
            }
            return new StringValue(this.value.replace(sv.jvmValue(), ""));
        }
        return new StringValue(this.value.replace(other.stringValue(), ""));
    }

    @Override
    public String typeName() { return "string"; }

    @Override
    public String toString() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StringValue sv)) return false;
        return value != null ? value.equals(sv.value) : sv.value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }
}
