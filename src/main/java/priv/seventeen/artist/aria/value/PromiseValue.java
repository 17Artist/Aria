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

package priv.seventeen.artist.aria.value;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 基于 CompletableFuture 的 Promise，支持 await（阻塞获取）、then/catch/all 组合。
 *
 * 语义：mod 脚本场景不是单线程事件循环，await 直接阻塞当前线程。
 * 生产者（如 fetch）把实际工作提交到 ThreadPoolManager 得到 CompletableFuture，
 * 包装成 PromiseValue 返回即可。
 */
public final class PromiseValue extends IValue<CompletableFuture<IValue<?>>> {

    private final CompletableFuture<IValue<?>> future;

    public PromiseValue(CompletableFuture<IValue<?>> future) {
        this.future = future;
    }

    /** 已解析的 Promise（Promise.resolve 路径）。 */
    public static PromiseValue resolved(IValue<?> value) {
        return new PromiseValue(CompletableFuture.completedFuture(value != null ? value : NoneValue.NONE));
    }

    /** 已拒绝的 Promise（Promise.reject 路径）。 */
    public static PromiseValue rejected(IValue<?> reason) {
        CompletableFuture<IValue<?>> f = new CompletableFuture<>();
        f.completeExceptionally(new PromiseRejection(reason));
        return new PromiseValue(f);
    }

    public CompletableFuture<IValue<?>> getFuture() { return future; }

    /** 阻塞获取 — await 使用；未完成时挂起当前线程。 */
    public IValue<?> awaitValue() {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PromiseRejection pr) {
                // 被 reject：把 reason 以 RuntimeException 冒泡，Aria 层用 try/catch 捕获
                throw new RuntimeException("Promise rejected: " + pr.getReason().stringValue());
            }
            throw new RuntimeException("Promise failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Await interrupted", e);
        }
    }

    public boolean isDone() { return future.isDone(); }
    public boolean isCompletedExceptionally() { return future.isCompletedExceptionally(); }

    @Override public CompletableFuture<IValue<?>> jvmValue() { return future; }
    @Override public double numberValue() { return 0; }
    @Override public String stringValue() {
        if (!future.isDone()) return "Promise{pending}";
        if (future.isCompletedExceptionally()) return "Promise{rejected}";
        try { return "Promise{resolved: " + future.getNow(NoneValue.NONE).stringValue() + "}"; }
        catch (Throwable t) { return "Promise{?}"; }
    }
    @Override public boolean booleanValue() { return true; }
    @Override public int typeID() { return 15; }
    @Override public boolean canMath() { return false; }
    @Override public boolean isBaseType() { return false; }

    @Override public String toString() { return stringValue(); }

    /** 内部异常类型，用于携带 Aria 侧的 reason 值穿透 CompletableFuture 的 ExecutionException。 */
    public static final class PromiseRejection extends RuntimeException {
        private final IValue<?> reason;
        public PromiseRejection(IValue<?> reason) {
            super(reason != null ? reason.stringValue() : "rejected");
            this.reason = reason != null ? reason : NoneValue.NONE;
        }
        public IValue<?> getReason() { return reason; }
    }
}
