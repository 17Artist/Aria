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
import priv.seventeen.artist.aria.runtime.ThreadPoolManager;
import priv.seventeen.artist.aria.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PromiseFunctions {

    public static void register(CallableManager manager) {
        // Promise.resolve(x) → 已解析的 Promise
        manager.registerStaticFunction("Promise", "resolve", data ->
                PromiseValue.resolved(data.argCount() > 0 ? data.get(0) : NoneValue.NONE));

        // Promise.reject(reason) → 已拒绝的 Promise
        manager.registerStaticFunction("Promise", "reject", data ->
                PromiseValue.rejected(data.argCount() > 0 ? data.get(0) : NoneValue.NONE));

        // Promise.all([p1, p2, ...]) → 所有解析则解析为列表，任一拒绝则整体拒绝
        manager.registerStaticFunction("Promise", "all", data -> {
            IValue<?> arg = data.get(0);
            if (!(arg instanceof ListValue lv)) {
                return PromiseValue.rejected(new StringValue("Promise.all: expected list", true));
            }
            List<IValue<?>> items = lv.jvmValue();
            CompletableFuture<?>[] futures = new CompletableFuture<?>[items.size()];
            for (int i = 0; i < items.size(); i++) {
                IValue<?> item = items.get(i);
                if (item instanceof PromiseValue pv) {
                    futures[i] = pv.getFuture();
                } else {
                    futures[i] = CompletableFuture.completedFuture(item);
                }
            }
            CompletableFuture<IValue<?>> combined = CompletableFuture.allOf(futures).thenApply(v -> {
                List<IValue<?>> results = new ArrayList<>(futures.length);
                for (CompletableFuture<?> f : futures) {
                    try { results.add((IValue<?>) f.get()); }
                    catch (Exception e) { results.add(NoneValue.NONE); }
                }
                return new ListValue(results);
            });
            return new PromiseValue(combined);
        });

        // promise.then(fn) → 返回链式新 Promise：fn 接收解析值，返回值作为新 Promise 的解析值
        manager.registerObjectFunction(PromiseValue.class, "then", PromiseFunctions::promiseThen);

        // promise.catch(fn) — 注意 catch 是关键字，从脚本端用 .catch 可能有解析问题，保险起见也注册 catchErr
        manager.registerObjectFunction(PromiseValue.class, "catchErr", PromiseFunctions::promiseCatch);
        manager.registerObjectFunction(PromiseValue.class, "catch", PromiseFunctions::promiseCatch);

        // promise.await() — 同步阻塞获取（显式方法调用，与 await 关键字等价）
        manager.registerObjectFunction(PromiseValue.class, "await", data -> {
            PromiseValue p = (PromiseValue) data.get(0);
            return p.awaitValue();
        });
    }

    /** 把内建 ICallable（有 then/catch 各种调用）的单参回调包装为可从 CompletableFuture 调用的函数。 */
    private static IValue<?> invokeCallback(FunctionValue fn, IValue<?> arg) {
        try {
            return fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[]{ arg }));
        } catch (priv.seventeen.artist.aria.exception.AriaException e) {
            throw new RuntimeException(e);
        }
    }

    private static IValue<?> promiseThen(InvocationData data) {
        PromiseValue p = (PromiseValue) data.get(0);
        if (!(data.get(1) instanceof FunctionValue fn)) {
            return PromiseValue.rejected(new StringValue("then: callback must be a function", true));
        }
        // 在 Promise 解析后，在线程池上调用回调；回调若返回 Promise 则平铺为链
        CompletableFuture<IValue<?>> next = p.getFuture().thenApplyAsync(v -> {
            IValue<?> r = invokeCallback(fn, v);
            if (r instanceof PromiseValue rp) {
                // 平铺：这里先阻塞等待内部 Promise（简化实现；mod 脚本够用）
                return rp.awaitValue();
            }
            return r != null ? r : NoneValue.NONE;
        }, ThreadPoolManager.INSTANCE.executor());
        return new PromiseValue(next);
    }

    private static IValue<?> promiseCatch(InvocationData data) {
        PromiseValue p = (PromiseValue) data.get(0);
        if (!(data.get(1) instanceof FunctionValue fn)) {
            return p;
        }
        CompletableFuture<IValue<?>> next = p.getFuture().exceptionally(throwable -> {
            Throwable cause = throwable;
            if (cause instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null) {
                cause = ce.getCause();
            }
            IValue<?> reason;
            if (cause instanceof PromiseValue.PromiseRejection pr) {
                reason = pr.getReason();
            } else {
                reason = new StringValue(cause.getMessage() != null ? cause.getMessage() : "error", true);
            }
            IValue<?> r = invokeCallback(fn, reason);
            return r != null ? r : NoneValue.NONE;
        });
        return new PromiseValue(next);
    }
}
