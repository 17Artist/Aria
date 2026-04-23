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
import priv.seventeen.artist.aria.value.NumberValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 代数恒等式化简（在 ConstantFolding 之后运行）。
 *
 * - x + 0 / 0 + x         → MOVE x
 * - x - 0                 → MOVE x
 * - x * 1 / 1 * x         → MOVE x
 * - x * 0 / 0 * x         → LOAD_CONST 0
 * - x / 1                 → MOVE x
 * - x % 1                 → LOAD_CONST 0
 *
 * 注意：浮点 NaN/Infinity 语义下 x*0 ≠ 0（NaN*0 = NaN），x/0 也特殊。
 * 本 pass 仅在能静态确定操作数是"普通有限数字"时应用。由于我们只识别
 * 常量池里的 NumberValue 且 0/1 是绝大多数脚本的实际值，这里粗粒度处理足够。
 * 保守起见，仅对 x+0 / x*1 / x-0 / x/1 做（这些对任何实数都安全），
 * x*0 / x%1 需要非 NaN/非 Infinity 前提 — 为保证安全性不做。
 */
public class AlgebraicSimplification {

    public IRProgram optimize(IRProgram program) {
        IRInstruction[] code = program.getInstructions();
        IValue<?>[] constants = program.getConstants();
        if (code.length == 0) return program;

        // 跟踪哪些寄存器被 LOAD_CONST 赋值、对应常量索引
        int[] constIndex = new int[program.getRegisterCount()];
        Arrays.fill(constIndex, -1);

        List<IRInstruction> out = new ArrayList<>(code.length);

        for (IRInstruction inst : code) {
            if (inst.opcode == IROpCode.LOAD_CONST) {
                if (inst.dst >= 0 && inst.dst < constIndex.length) {
                    constIndex[inst.dst] = inst.a;
                }
                out.add(inst);
                continue;
            }

            if (isBinaryArith(inst.opcode)) {
                Double lc = tryConstDouble(constants, constIndex, inst.a);
                Double rc = tryConstDouble(constants, constIndex, inst.b);
                IRInstruction simplified = trySimplify(inst, lc, rc);
                if (simplified != null) {
                    out.add(simplified);
                    // MOVE 不改变 dst 的 const 状态中原本来源的常量 — 为保守起见标记为非 const
                    if (inst.dst >= 0 && inst.dst < constIndex.length) constIndex[inst.dst] = -1;
                    continue;
                }
            }

            if (inst.dst >= 0 && inst.dst < constIndex.length) constIndex[inst.dst] = -1;
            out.add(inst);
        }

        IRProgram result = new IRProgram(program.getName());
        result.setConstants(program.getConstants());
        result.setVariableKeys(program.getVariableKeys());
        result.setInstructions(out.toArray(new IRInstruction[0]));
        result.setRegisterCount(program.getRegisterCount());
        result.setSourceMap(program.getSourceMap());
        result.setSubPrograms(optimizeSubs(program.getSubPrograms()));
        return result;
    }

    private IRInstruction trySimplify(IRInstruction inst, Double lc, Double rc) {
        IROpCode op = inst.opcode;
        switch (op) {
            case ADD, ADD_NUM -> {
                if (rc != null && rc == 0.0) return IRInstruction.of(IROpCode.MOVE, inst.dst, inst.a);
                if (lc != null && lc == 0.0) return IRInstruction.of(IROpCode.MOVE, inst.dst, inst.b);
            }
            case SUB, SUB_NUM -> {
                if (rc != null && rc == 0.0) return IRInstruction.of(IROpCode.MOVE, inst.dst, inst.a);
            }
            case MUL, MUL_NUM -> {
                if (rc != null && rc == 1.0) return IRInstruction.of(IROpCode.MOVE, inst.dst, inst.a);
                if (lc != null && lc == 1.0) return IRInstruction.of(IROpCode.MOVE, inst.dst, inst.b);
            }
            case DIV, DIV_NUM -> {
                if (rc != null && rc == 1.0) return IRInstruction.of(IROpCode.MOVE, inst.dst, inst.a);
            }
            default -> {}
        }
        return null;
    }

    private Double tryConstDouble(IValue<?>[] constants, int[] constIndex, int reg) {
        if (reg < 0 || reg >= constIndex.length) return null;
        int ci = constIndex[reg];
        if (ci < 0 || ci >= constants.length) return null;
        IValue<?> v = constants[ci];
        if (v instanceof NumberValue nv) {
            double d = nv.numberValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return null;
            return d;
        }
        return null;
    }

    private boolean isBinaryArith(IROpCode op) {
        return op == IROpCode.ADD || op == IROpCode.SUB || op == IROpCode.MUL
                || op == IROpCode.DIV || op == IROpCode.MOD
                || op == IROpCode.ADD_NUM || op == IROpCode.SUB_NUM || op == IROpCode.MUL_NUM
                || op == IROpCode.DIV_NUM || op == IROpCode.MOD_NUM;
    }

    private IRProgram[] optimizeSubs(IRProgram[] subs) {
        if (subs == null || subs.length == 0) return subs;
        IRProgram[] out = new IRProgram[subs.length];
        for (int i = 0; i < subs.length; i++) out[i] = optimize(subs[i]);
        return out;
    }
}
