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
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.MapValue;
import priv.seventeen.artist.aria.value.StringValue;

public class TemplateFunctions {

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("template", "render", TemplateFunctions::render);
    }

    public static IValue<?> render(InvocationData data) throws AriaException {
        String template = data.get(0).stringValue();
        IValue<?> dataArg = data.get(1);

        if (!(dataArg instanceof MapValue mv)) {
            return new StringValue(template);
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < template.length() && template.charAt(i + 1) != '{') {
                // 查找闭合 }
                int end = template.indexOf('}', i + 1);
                if (end > i) {
                    String key = template.substring(i + 1, end).trim();
                    IValue<?> val = mv.jvmValue().get(new StringValue(key));
                    result.append(val != null ? val.stringValue() : "");
                    i = end + 1;
                    continue;
                }
            }
            if (c == '{' && i + 1 < template.length() && template.charAt(i + 1) == '{') {
                // 转义 {{ → {
                result.append('{');
                i += 2;
                continue;
            }
            if (c == '}' && i + 1 < template.length() && template.charAt(i + 1) == '}') {
                // 转义 }} → }
                result.append('}');
                i += 2;
                continue;
            }
            result.append(c);
            i++;
        }
        return new StringValue(result.toString());
    }
}
