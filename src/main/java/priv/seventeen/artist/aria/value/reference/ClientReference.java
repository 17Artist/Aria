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

package priv.seventeen.artist.aria.value.reference;

import priv.seventeen.artist.aria.context.listener.ClientVariableListener;
import priv.seventeen.artist.aria.value.IValue;

public final class ClientReference implements IReference {
    private IValue<?> value;
    private final ClientVariableListener listener;

    public ClientReference(IValue<?> value, ClientVariableListener listener) {
        this.value = value;
        this.listener = listener;
    }

    @Override
    public IValue<?> getValue() {
        return value;
    }

    @Override
    public IValue<?> setValue(IValue<?> value) {
        this.value = value;
        listener.onVariableChange(value);
        return value;
    }

    @Override
    public IValue<?> forceSetValue(IValue<?> value) {
        return setValue(value);
    }
}
