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

package priv.seventeen.artist.aria.compiler.optimize;

import priv.seventeen.artist.aria.compiler.ir.IRInstruction;
import priv.seventeen.artist.aria.compiler.ir.IROpCode;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.NumberValue;

import java.util.ArrayList;
import java.util.List;

public class ConstantFolding {

    public IRProgram optimize(IRProgram program) {
        IRInstruction[] code = program.getInstructions();
        IValue<?>[] constants = program.getConstants();
        if (code.length < 3) return program;

        // Track which registers hold constants
        int[] constIndex = new int[program.getRegisterCount()]; // -1 = not const
        java.util.Arrays.fill(constIndex, -1);

        List<IRInstruction> optimized = new ArrayList<>();
        List<IValue<?>> newConstants = new ArrayList<>(java.util.Arrays.asList(constants));

        for (IRInstruction inst : code) {
            if (inst.opcode == IROpCode.LOAD_CONST) {
                constIndex[inst.dst] = inst.a;
                optimized.add(inst);
                continue;
            }

            // Try to fold binary arithmetic
            if (isBinaryArith(inst.opcode) && constIndex[inst.a] >= 0 && constIndex[inst.b] >= 0) {
                IValue<?> left = getConst(newConstants, constIndex[inst.a]);
                IValue<?> right = getConst(newConstants, constIndex[inst.b]);
                if (left instanceof NumberValue && right instanceof NumberValue) {
                    double l = left.numberValue();
                    double r = right.numberValue();
                    double result = switch (inst.opcode) {
                        case ADD, ADD_NUM -> l + r;
                        case SUB, SUB_NUM -> l - r;
                        case MUL, MUL_NUM -> l * r;
                        case DIV, DIV_NUM -> r != 0 ? l / r : (l > 0 ? Double.POSITIVE_INFINITY : l < 0 ? Double.NEGATIVE_INFINITY : Double.NaN);
                        case MOD, MOD_NUM -> r != 0 ? l % r : Double.NaN;
                        default -> Double.NaN;
                    };
                    if (!Double.isNaN(result)) {
                        int idx = newConstants.size();
                        newConstants.add(new NumberValue(result));
                        IRInstruction folded = IRInstruction.of(IROpCode.LOAD_CONST, inst.dst, idx);
                        optimized.add(folded);
                        constIndex[inst.dst] = idx;
                        continue;
                    }
                }
            }

            // Invalidate dst register's const status
            if (inst.dst >= 0 && inst.dst < constIndex.length) {
                constIndex[inst.dst] = -1;
            }
            optimized.add(inst);
        }

        IRProgram result = new IRProgram(program.getName());
        result.setConstants(newConstants.toArray(new IValue<?>[0]));
        result.setVariableKeys(program.getVariableKeys());
        result.setInstructions(optimized.toArray(new IRInstruction[0]));
        result.setRegisterCount(program.getRegisterCount());
        result.setSourceMap(program.getSourceMap());
        result.setSubPrograms(optimizeSubPrograms(program.getSubPrograms()));
        return result;
    }

    private boolean isBinaryArith(IROpCode op) {
        return op == IROpCode.ADD || op == IROpCode.SUB || op == IROpCode.MUL || op == IROpCode.DIV || op == IROpCode.MOD
            || op == IROpCode.ADD_NUM || op == IROpCode.SUB_NUM || op == IROpCode.MUL_NUM || op == IROpCode.DIV_NUM || op == IROpCode.MOD_NUM;
    }

    private IValue<?> getConst(List<IValue<?>> constants, int index) {
        if (index >= 0 && index < constants.size()) return constants.get(index);
        return NoneValue.NONE;
    }

    private IRProgram[] optimizeSubPrograms(IRProgram[] subs) {
        if (subs == null || subs.length == 0) return subs;
        IRProgram[] result = new IRProgram[subs.length];
        for (int i = 0; i < subs.length; i++) {
            result[i] = optimize(subs[i]);
        }
        return result;
    }
}
