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

package priv.seventeen.artist.aria.object;

public class RangeObject implements IAriaObject {
    private final double start;
    private final double end;
    private final double step;

    // Shimmer 对齐(builtins-static-2/9, controlflow-01/02):默认 step 恒为 +1(不再对 start>end 自动 -1),
    // start>end 时迭代/包含自然为空。显式第 3 参 step 为 Aria 扩展,保留。
    public RangeObject(double start, double end) {
        this(start, end, 1);
    }

    public RangeObject(double start, double end, double step) {
        this.start = start;
        this.end = end;
        this.step = step;
    }

    public double getStart() { return start; }
    public double getEnd() { return end; }
    public double getStep() { return step; }

    // Shimmer 对齐(operators-6):双端闭 [start, end](负 step 的 Aria 扩展同样双端闭)。
    public boolean contains(double value) {
        if (step > 0) return value >= start && value <= end;
        return value <= start && value >= end;
    }

    // Shimmer 对齐(interop-11/builtins-object-12):getTypeName="range"、
    // stringValue="[range:1.0-3.0]" 格式、numberValue=0(接口默认)。
    @Override public String getTypeName() { return "range"; }
    @Override public String stringValue() { return "[range:" + start + "-" + end + "]"; }
    @Override public boolean canMath() { return false; }
}
