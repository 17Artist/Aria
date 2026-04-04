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

package priv.seventeen.artist.aria.compiler.ir;

public class IRInstruction {
    public final IROpCode opcode;
    public int dst;      // 目标寄存器
    public int a;        // 操作数A（源寄存器或常量池索引）
    public int b;        // 操作数B
    public int c;        // 操作数C（用于CALL的参数数量等）
    public String name;  // 属性名（GET_PROP/SET_PROP/CALL_METHOD/CALL_STATIC等）
    public volatile Object cache;  // 运行时缓存（幂等写入，线程安全）
    public Object metadata;  // 编译期附加数据（DEFINE_CLASS 的注解信息等）

    public IRInstruction(IROpCode opcode) {
        this.opcode = opcode;
    }

    public static IRInstruction of(IROpCode opcode) {
        return new IRInstruction(opcode);
    }

    public static IRInstruction of(IROpCode opcode, int dst) {
        IRInstruction inst = new IRInstruction(opcode);
        inst.dst = dst;
        return inst;
    }

    public static IRInstruction of(IROpCode opcode, int dst, int a) {
        IRInstruction inst = new IRInstruction(opcode);
        inst.dst = dst;
        inst.a = a;
        return inst;
    }

    public static IRInstruction of(IROpCode opcode, int dst, int a, int b) {
        IRInstruction inst = new IRInstruction(opcode);
        inst.dst = dst;
        inst.a = a;
        inst.b = b;
        return inst;
    }

    public IRInstruction withName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(opcode);
        if (name != null) sb.append(" ").append(name);
        sb.append(" r").append(dst);
        if (a != 0 || b != 0) sb.append(", ").append(a);
        if (b != 0) sb.append(", ").append(b);
        if (c != 0) sb.append(", ").append(c);
        return sb.toString();
    }
}
