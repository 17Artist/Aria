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

import priv.seventeen.artist.aria.value.reference.IReference;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.util.function.BiFunction;
import java.util.function.Function;

public sealed abstract class Variable extends IData permits Variable.Normal, Variable.ObjectVar {

    @Override public boolean canMath() { return ariaValue() != null && ariaValue().canMath(); }
    @Override public boolean isBaseType() { return ariaValue() != null && ariaValue().isBaseType(); }
    @Override public int typeID() { return ariaValue() != null ? ariaValue().typeID() : 0; }

    public abstract IValue<?> setValue(IValue<?> value);

        public static final class Normal extends Variable {

        public static final Normal NONE = new Normal(new VariableReference(NoneValue.NONE));

        private final IReference reference;

        public Normal(IReference reference) {
            this.reference = reference;
        }

        public IReference getReference() { return reference; }

        @Override
        public IValue<?> ariaValue() { return reference.getValue(); }

        @Override
        public IValue<?> setValue(IValue<?> value) {
            // 赋值 NumberValue 时创建新实例避免引用共享
            if (value instanceof NumberValue nv) {
                return reference.setValue(new NumberValue(nv.numberValue()));
            }
            return reference.setValue(value);
        }
    }

        public static final class ObjectVar<T> extends Variable {

        private final BiFunction<IValue<?>, T, IValue<?>> setter;
        private final Function<T, IValue<?>> getter;
        private final T target;

        public ObjectVar(BiFunction<IValue<?>, T, IValue<?>> setter, Function<T, IValue<?>> getter, T target) {
            this.setter = setter;
            this.getter = getter;
            this.target = target;
        }

        @Override
        public IValue<?> ariaValue() { return getter.apply(target); }

        @Override
        public IValue<?> setValue(IValue<?> value) { return setter.apply(value, target); }
    }
}
