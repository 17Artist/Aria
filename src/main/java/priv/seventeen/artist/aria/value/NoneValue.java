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

public final class NoneValue extends IValue<Object> {

    public static final NoneValue NONE = new NoneValue();

    private NoneValue() {}

    @Override public Object jvmValue() { return null; }
    @Override public double numberValue() { return 0; }
    @Override public String stringValue() { return "none"; }
    @Override public boolean booleanValue() { return false; }
    @Override public int typeID() { return 0; }
    @Override public boolean canMath() { return true; }
    @Override public boolean isBaseType() { return false; }

    @Override
    protected IValue<?> addValue(IValue<?> other) { return other; }

    @Override
    protected IValue<?> subValue(IValue<?> other) { return this; }

    @Override
    public String toString() { return "none"; }
}
