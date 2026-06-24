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

/**
 * val 不可变存储引用。脚本端只能初始化一次——重赋的拦截由解释器 STORE_VAL 处理器据 {@link #isAssigned()}
 * 抛错完成(本类 setValue 不抛受检异常);Java 宿主端 forceSetValue 始终可覆盖(host 强制赋值)。
 */
public final class ValueReference implements IReference {
    private IValue<?> value;
    private boolean assigned;

    public ValueReference(IValue<?> value) {
        this.value = value;
    }

    /** 是否已被赋过值(脚本初始化或宿主 forceSet)。用于 val 不可变性判定。 */
    public boolean isAssigned() { return assigned; }

    @Override
    public IValue<?> getValue() {
        return value;
    }

    @Override
    public IValue<?> setValue(IValue<?> value) {
        this.value = value;
        this.assigned = true;
        return value;
    }

    @Override
    public IValue<?> forceSetValue(IValue<?> value) {
        this.value = value;
        this.assigned = true;
        return value;
    }
}
