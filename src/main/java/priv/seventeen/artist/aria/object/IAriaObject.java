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

import priv.seventeen.artist.aria.value.Variable;

public interface IAriaObject {
    String getTypeName();
    // Shimmer 对齐(operators-10/interop-7/gui-chain-6/7, IShimmerObject 默认值):
    // numberValue=0 / stringValue="" / booleanValue=false。
    default double numberValue() { return 0; }
    default String stringValue() { return ""; }
    default boolean booleanValue() { return false; }
    default boolean canMath() { return false; }
    default Variable getVariable(String name) { return Variable.Normal.NONE; }
    default Variable getElement(String name) { return Variable.Normal.NONE; }
}
