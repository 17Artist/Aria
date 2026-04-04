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

    public RangeObject(double start, double end) {
        this(start, end, start <= end ? 1 : -1);
    }

    public RangeObject(double start, double end, double step) {
        this.start = start;
        this.end = end;
        this.step = step;
    }

    public double getStart() { return start; }
    public double getEnd() { return end; }
    public double getStep() { return step; }

    public boolean contains(double value) {
        if (step > 0) return value >= start && value < end;
        return value <= start && value > end;
    }

    @Override public String getTypeName() { return "Range"; }
    @Override public double numberValue() { return end - start; }
    @Override public String stringValue() { return "Range(" + start + ", " + end + ")"; }
    @Override public boolean canMath() { return false; }
}
