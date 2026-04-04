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
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexFunctions {

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("regex", "match", RegexFunctions::match);
        manager.registerStaticFunction("regex", "matchAll", RegexFunctions::matchAll);

        manager.registerStaticFunction("regex", "test", RegexFunctions::test);

        manager.registerStaticFunction("regex", "replace", RegexFunctions::replace);

        manager.registerStaticFunction("regex", "replaceFirst", RegexFunctions::replaceFirst);

        manager.registerStaticFunction("regex", "split", RegexFunctions::split);
    }

    public static IValue<?> match(InvocationData data) throws AriaException {
        String pattern = data.get(0).stringValue();
        String str = data.get(1).stringValue();
        try {
            Matcher m = Pattern.compile(pattern).matcher(str);
            if (m.find()) {
                List<IValue<?>> groups = new ArrayList<>();
                for (int i = 0; i <= m.groupCount(); i++) {
                    String g = m.group(i);
                    groups.add(g != null ? new StringValue(g) : NoneValue.NONE);
                }
                return new ListValue(groups);
            }
            return NoneValue.NONE;
        } catch (Exception e) {
            throw new AriaRuntimeException("Invalid regex: " + e.getMessage());
        }
    }

    public static IValue<?> matchAll(InvocationData data) throws AriaException {
        String pattern = data.get(0).stringValue();
        String str = data.get(1).stringValue();
        try {
            Matcher m = Pattern.compile(pattern).matcher(str);
            List<IValue<?>> results = new ArrayList<>();
            while (m.find()) {
                List<IValue<?>> groups = new ArrayList<>();
                for (int i = 0; i <= m.groupCount(); i++) {
                    String g = m.group(i);
                    groups.add(g != null ? new StringValue(g) : NoneValue.NONE);
                }
                results.add(new ListValue(groups));
            }
            return new ListValue(results);
        } catch (Exception e) {
            throw new AriaRuntimeException("Invalid regex: " + e.getMessage());
        }
    }

    public static IValue<?> test(InvocationData data) throws AriaException {
        String pattern = data.get(0).stringValue();
        String str = data.get(1).stringValue();
        try {
            return BooleanValue.of(Pattern.compile(pattern).matcher(str).find());
        } catch (Exception e) {
            throw new AriaRuntimeException("Invalid regex: " + e.getMessage());
        }
    }

    public static IValue<?> replace(InvocationData data) throws AriaException {
        String pattern = data.get(0).stringValue();
        String str = data.get(1).stringValue();
        String replacement = data.get(2).stringValue();
        try {
            return new StringValue(str.replaceAll(pattern, replacement));
        } catch (Exception e) {
            throw new AriaRuntimeException("Invalid regex: " + e.getMessage());
        }
    }

    public static IValue<?> replaceFirst(InvocationData data) throws AriaException {
        String pattern = data.get(0).stringValue();
        String str = data.get(1).stringValue();
        String replacement = data.get(2).stringValue();
        try {
            return new StringValue(str.replaceFirst(pattern, replacement));
        } catch (Exception e) {
            throw new AriaRuntimeException("Invalid regex: " + e.getMessage());
        }
    }

    public static IValue<?> split(InvocationData data) throws AriaException {
        String pattern = data.get(0).stringValue();
        String str = data.get(1).stringValue();
        try {
            String[] parts = str.split(pattern, -1);
            List<IValue<?>> result = new ArrayList<>(parts.length);
            for (String part : parts) result.add(new StringValue(part));
            return new ListValue(result);
        } catch (Exception e) {
            throw new AriaRuntimeException("Invalid regex: " + e.getMessage());
        }
    }
}
