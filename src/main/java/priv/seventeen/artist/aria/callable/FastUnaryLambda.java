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
import priv.seventeen.artist.aria.value.NumberValue;

public final class FastUnaryLambda implements ICallable {

    private final IROpCode op;
    private final double constant;
    private final boolean argFirst;

    public FastUnaryLambda(IROpCode op, double constant, boolean argFirst) {
        this.op = op;
        this.constant = constant;
        this.argFirst = argFirst;
    }

    @Override
    public IValue<?> invoke(InvocationData data) throws AriaException {
        double a = data.get(0).numberValue();
        double result = argFirst ? compute(a, constant) : compute(constant, a);
        return new NumberValue(result);
    }

    public double invokeFastDouble(double a) {
        return argFirst ? compute(a, constant) : compute(constant, a);
    }

    private double compute(double left, double right) {
        return switch (op) {
            case ADD -> left + right;
            case SUB -> left - right;
            case MUL -> left * right;
            case DIV -> right != 0 ? left / right : 0;
            case MOD -> right != 0 ? left % right : 0;
            default -> 0;
        };
    }
}
