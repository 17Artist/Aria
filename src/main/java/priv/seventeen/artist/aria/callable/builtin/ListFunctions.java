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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListFunctions {

    public static void register(CallableManager manager) {
        manager.registerObjectFunction(ListValue.class, "add", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            list.add(d.get(1));
            return NoneValue.NONE;
        });
        manager.registerObjectFunction(ListValue.class, "remove", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            int idx = (int) d.get(1).numberValue();
            return (idx >= 0 && idx < list.size()) ? list.remove(idx) : NoneValue.NONE;
        });
        manager.registerObjectFunction(ListValue.class, "size", d ->
                new NumberValue(((ListValue) d.get(0)).jvmValue().size()));
        manager.registerObjectFunction(ListValue.class, "get", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            int idx = (int) d.get(1).numberValue();
            return (idx >= 0 && idx < list.size()) ? list.get(idx) : NoneValue.NONE;
        });
        manager.registerObjectFunction(ListValue.class, "set", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            int idx = (int) d.get(1).numberValue();
            if (idx >= 0 && idx < list.size()) list.set(idx, d.get(2));
            return NoneValue.NONE;
        });
        manager.registerObjectFunction(ListValue.class, "contains", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            String target = d.get(1).stringValue();
            for (IValue<?> item : list) {
                if (item.stringValue().equals(target)) return BooleanValue.TRUE;
            }
            return BooleanValue.FALSE;
        });
        manager.registerObjectFunction(ListValue.class, "indexOf", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            String target = d.get(1).stringValue();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).stringValue().equals(target)) return new NumberValue(i);
            }
            return new NumberValue(-1);
        });
        manager.registerObjectFunction(ListValue.class, "sort", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            list.sort((a, b) -> Double.compare(a.numberValue(), b.numberValue()));
            return NoneValue.NONE;
        });
        manager.registerObjectFunction(ListValue.class, "reverse", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            Collections.reverse(list);
            return NoneValue.NONE;
        });
        manager.registerObjectFunction(ListValue.class, "clear", d -> {
            ((ListValue) d.get(0)).jvmValue().clear();
            return NoneValue.NONE;
        });
        manager.registerObjectFunction(ListValue.class, "isEmpty", d ->
                BooleanValue.of(((ListValue) d.get(0)).jvmValue().isEmpty()));
        manager.registerObjectFunction(ListValue.class, "subList", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            int from = (int) d.get(1).numberValue();
            int to = d.argCount() > 2 ? (int) d.get(2).numberValue() : list.size();
            return new ListValue(new ArrayList<>(list.subList(from, to)));
        });
        manager.registerObjectFunction(ListValue.class, "lastIndexOf", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            String target = d.get(1).stringValue();
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).stringValue().equals(target)) return new NumberValue(i);
            }
            return new NumberValue(-1);
        });
        manager.registerObjectFunction(ListValue.class, "addAll", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            List<IValue<?>> other = ((ListValue) d.get(1)).jvmValue();
            list.addAll(other);
            return NoneValue.NONE;
        });
        manager.registerObjectFunction(ListValue.class, "removeAll", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            List<IValue<?>> other = ((ListValue) d.get(1)).jvmValue();
            list.removeAll(other);
            return NoneValue.NONE;
        });


        // list.map(fn) → 对每个元素调用 fn，返回新列表
        manager.registerObjectFunction(ListValue.class, "map", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            List<IValue<?>> result = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                result.add(fn.getCallable().invoke(new InvocationData(null, null,
                        new IValue<?>[]{ list.get(i), new NumberValue(i) })));
            }
            return new ListValue(result);
        });

        // list.filter(fn) → 保留 fn 返回 true 的元素
        manager.registerObjectFunction(ListValue.class, "filter", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            List<IValue<?>> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                IValue<?> keep = fn.getCallable().invoke(new InvocationData(null, null,
                        new IValue<?>[]{ list.get(i), new NumberValue(i) }));
                if (keep.booleanValue()) result.add(list.get(i));
            }
            return new ListValue(result);
        });

        // list.reduce(fn, initial) → 累积计算
        manager.registerObjectFunction(ListValue.class, "reduce", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            IValue<?> acc = d.argCount() > 2 ? d.get(2) : (list.isEmpty() ? NoneValue.NONE : list.get(0));
            int start = d.argCount() > 2 ? 0 : 1;
            for (int i = start; i < list.size(); i++) {
                acc = fn.getCallable().invoke(new InvocationData(null, null,
                        new IValue<?>[]{ acc, list.get(i), new NumberValue(i) }));
            }
            return acc;
        });

        // list.forEach(fn) → 对每个元素调用 fn，无返回值
        manager.registerObjectFunction(ListValue.class, "forEach", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            for (int i = 0; i < list.size(); i++) {
                fn.getCallable().invoke(new InvocationData(null, null,
                        new IValue<?>[]{ list.get(i), new NumberValue(i) }));
            }
            return NoneValue.NONE;
        });

        // list.sortBy(fn) → 按 fn 返回值排序
        manager.registerObjectFunction(ListValue.class, "sortBy", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            list.sort((a, b) -> {
                try {
                    double va = fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[]{ a })).numberValue();
                    double vb = fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[]{ b })).numberValue();
                    return Double.compare(va, vb);
                } catch (Exception e) { return 0; }
            });
            return NoneValue.NONE;
        });

        // list.find(fn) → 返回第一个 fn 返回 true 的元素
        manager.registerObjectFunction(ListValue.class, "find", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            for (IValue<?> item : list) {
                IValue<?> result = fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[]{ item }));
                if (result.booleanValue()) return item;
            }
            return NoneValue.NONE;
        });

        // list.findIndex(fn) → 返回第一个 fn 返回 true 的索引
        manager.registerObjectFunction(ListValue.class, "findIndex", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            for (int i = 0; i < list.size(); i++) {
                IValue<?> result = fn.getCallable().invoke(new InvocationData(null, null,
                        new IValue<?>[]{ list.get(i), new NumberValue(i) }));
                if (result.booleanValue()) return new NumberValue(i);
            }
            return new NumberValue(-1);
        });

        // list.every(fn) → 所有元素都满足 fn
        manager.registerObjectFunction(ListValue.class, "every", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            for (IValue<?> item : list) {
                if (!fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[]{ item })).booleanValue())
                    return BooleanValue.FALSE;
            }
            return BooleanValue.TRUE;
        });

        // list.some(fn) → 至少一个元素满足 fn
        manager.registerObjectFunction(ListValue.class, "some", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            for (IValue<?> item : list) {
                if (fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[]{ item })).booleanValue())
                    return BooleanValue.TRUE;
            }
            return BooleanValue.FALSE;
        });

        // list.flatMap(fn) → map + flatten
        manager.registerObjectFunction(ListValue.class, "flatMap", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            List<IValue<?>> result = new ArrayList<>();
            for (IValue<?> item : list) {
                IValue<?> mapped = fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[]{ item }));
                if (mapped instanceof ListValue lv) {
                    result.addAll(lv.jvmValue());
                } else {
                    result.add(mapped);
                }
            }
            return new ListValue(result);
        });

        // list.join(separator) → 拼接为字符串
        manager.registerObjectFunction(ListValue.class, "join", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            String sep = d.argCount() > 1 ? d.get(1).stringValue() : ",";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(sep);
                sb.append(list.get(i).stringValue());
            }
            return new StringValue(sb.toString());
        });
    }
}
