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

package priv.seventeen.artist.aria.ast.statement;

import priv.seventeen.artist.aria.ast.ASTNode;
import priv.seventeen.artist.aria.parser.SourceLocation;

import java.util.List;

public class DestructureStmt extends ASTNode {
    private final boolean mutable;          // var = true, val = false
    private final List<String> names;
    private final String restName;          // ...rest 的名字，nullable
    private final ASTNode value;
    private final boolean objectPattern;    // true = {a,b} 按名字取键；false = [a,b] 按数字索引

    public DestructureStmt(SourceLocation location, boolean mutable,
                           List<String> names, String restName, ASTNode value) {
        this(location, mutable, names, restName, value, false);
    }

    public DestructureStmt(SourceLocation location, boolean mutable,
                           List<String> names, String restName, ASTNode value,
                           boolean objectPattern) {
        super(location);
        this.mutable = mutable;
        this.names = names;
        this.restName = restName;
        this.value = value;
        this.objectPattern = objectPattern;
    }

    public boolean isMutable() { return mutable; }
    public List<String> getNames() { return names; }
    public String getRestName() { return restName; }
    public ASTNode getValue() { return value; }
    public boolean isObjectPattern() { return objectPattern; }
}
