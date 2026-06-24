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

package priv.seventeen.artist.aria.service.serial;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BinarySerializer {

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("serial", "encode", BinarySerializer::encode);
        manager.registerStaticFunction("serial", "decode", BinarySerializer::decode);
    }

    public static IValue<?> encode(InvocationData data) throws AriaException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            writeValue(dos, data.get(0));
            dos.flush();
            return new StoreOnlyValue<>(baos.toByteArray());
        } catch (IOException e) {
            throw new AriaRuntimeException("serial.encode error: " + e.getMessage());
        }
    }

    public static IValue<?> decode(InvocationData data) throws AriaException {
        try {
            IValue<?> arg = data.get(0);
            byte[] bytes;
            if (arg instanceof StoreOnlyValue<?> sv && sv.jvmValue() instanceof byte[] b) {
                bytes = b;
            } else {
                throw new AriaRuntimeException("serial.decode expects byte array");
            }
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
            return readValue(dis);
        } catch (IOException e) {
            throw new AriaRuntimeException("serial.decode error: " + e.getMessage());
        }
    }

    private static void writeValue(DataOutputStream dos, IValue<?> value) throws IOException {
        dos.writeByte(value.typeID());
        switch (value.typeID()) {
            case 0 -> {} // none
            case 1 -> dos.writeDouble(value.numberValue());
            case 2 -> dos.writeBoolean(value.booleanValue());
            case 3 -> dos.writeUTF(value.stringValue());
            case 11 -> {
                ListValue lv = (ListValue) value;
                dos.writeInt(lv.jvmValue().size());
                for (IValue<?> item : lv.jvmValue()) writeValue(dos, item);
            }
            case 12 -> {
                MapValue mv = (MapValue) value;
                dos.writeInt(mv.jvmValue().size());
                for (Map.Entry<IValue<?>, IValue<?>> e : mv.jvmValue().entrySet()) {
                    writeValue(dos, e.getKey());
                    writeValue(dos, e.getValue());
                }
            }
            default -> dos.writeUTF(value.stringValue());
        }
    }

    private static IValue<?> readValue(DataInputStream dis) throws IOException {
        int typeID = dis.readByte();
        return switch (typeID) {
            case 0 -> NoneValue.NONE;
            case 1 -> new NumberValue(dis.readDouble());
            case 2 -> BooleanValue.of(dis.readBoolean());
            case 3 -> new StringValue(dis.readUTF());
            case 11 -> {
                int size = dis.readInt();
                List<IValue<?>> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) list.add(readValue(dis));
                yield new ListValue(list);
            }
            case 12 -> {
                int size = dis.readInt();
                LinkedHashMap<IValue<?>, IValue<?>> map = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) map.put(readValue(dis), readValue(dis));
                yield new MapValue(map);
            }
            default -> new StringValue(dis.readUTF());
        };
    }
}
