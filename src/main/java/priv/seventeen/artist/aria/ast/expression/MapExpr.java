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

package priv.seventeen.artist.aria.ast.expression;

import priv.seventeen.artist.aria.ast.ASTNode;
import priv.seventeen.artist.aria.parser.SourceLocation;

import java.util.List;

public class MapExpr extends ASTNode {
    private final List<MapEntry> entries;

    public MapExpr(SourceLocation location, List<MapEntry> entries) {
        super(location);
        this.entries = entries;
    }

    public List<MapEntry> getEntries() { return entries; }

    public record MapEntry(ASTNode key, ASTNode value) {}
}
