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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeadCodeElimination {

    public IRProgram optimize(IRProgram program) {
        IRInstruction[] code = program.getInstructions();
        if (code.length < 2) return program;

        // 收集所有跳转目标
        Set<Integer> jumpTargets = new HashSet<>();
        for (IRInstruction inst : code) {
            if (isJump(inst.opcode)) {
                jumpTargets.add(inst.a);
            }
        }

        // 标记可达指令
        boolean[] reachable = new boolean[code.length];
        boolean dead = false;
        for (int i = 0; i < code.length; i++) {
            if (jumpTargets.contains(i)) {
                dead = false;
            }
            reachable[i] = !dead;
            if (!dead && isTerminator(code[i].opcode)) {
                dead = true;
            }
        }

        // 检查是否有任何死代码
        boolean hasDead = false;
        for (int i = 0; i < code.length; i++) {
            if (!reachable[i]) { hasDead = true; break; }
        }
        if (!hasDead) {
            // 没有死代码，只优化子程序
            IRProgram result = cloneProgram(program);
            result.setSubPrograms(optimizeSubPrograms(program.getSubPrograms()));
            return result;
        }

        // 建立旧 PC → 新 PC 映射
        int[] pcMap = new int[code.length + 1]; // +1 for end-of-code target
        int newPC = 0;
        for (int i = 0; i < code.length; i++) {
            pcMap[i] = newPC;
            if (reachable[i]) newPC++;
        }
        pcMap[code.length] = newPC; // end sentinel

        // 收集可达指令
        List<IRInstruction> optimized = new ArrayList<>();
        for (int i = 0; i < code.length; i++) {
            if (reachable[i]) {
                optimized.add(code[i]);
            }
        }

        // 重映射跳转目标
        for (IRInstruction inst : optimized) {
            if (isJump(inst.opcode)) {
                int oldTarget = inst.a;
                if (oldTarget >= 0 && oldTarget <= code.length) {
                    inst.a = pcMap[oldTarget];
                }
            }
        }

        // 重映射 sourceMap
        SourceLocation[] oldMap = program.getSourceMap();
        SourceLocation[] newMap = null;
        if (oldMap != null && oldMap.length == code.length) {
            newMap = new SourceLocation[optimized.size()];
            int idx = 0;
            for (int i = 0; i < code.length; i++) {
                if (reachable[i]) {
                    newMap[idx++] = oldMap[i];
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

    private boolean isTerminator(IROpCode op) {
        return op == IROpCode.RETURN || op == IROpCode.BREAK || op == IROpCode.NEXT;
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
        for (int i = 0; i < subs.length; i++) {
            result[i] = optimize(subs[i]);
        }
        return result;
    }
}
