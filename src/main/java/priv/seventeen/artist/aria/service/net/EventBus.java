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

package priv.seventeen.artist.aria.service.net;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.CallableWithInvoker;
import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.FunctionValue;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.StoreOnlyValue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {

    private static final Map<String, List<ICallable>> listeners = new ConcurrentHashMap<>();

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("event", "on", EventBus::on);
        manager.registerStaticFunction("event", "emit", EventBus::emit);
        manager.registerStaticFunction("event", "off", EventBus::off);
    }

    public static IValue<?> on(InvocationData data) {
        String event = data.get(0).stringValue();
        IValue<?> handler = data.get(1);
        if (handler instanceof StoreOnlyValue<?> sv && sv.jvmValue() instanceof CallableWithInvoker cwi) {
            listeners.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>())
                .add(cwi.getCallable());
        } else if (handler instanceof FunctionValue fv) {
            listeners.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>())
                .add(fv.getCallable());
        }
        return NoneValue.NONE;
    }

    public static IValue<?> emit(InvocationData data) throws AriaException {
        String event = data.get(0).stringValue();
        IValue<?>[] args = new IValue<?>[data.argCount() - 1];
        for (int i = 1; i < data.argCount(); i++) args[i - 1] = data.get(i);

        List<ICallable> handlers = listeners.get(event);
        if (handlers != null) {
            for (ICallable handler : handlers) {
                handler.invoke(new InvocationData(data.getContext(), null, args));
            }
        }
        return NoneValue.NONE;
    }

    public static IValue<?> off(InvocationData data) {
        String event = data.get(0).stringValue();
        listeners.remove(event);
        return NoneValue.NONE;
    }
}
