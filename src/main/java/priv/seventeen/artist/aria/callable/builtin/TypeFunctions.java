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

package priv.seventeen.artist.aria.callable.builtin;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.value.*;

public class TypeFunctions {

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("type", "typeof", TypeFunctions::typeOf);
        manager.registerStaticFunction("type", "isNone", d -> BooleanValue.of(d.get(0) instanceof NoneValue));
        manager.registerStaticFunction("type", "isNumber", d -> BooleanValue.of(d.get(0) instanceof NumberValue));
        manager.registerStaticFunction("type", "isString", d -> BooleanValue.of(d.get(0) instanceof StringValue));
        manager.registerStaticFunction("type", "isList", d -> BooleanValue.of(d.get(0) instanceof ListValue));
        manager.registerStaticFunction("type", "isMap", d -> BooleanValue.of(d.get(0) instanceof MapValue));
        manager.registerStaticFunction("type", "isFunction", d -> BooleanValue.of(d.get(0) instanceof FunctionValue));
        manager.registerStaticFunction("type", "toNumber", d -> new NumberValue(d.get(0).numberValue()));
        manager.registerStaticFunction("type", "toString", d -> new StringValue(d.get(0).stringValue()));
        manager.registerStaticFunction("type", "toBoolean", d -> BooleanValue.of(d.get(0).booleanValue()));
    }

    public static IValue<?> typeOf(InvocationData data) {
        IValue<?> val = data.get(0);
        String typeName = switch (val.typeID()) {
            case 0 -> "none";
            case 1 -> "number";
            case 2 -> "boolean";
            case 3 -> "string";
            case 4 -> "object";
            case 5 -> "store";
            case 6 -> "class";
            case 7 -> "function";
            case 11 -> "list";
            case 12 -> "map";
            default -> "unknown";
        };
        return new StringValue(typeName);
    }
}
