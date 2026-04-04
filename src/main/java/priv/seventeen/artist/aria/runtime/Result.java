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

package priv.seventeen.artist.aria.runtime;

import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

public class Result {
    private final IValue<?> value;
    private final ControlType type;

    private Result(IValue<?> value, ControlType type) {
        this.value = value;
        this.type = type;
    }

    public static Result noneResult(IValue<?> value) { return new Result(value, ControlType.NONE); }
    public static Result returnResult(IValue<?> value) { return new Result(value, ControlType.RETURN); }
    public static Result breakResult() { return new Result(NoneValue.NONE, ControlType.BREAK); }
    public static Result nextResult() { return new Result(NoneValue.NONE, ControlType.NEXT); }

    public IValue<?> getValue() { return value; }
    public ControlType getType() { return type; }
    public boolean isNone() { return type == ControlType.NONE; }
    public boolean isReturn() { return type == ControlType.RETURN; }
    public boolean isBreak() { return type == ControlType.BREAK; }
    public boolean isNext() { return type == ControlType.NEXT; }
}
