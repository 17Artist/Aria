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

package priv.seventeen.artist.aria.annotation;

import priv.seventeen.artist.aria.value.IValue;

public record AriaAnnotation(String name, IValue<?>[] args) {
    public AriaAnnotation(String name) {
        this(name, new IValue<?>[0]);
    }

    public boolean hasArgs() { return args != null && args.length > 0; }

    public IValue<?> getArg(int index) {
        if (args != null && index >= 0 && index < args.length) return args[index];
        return null;
    }
}
