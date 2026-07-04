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
import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A5(内建函数矩阵)：逐函数对照 Shimmer TextObjectFunction 对齐(名字/参数个数/返回/边界)。
 * 约定：d.get(0)=self(字符串值,前插)，用户参数从 d.get(1) 起；
 * Shimmer 的 data.size()==N 对应这里 d.argCount()==N+1。
 * 元数不符时的回退值逐一照抄 Shimmer：布尔类 false、索引类 -1、replace/substring 返回原串、split 空列表。
 */
public class StringFunctions {

    /** Shimmer 对齐：handler 内 Java 异常(substring/charAt 越界、非法正则等)→ 脚本级运行时错误。 */
    private static void reg(CallableManager manager, String name, ICallable body) {
        manager.registerObjectFunction(StringValue.class, name, d -> {
            try {
                return body.invoke(d);
            } catch (AriaException e) {
                throw e;
            } catch (Exception e) {
                throw new AriaRuntimeException("对象函数调用失败: string." + name + " — " + e.getMessage());
            }
        });
    }

    public static void register(CallableManager manager) {
        reg(manager, "length", d ->
                new NumberValue(d.get(0).stringValue().length()));
        // Shimmer 对齐：substring(from) / substring(from,to)；其它元数返回原串。越界照 Java 抛。
        reg(manager, "substring", d -> {
            String s = d.get(0).stringValue();
            if (d.argCount() == 2) {
                return new StringValue(s.substring((int) d.get(1).numberValue()));
            }
            if (d.argCount() == 3) {
                return new StringValue(s.substring((int) d.get(1).numberValue(), (int) d.get(2).numberValue()));
            }
            return new StringValue(s);
        });
        // Shimmer 对齐(builtins-object-8)：replace 仅双参才替换；单参(或其它元数)返回原串(不是删除)。
        reg(manager, "replace", d -> {
            String s = d.get(0).stringValue();
            if (d.argCount() == 3) {
                return new StringValue(s.replace(d.get(1).stringValue(), d.get(2).stringValue()));
            }
            return new StringValue(s);
        });
        // Shimmer 对齐(builtins-object-5)：split(regex) 用 Java 默认 limit=0(丢尾部空串)；
        // split(regex,limit) 支持双参；其它元数返回空列表。
        reg(manager, "split", d -> {
            String s = d.get(0).stringValue();
            String[] parts;
            if (d.argCount() == 2) {
                parts = s.split(d.get(1).stringValue());
            } else if (d.argCount() == 3) {
                parts = s.split(d.get(1).stringValue(), (int) d.get(2).numberValue());
            } else {
                return new ListValue(new ArrayList<>());
            }
            List<IValue<?>> list = new ArrayList<>(parts.length);
            for (String part : parts) list.add(new StringValue(part));
            return new ListValue(list);
        });
        reg(manager, "trim", d ->
                new StringValue(d.get(0).stringValue().trim()));
        // Shimmer 对齐(builtins-object-8)：startsWith(prefix) / startsWith(prefix,toffset)；其它元数 false。
        reg(manager, "startsWith", d -> {
            String s = d.get(0).stringValue();
            if (d.argCount() == 2) {
                return BooleanValue.of(s.startsWith(d.get(1).stringValue()));
            }
            if (d.argCount() == 3) {
                return BooleanValue.of(s.startsWith(d.get(1).stringValue(), (int) d.get(2).numberValue()));
            }
            return BooleanValue.FALSE;
        });
        reg(manager, "endsWith", d -> {
            if (d.argCount() != 2) return BooleanValue.FALSE;
            return BooleanValue.of(d.get(0).stringValue().endsWith(d.get(1).stringValue()));
        });
        reg(manager, "contains", d -> {
            if (d.argCount() != 2) return BooleanValue.FALSE;
            return BooleanValue.of(d.get(0).stringValue().contains(d.get(1).stringValue()));
        });
        // Shimmer 对齐(builtins-object-8)：indexOf(s) / indexOf(s,fromIndex)；其它元数 -1。
        reg(manager, "indexOf", d -> {
            String s = d.get(0).stringValue();
            if (d.argCount() == 2) {
                return new NumberValue(s.indexOf(d.get(1).stringValue()));
            }
            if (d.argCount() == 3) {
                return new NumberValue(s.indexOf(d.get(1).stringValue(), (int) d.get(2).numberValue()));
            }
            return new NumberValue(-1);
        });
        reg(manager, "lastIndexOf", d -> {
            String s = d.get(0).stringValue();
            if (d.argCount() == 2) {
                return new NumberValue(s.lastIndexOf(d.get(1).stringValue()));
            }
            if (d.argCount() == 3) {
                return new NumberValue(s.lastIndexOf(d.get(1).stringValue(), (int) d.get(2).numberValue()));
            }
            return new NumberValue(-1);
        });
        // Shimmer 对齐(builtins-object-8)：toUpper/LowerCase 用 Locale.ROOT。
        reg(manager, "toUpperCase", d ->
                new StringValue(d.get(0).stringValue().toUpperCase(Locale.ROOT)));
        reg(manager, "toLowerCase", d ->
                new StringValue(d.get(0).stringValue().toLowerCase(Locale.ROOT)));
        reg(manager, "charAt", d -> {
            String s = d.get(0).stringValue();
            int idx = (int) d.get(1).numberValue();
            return new StringValue(String.valueOf(s.charAt(idx)));
        });
        reg(manager, "equals", d -> {
            if (d.argCount() != 2) return BooleanValue.FALSE;
            return BooleanValue.of(d.get(0).stringValue().equals(d.get(1).stringValue()));
        });
        reg(manager, "equalsIgnoreCase", d -> {
            if (d.argCount() != 2) return BooleanValue.FALSE;
            return BooleanValue.of(d.get(0).stringValue().equalsIgnoreCase(d.get(1).stringValue()));
        });
        // Shimmer 对齐：replaceAll/replaceFirst 仅双参才替换；其它元数返回原串(与 replace 同模式)。
        reg(manager, "replaceAll", d -> {
            String s = d.get(0).stringValue();
            if (d.argCount() == 3) {
                return new StringValue(s.replaceAll(d.get(1).stringValue(), d.get(2).stringValue()));
            }
            return new StringValue(s);
        });
        reg(manager, "replaceFirst", d -> {
            String s = d.get(0).stringValue();
            if (d.argCount() == 3) {
                return new StringValue(s.replaceFirst(d.get(1).stringValue(), d.get(2).stringValue()));
            }
            return new StringValue(s);
        });
        reg(manager, "isEmpty", d ->
                BooleanValue.of(d.get(0).stringValue().isEmpty()));

        // ---- 以下为 Aria 自由区扩展(Shimmer 无对应方法) ----
        reg(manager, "repeat", data -> {
            String str = data.get(0).stringValue();
            int count = (int) data.get(1).numberValue();
            return new StringValue(str.repeat(Math.max(0, count)));
        });
    }
}
