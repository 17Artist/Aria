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

package priv.seventeen.artist.aria.classystem;

import priv.seventeen.artist.aria.annotation.AriaAnnotation;
import priv.seventeen.artist.aria.ast.ASTNode;

import java.util.Collections;
import java.util.List;

public class AriaField {
    private final String name;
    private final boolean mutable;  // var = true, val = false
    private final ASTNode defaultValue;  // nullable
    private final List<AriaAnnotation> annotations;

    public AriaField(String name, boolean mutable, ASTNode defaultValue, List<AriaAnnotation> annotations) {
        this.name = name;
        this.mutable = mutable;
        this.defaultValue = defaultValue;
        this.annotations = annotations != null ? annotations : Collections.emptyList();
    }

    public String getName() { return name; }
    public boolean isMutable() { return mutable; }
    public ASTNode getDefaultValue() { return defaultValue; }
    public List<AriaAnnotation> getAnnotations() { return annotations; }
    public boolean hasAnnotation(String name) { return annotations.stream().anyMatch(a -> a.name().equals(name)); }
}
