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
 * val 存储引用。Shimmer 对齐(variables-8, Shimmer ValueReference)：{@link #setValue}(脚本路径)
 * 是 no-op——返回入参但不存储，脚本对 val 的一切写入静默失效；只有 Java 宿主端
 * {@link #forceSetValue} 能真正写入(host 注入控件句柄等)。
 */
public final class ValueReference implements IReference {
    private IValue<?> value;
    private boolean assigned;

    public ValueReference(IValue<?> value) {
        this.value = value;
    }

    /** 是否已被宿主 forceSet 过值。 */
    public boolean isAssigned() { return assigned; }

    @Override
    public IValue<?> getValue() {
        return value;
    }

    @Override
    public IValue<?> setValue(IValue<?> value) {
        // Shimmer 对齐(variables-8)：脚本写 val 静默忽略(返回入参不存储)。
        return value;
    }

    @Override
    public IValue<?> forceSetValue(IValue<?> value) {
        this.value = value;
        this.assigned = true;
        return value;
    }
}
