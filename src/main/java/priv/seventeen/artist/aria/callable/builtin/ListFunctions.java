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
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A5(内建函数矩阵)：逐函数对照 Shimmer ListObjectFunction 对齐(名字/参数个数/返回/边界,bug-for-bug)。
 * 约定：d.get(0)=self(ListValue,非 ObjectValue 接收者前插)，用户参数从 d.get(1) 起；
 * Shimmer 的 data.size()==N 对应这里 d.argCount()==N+1。
 * 元素相等语义：Shimmer 靠 IValue.equals→eq 桥接(跨类型数值相等)使 List.remove/contains/indexOf 生效；
 * Aria 的 IValue.equals 是 Java 严格等(同型比值)——这里选择在函数内手写逐元素 eq 循环(方案 b)，
 * 不给 IValue 家族全局改 equals/hashCode(方案 a 会改变 MapValue 键行为并波及全部集合语义)。
 */
public class ListFunctions {

    /**
     * Shimmer 对齐：对象函数 handler 内的 Java 异常(负索引 IndexOutOfBounds 等)在 Shimmer 被
     * CallableManager 包成 ShimmerRuntimeException("对象函数调用失败")→脚本级错误。这里统一包成
     * AriaRuntimeException——同时保证 JIT 路径不会把原生异常当成编译码缺陷触发 deopt(jit-10)。
     */
    private static void reg(CallableManager manager, String name, ICallable body) {
        manager.registerObjectFunction(ListValue.class, name, d -> {
            try {
                return body.invoke(d);
            } catch (AriaException e) {
                throw e;
            } catch (Exception e) {
                throw new AriaRuntimeException("对象函数调用失败: list." + name + " — " + e.getMessage());
            }
        });
    }

    /** Shimmer IData.listValue() 语义：list → 其底层列表；其余值(含 none) → List.of(自身) 单元素。 */
    private static List<IValue<?>> asShimmerList(IValue<?> v) {
        if (v instanceof ListValue lv) return lv.jvmValue();
        return List.of(v);
    }

    /** Shimmer 相等语义(builtins-object-4)：query.equals(元素) → query.eq(元素)，跨类型数值相等。 */
    private static boolean eqContains(List<IValue<?>> list, IValue<?> query) {
        for (IValue<?> item : list) {
            if (query.eq(item).booleanValue()) return true;
        }
        return false;
    }

    /**
     * Shimmer containsAll 走 HashSet(哈希桶先比 hashCode 再 equals)——跨类型 eq 相等但 hash 不同的
     * 元素查不到(如 1 与 "1")，bug-for-bug 需带 hash 门。Shimmer 各 IValue 的 hashCode：
     * Number→Double.hashCode、String→字符串 hash、Boolean→Boolean.hashCode(与 Aria 同)；
     * List/Map→内容 hash(Aria 的 List/MapValue 是身份 hash，这里递归复刻内容 hash)。
     */
    private static boolean hashEqContains(List<IValue<?>> list, IValue<?> query) {
        int h = shimmerHash(query);
        for (IValue<?> item : list) {
            if (shimmerHash(item) == h && query.eq(item).booleanValue()) return true;
        }
        return false;
    }

    private static int shimmerHash(IValue<?> v) {
        if (v instanceof ListValue lv) {
            int h = 1;
            for (IValue<?> e : lv.jvmValue()) h = 31 * h + shimmerHash(e);
            return h;
        }
        if (v instanceof MapValue mv) {
            int h = 0;
            for (Map.Entry<IValue<?>, IValue<?>> en : mv.jvmValue().entrySet()) {
                h += shimmerHash(en.getKey()) ^ shimmerHash(en.getValue());
            }
            return h;
        }
        return v.hashCode();
    }

