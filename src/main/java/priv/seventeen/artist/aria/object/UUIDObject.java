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

import java.util.UUID;

public class UUIDObject implements IAriaObject {

    private final UUID uuid;

    public UUIDObject() {
        this.uuid = UUID.randomUUID();
    }

    public UUIDObject(String str) {
        this.uuid = UUID.fromString(str);
    }

    public UUID getUuid() { return uuid; }

    @Override public String getTypeName() { return "UUID"; }
    @Override public String stringValue() { return uuid.toString(); }

    @Override
    public String toString() { return uuid.toString(); }
}
