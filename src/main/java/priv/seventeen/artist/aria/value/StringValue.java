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

public final class StringValue extends IValue<String> {

    private final String value;
    private final boolean canBeNumber;
    private double numericValue;

    public StringValue(String value) {
        this.value = value;
        boolean canParse = false;
        double parsed = 0;
        if (value != null && !value.isEmpty() && value.length() <= 30) {
            char c = value.charAt(0);
            if ((c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.') {
                try {
                    parsed = Double.parseDouble(value);
                    canParse = true;
                } catch (NumberFormatException ignored) {}
            }
        }
        this.canBeNumber = canParse;
        this.numericValue = parsed;
    }

        public StringValue(String value, boolean concat) {
        this.value = value;
        this.canBeNumber = false;
        this.numericValue = 0;
    }

    public boolean canBeNumber() { return canBeNumber; }

    @Override public String jvmValue() { return value; }
    @Override public double numberValue() { return numericValue; }
    @Override public String stringValue() { return value; }
    @Override public boolean booleanValue() { return value != null && !value.isEmpty(); }
    @Override public int typeID() { return 3; }
    @Override public boolean canMath() { return true; }
    @Override public boolean isBaseType() { return true; }

    @Override
    protected IValue<?> addValue(IValue<?> other) {
        if (other instanceof StringValue sv) {
            if (this.canBeNumber && sv.canBeNumber()) {
                return new NumberValue(this.numericValue + sv.numberValue());
            }
            return new StringValue(this.value + sv.stringValue(), true);
        }
        if (other instanceof NumberValue) {
            if (this.canBeNumber) {
                return new NumberValue(this.numericValue + other.numberValue());
            }
            return new StringValue(this.value + other.stringValue(), true);
        }
        return new StringValue(this.value + other.stringValue(), true);
    }

    @Override
    protected IValue<?> subValue(IValue<?> other) {
        if (other instanceof StringValue sv) {
            if (this.canBeNumber && sv.canBeNumber()) {
                return new NumberValue(this.numericValue - sv.numberValue());
            }
            return new StringValue(this.value.replace(sv.stringValue(), ""), true);
        }
        if (this.canBeNumber) {
            return new NumberValue(this.numericValue - other.numberValue());
        }
        return new StringValue(this.value.replace(other.stringValue(), ""), true);
    }

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
