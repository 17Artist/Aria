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
import priv.seventeen.artist.aria.compiler.ir.IRProgram;

import java.util.Collections;
import java.util.List;

public class AriaMethod {
    private final String name;
    private IRProgram body;
    private final List<AriaAnnotation> annotations;

    public AriaMethod(String name, IRProgram body, List<AriaAnnotation> annotations) {
        this.name = name;
        this.body = body;
        this.annotations = annotations != null ? annotations : Collections.emptyList();
    }

    public String getName() { return name; }
    public IRProgram getBody() { return body; }
    public void setBody(IRProgram body) { this.body = body; }
    public List<AriaAnnotation> getAnnotations() { return annotations; }
    public boolean hasAnnotation(String name) { return annotations.stream().anyMatch(a -> a.name().equals(name)); }
}
