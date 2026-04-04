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

import priv.seventeen.artist.aria.compiler.ir.IRInstruction;
import priv.seventeen.artist.aria.compiler.ir.IROpCode;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.Result;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.NumberValue;

public class FunctionCallable implements ICallable {
    private final IRProgram body;
    private final Context capturedContext;
    private volatile ICallable fastPath;
    private volatile boolean analyzed = false;

    public FunctionCallable(IRProgram body, Context capturedContext) {
        this.body = body;
        this.capturedContext = capturedContext;
    }

    public IRProgram getBody() { return body; }

    public ICallable getFastPath() {
        if (!analyzed) {
            analyzed = true;
            fastPath = detectFastLambda(body);
        }
        return fastPath;
    }

    private static final ThreadLocal<Interpreter> INTERPRETER_CACHE = ThreadLocal.withInitial(Interpreter::new);

    @Override
    public IValue<?> invoke(InvocationData data) throws AriaException {
        if (!analyzed) {
            analyzed = true;
            fastPath = detectFastLambda(body);
        }
        if (fastPath != null) {
            return fastPath.invoke(data);
        }
        if (body.isCompiled()) {
            return body.getCompiledCode().invoke(data);
        }

        Context callContext = capturedContext.createCallContext(
            data.getTarget() instanceof IValue<?> iv ? iv : NoneValue.NONE,
            data.getArgs()
        );
        
        Interpreter interpreter = INTERPRETER_CACHE.get();
        Result result = interpreter.execute(body, callContext);
        return result.getValue();
    }

    private static ICallable detectFastLambda(IRProgram sub) {
        if (sub == null) return null;
        IRInstruction[] c = sub.getInstructions();
        if (c == null) return null;
        int loadArgCount = 0;
        int maxArgIndex = -1;
        int loadConstCount = 0;
        double constValue = 0;
        IROpCode arithOp = null;
        int arithA = -1;
        int argReg = -1;
        for (IRInstruction inst : c) {
            switch (inst.opcode) {
                case PUSH_SCOPE, POP_SCOPE, NOP, MOVE -> {}
                case LOAD_ARG -> {
                    loadArgCount++;
                    maxArgIndex = Math.max(maxArgIndex, inst.a);
                    argReg = inst.dst;
                }
                case LOAD_CONST -> {
                    loadConstCount++;
                    if (sub.getConstants() != null && inst.a < sub.getConstants().length
                            && sub.getConstants()[inst.a] instanceof NumberValue nv) {
                        constValue = nv.value;
                    }
                }
                case ADD, SUB, MUL, DIV, MOD -> {
                    if (arithOp == null) {
                        arithOp = inst.opcode;
                        arithA = inst.a;
                    } else return null;
                }
                case RETURN -> {}
                default -> { return null; }
            }
        }
        if (arithOp == null) return null;

        if (loadArgCount == 2 && maxArgIndex == 1 && loadConstCount == 0) {
            return new FastBinaryLambda(arithOp);
        }

        if (loadArgCount == 1 && maxArgIndex == 0 && loadConstCount == 1) {
            boolean argFirst = (arithA == argReg);
            return new FastUnaryLambda(arithOp, constValue, argFirst);
        }

        return null;
    }
}
