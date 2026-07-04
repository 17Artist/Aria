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

public final class NumberValue extends IValue<Double> {

    private static final Double ZERO = 0.0;

    // 小整数缓存：[-1, 128]，覆盖 fib/循环中最常见的数值
    private static final int CACHE_LOW = -1;
    private static final int CACHE_HIGH = 128;
    private static final NumberValue[] CACHE = new NumberValue[CACHE_HIGH - CACHE_LOW + 1];
    static {
        for (int i = 0; i < CACHE.length; i++) {
            CACHE[i] = new NumberValue(i + CACHE_LOW);
        }
    }


    public static NumberValue of(double v) {
        if (v == (int) v) {
            int iv = (int) v;
            if (iv >= CACHE_LOW && iv <= CACHE_HIGH) {
                return CACHE[iv - CACHE_LOW];
            }
        }
        return new NumberValue(v);
    }


    public double value;

    public NumberValue(double value) {
        this.value = value;
    }

    @Override public Double jvmValue() { return value; }
    @Override public double numberValue() { return value; }
    // Shimmer 对齐：数字恒以 double 形式字符串化（整数 5 -> "5.0"），等价 Shimmer 的 String.valueOf(double)。
    @Override public String stringValue() { return Double.toString(value); }
    // Shimmer 对齐：真值为 value > 0（0、负数、NaN 均为 false）。
    @Override public boolean booleanValue() { return value > 0; }
    @Override public int typeID() { return 1; }
    @Override public boolean canMath() { return true; }
    @Override public boolean isBaseType() { return true; }

    /** Shimmer 对齐(NumberValue.nc()):一元负号。 */
    public NumberValue nc() { return new NumberValue(-value); }

    // Shimmer 对齐(NumberValue.addValue 逐行):数字/可数串数值相加;不可数串拼接;
    // 其余 canMath(boolean/none/可数对象)数值相加;canMath=false(list/map/StoreOnly 等)抛异常(operators-8)。
    @Override
    protected IValue<?> addValue(IValue<?> other) throws AriaRuntimeException {
        if (other instanceof NumberValue nv) {
            return new NumberValue(this.value + nv.numberValue());
        }
        if (other instanceof StringValue sv) {
            if (sv.canBeNumber()) {
                return new NumberValue(this.value + sv.numberValue());
            }
            return new StringValue(this.value + sv.jvmValue());
        }
        if (other.canMath()) {
            return new NumberValue(this.value + other.numberValue());
        }
        throw new AriaRuntimeException(typeName() + " 类型不支持与 " + other.typeName() + " 类型进行加法运算");
    }

    // Shimmer 对齐(NumberValue.subValue 逐行):不可数串 → String.valueOf(value).replace(str,"")(operators-9)。
    @Override
    protected IValue<?> subValue(IValue<?> other) throws AriaRuntimeException {
        if (other instanceof NumberValue nv) {
            return new NumberValue(this.value - nv.numberValue());
        }
        if (other instanceof StringValue sv) {
            if (sv.canBeNumber()) {
                return new NumberValue(this.value - sv.numberValue());
            }
            return new StringValue(String.valueOf(this.value).replace(sv.jvmValue(), ""));
        }
        if (other.canMath()) {
            return new NumberValue(this.value - other.numberValue());
        }
        throw new AriaRuntimeException(typeName() + " 类型不支持与 " + other.typeName() + " 类型进行减法运算");
    }

    @Override
    public String typeName() { return "number"; }

    @Override
    public String toString() { return stringValue(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NumberValue nv)) return false;
        return Double.compare(value, nv.value) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }
}
