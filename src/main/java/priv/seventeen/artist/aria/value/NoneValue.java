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

public final class NoneValue extends IValue<Object> {

    public static final NoneValue NONE = new NoneValue();

    /**
     * for-in 迭代结束哨兵(Shimmer 对齐 controlflow-09)：与 {@link #NONE} 身份不同的第二个 none 实例。
     * 只在 GET_INDEX(forIn) → JUMP_IF_NONE(forIn) 之间流转，不进入脚本可见值域——
     * 解释器 for-in 判终用身份比较(== ITER_END)，含 none 元素的列表因此可完整遍历；
     * 仍是 NoneValue 实例，JIT 生成码的 INSTANCEOF NoneValue 判终不受影响(不会死循环)。
     */
    public static final NoneValue ITER_END = new NoneValue();

    private NoneValue() {}

    @Override public Object jvmValue() { return null; }
    @Override public double numberValue() { return 0; }
    // Shimmer 对齐：none 字符串化为空串（避免 HUD/拼接冒出字面 "none"）。
    @Override public String stringValue() { return ""; }
    @Override public boolean booleanValue() { return false; }
    @Override public int typeID() { return 0; }
    @Override public boolean canMath() { return true; }
    @Override public boolean isBaseType() { return false; }

    @Override
    protected IValue<?> addValue(IValue<?> other) { return other; }

    @Override
    protected IValue<?> subValue(IValue<?> other) { return other; }

    @Override
    public String typeName() { return "none"; }

    @Override
    public String toString() { return "none"; }
}
