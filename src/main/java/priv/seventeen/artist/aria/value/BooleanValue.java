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

public final class BooleanValue extends IValue<Boolean> {

    public static final BooleanValue TRUE = new BooleanValue(true);
    public static final BooleanValue FALSE = new BooleanValue(false);

    private final boolean value;

    private BooleanValue(boolean value) {
        this.value = value;
    }

    public static BooleanValue of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public BooleanValue not() {
        return value ? FALSE : TRUE;
    }

    @Override public Boolean jvmValue() { return value; }
    @Override public double numberValue() { return value ? 1 : 0; }
    @Override public String stringValue() { return value ? "true" : "false"; }
    @Override public boolean booleanValue() { return value; }
    @Override public int typeID() { return 2; }
    @Override public boolean canMath() { return true; }
    @Override public boolean isBaseType() { return true; }

    @Override
    public String toString() { return stringValue(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BooleanValue bv)) return false;
        return value == bv.value;
    }

    @Override
    public int hashCode() { return Boolean.hashCode(value); }
}
