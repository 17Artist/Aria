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
import priv.seventeen.artist.aria.value.*;

import java.util.ArrayList;
import java.util.List;

public class StringFunctions {

    public static void register(CallableManager manager) {
        manager.registerObjectFunction(StringValue.class, "length", d -> {
            String s = d.get(0).stringValue();
            return new NumberValue(s.length());
        });
        manager.registerObjectFunction(StringValue.class, "substring", d -> {
            String s = d.get(0).stringValue();
            int start = (int) d.get(1).numberValue();
            int end = d.argCount() > 2 ? (int) d.get(2).numberValue() : s.length();
            return new StringValue(s.substring(start, end));
        });
        manager.registerObjectFunction(StringValue.class, "replace", d -> {
            String s = d.get(0).stringValue();
            String target = d.get(1).stringValue();
            String replacement = d.get(2).stringValue();
            return new StringValue(s.replace(target, replacement));
        });
        manager.registerObjectFunction(StringValue.class, "split", d -> {
            String s = d.get(0).stringValue();
            String delimiter = d.get(1).stringValue();
            String[] parts = s.split(delimiter, -1);
            List<IValue<?>> list = new ArrayList<>(parts.length);
            for (String part : parts) list.add(new StringValue(part));
            return new ListValue(list);
        });
        manager.registerObjectFunction(StringValue.class, "trim", d ->
                new StringValue(d.get(0).stringValue().trim()));
        manager.registerObjectFunction(StringValue.class, "startsWith", d ->
                BooleanValue.of(d.get(0).stringValue().startsWith(d.get(1).stringValue())));
        manager.registerObjectFunction(StringValue.class, "endsWith", d ->
                BooleanValue.of(d.get(0).stringValue().endsWith(d.get(1).stringValue())));
        manager.registerObjectFunction(StringValue.class, "contains", d ->
                BooleanValue.of(d.get(0).stringValue().contains(d.get(1).stringValue())));
        manager.registerObjectFunction(StringValue.class, "indexOf", d ->
                new NumberValue(d.get(0).stringValue().indexOf(d.get(1).stringValue())));
        manager.registerObjectFunction(StringValue.class, "toUpperCase", d ->
                new StringValue(d.get(0).stringValue().toUpperCase()));
        manager.registerObjectFunction(StringValue.class, "toLowerCase", d ->
                new StringValue(d.get(0).stringValue().toLowerCase()));
        manager.registerObjectFunction(StringValue.class, "charAt", d -> {
            String s = d.get(0).stringValue();
            int idx = (int) d.get(1).numberValue();
            return new StringValue(String.valueOf(s.charAt(idx)));
        });
        manager.registerObjectFunction(StringValue.class, "equals", d ->
                BooleanValue.of(d.get(0).stringValue().equals(d.get(1).stringValue())));
        manager.registerObjectFunction(StringValue.class, "equalsIgnoreCase", d ->
                BooleanValue.of(d.get(0).stringValue().equalsIgnoreCase(d.get(1).stringValue())));
        manager.registerObjectFunction(StringValue.class, "replaceAll", d -> {
            String s = d.get(0).stringValue();
            String regex = d.get(1).stringValue();
            String replacement = d.get(2).stringValue();
            return new StringValue(s.replaceAll(regex, replacement));
        });
        manager.registerObjectFunction(StringValue.class, "replaceFirst", d -> {
            String s = d.get(0).stringValue();
            String regex = d.get(1).stringValue();
            String replacement = d.get(2).stringValue();
            return new StringValue(s.replaceFirst(regex, replacement));
        });
        manager.registerObjectFunction(StringValue.class, "lastIndexOf", d ->
                new NumberValue(d.get(0).stringValue().lastIndexOf(d.get(1).stringValue())));
        manager.registerObjectFunction(StringValue.class, "isEmpty", d ->
                BooleanValue.of(d.get(0).stringValue().isEmpty()));
        manager.registerObjectFunction(StringValue.class, "repeat", data -> {
            String str = data.get(0).stringValue();
            int count = (int) data.get(1).numberValue();
            return new StringValue(str.repeat(Math.max(0, count)));
        });
    }
}
