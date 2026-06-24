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

package priv.seventeen.artist.aria.callable.builtin;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.*;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class SchedulerFunctions {

    private static final ScheduledExecutorService executor =
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "aria-scheduler");
            t.setDaemon(true);
            return t;
        });
    private static final AtomicLong taskIdCounter = new AtomicLong(0);
    private static final Map<Long, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("scheduler", "delay", SchedulerFunctions::delay);
        manager.registerStaticFunction("scheduler", "interval", SchedulerFunctions::interval);
        manager.registerStaticFunction("scheduler", "cancel", SchedulerFunctions::cancel);
        manager.registerStaticFunction("scheduler", "cancelAll", d -> { cancelAll(); return NoneValue.NONE; });
    }

    public static IValue<?> delay(InvocationData data) throws AriaException {
        long millis = (long) data.get(0).numberValue();
        FunctionValue fn = (FunctionValue) data.get(1);
        long id = taskIdCounter.incrementAndGet();
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[0]));
            } catch (Exception e) {
                System.err.println("[Aria] Scheduler error: " + e.getMessage());
            }
        }, millis, TimeUnit.MILLISECONDS);
        tasks.put(id, future);
        return new NumberValue(id);
    }

    public static IValue<?> interval(InvocationData data) throws AriaException {
        long millis = (long) data.get(0).numberValue();
        FunctionValue fn = (FunctionValue) data.get(1);
        long id = taskIdCounter.incrementAndGet();
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            try {
                fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[0]));
            } catch (Exception e) {
                System.err.println("[Aria] Scheduler error: " + e.getMessage());
            }
        }, millis, millis, TimeUnit.MILLISECONDS);
        tasks.put(id, future);
        return new NumberValue(id);
    }

    public static IValue<?> cancel(InvocationData data) throws AriaException {
        long id = (long) data.get(0).numberValue();
        ScheduledFuture<?> future = tasks.remove(id);
        if (future != null) {
            future.cancel(false);
            return BooleanValue.TRUE;
        }
        return BooleanValue.FALSE;
    }

    public static void cancelAll() {
        tasks.values().forEach(f -> f.cancel(false));
        tasks.clear();
    }
}
