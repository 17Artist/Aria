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
 * {@code server} 命名空间变量的读取监听：脚本每次读取 {@code server.xxx} 时回调，携带被读取的变量键。
 *
 * <p>返回值语义：
 * <ul>
 *   <li>返回<b>非 {@code null}</b> → 直接作为本次读取的值（"监听器即数据源"模式，宿主每次实时提供）。</li>
 *   <li>返回 <b>{@code null}</b> → 使用该变量当前已存储的值（宿主推送模型：宿主异步刷新后经
 *       {@link priv.seventeen.artist.aria.value.reference.ServerReference#forceSetValue} 写回，读取时拿到
 *       上次已知值；本回调仅作"被读取"通知，可用于登记异步刷新）。</li>
 * </ul>
 */
@FunctionalInterface
public interface ServerVariableListener {

    /**
     * @param key 被读取的变量键
     * @return 本次读取的值；返回 {@code null} 则回退到该变量已存储的值
     */
    IValue<?> onVariableGet(VariableKey key);
}
