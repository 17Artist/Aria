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


public final class MutableStringValue extends IValue<String> {

    private final StringBuilder builder;
    private String cached;

    public MutableStringValue(String initial) {
        this.builder = new StringBuilder(Math.max(initial.length() + 64, 256));
        this.builder.append(initial);
        this.cached = null;
    }

    public MutableStringValue append(String s) {
        builder.append(s);
        cached = null;
        return this;
    }

    @Override public String jvmValue() { return stringValue(); }
    @Override public double numberValue() { return 0; }
    @Override public String stringValue() {
        if (cached == null) cached = builder.toString();
        return cached;
    }
    @Override public boolean booleanValue() { return builder.length() > 0; }
    @Override public int typeID() { return 3; }
    @Override public boolean canMath() { return false; }
    @Override public boolean isBaseType() { return true; }

    public int length() { return builder.length(); }

    @Override
    protected IValue<?> addValue(IValue<?> other) {
        builder.append(other.stringValue());
        cached = null;
        return this;
    }

    @Override
    protected IValue<?> subValue(IValue<?> other) {
        return new StringValue(stringValue(), true).sub(other);
    }

    @Override
    public String toString() { return stringValue(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof MutableStringValue ms) return stringValue().equals(ms.stringValue());
        if (o instanceof StringValue sv) return stringValue().equals(sv.stringValue());
        return false;
    }

    @Override
    public int hashCode() { return stringValue().hashCode(); }
}
