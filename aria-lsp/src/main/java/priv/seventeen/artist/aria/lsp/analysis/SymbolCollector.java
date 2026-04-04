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

package priv.seventeen.artist.aria.lsp.analysis;

import priv.seventeen.artist.aria.ast.ASTNode;
import priv.seventeen.artist.aria.ast.expression.AssignmentExpr;
import priv.seventeen.artist.aria.ast.expression.DotExpr;
import priv.seventeen.artist.aria.ast.expression.IdentifierExpr;
import priv.seventeen.artist.aria.ast.expression.LambdaExpr;
import priv.seventeen.artist.aria.ast.statement.*;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis.SymbolInfo;
import priv.seventeen.artist.aria.lsp.analysis.DocumentAnalysis.SymbolInfo.SymbolKind;

import java.util.List;

public class SymbolCollector {

    public static void collect(ASTNode node, List<SymbolInfo> symbols) {
        if (node == null) return;
        visit(node, symbols);
    }

    private static void visit(ASTNode node, List<SymbolInfo> symbols) {
        if (node == null) return;

        // 语句块：递归遍历子语句
        if (node instanceof BlockStmt block) {
            for (ASTNode stmt : block.getStatements()) {
                visit(stmt, symbols);
            }
            return;
        }

        // 表达式语句：检查内部表达式
        if (node instanceof ExpressionStmt exprStmt) {
            visit(exprStmt.getExpression(), symbols);
            return;
        }

        // 赋值表达式：识别 var.x = ... / val.x = ... 形式的变量声明
        if (node instanceof AssignmentExpr assign) {
            extractVariableDecl(assign, symbols);
            return;
        }

        // 类声明
        if (node instanceof ClassDeclStmt classDecl) {
            symbols.add(new SymbolInfo(
                    classDecl.getName(),
                    SymbolKind.CLASS,
                    classDecl.getLocation(),
                    buildClassDetail(classDecl)
            ));
            // 收集字段和方法
            for (ClassDeclStmt.ClassFieldDecl field : classDecl.getFields()) {
                if (field.defaultValue() != null) {
                    symbols.add(new SymbolInfo(
                            classDecl.getName() + "." + field.name(),
                            SymbolKind.FIELD,
                            classDecl.getLocation(),
                            field.mutable() ? "var" : "val"
                    ));
                }
            }
            for (ClassDeclStmt.ClassMethodDecl method : classDecl.getMethods()) {
                symbols.add(new SymbolInfo(
                        classDecl.getName() + "." + method.name(),
                        SymbolKind.METHOD,
                        classDecl.getLocation(),
                        "method"
                ));
            }
            return;
        }

        // import 语句
        if (node instanceof ImportStmt importStmt) {
            String name = importStmt.getAlias() != null
                    ? importStmt.getAlias()
                    : (importStmt.getPath() != null && !importStmt.getPath().isEmpty()
                    ? importStmt.getPath().get(importStmt.getPath().size() - 1)
                    : null);
            if (name != null) {
                symbols.add(new SymbolInfo(name, SymbolKind.IMPORT, importStmt.getLocation(), "import"));
            }
            // 命名导入
            if (importStmt.getNames() != null) {
                for (String n : importStmt.getNames()) {
                    symbols.add(new SymbolInfo(n, SymbolKind.IMPORT, importStmt.getLocation(), "import"));
                }
            }
            return;
        }

        // for-in 循环变量
        if (node instanceof ForInStmt forIn) {
            for (String varName : forIn.getVariables()) {
                symbols.add(new SymbolInfo(varName, SymbolKind.VARIABLE, forIn.getLocation(), "for-in variable"));
            }
            visit(forIn.getBody(), symbols);
            return;
        }

        // if/elif/else
        if (node instanceof IfStmt ifStmt) {
            visit(ifStmt.getThenBlock(), symbols);
            if (ifStmt.getElifBlocks() != null) {
                for (IfStmt elif : ifStmt.getElifBlocks()) {
                    visit(elif, symbols);
                }
            }
            visit(ifStmt.getElseBlock(), symbols);
            return;
        }

        // while
        if (node instanceof WhileStmt whileStmt) {
            visit(whileStmt.getBody(), symbols);
            return;
        }

        // for
        if (node instanceof ForStmt forStmt) {
            visit(forStmt.getInit(), symbols);
            visit(forStmt.getBody(), symbols);
            return;
        }

        // try/catch/finally
        if (node instanceof TryCatchStmt tryCatch) {
            visit(tryCatch.getTryBlock(), symbols);
            if (tryCatch.getCatchVar() != null) {
                symbols.add(new SymbolInfo(
                        tryCatch.getCatchVar(), SymbolKind.VARIABLE,
                        tryCatch.getLocation(), "catch variable"));
            }
            visit(tryCatch.getCatchBlock(), symbols);
            visit(tryCatch.getFinallyBlock(), symbols);
            return;
        }

        // switch/match
        if (node instanceof SwitchStmt switchStmt) {
            for (CaseStmt caseStmt : switchStmt.getCases()) {
                visit(caseStmt.getBody(), symbols);
            }
            visit(switchStmt.getElseBlock(), symbols);
            return;
        }

        // async
        if (node instanceof AsyncStmt asyncStmt) {
            visit(asyncStmt.getBody(), symbols);
        }
    }

    private static void extractVariableDecl(AssignmentExpr assign, List<SymbolInfo> symbols) {
        ASTNode target = assign.getTarget();
        if (!(target instanceof DotExpr dot)) return;

        ASTNode obj = dot.getObject();
        if (!(obj instanceof IdentifierExpr id)) return;

        String namespace = id.getName();
        String varName = dot.getProperty();

        // 识别 var.x / val.x / global.x / server.x / client.x
        switch (namespace) {
            case "var", "val", "global", "server", "client" -> {
                boolean isFunction = assign.getValue() instanceof LambdaExpr;
                SymbolKind kind = isFunction ? SymbolKind.FUNCTION : SymbolKind.VARIABLE;
                String detail = namespace + (isFunction ? " function" : " variable");
                symbols.add(new SymbolInfo(varName, kind, assign.getLocation(), detail));
            }
        }
    }

    private static String buildClassDetail(ClassDeclStmt classDecl) {
        StringBuilder sb = new StringBuilder("class " + classDecl.getName());
        if (classDecl.getParentName() != null) {
            sb.append(" extends ").append(classDecl.getParentName());
        }
        return sb.toString();
    }
}
