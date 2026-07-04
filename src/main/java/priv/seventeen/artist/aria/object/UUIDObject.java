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

package priv.seventeen.artist.aria.object;

import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler;
import priv.seventeen.artist.aria.callable.InvocationData;

import java.util.UUID;

public class UUIDObject implements IAriaObject {

    private final UUID uuid;

    public UUIDObject() {
        this.uuid = UUID.randomUUID();
    }

    // Shimmer 对齐(builtins-static-7):解析失败回退 randomUUID(不抛异常)。
    public UUIDObject(String str) {
        UUID temp;
        try {
            temp = UUID.fromString(str);
        } catch (Exception e) {
            temp = UUID.randomUUID();
        }
        this.uuid = temp;
    }

    public UUID getUuid() { return uuid; }

    // Shimmer 对齐(builtins-object-12):getTypeName="uuid"(小写)。
    @Override public String getTypeName() { return "uuid"; }
    @Override public String stringValue() { return uuid.toString(); }

    // Shimmer 对齐(builtins-object-11):四个实例方法,返回值同 Shimmer(long/int → number)。
    @AriaInvokeHandler("getMostSignificantBits")
    public long getMostSignificantBits(InvocationData data) {
        return uuid.getMostSignificantBits();
    }

    @AriaInvokeHandler("getLeastSignificantBits")
    public long getLeastSignificantBits(InvocationData data) {
        return uuid.getLeastSignificantBits();
    }

    @AriaInvokeHandler("version")
    public int version(InvocationData data) {
        return uuid.version();
    }

    @AriaInvokeHandler("variant")
    public int variant(InvocationData data) {
        return uuid.variant();
    }

    @Override
    public String toString() { return uuid.toString(); }
}
