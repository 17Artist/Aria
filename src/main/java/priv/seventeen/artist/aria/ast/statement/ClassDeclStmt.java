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
import priv.seventeen.artist.aria.ast.expression.AnnotationExpr;
import priv.seventeen.artist.aria.parser.SourceLocation;

import java.util.List;

public class ClassDeclStmt extends ASTNode {
    private final String name;
    private final String parentName;  // nullable
    private final List<AnnotationExpr> annotations;
    private final List<ClassFieldDecl> fields;
    private final List<ClassMethodDecl> methods;
    private final ASTNode constructor;  // nullable

    public ClassDeclStmt(SourceLocation location, String name, String parentName,
                         List<AnnotationExpr> annotations, List<ClassFieldDecl> fields,
                         List<ClassMethodDecl> methods, ASTNode constructor) {
        super(location);
        this.name = name;
        this.parentName = parentName;
        this.annotations = annotations;
        this.fields = fields;
        this.methods = methods;
        this.constructor = constructor;
    }

    public String getName() { return name; }
    public String getParentName() { return parentName; }
    public List<AnnotationExpr> getAnnotations() { return annotations; }
    public List<ClassFieldDecl> getFields() { return fields; }
    public List<ClassMethodDecl> getMethods() { return methods; }
    public ASTNode getConstructor() { return constructor; }

    public record ClassFieldDecl(String name, boolean mutable, ASTNode defaultValue,
                                 List<AnnotationExpr> annotations, boolean isStatic) {
        public ClassFieldDecl(String name, boolean mutable, ASTNode defaultValue,
                              List<AnnotationExpr> annotations) {
            this(name, mutable, defaultValue, annotations, false);
        }
    }

    public record ClassMethodDecl(String name, ASTNode body,
                                  List<AnnotationExpr> annotations, boolean isStatic) {
        public ClassMethodDecl(String name, ASTNode body, List<AnnotationExpr> annotations) {
            this(name, body, annotations, false);
        }
    }
}
