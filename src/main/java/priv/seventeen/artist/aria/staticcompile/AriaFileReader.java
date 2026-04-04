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
import priv.seventeen.artist.aria.compiler.ir.IROpCode;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.parser.SourceLocation;
import priv.seventeen.artist.aria.value.*;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

public class AriaFileReader {

    public static IRProgram read(Path input) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(input.toFile())))) {
            // Magic
            byte[] magic = new byte[4];
            dis.readFully(magic);
            if (magic[0] != 'A' || magic[1] != 'R' || magic[2] != 0 || magic[3] != 1) {
                throw new IOException("Invalid Aria file format");
            }
            
            short flags = dis.readShort();
            String name = dis.readUTF();
            int registerCount = dis.readInt();
            
            // Constants
            int constCount = dis.readInt();
            IValue<?>[] constants = new IValue<?>[constCount];
            for (int i = 0; i < constCount; i++) {
                constants[i] = readConstant(dis);
            }
            
            // Variable keys
            int keyCount = dis.readInt();
            VariableKey[] keys = new VariableKey[keyCount];
            for (int i = 0; i < keyCount; i++) {
                keys[i] = VariableKey.of(dis.readUTF());
            }
            
            // Instructions
            int instCount = dis.readInt();
            IROpCode[] opcodes = IROpCode.values();
            IRInstruction[] code = new IRInstruction[instCount];
            for (int i = 0; i < instCount; i++) {
                int ordinal = dis.readShort();
                IRInstruction inst = new IRInstruction(opcodes[ordinal]);
                inst.dst = dis.readInt();
                inst.a = dis.readInt();
                inst.b = dis.readInt();
                inst.c = dis.readInt();
                String n = dis.readUTF();
                inst.name = n.isEmpty() ? null : n;
                code[i] = inst;
            }
            
            // Sub programs
            int subCount = dis.readInt();
            IRProgram[] subs = new IRProgram[subCount];
            for (int i = 0; i < subCount; i++) {
                subs[i] = readSubProgram(dis);
            }
            
            // Source map
            SourceLocation[] sourceMap = null;
            if ((flags & AriaBinaryFormat.FLAG_HAS_SOURCE_MAP) != 0) {
                int mapCount = dis.readInt();
                sourceMap = new SourceLocation[mapCount];
                for (int i = 0; i < mapCount; i++) {
                    sourceMap[i] = new SourceLocation(dis.readInt(), dis.readInt());
                }
            }
            
            IRProgram program = new IRProgram(name);
            program.setConstants(constants);
            program.setVariableKeys(keys);
            program.setInstructions(code);
            program.setRegisterCount(registerCount);
            program.setSubPrograms(subs);
            if (sourceMap != null) program.setSourceMap(sourceMap);
            return program;
        }
    }

    private static IValue<?> readConstant(DataInputStream dis) throws IOException {
        byte type = dis.readByte();
        return switch (type) {
            case AriaBinaryFormat.CONST_NUMBER -> new NumberValue(dis.readDouble());
            case AriaBinaryFormat.CONST_BOOLEAN -> BooleanValue.of(dis.readBoolean());
            case AriaBinaryFormat.CONST_STRING -> new StringValue(dis.readUTF());
            default -> NoneValue.NONE;
        };
    }

    private static IRProgram readSubProgram(DataInputStream dis) throws IOException {
        String name = dis.readUTF();
        int registerCount = dis.readInt();
        
        int constCount = dis.readInt();
        IValue<?>[] constants = new IValue<?>[constCount];
        for (int i = 0; i < constCount; i++) constants[i] = readConstant(dis);

        // Variable keys
        int keyCount = dis.readInt();
        VariableKey[] keys = new VariableKey[keyCount];
        for (int i = 0; i < keyCount; i++) keys[i] = VariableKey.of(dis.readUTF());
        
        IROpCode[] opcodes = IROpCode.values();
        int instCount = dis.readInt();
        IRInstruction[] code = new IRInstruction[instCount];
        for (int i = 0; i < instCount; i++) {
            IRInstruction inst = new IRInstruction(opcodes[dis.readShort()]);
            inst.dst = dis.readInt();
            inst.a = dis.readInt();
            inst.b = dis.readInt();
            inst.c = dis.readInt();
            String n = dis.readUTF();
            inst.name = n.isEmpty() ? null : n;
            code[i] = inst;
        }
        
        int subCount = dis.readInt();
        IRProgram[] subs = new IRProgram[subCount];
        for (int i = 0; i < subCount; i++) subs[i] = readSubProgram(dis);
        
        IRProgram program = new IRProgram(name);
        program.setConstants(constants);
        program.setVariableKeys(keys);
        program.setInstructions(code);
        program.setRegisterCount(registerCount);
        program.setSubPrograms(subs);
        return program;
    }
}
