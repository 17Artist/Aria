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

import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

/**
 * Variable.Normal.NONE 共享单例的只读引用。
 * Shimmer 对齐(gui-chain-3):对照 Shimmer ValueReference.setValue——返回入参但不存储(no-op),
 * 使 IAriaObject 缺省 getVariable/getElement 返回的 NONE 变量无法被赋值污染全局。
 */
public final class ImmutableNoneReference implements IReference {

    @Override
    public IValue<?> getValue() {
        return NoneValue.NONE;
    }

    @Override
    public IValue<?> setValue(IValue<?> value) {
        return value; // no-op:不存储
    }

    @Override
    public IValue<?> forceSetValue(IValue<?> value) {
        return value; // 共享单例,宿主也不允许写入
    }
}
