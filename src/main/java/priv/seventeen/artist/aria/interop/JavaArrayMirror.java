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

package priv.seventeen.artist.aria.interop;

import priv.seventeen.artist.aria.callable.NativeCallable;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.Variable;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.lang.reflect.Array;

public class JavaArrayMirror implements IAriaObject {
    private final Object array; // Java array (int[], String[], etc.)

    public JavaArrayMirror(Object array) {
        this.array = array;
    }

    public Object getArray() { return array; }
    public int length() { return Array.getLength(array); }

    public IValue<?> get(int index) {
        if (index < 0 || index >= length()) return NoneValue.NONE;
        return NativeCallable.wrapObject(Array.get(array, index));
    }

    public void set(int index, IValue<?> value) {
        Class<?> componentType = array.getClass().getComponentType();
        Array.set(array, index, JavaClassMirror.convertToJava(componentType, value));
    }

    @Override public String getTypeName() { return "JavaArray"; }
    @Override public String stringValue() { return "JavaArray[" + length() + "]"; }
    @Override public double numberValue() { return length(); }

    @Override
    public Variable getVariable(String name) {
        if ("length".equals(name)) {
            return new Variable.Normal(new VariableReference(new NumberValue(length())));
        }
        return Variable.Normal.NONE;
    }

    @Override
    public Variable getElement(String name) {
        try {
            int idx = Integer.parseInt(name);
            if (idx >= 0 && idx < length()) {
                return new Variable.Normal(new VariableReference(get(idx)));
            }
        } catch (NumberFormatException ignored) {}
        return getVariable(name);
    }
}
