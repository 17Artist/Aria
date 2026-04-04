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

package priv.seventeen.artist.aria.callable;

import priv.seventeen.artist.aria.compiler.ir.IROpCode;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.NumberValue;

public final class FastBinaryLambda implements ICallable {

    private final IROpCode op;

    public FastBinaryLambda(IROpCode op) {
        this.op = op;
    }

    public double invokeFastDouble(double a, double b) {
        return switch (op) {
            case ADD -> a + b;
            case SUB -> a - b;
            case MUL -> a * b;
            case DIV -> b != 0 ? a / b : 0;
            default -> 0;
        };
    }

    @Override
    public IValue<?> invoke(InvocationData data) throws AriaException {
        IValue<?> a = data.get(0);
        IValue<?> b = data.get(1);
        if (a instanceof NumberValue na && b instanceof NumberValue nb) {
            return new NumberValue(switch (op) {
                case ADD -> na.value + nb.value;
                case SUB -> na.value - nb.value;
                case MUL -> na.value * nb.value;
                case DIV -> nb.value != 0 ? na.value / nb.value : 0;
                default -> 0;
            });
        }
        return switch (op) {
            case ADD -> a.add(b);
            case SUB -> a.sub(b);
            case MUL -> a.mul(b);
            case DIV -> a.div(b);
            default -> NoneValue.NONE;
        };
    }
}
