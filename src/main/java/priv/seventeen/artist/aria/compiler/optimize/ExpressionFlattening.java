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
import priv.seventeen.artist.aria.parser.SourceLocation;

import java.util.ArrayList;
import java.util.List;


public class ExpressionFlattening {

    public IRProgram optimize(IRProgram program) {
        IRInstruction[] code = program.getInstructions();
        if (code == null || code.length < 2) return program;

        int regCount = Math.max(program.getRegisterCount(), 1);

        // Pass 1: 统计每个寄存器作为源操作数的使用次数
        int[] useCount = new int[regCount];
        for (IRInstruction inst : code) {
            if (inst.a >= 0 && inst.a < regCount) useCount[inst.a]++;
            if (inst.b >= 0 && inst.b < regCount) useCount[inst.b]++;
        }

        // Pass 2: 标记要删除的指令
        boolean[] removed = new boolean[code.length];
        for (int i = 0; i < code.length - 1; i++) {
            IRInstruction next = code[i + 1];
            if (next.opcode != IROpCode.MOVE) continue;
            if (next.dst == next.a) { removed[i + 1] = true; continue; }

            int src = next.a;
            if (src >= 0 && src < regCount && useCount[src] == 1) {
                IRInstruction prev = code[i];
                if (prev.dst == src && canRewriteDst(prev.opcode)) {
                    prev.dst = next.dst;
                    removed[i + 1] = true;
                }
            }
        }
        // 也标记 NOP 和冗余 MOVE
        for (int i = 0; i < code.length; i++) {
            if (removed[i]) continue;
            if (code[i].opcode == IROpCode.NOP) removed[i] = true;
            if (code[i].opcode == IROpCode.MOVE && code[i].dst == code[i].a) removed[i] = true;
        }

        // 检查是否有任何删除
        boolean hasRemoved = false;
        for (boolean r : removed) if (r) { hasRemoved = true; break; }
        if (!hasRemoved) {
            IRProgram[] newSubs = optimizeSubPrograms(program.getSubPrograms());
            if (newSubs == program.getSubPrograms()) return program;
            IRProgram result = cloneProgram(program);
            result.setSubPrograms(newSubs);
            return result;
        }

        // Pass 3: 建立旧 PC → 新 PC 映射
        int[] pcMap = new int[code.length + 1];
        int newPC = 0;
        for (int i = 0; i < code.length; i++) {
            pcMap[i] = newPC;
            if (!removed[i]) newPC++;
        }
        pcMap[code.length] = newPC;

        // Pass 4: 收集保留的指令
        List<IRInstruction> optimized = new ArrayList<>(newPC);
        for (int i = 0; i < code.length; i++) {
            if (!removed[i]) optimized.add(code[i]);
        }

        // Pass 5: 重映射跳转目标
        for (IRInstruction inst : optimized) {
            if (isJump(inst.opcode)) {
                int oldTarget = inst.a;
                if (oldTarget >= 0 && oldTarget <= code.length) {
                    inst.a = pcMap[oldTarget];
                }
            }
        }

        // Pass 6: 重映射 sourceMap
        SourceLocation[] oldMap = program.getSourceMap();
        SourceLocation[] newMap = null;
        if (oldMap != null && oldMap.length == code.length) {
            newMap = new SourceLocation[optimized.size()];
            int idx = 0;
            for (int i = 0; i < code.length; i++) {
                if (!removed[i] && idx < newMap.length) {
                    newMap[idx++] = i < oldMap.length ? oldMap[i] : null;
                }
            }
        }

        IRProgram result = new IRProgram(program.getName());
        result.setConstants(program.getConstants());
        result.setVariableKeys(program.getVariableKeys());
        result.setInstructions(optimized.toArray(new IRInstruction[0]));
        result.setRegisterCount(program.getRegisterCount());
        result.setSourceMap(newMap);
        result.setSubPrograms(optimizeSubPrograms(program.getSubPrograms()));
        return result;
    }

    private boolean isJump(IROpCode op) {
        return op == IROpCode.JUMP || op == IROpCode.JUMP_IF_TRUE
            || op == IROpCode.JUMP_IF_FALSE || op == IROpCode.JUMP_IF_NONE
            || op == IROpCode.TRY_BEGIN;
    }

    private boolean canRewriteDst(IROpCode op) {
        return switch (op) {
            case LOAD_CONST, LOAD_NONE, LOAD_TRUE, LOAD_FALSE,
                 LOAD_VAR, LOAD_VAL, LOAD_GLOBAL, LOAD_CLIENT, LOAD_SERVER,
                 LOAD_SCOPE, LOAD_SELF, LOAD_ARG, LOAD_ARGS,
                 ADD, SUB, MUL, DIV, MOD, NEG, INC, DEC,
                 ADD_NUM, SUB_NUM, MUL_NUM, DIV_NUM, MOD_NUM,
                 BIT_AND, BIT_OR, BIT_XOR, BIT_NOT, SHL, SHR, USHR,
                 EQ, NE, GT, LT, GE, LE, IN_RANGE,
                 NOT, AND, OR,
                 GET_PROP, GET_INDEX,
                 NEW_LIST, NEW_MAP, NEW_FUNCTION, NEW_INSTANCE,
                 CONCAT, INIT_OR_GET -> true;
            default -> false;
        };
    }

    private IRProgram cloneProgram(IRProgram src) {
        IRProgram result = new IRProgram(src.getName());
        result.setConstants(src.getConstants());
        result.setVariableKeys(src.getVariableKeys());
        result.setInstructions(src.getInstructions());
        result.setRegisterCount(src.getRegisterCount());
        result.setSourceMap(src.getSourceMap());
        return result;
    }

    private IRProgram[] optimizeSubPrograms(IRProgram[] subs) {
        if (subs == null || subs.length == 0) return subs;
        IRProgram[] result = new IRProgram[subs.length];
        boolean changed = false;
        for (int i = 0; i < subs.length; i++) {
            result[i] = optimize(subs[i]);
            if (result[i] != subs[i]) changed = true;
        }
        return changed ? result : subs;
    }
}
