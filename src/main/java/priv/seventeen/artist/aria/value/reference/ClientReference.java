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

import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.context.listener.ClientVariableListener;
import priv.seventeen.artist.aria.value.IValue;

/**
 * {@code client} 命名空间变量引用：首次访问经 {@link ClientVariableListener#onVariableCreate(VariableKey)}
 * 懒加载初值；脚本写入经 {@link ClientVariableListener#onVariableChange(VariableKey, IValue)} 回调（持久化等）。
 *
 * <p>{@link #forceSetValue} 为宿主内部写入：写值并标记已初始化，但<b>不</b>触发 {@code onVariableChange}
 * （避免宿主回写与持久化形成回环）。
 */
public final class ClientReference implements IReference {

    private final VariableKey key;
    private IValue<?> value;
    private boolean initialized;
    private final ClientVariableListener listener;

    public ClientReference(VariableKey key, IValue<?> value, ClientVariableListener listener) {
        this.key = key;
        this.value = value;
        this.listener = listener;
    }

    /** 首次访问懒加载初值（onVariableCreate 返回非 null 时采用）；仅执行一次。 */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (listener != null) {
            IValue<?> initial = listener.onVariableCreate(key);
            if (initial != null) {
                this.value = initial;
            }
        }
    }

    @Override
    public IValue<?> getValue() {
        ensureInitialized();
        return value;
    }

    @Override
    public IValue<?> setValue(IValue<?> value) {
        this.value = value;
        this.initialized = true;
        if (listener != null) {
            listener.onVariableChange(key, value);
        }
        return value;
    }

    @Override
    public IValue<?> forceSetValue(IValue<?> value) {
        // 宿主内部写入：不回调 onVariableChange（避免持久化回环），但视为已初始化
        this.value = value;
        this.initialized = true;
        return value;
    }
}
