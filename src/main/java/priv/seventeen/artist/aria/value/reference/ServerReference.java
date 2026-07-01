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
import priv.seventeen.artist.aria.context.listener.ServerVariableListener;
import priv.seventeen.artist.aria.value.IValue;

/**
 * {@code server} 命名空间变量引用：脚本端只读，宿主经 {@link #forceSetValue} 推送值。
 *
 * <p>每次读取都回调 {@link ServerVariableListener#onVariableGet(VariableKey)} 并携带本变量的键：
 * 回调返回非 {@code null} 即用其值（监听器即数据源）；返回 {@code null}（或无监听器）则返回已存储值
 * （宿主推送模型：宿主异步刷新后 {@code forceSetValue} 写回）。无监听器时读取不再 NPE。
 */
public final class ServerReference implements IReference {

    private final VariableKey key;
    private IValue<?> value;
    private final ServerVariableListener listener;

    public ServerReference(VariableKey key, IValue<?> value, ServerVariableListener listener) {
        this.key = key;
        this.value = value;
        this.listener = listener;
    }

    @Override
    public IValue<?> getValue() {
        if (listener != null) {
            IValue<?> provided = listener.onVariableGet(key);
            if (provided != null) {
                return provided;
            }
        }
        return value;
    }

    @Override
    public IValue<?> setValue(IValue<?> value) {
        // server 变量脚本端只读：忽略脚本写入（与原语义一致）
        return value;
    }

    @Override
    public IValue<?> forceSetValue(IValue<?> value) {
        // 宿主推送：写回已知值，供 onVariableGet 返回 null 时读取
        this.value = value;
        return value;
    }
}
