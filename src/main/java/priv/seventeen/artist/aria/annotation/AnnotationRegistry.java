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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;


public class AnnotationRegistry {

    public record AnnotatedTarget(
            AriaAnnotation annotation,
            TargetKind kind,
            String name,           // 类名/函数名/字段名
            String className,      // 所属类名（字段/方法时有值）
            IValue<?> value        // 关联的值（FunctionValue/AriaClassValue 等），可能为 null
    ) {
        public enum TargetKind { CLASS, METHOD, FIELD, FUNCTION }
    }

    // 所有已注册的注解目标
    private final List<AnnotatedTarget> targets = Collections.synchronizedList(new ArrayList<>());


    private final Map<String, List<AnnotatedTarget>> byName = new ConcurrentHashMap<>();


    private final Map<String, List<BiConsumer<AriaAnnotation, AnnotatedTarget>>> handlers = new ConcurrentHashMap<>();

    public void onAnnotation(String name, BiConsumer<AriaAnnotation, AnnotatedTarget> handler) {
        handlers.computeIfAbsent(name, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
    }

    public void register(AnnotatedTarget target) {
        targets.add(target);
        byName.computeIfAbsent(target.annotation().name(), k -> Collections.synchronizedList(new ArrayList<>())).add(target);

        // 触发已注册的处理器
        List<BiConsumer<AriaAnnotation, AnnotatedTarget>> list = handlers.get(target.annotation().name());
        if (list != null) {
            for (BiConsumer<AriaAnnotation, AnnotatedTarget> handler : list) {
                handler.accept(target.annotation(), target);
            }
        }
    }

    public List<AnnotatedTarget> findByAnnotation(String name) {
        return byName.getOrDefault(name, Collections.emptyList());
    }

    public List<AnnotatedTarget> findClassesByAnnotation(String name) {
        return findByAnnotation(name).stream()
                .filter(t -> t.kind() == AnnotatedTarget.TargetKind.CLASS)
                .toList();
    }

    public List<AnnotatedTarget> findFunctionsByAnnotation(String name) {
        return findByAnnotation(name).stream()
                .filter(t -> t.kind() == AnnotatedTarget.TargetKind.FUNCTION || t.kind() == AnnotatedTarget.TargetKind.METHOD)
                .toList();
    }

    public List<AnnotatedTarget> getAll() {
        return Collections.unmodifiableList(targets);
    }

    public void clear() {
        targets.clear();
        byName.clear();
    }
}
