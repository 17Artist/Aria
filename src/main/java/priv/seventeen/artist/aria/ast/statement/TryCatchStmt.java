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

public class TryCatchStmt extends ASTNode {
    private final ASTNode tryBlock;
    private final String catchVar;       // nullable
    private final ASTNode catchBlock;    // nullable
    private final ASTNode finallyBlock;  // nullable

    public TryCatchStmt(SourceLocation location, ASTNode tryBlock,
                        String catchVar, ASTNode catchBlock, ASTNode finallyBlock) {
        super(location);
        this.tryBlock = tryBlock;
        this.catchVar = catchVar;
        this.catchBlock = catchBlock;
        this.finallyBlock = finallyBlock;
    }

    public ASTNode getTryBlock() { return tryBlock; }
    public String getCatchVar() { return catchVar; }
    public ASTNode getCatchBlock() { return catchBlock; }
    public ASTNode getFinallyBlock() { return finallyBlock; }
}
