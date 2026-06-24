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
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

public class ConsoleFunctions {

    public static void register(CallableManager manager) {
        // print 作为全局函数（无命名空间）
        manager.registerStaticFunction("", "print", ConsoleFunctions::print);
        manager.registerStaticFunction("", "println", ConsoleFunctions::println);

        // console 命名空间
        manager.registerStaticFunction("console", "log", ConsoleFunctions::print);
        manager.registerStaticFunction("console", "error", ConsoleFunctions::error);
        manager.registerStaticFunction("console", "warn", ConsoleFunctions::warn);
    }

    public static IValue<?> print(InvocationData data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.argCount(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(data.get(i).stringValue());
        }
        System.out.print(sb);
        return NoneValue.NONE;
    }

    public static IValue<?> println(InvocationData data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.argCount(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(data.get(i).stringValue());
        }
        System.out.println(sb);
        return NoneValue.NONE;
    }

    public static IValue<?> error(InvocationData data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.argCount(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(data.get(i).stringValue());
        }
        System.err.println(sb);
        return NoneValue.NONE;
    }

    public static IValue<?> warn(InvocationData data) {
        return error(data);
    }
}
