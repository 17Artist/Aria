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

public class ForInStmt extends ASTNode {
    private final List<String> variables;
    private final ASTNode iterable;
    private final ASTNode body;

    public ForInStmt(SourceLocation location, List<String> variables, ASTNode iterable, ASTNode body) {
        super(location);
        this.variables = variables;
        this.iterable = iterable;
        this.body = body;
    }

    public List<String> getVariables() { return variables; }
    public ASTNode getIterable() { return iterable; }
    public ASTNode getBody() { return body; }
}
