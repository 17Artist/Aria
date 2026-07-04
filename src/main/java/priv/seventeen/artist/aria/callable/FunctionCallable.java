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
            // jit-19(helper): compiled body must run with a call context built from capturedContext
            // (closure capture) -- invoking with raw data uses the CALLER context, so rtLoadScope
            // cannot resolve captured scope vars (none -> CHECKCAST NumberValue CCE in generated code).
            // Mirrors the two NEW_FUNCTION compiled branches in Interpreter.
            if (body.isJitContextFree()) {
                return body.getCompiledCode().invoke(data);
            }
            Context compiledCtx = capturedContext.createCallContext(
                data.getTarget() instanceof IValue<?> iv ? iv : NoneValue.NONE,
                data.getArgs()
            );
            return body.getCompiledCode().invoke(new InvocationData(compiledCtx, null, data));
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
        // A4(jit-13)：严格化——二元形必须恰为「LOAD_ARG0→rA, LOAD_ARG1→rB, 单算术(a==rA,b==rB), RETURN 结果」，
        // 一元形必须操作数精确对应 (argReg, constReg) 或 (constReg, argReg)。
        // FastBinaryLambda 恒按 args[0] op args[1] 计算，顺序颠倒(args[1]-args[0])若放行会符号颠倒。
        int loadArgCount = 0;
        int arg0Reg = -1, arg1Reg = -1;
        int loadConstCount = 0;
        int constReg = -1;
        double constValue = 0;
        boolean constIsNumber = false;
        IROpCode arithOp = null;
        int arithA = -1, arithB = -1, arithDst = -1;
        int returnReg = Integer.MIN_VALUE;
        for (IRInstruction inst : c) {
            switch (inst.opcode) {
                case PUSH_SCOPE, POP_SCOPE, NOP -> {}
                case MOVE -> { return null; } // 形状不标准，保守放弃(走完整执行,语义精确)
                case LOAD_ARG -> {
                    loadArgCount++;
                    if (inst.a == 0) arg0Reg = inst.dst;
                    else if (inst.a == 1) arg1Reg = inst.dst;
                    else return null;
                }
                case LOAD_CONST -> {
                    loadConstCount++;
                    constReg = inst.dst;
                    if (sub.getConstants() != null && inst.a < sub.getConstants().length
                            && sub.getConstants()[inst.a] instanceof NumberValue nv) {
                        constValue = nv.value;
                        constIsNumber = true;
                    }
                }
                case ADD, SUB, MUL, DIV, MOD -> {
                    if (arithOp == null) {
                        arithOp = inst.opcode;
                        arithA = inst.a;
                        arithB = inst.b;
                        arithDst = inst.dst;
                    } else return null;
                }
                case RETURN -> returnReg = inst.dst;
                default -> { return null; }
            }
        }
        if (arithOp == null || returnReg != arithDst) return null;

        // 二元：args[0] op args[1]，操作数顺序精确匹配
        if (loadArgCount == 2 && arg0Reg >= 0 && arg1Reg >= 0 && loadConstCount == 0
                && arithA == arg0Reg && arithB == arg1Reg) {
            return new FastBinaryLambda(arithOp);
        }

        // 一元：args[0] op const 或 const op args[0]，操作数来源精确匹配
        if (loadArgCount == 1 && arg0Reg >= 0 && arg1Reg < 0 && loadConstCount == 1 && constIsNumber) {
            if (arithA == arg0Reg && arithB == constReg) {
                return new FastUnaryLambda(arithOp, constValue, true);
            }
            if (arithA == constReg && arithB == arg0Reg) {
                return new FastUnaryLambda(arithOp, constValue, false);
            }
        }

        return null;
    }
}
