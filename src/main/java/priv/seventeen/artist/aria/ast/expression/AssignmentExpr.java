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

public class AssignmentExpr extends ASTNode {
    private final ASTNode target;
    private final ASTNode value;
    private final AssignOp operator;

    public AssignmentExpr(SourceLocation location, ASTNode target, AssignOp operator, ASTNode value) {
        super(location);
        this.target = target;
        this.operator = operator;
        this.value = value;
    }

    public ASTNode getTarget() { return target; }
    public ASTNode getValue() { return value; }
    public AssignOp getOperator() { return operator; }

    public enum AssignOp {
        ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN,
        INIT_OR_GET,
        BIT_AND_ASSIGN, BIT_OR_ASSIGN, BIT_XOR_ASSIGN, SHL_ASSIGN, SHR_ASSIGN, USHR_ASSIGN
    }
}
