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

public class BinaryExpr extends ASTNode {
    private final ASTNode left;
    private final ASTNode right;
    private final BinaryOp operator;

    public BinaryExpr(SourceLocation location, ASTNode left, BinaryOp operator, ASTNode right) {
        super(location);
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    public ASTNode getLeft() { return left; }
    public ASTNode getRight() { return right; }
    public BinaryOp getOperator() { return operator; }

    public enum BinaryOp {
        ADD, SUB, MUL, DIV, MOD,
        EQ, NE, LT, GT, LE, GE, IN_RANGE,
        AND, OR,
        BIT_AND, BIT_OR, BIT_XOR, SHL, SHR, USHR,
        NULLISH_COALESCE,
        COMMA,          // JS comma operator: evaluate both, return right
        IN_OBJ,         // JS 'in' operator: 'key' in obj
        INSTANCEOF      // JS instanceof operator: a instanceof B
    }
}
