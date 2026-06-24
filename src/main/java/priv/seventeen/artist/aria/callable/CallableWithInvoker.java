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

import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;

public class CallableWithInvoker {
    private final ICallable callable;
    private final Object invoker;

    public CallableWithInvoker(ICallable callable, Object invoker) {
        this.callable = callable;
        this.invoker = invoker;
    }

    public ICallable getCallable() { return callable; }
    public Object getInvoker() { return invoker; }

    public IValue<?> invoke(Context context, IValue<?>[] args) throws AriaException {
        return callable.invoke(new InvocationData(context, invoker, args));
    }
}
