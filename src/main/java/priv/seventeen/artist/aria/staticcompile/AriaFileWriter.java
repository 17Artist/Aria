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

package priv.seventeen.artist.aria.staticcompile;

import priv.seventeen.artist.aria.compiler.ir.IRInstruction;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.parser.SourceLocation;
import priv.seventeen.artist.aria.value.*;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

public class AriaFileWriter {

    public static void write(IRProgram program, Path output) throws IOException {
        write(program, output, false);
    }

    public static void write(IRProgram program, Path output, boolean includeSourceMap) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(output.toFile())))) {
            // Magic
            dos.write(AriaBinaryFormat.MAGIC);
            
            // Flags
            short flags = 0;
            if (includeSourceMap) flags |= AriaBinaryFormat.FLAG_HAS_SOURCE_MAP;
            dos.writeShort(flags);
            
            // Name
            dos.writeUTF(program.getName());
            
            // Register count
            dos.writeInt(program.getRegisterCount());
            
            // Constants
            IValue<?>[] constants = program.getConstants();
            dos.writeInt(constants.length);
            for (IValue<?> c : constants) {
                writeConstant(dos, c);
            }
            
            // Variable keys
            VariableKey[] keys = program.getVariableKeys();
            dos.writeInt(keys != null ? keys.length : 0);
            if (keys != null) {
                for (VariableKey key : keys) {
                    dos.writeUTF(key.getName());
                }
            }
            
            // Instructions
            IRInstruction[] code = program.getInstructions();
            dos.writeInt(code.length);
            for (IRInstruction inst : code) {
                dos.writeShort(inst.opcode.ordinal());
                dos.writeInt(inst.dst);
                dos.writeInt(inst.a);
                dos.writeInt(inst.b);
                dos.writeInt(inst.c);
                dos.writeUTF(inst.name != null ? inst.name : "");
            }
            
            // Sub programs
            IRProgram[] subs = program.getSubPrograms();
            dos.writeInt(subs != null ? subs.length : 0);
            if (subs != null) {
                for (IRProgram sub : subs) {
                    writeSubProgram(dos, sub);
                }
            }
            
            // Source map
            if (includeSourceMap && program.getSourceMap() != null) {
                SourceLocation[] map = program.getSourceMap();
                dos.writeInt(map.length);
                for (SourceLocation loc : map) {
                    dos.writeInt(loc.startLine());
                    dos.writeInt(loc.startColumn());
                }
            }
        }
    }

    private static void writeConstant(DataOutputStream dos, IValue<?> value) throws IOException {
        if (value instanceof NoneValue) {
            dos.writeByte(AriaBinaryFormat.CONST_NONE);
        } else if (value instanceof NumberValue nv) {
            dos.writeByte(AriaBinaryFormat.CONST_NUMBER);
            dos.writeDouble(nv.numberValue());
        } else if (value instanceof BooleanValue bv) {
            dos.writeByte(AriaBinaryFormat.CONST_BOOLEAN);
            dos.writeBoolean(bv.booleanValue());
        } else if (value instanceof StringValue sv) {
            dos.writeByte(AriaBinaryFormat.CONST_STRING);
            dos.writeUTF(sv.stringValue());
        } else {
            dos.writeByte(AriaBinaryFormat.CONST_NONE);
        }
    }

    private static void writeSubProgram(DataOutputStream dos, IRProgram sub) throws IOException {
        dos.writeUTF(sub.getName());
        dos.writeInt(sub.getRegisterCount());
        
        IValue<?>[] constants = sub.getConstants();
        dos.writeInt(constants != null ? constants.length : 0);
        if (constants != null) {
            for (IValue<?> c : constants) writeConstant(dos, c);
        }

        // Variable keys
        VariableKey[] keys = sub.getVariableKeys();
        dos.writeInt(keys != null ? keys.length : 0);
        if (keys != null) {
            for (VariableKey key : keys) dos.writeUTF(key.getName());
        }
        
        IRInstruction[] code = sub.getInstructions();
        dos.writeInt(code != null ? code.length : 0);
        if (code != null) {
            for (IRInstruction inst : code) {
                dos.writeShort(inst.opcode.ordinal());
                dos.writeInt(inst.dst);
                dos.writeInt(inst.a);
                dos.writeInt(inst.b);
                dos.writeInt(inst.c);
                dos.writeUTF(inst.name != null ? inst.name : "");
            }
        }
        
        IRProgram[] subs = sub.getSubPrograms();
        dos.writeInt(subs != null ? subs.length : 0);
        if (subs != null) {
            for (IRProgram s : subs) writeSubProgram(dos, s);
        }
    }
}
