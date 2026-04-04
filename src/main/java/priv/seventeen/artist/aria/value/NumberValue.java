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

    @Override public Double jvmValue() { return ZERO; }
    @Override public double numberValue() { return value; }
    @Override public String stringValue() {
        if (value == (long) value) return Long.toString((long) value);
        return Double.toString(value);
    }
    @Override public boolean booleanValue() { return value != 0; }
    @Override public int typeID() { return 1; }
    @Override public boolean canMath() { return true; }
    @Override public boolean isBaseType() { return true; }

    @Override
    protected IValue<?> addValue(IValue<?> other) {
        if (other instanceof StringValue sv && !sv.canBeNumber()) {
            return new StringValue(this.stringValue() + sv.stringValue());
        }
        return new NumberValue(this.value + other.numberValue());
    }

    @Override
    protected IValue<?> subValue(IValue<?> other) {
        return new NumberValue(this.value - other.numberValue());
    }

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
