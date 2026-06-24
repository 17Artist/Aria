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

public class SwitchStmt extends ASTNode {
    private final ASTNode condition;
    private final List<CaseStmt> cases;
    private final ASTNode elseBlock;  // nullable
    private final boolean fallthrough; // true=switch穿透, false=match自动跳出

    public SwitchStmt(SourceLocation location, ASTNode condition, List<CaseStmt> cases, ASTNode elseBlock) {
        this(location, condition, cases, elseBlock, true); // 默认 Aria 穿透语义
    }

    public SwitchStmt(SourceLocation location, ASTNode condition, List<CaseStmt> cases, ASTNode elseBlock, boolean fallthrough) {
        super(location);
        this.condition = condition;
        this.cases = cases;
        this.elseBlock = elseBlock;
        this.fallthrough = fallthrough;
    }

    public ASTNode getCondition() { return condition; }
    public List<CaseStmt> getCases() { return cases; }
    public ASTNode getElseBlock() { return elseBlock; }
    public boolean isFallthrough() { return fallthrough; }
}
