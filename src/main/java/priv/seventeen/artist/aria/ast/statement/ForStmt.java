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

public class ForStmt extends ASTNode {
    private final ASTNode init;       // nullable
    private final ASTNode condition;  // nullable
    private final ASTNode update;     // nullable
    private final ASTNode body;

    public ForStmt(SourceLocation location, ASTNode init, ASTNode condition, ASTNode update, ASTNode body) {
        super(location);
        this.init = init;
        this.condition = condition;
        this.update = update;
        this.body = body;
    }

    public ASTNode getInit() { return init; }
    public ASTNode getCondition() { return condition; }
    public ASTNode getUpdate() { return update; }
    public ASTNode getBody() { return body; }
}
