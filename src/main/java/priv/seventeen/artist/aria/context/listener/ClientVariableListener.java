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

package priv.seventeen.artist.aria.context.listener;

import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.value.IValue;

/**
 * {@code client} 命名空间变量的监听：懒加载初值 + 写入回调，均携带被访问的变量键。
 *
 * <p>{@link #onVariableCreate(VariableKey)} 有默认实现（返回 {@code null} 表示不提供初值），故本接口仍是
 * 单抽象方法（{@link #onVariableChange}）的函数式接口，可用 lambda 只实现写入回调。
 */
@FunctionalInterface
public interface ClientVariableListener {

    /**
     * 脚本首次访问某 {@code client} 变量时回调，用于懒加载初值（如从磁盘/配置读取）。默认不提供初值。
     *
     * @param key 被首次访问的变量键
     * @return 初值；返回 {@code null} 表示无初值（沿用默认 {@code NONE}）
     */
    default IValue<?> onVariableCreate(VariableKey key) {
        return null;
    }

    /**
     * 脚本写入某 {@code client} 变量时回调（如持久化）。
     *
     * @param key      被写入的变量键
     * @param newValue 新值
     */
    void onVariableChange(VariableKey key, IValue<?> newValue);
}
