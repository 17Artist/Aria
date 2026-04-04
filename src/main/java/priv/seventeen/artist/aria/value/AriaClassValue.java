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

import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.object.ClassInstance;
import priv.seventeen.artist.aria.value.reference.IReference;

public final class AriaClassValue extends IValue<ClassInstance> {

    private final ClassInstance value;

    public AriaClassValue(ClassInstance value) {
        this.value = value;
    }

    @Override public ClassInstance jvmValue() { return value; }
    @Override public double numberValue() { return 0; }
    @Override public String stringValue() { return value != null ? value.toString() : "none"; }
    @Override public boolean booleanValue() { return value != null; }
    @Override public int typeID() { return 6; }
    @Override public boolean canMath() { return false; }
    @Override public boolean isBaseType() { return false; }

    @Override
    protected IValue<?> addValue(IValue<?> other) {
        FunctionValue fn = findOperator("__add__");
        if (fn != null) {
            return invokeOperator(fn, other);
        }
        return super.addValue(other);
    }

    @Override
    protected IValue<?> subValue(IValue<?> other) {
        FunctionValue fn = findOperator("__sub__");
        if (fn != null) {
            return invokeOperator(fn, other);
        }
        return super.subValue(other);
    }

    private FunctionValue findOperator(String name) {
        if (value == null) return null;
        IReference ref = value.getFields().get(name);
        if (ref != null && ref.getValue() instanceof FunctionValue fv) {
            return fv;
        }
        return null;
    }

    private IValue<?> invokeOperator(FunctionValue fn, IValue<?> operand) {
        try {
            return fn.getCallable().invoke(
                new InvocationData(null, value, new IValue<?>[]{ operand }));
        } catch (Exception e) {
            return NoneValue.NONE;
        }
    }

    @Override
    public String toString() { return stringValue(); }
}