    public static void register(CallableManager manager) {
        // Shimmer 对齐(builtins-object-3)：add(e) 追加、add(i,e) 在 i 处插入；返回 void→none；
        // 其它元数 no-op。插入越界(负/大于 size)照 Java 抛(bug-for-bug)。
        reg(manager, "add", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            if (d.argCount() == 2) {
                list.add(d.get(1));
            } else if (d.argCount() == 3) {
                int idx = (int) d.get(1).numberValue();
                if (idx < 0 || idx > list.size()) {
                    throw new AriaRuntimeException("对象函数调用失败: list.add 插入索引越界 " + idx);
                }
                list.add(idx, d.get(2));
            }
            return NoneValue.NONE;
        });
        // Shimmer 对齐(builtins-object-1/interop-5)：remove(value) 按值删除(eq 语义)返回 boolean；
        // 按索引删除是 removeIndex。
        reg(manager, "remove", d -> {
            if (d.argCount() != 2) return BooleanValue.FALSE;
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            IValue<?> target = d.get(1);
            for (int i = 0; i < list.size(); i++) {
                if (target.eq(list.get(i)).booleanValue()) {
                    list.remove(i);
                    return BooleanValue.TRUE;
                }
            }
            return BooleanValue.FALSE;
        });
        // Shimmer 对齐(builtins-object-2)：removeIndex(i) 按索引删，上越界返回 none、
        // 负索引照 Java 抛(bug-for-bug)，成功返回被删元素。
        reg(manager, "removeIndex", d -> {
            if (d.argCount() != 2) return NoneValue.NONE;
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            int idx = (int) d.get(1).numberValue();
            if (idx >= list.size()) return NoneValue.NONE;
            if (idx < 0) {
                throw new AriaRuntimeException("对象函数调用失败: list.removeIndex 负索引 " + idx);
            }
            return list.remove(idx);
        });
        reg(manager, "size", d ->
                new NumberValue(((ListValue) d.get(0)).jvmValue().size()));
        // Shimmer 对齐(builtins-object-9)：get(i) 上越界返回 none、负索引照 Java 抛；其它元数 none。
        reg(manager, "get", d -> {
            if (d.argCount() != 2) return NoneValue.NONE;
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            int idx = (int) d.get(1).numberValue();
            if (idx >= list.size()) return NoneValue.NONE;
            if (idx < 0) {
                throw new AriaRuntimeException("对象函数调用失败: list.get 负索引 " + idx);
            }
            return list.get(idx);
        });
        // Shimmer 对齐(builtins-object-9)：set(i,v) 成功返回被替换的旧元素、i>=size 返回 none、
        // 负索引照 Java 抛；其它元数 none。
        reg(manager, "set", d -> {
            if (d.argCount() != 3) return NoneValue.NONE;
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            int idx = (int) d.get(1).numberValue();
            if (idx >= list.size()) return NoneValue.NONE;
            if (idx < 0) {
                throw new AriaRuntimeException("对象函数调用失败: list.set 负索引 " + idx);
            }
            return list.set(idx, d.get(2));
        });
        // Shimmer 对齐(builtins-object-4)：contains/indexOf/lastIndexOf 走 eq 语义(跨类型数值相等，
        // [1,2,3].contains("2")=true)；元数不符分别返回 false/-1。
        reg(manager, "contains", d -> {
            if (d.argCount() != 2) return BooleanValue.FALSE;
            return BooleanValue.of(eqContains(((ListValue) d.get(0)).jvmValue(), d.get(1)));
        });
        reg(manager, "indexOf", d -> {
            if (d.argCount() != 2) return new NumberValue(-1);
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            IValue<?> target = d.get(1);
            for (int i = 0; i < list.size(); i++) {
                if (target.eq(list.get(i)).booleanValue()) return new NumberValue(i);
            }
            return new NumberValue(-1);
        });
        reg(manager, "lastIndexOf", d -> {
            if (d.argCount() != 2) return new NumberValue(-1);
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            IValue<?> target = d.get(1);
            for (int i = list.size() - 1; i >= 0; i--) {
                if (target.eq(list.get(i)).booleanValue()) return new NumberValue(i);
            }
            return new NumberValue(-1);
        });
        reg(manager, "sort", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            list.sort((a, b) -> Double.compare(a.numberValue(), b.numberValue()));
            return NoneValue.NONE;
        });
        reg(manager, "reverse", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            Collections.reverse(list);
            return NoneValue.NONE;
        });
        reg(manager, "clear", d -> {
            ((ListValue) d.get(0)).jvmValue().clear();
            return NoneValue.NONE;
        });
        reg(manager, "isEmpty", d ->
                BooleanValue.of(((ListValue) d.get(0)).jvmValue().isEmpty()));
        // 内部辅助(编译器 rest 解构 var.[a, ...rest] 专用；名字含 '#'，脚本方法调用语法不可达)：
        // 取 [from, size) 剩余元素——不受下方 subList 的 Shimmer 边界(bug-for-bug none)影响。
        reg(manager, "#rest", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            int from = (int) d.get(1).numberValue();
            if (from >= list.size()) return new ListValue(new ArrayList<>());
            return new ListValue(new ArrayList<>(list.subList(Math.max(0, from), list.size())));
        });
        // Shimmer 对齐(builtins-object-9)：subList(a,b) 仅双参；a>=size 或 b>=size 返回 none
        // (subList(0,size) 也是 none，bug-for-bug)；负索引/a>b 照 Java 抛；单参形态删除(Shimmer 无)。
        reg(manager, "subList", d -> {
            if (d.argCount() != 3) return NoneValue.NONE;
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            int from = (int) d.get(1).numberValue();
            int to = (int) d.get(2).numberValue();
            if (from >= list.size() || to >= list.size()) return NoneValue.NONE;
            if (from < 0 || to < from) {
                throw new AriaRuntimeException("对象函数调用失败: list.subList 非法区间 [" + from + ", " + to + ")");
            }
            return new ListValue(new ArrayList<>(list.subList(from, to)));
        });
        // Shimmer 对齐(builtins-object-7)：addAll/removeAll 返回 boolean；参数非 list 走
        // IData.listValue() 语义(单元素包装)，不强转 CCE。
        reg(manager, "addAll", d -> {
            if (d.argCount() != 2) return BooleanValue.FALSE;
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            return BooleanValue.of(list.addAll(asShimmerList(d.get(1))));
        });
        reg(manager, "removeAll", d -> {
            if (d.argCount() != 2) return BooleanValue.FALSE;
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            List<IValue<?>> other = asShimmerList(d.get(1));
            boolean modified = false;
            for (Iterator<IValue<?>> it = list.iterator(); it.hasNext(); ) {
                IValue<?> e = it.next();
                if (eqContains(other, e)) {
                    it.remove();
                    modified = true;
                }
            }
            return BooleanValue.of(modified);
        });
        // Shimmer 对齐(builtins-object-7)：containsAll/retainAll 新增，返回 boolean。
        // containsAll 复刻 Shimmer 的 HashSet 查找(hash 门 + eq)；retainAll 是 List.contains 纯 eq。
        reg(manager, "containsAll", d -> {
            if (d.argCount() != 2) return BooleanValue.FALSE;
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            for (IValue<?> x : asShimmerList(d.get(1))) {
                if (!hashEqContains(list, x)) return BooleanValue.FALSE;
            }
            return BooleanValue.TRUE;
        });
        reg(manager, "retainAll", d -> {
            if (d.argCount() != 2) return BooleanValue.FALSE;
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            List<IValue<?>> other = asShimmerList(d.get(1));
            boolean modified = false;
            for (Iterator<IValue<?>> it = list.iterator(); it.hasNext(); ) {
                IValue<?> e = it.next();
                if (!eqContains(other, e)) {
                    it.remove();
                    modified = true;
                }
            }
            return BooleanValue.of(modified);
        });


        // ---- 以下为 Aria 自由区扩展(Shimmer 无对应方法，旧脚本不会调用) ----

        // list.map(fn) → 对每个元素调用 fn，返回新列表
        reg(manager, "map", d -> {
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
        reg(manager, "filter", d -> {
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
        reg(manager, "reduce", d -> {
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
        reg(manager, "forEach", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            for (int i = 0; i < list.size(); i++) {
                fn.getCallable().invoke(new InvocationData(null, null,
                        new IValue<?>[]{ list.get(i), new NumberValue(i) }));
            }
            return NoneValue.NONE;
        });

        // list.sortBy(fn) → 按 fn 返回值排序
        reg(manager, "sortBy", d -> {
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
        reg(manager, "find", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            for (IValue<?> item : list) {
                IValue<?> result = fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[]{ item }));
                if (result.booleanValue()) return item;
            }
            return NoneValue.NONE;
        });

        // list.findIndex(fn) → 返回第一个 fn 返回 true 的索引
        reg(manager, "findIndex", d -> {
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
        reg(manager, "every", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            for (IValue<?> item : list) {
                if (!fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[]{ item })).booleanValue())
                    return BooleanValue.FALSE;
            }
            return BooleanValue.TRUE;
        });

        // list.some(fn) → 至少一个元素满足 fn
        reg(manager, "some", d -> {
            List<IValue<?>> list = ((ListValue) d.get(0)).jvmValue();
            FunctionValue fn = (FunctionValue) d.get(1);
            for (IValue<?> item : list) {
                if (fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[]{ item })).booleanValue())
                    return BooleanValue.TRUE;
            }
            return BooleanValue.FALSE;
        });

        // list.flatMap(fn) → map + flatten
        reg(manager, "flatMap", d -> {
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
        reg(manager, "join", d -> {
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
