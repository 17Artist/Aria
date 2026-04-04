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

public class IfStmt extends ASTNode {
    private final ASTNode condition;
    private final ASTNode thenBlock;
    private final List<IfStmt> elifBlocks;
    private final ASTNode elseBlock;  // nullable

    public IfStmt(SourceLocation location, ASTNode condition, ASTNode thenBlock,
                  List<IfStmt> elifBlocks, ASTNode elseBlock) {
        super(location);
        this.condition = condition;
        this.thenBlock = thenBlock;
        this.elifBlocks = elifBlocks;
        this.elseBlock = elseBlock;
    }

    public ASTNode getCondition() { return condition; }
    public ASTNode getThenBlock() { return thenBlock; }
    public List<IfStmt> getElifBlocks() { return elifBlocks; }
    public ASTNode getElseBlock() { return elseBlock; }
}
