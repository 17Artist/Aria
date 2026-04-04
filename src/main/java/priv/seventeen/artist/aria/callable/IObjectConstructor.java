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

package priv.seventeen.artist.aria.callable;

import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.ObjectValue;

public interface IObjectConstructor<T extends IAriaObject> extends ICallable {
    T constructor(InvocationData data) throws AriaException;

    @Override
    default IValue<?> invoke(InvocationData data) throws AriaException {
        return new ObjectValue<>(constructor(data));
    }
}
