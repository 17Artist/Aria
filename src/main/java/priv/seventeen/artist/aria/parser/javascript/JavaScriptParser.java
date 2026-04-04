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

package priv.seventeen.artist.aria.parser.javascript;

import priv.seventeen.artist.aria.ast.ASTNode;
import priv.seventeen.artist.aria.ast.expression.*;
import priv.seventeen.artist.aria.ast.statement.*;
import priv.seventeen.artist.aria.exception.CompileException;
import priv.seventeen.artist.aria.parser.Lexer;
import priv.seventeen.artist.aria.parser.SourceLocation;
import priv.seventeen.artist.aria.parser.Token;
import priv.seventeen.artist.aria.parser.TokenType;
import priv.seventeen.artist.aria.value.BooleanValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.StringValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaScriptParser {

    private final Lexer lexer;
    private Token current;

    public JavaScriptParser(Lexer lexer) throws CompileException {
        this.lexer = lexer;
        this.current = lexer.nextToken();
    }


    private Token advance() throws CompileException {
        Token prev = current;
        current = lexer.nextToken();
        return prev;
    }

    private Token peek() throws CompileException {
        return lexer.peek();
    }

    private Token expect(TokenType type) throws CompileException {
        if (current.getType() != type) {
            throw error("期望 " + type + "，但得到 " + current.getType() + " (" + current.getValue() + ")");
        }
        return advance();
    }

    private boolean check(TokenType type) {
        return current.getType() == type;
    }

    private boolean match(TokenType type) throws CompileException {
        if (check(type)) { advance(); return true; }
        return false;
    }

    private void skipNewlines() throws CompileException {
        while (current.getType() == TokenType.NEWLINE) advance();
    }

    private void consumeStmtEnd() throws CompileException {
        while (current.getType() == TokenType.SEMICOLON || current.getType() == TokenType.NEWLINE) {
            advance();
        }
    }

    private boolean isStmtEnd() {
        TokenType t = current.getType();
        return t == TokenType.SEMICOLON || t == TokenType.NEWLINE || t == TokenType.RBRACE || t == TokenType.EOF;
    }

    private SourceLocation loc(Token t) {
        return new SourceLocation(t.getLine(), t.getColumn());
    }

    private SourceLocation loc(ASTNode node) {
        return node.getLocation();
    }

    private CompileException error(String msg) {
        return new CompileException(msg, current.getLine(), current.getColumn());
    }


    public ASTNode parse() throws CompileException {
        List<ASTNode> stmts = new ArrayList<>();
        skipNewlines();
        while (current.getType() != TokenType.EOF) {
            stmts.add(parseStatement());
            skipNewlines();
        }
        if (stmts.isEmpty()) return new BlockStmt(SourceLocation.UNKNOWN, stmts);
        if (stmts.size() == 1) return stmts.get(0);
        return new BlockStmt(loc(stmts.get(0)), stmts);
    }


    private ASTNode parseStatement() throws CompileException {
        skipNewlines();
        Token t = current;

        // Label statement: identifier followed by colon
        if (t.getType() == TokenType.IDENTIFIER) {
            Token peeked = peek();
            if (peeked.getType() == TokenType.COLON) {
                advance(); // consume label identifier
                advance(); // consume ':'
                skipNewlines();
                return parseStatement(); // parse the labeled statement, discard label
            }
        }

        return switch (t.getType()) {
            case SEMICOLON -> { advance(); yield parseStatement(); }
            case LBRACE -> parseBlockStmt();
            case VAR_KW, LET -> parseVarDecl(true);
            case CONST -> parseVarDecl(false);
            case FUNCTION -> parseFunctionDecl();
            case IF -> parseIfStmt();
            case FOR -> parseForStmt();
            case WHILE -> parseWhileStmt();
            case DO -> parseDoWhileStmt();
            case SWITCH -> parseSwitchStmt();
            case TRY -> parseTryCatchStmt();
            case CLASS -> parseClassDecl();
            case IMPORT -> parseImportStmt();
            case EXPORT -> parseExportStmt();
            case RETURN -> parseReturnStmt();
            case BREAK -> parseBreakStmt();
            case NEXT -> parseContinueStmt();
            case THROW -> parseThrowStmt();
            case ASYNC -> parseAsyncStmt();
            case AT -> parseAnnotatedDecl();
            default -> parseExpressionStmt();
        };
    }

        private ASTNode parseAnnotatedDecl() throws CompileException {
        List<AnnotationExpr> annotations = parseAnnotations();
        skipNewlines();
        if (check(TokenType.CLASS)) {
            return parseClassDeclWithAnnotations(annotations);
        }
        // 注解修饰函数或变量声明 — 作为独立注解表达式语句
        if (annotations.size() == 1) {
            consumeStmtEnd();
            return new ExpressionStmt(annotations.get(0).getLocation(), annotations.get(0));
        }
        throw error("Unexpected annotations without class declaration");
    }

    private List<AnnotationExpr> parseAnnotations() throws CompileException {
        List<AnnotationExpr> annotations = new ArrayList<>();
        while (check(TokenType.AT)) {
            Token start = advance(); // @
            skipNewlines();
            String name = expect(TokenType.IDENTIFIER).getValue();
            List<ASTNode> args = Collections.emptyList();
            if (check(TokenType.LPAREN)) {
                advance();
                skipNewlines();
                if (!check(TokenType.RPAREN)) {
                    args = new ArrayList<>();
                    args.add(parseAssignExpr());
                    while (check(TokenType.COMMA)) {
                        advance();
                        skipNewlines();
                        args.add(parseAssignExpr());
                    }
                }
                skipNewlines();
                expect(TokenType.RPAREN);
            }
            annotations.add(new AnnotationExpr(loc(start), name, args));
            skipNewlines();
        }
        return annotations;
    }

    private ASTNode parseBlockStmt() throws CompileException {
        Token start = expect(TokenType.LBRACE);
        skipNewlines();
        List<ASTNode> stmts = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            stmts.add(parseStatement());
            skipNewlines();
        }
        expect(TokenType.RBRACE);
        consumeStmtEnd();
        return new BlockStmt(loc(start), stmts);
    }

    private ASTNode parseExpressionStmt() throws CompileException {
        ASTNode expr = parseExpression();
        consumeStmtEnd();
        return new ExpressionStmt(loc(expr), expr);
    }


    private ASTNode parseVarDecl(boolean mutable) throws CompileException {
        Token start = advance(); // consume var/let/const
        skipNewlines();
        String scope = mutable ? "var" : "val";

        // 解构: let [a, b] = ... 或 const {a, b} = ...
        if (check(TokenType.LBRACKET)) {
            return parseArrayDestructure(start, mutable);
        }
        if (check(TokenType.LBRACE)) {
            return parseObjectDestructure(start, mutable);
        }

        String name = expect(TokenType.IDENTIFIER).getValue();
        skipNewlines();
        ASTNode value = null;
        if (match(TokenType.ASSIGN)) {
            skipNewlines();
            value = parseAssignExpr();
        }
        consumeStmtEnd();
        if (value == null) {
            value = new LiteralExpr(loc(start), NoneValue.NONE);
        }
        // JS 变量声明使用 var.xxx / val.xxx → STORE_VAR（持久存储）
        ASTNode target = new DotExpr(loc(start), new IdentifierExpr(loc(start), scope), name);
        return new ExpressionStmt(loc(start),
                new AssignmentExpr(loc(start), target, AssignmentExpr.AssignOp.ASSIGN, value));
    }

    private ASTNode parseArrayDestructure(Token start, boolean mutable) throws CompileException {
        expect(TokenType.LBRACKET);
        skipNewlines();
        List<String> names = new ArrayList<>();
        String restName = null;
        while (!check(TokenType.RBRACKET) && !check(TokenType.EOF)) {
            skipNewlines();
            if (check(TokenType.SPREAD)) {
                advance();
                restName = expect(TokenType.IDENTIFIER).getValue();
                skipNewlines();
                break;
            }
            names.add(expect(TokenType.IDENTIFIER).getValue());
            skipNewlines();
            if (!check(TokenType.RBRACKET)) expect(TokenType.COMMA);
            skipNewlines();
        }
        expect(TokenType.RBRACKET);
        skipNewlines();
        expect(TokenType.ASSIGN);
        skipNewlines();
        ASTNode value = parseAssignExpr();
        consumeStmtEnd();
        return new DestructureStmt(loc(start), mutable, names, restName, value);
    }

    private ASTNode parseObjectDestructure(Token start, boolean mutable) throws CompileException {
        expect(TokenType.LBRACE);
        skipNewlines();
        List<String> names = new ArrayList<>();
        String restName = null;
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            skipNewlines();
            if (check(TokenType.SPREAD)) {
                advance();
                restName = expect(TokenType.IDENTIFIER).getValue();
                skipNewlines();
                break;
            }
            names.add(expect(TokenType.IDENTIFIER).getValue());
            skipNewlines();
            if (!check(TokenType.RBRACE)) expect(TokenType.COMMA);
            skipNewlines();
        }
        expect(TokenType.RBRACE);
        skipNewlines();
        expect(TokenType.ASSIGN);
        skipNewlines();
        ASTNode value = parseAssignExpr();
        consumeStmtEnd();
        return new DestructureStmt(loc(start), mutable, names, restName, value);
    }


    private ASTNode parseFunctionDecl() throws CompileException {
        Token start = advance(); // consume 'function'
        skipNewlines();
        String name = expect(TokenType.IDENTIFIER).getValue();
        skipNewlines();
        List<ParamInfo> params = parseParamListWithDefaults();
        skipNewlines();
        ASTNode body = parseFunctionBody(params, loc(start));
        ASTNode lambda = new LambdaExpr(loc(start), body);
        // 函数声明使用 var.xxx → STORE_VAR（持久存储，跨调用可见）
        ASTNode target = new DotExpr(loc(start), new IdentifierExpr(loc(start), "var"), name);
        consumeStmtEnd();
        return new ExpressionStmt(loc(start),
                new AssignmentExpr(loc(start), target, AssignmentExpr.AssignOp.ASSIGN, lambda));
    }

        private record ParamInfo(String name, ASTNode defaultValue) {}

    private List<ParamInfo> parseParamListWithDefaults() throws CompileException {
        expect(TokenType.LPAREN);
        skipNewlines();
        List<ParamInfo> params = new ArrayList<>();
        while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
            skipNewlines();
            if (check(TokenType.SPREAD)) {
                advance();
            }
            String name = expect(TokenType.IDENTIFIER).getValue();
            skipNewlines();
            // 解析默认值: = expr
            ASTNode defaultValue = null;
            if (check(TokenType.ASSIGN)) {
                advance();
                skipNewlines();
                defaultValue = parseAssignExpr();
            }
            params.add(new ParamInfo(name, defaultValue));
            skipNewlines();
            if (!check(TokenType.RPAREN)) {
                expect(TokenType.COMMA);
                skipNewlines();
            }
        }
        expect(TokenType.RPAREN);
        return params;
    }

    private List<String> parseParamList() throws CompileException {
        List<ParamInfo> infos = parseParamListWithDefaults();
        List<String> names = new ArrayList<>(infos.size());
        for (ParamInfo info : infos) {
            names.add(info.name());
        }
        return names;
    }

    private ASTNode parseFunctionBody(List<ParamInfo> params, SourceLocation loc) throws CompileException {
        skipNewlines();
        List<ASTNode> bodyStmts = new ArrayList<>(buildParamBindings(params, loc));
        if (check(TokenType.LBRACE)) {
            Token start = expect(TokenType.LBRACE);
            skipNewlines();
            while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                bodyStmts.add(parseStatement());
                skipNewlines();
            }
            expect(TokenType.RBRACE);
        } else {
            // 表达式体（箭头函数）
            ASTNode expr = parseAssignExpr();
            bodyStmts.add(new ReturnStmt(loc(expr), expr, ExpressionStmt.StmtControl.RETURN));
        }
        return new BlockStmt(loc, bodyStmts);
    }

        private ASTNode parseFunctionBodyFromNames(List<String> params, SourceLocation loc) throws CompileException {
        List<ParamInfo> infos = new ArrayList<>(params.size());
        for (String p : params) {
            infos.add(new ParamInfo(p, null));
        }
        return parseFunctionBody(infos, loc);
    }

    private List<ASTNode> buildParamBindings(List<ParamInfo> params, SourceLocation loc) {
        List<ASTNode> bindings = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            ParamInfo p = params.get(i);
            // 使用裸标识符赋值 → STORE_SCOPE，确保每次函数调用有独立的参数作用域
            ASTNode target = new IdentifierExpr(loc, p.name());
            ASTNode idx = new IndexExpr(loc, new IdentifierExpr(loc, "args"), new LiteralExpr(loc, new NumberValue(i)));

            if (p.defaultValue() != null) {
                // param = args[i] == none ? defaultValue : args[i]
                // 使用三元表达式: args[i] ?? defaultValue (nullish coalesce)
                ASTNode value = new BinaryExpr(loc, idx, BinaryExpr.BinaryOp.NULLISH_COALESCE, p.defaultValue());
                ASTNode assign = new AssignmentExpr(loc, target, AssignmentExpr.AssignOp.ASSIGN, value);
                bindings.add(new ExpressionStmt(loc, assign));
            } else {
                ASTNode assign = new AssignmentExpr(loc, target, AssignmentExpr.AssignOp.ASSIGN, idx);
                bindings.add(new ExpressionStmt(loc, assign));
            }
        }
        // Feature 5: arguments object — alias to args
        ASTNode argsTarget = new IdentifierExpr(loc, "arguments");
        ASTNode argsValue = new IdentifierExpr(loc, "args");
        ASTNode argsAssign = new AssignmentExpr(loc, argsTarget, AssignmentExpr.AssignOp.ASSIGN, argsValue);
        bindings.add(new ExpressionStmt(loc, argsAssign));
        return bindings;
    }


    private ASTNode parseIfStmt() throws CompileException {
        Token start = advance(); // consume 'if'
        skipNewlines();
        expect(TokenType.LPAREN);
        skipNewlines();
        ASTNode condition = parseExpression();
        skipNewlines();
        expect(TokenType.RPAREN);
        skipNewlines();
        ASTNode thenBlock = parseStatement();

        List<IfStmt> elifBlocks = new ArrayList<>();
        ASTNode elseBlock = null;

        while (checkElse()) {
            advance(); // consume 'else'
            skipNewlines();
            if (check(TokenType.IF)) {
                Token elifStart = advance(); // consume 'if'
                skipNewlines();
                expect(TokenType.LPAREN);
                skipNewlines();
                ASTNode elifCond = parseExpression();
                skipNewlines();
                expect(TokenType.RPAREN);
                skipNewlines();
                ASTNode elifBody = parseStatement();
                elifBlocks.add(new IfStmt(loc(elifStart), elifCond, elifBody, Collections.emptyList(), null));
            } else {
                elseBlock = parseStatement();
                break;
            }
        }
        consumeStmtEnd();
        return new IfStmt(loc(start), condition, thenBlock, elifBlocks, elseBlock);
    }

    private boolean checkElse() throws CompileException {
        // 检查当前或跳过换行后是否是 else
        if (check(TokenType.ELSE)) return true;
        if (check(TokenType.NEWLINE) || check(TokenType.SEMICOLON)) {
            Token peeked = peek();
            if (peeked.getType() == TokenType.ELSE) {
                // 消费换行/分号，使 current 指向 else
                skipNewlines();
                consumeStmtEnd();
                return check(TokenType.ELSE);
            }
        }
        return false;
    }

    private ASTNode parseWhileStmt() throws CompileException {
        Token start = advance(); // consume 'while'
        skipNewlines();
        expect(TokenType.LPAREN);
        skipNewlines();
        ASTNode condition = parseExpression();
        skipNewlines();
        expect(TokenType.RPAREN);
        skipNewlines();
        ASTNode body = parseStatement();
        consumeStmtEnd();
        return new WhileStmt(loc(start), condition, body);
    }

    private ASTNode parseDoWhileStmt() throws CompileException {
        Token start = advance(); // consume 'do'
        skipNewlines();
        ASTNode body = parseStatement();
        skipNewlines();
        expect(TokenType.WHILE);
        skipNewlines();
        expect(TokenType.LPAREN);
        skipNewlines();
        ASTNode condition = parseExpression();
        skipNewlines();
        expect(TokenType.RPAREN);
        consumeStmtEnd();
        // do { body } while (cond) → while(true) { body; if(!cond) break; }
        ASTNode notCond = new UnaryExpr(loc(start), condition, UnaryExpr.UnaryOp.NOT, true);
        ASTNode breakStmt = new ReturnStmt(loc(start), null, ExpressionStmt.StmtControl.BREAK);
        ASTNode ifBreak = new IfStmt(loc(start), notCond,
                new BlockStmt(loc(start), List.of(breakStmt)), Collections.emptyList(), null);
        List<ASTNode> loopBody = new ArrayList<>();
        if (body instanceof BlockStmt bs) {
            loopBody.addAll(bs.getStatements());
        } else {
            loopBody.add(body);
        }
        loopBody.add(ifBreak);
        return new WhileStmt(loc(start), new LiteralExpr(loc(start), BooleanValue.TRUE),
                new BlockStmt(loc(start), loopBody));
    }

    private ASTNode parseReturnStmt() throws CompileException {
        Token start = advance(); // consume 'return'
        ASTNode value = null;
        if (!isStmtEnd()) {
            value = parseExpression();
        }
        consumeStmtEnd();
        return new ReturnStmt(loc(start), value, ExpressionStmt.StmtControl.RETURN);
    }

    private ASTNode parseBreakStmt() throws CompileException {
        Token start = advance(); // consume 'break'
        // Skip optional label: break labelName
        if (check(TokenType.IDENTIFIER) && !isStmtEnd()) {
            advance(); // consume label (ignored)
        }
        consumeStmtEnd();
        return new ReturnStmt(loc(start), null, ExpressionStmt.StmtControl.BREAK);
    }

    private ASTNode parseContinueStmt() throws CompileException {
        Token start = advance(); // consume 'next' (continue mapped to NEXT)
        // Skip optional label: continue labelName
        if (check(TokenType.IDENTIFIER) && !isStmtEnd()) {
            advance(); // consume label (ignored)
        }
        consumeStmtEnd();
        return new ReturnStmt(loc(start), null, ExpressionStmt.StmtControl.NEXT);
    }

    private ASTNode parseThrowStmt() throws CompileException {
        Token start = advance(); // consume 'throw'
        skipNewlines();
        ASTNode value = parseExpression();
        consumeStmtEnd();
        return new ReturnStmt(loc(start), value, ExpressionStmt.StmtControl.THROW);
    }

    private ASTNode parseAsyncStmt() throws CompileException {
        Token start = advance(); // consume 'async'
        skipNewlines();
        // async function ...
        if (check(TokenType.FUNCTION)) {
            ASTNode funcDecl = parseFunctionDecl();
            return new AsyncStmt(loc(start), funcDecl);
        }
        // async () => ... (箭头函数)
        ASTNode expr = parseAssignExpr();
        consumeStmtEnd();
        return new AsyncStmt(loc(start), new ExpressionStmt(loc(start), expr));
    }


    private ASTNode parseForStmt() throws CompileException {
        Token start = advance(); // consume 'for'
        skipNewlines();
        expect(TokenType.LPAREN);
        skipNewlines();

        // 检测 for...in / for...of
        // for (let/var/const x of/in expr)
        if (check(TokenType.VAR_KW) || check(TokenType.LET) || check(TokenType.CONST)) {
            Token declToken = advance();
            skipNewlines();
            String varName = expect(TokenType.IDENTIFIER).getValue();
            skipNewlines();
            if (check(TokenType.OF) || check(TokenType.IN)) {
                advance(); // consume of/in
                skipNewlines();
                ASTNode iterable = parseExpression();
                skipNewlines();
                expect(TokenType.RPAREN);
                skipNewlines();
                ASTNode body = parseStatement();
                consumeStmtEnd();
                return new ForInStmt(loc(start), List.of(varName), iterable, body);
            }
            // 经典 for: 已经消费了 var/let/const name，需要继续解析 init
            ASTNode init = parseForInit(declToken, varName);
            return parseClassicFor(start, init);
        }

        // 无声明的 for(;;)
        ASTNode init = null;
        if (!check(TokenType.SEMICOLON)) {
            init = parseExpression();
        }
        return parseClassicFor(start, init);
    }

    private ASTNode parseForInit(Token declToken, String varName) throws CompileException {
        boolean mutable = declToken.getType() != TokenType.CONST;
        String scope = mutable ? "var" : "val";
        SourceLocation loc = loc(declToken);
        skipNewlines();
        ASTNode value = null;
        if (match(TokenType.ASSIGN)) {
            skipNewlines();
            value = parseAssignExpr();
        }
        if (value == null) value = new LiteralExpr(loc, NoneValue.NONE);
        ASTNode target = new DotExpr(loc, new IdentifierExpr(loc, scope), varName);
        return new AssignmentExpr(loc, target, AssignmentExpr.AssignOp.ASSIGN, value);
    }

    private ASTNode parseClassicFor(Token start, ASTNode init) throws CompileException {
        expect(TokenType.SEMICOLON);
        skipNewlines();
        ASTNode condition = null;
        if (!check(TokenType.SEMICOLON)) {
            condition = parseExpression();
        }
        expect(TokenType.SEMICOLON);
        skipNewlines();
        ASTNode update = null;
        if (!check(TokenType.RPAREN)) {
            update = parseExpression();
        }
        expect(TokenType.RPAREN);
        skipNewlines();
        ASTNode body = parseStatement();
        consumeStmtEnd();
        return new ForStmt(loc(start), init, condition, update, body);
    }


    private ASTNode parseSwitchStmt() throws CompileException {
        Token start = advance(); // consume 'switch'
        skipNewlines();
        expect(TokenType.LPAREN);
        skipNewlines();
        ASTNode condition = parseExpression();
        skipNewlines();
        expect(TokenType.RPAREN);
        skipNewlines();
        expect(TokenType.LBRACE);
        skipNewlines();

        List<CaseStmt> cases = new ArrayList<>();
        ASTNode defaultBlock = null;

        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            skipNewlines();
            if (check(TokenType.CASE)) {
                Token caseStart = advance(); // consume 'case'
                skipNewlines();
                ASTNode caseVal = parseExpression();
                skipNewlines();
                expect(TokenType.COLON);
                skipNewlines();
                List<ASTNode> bodyStmts = new ArrayList<>();
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                    bodyStmts.add(parseStatement());
                    skipNewlines();
                }
                ASTNode body = bodyStmts.size() == 1 ? bodyStmts.get(0) : new BlockStmt(loc(caseStart), bodyStmts);
                cases.add(new CaseStmt(loc(caseStart), caseVal, body));
            } else if (check(TokenType.DEFAULT)) {
                advance(); // consume 'default'
                skipNewlines();
                expect(TokenType.COLON);
                skipNewlines();
                List<ASTNode> bodyStmts = new ArrayList<>();
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                    bodyStmts.add(parseStatement());
                    skipNewlines();
                }
                defaultBlock = bodyStmts.size() == 1 ? bodyStmts.get(0) : new BlockStmt(loc(start), bodyStmts);
            } else {
                break;
            }
        }
        expect(TokenType.RBRACE);
        consumeStmtEnd();
        return new SwitchStmt(loc(start), condition, cases, defaultBlock, true);
    }

    private ASTNode parseTryCatchStmt() throws CompileException {
        Token start = advance(); // consume 'try'
        skipNewlines();
        ASTNode tryBlock = parseBlockStmt();

        String catchVar = null;
        ASTNode catchBlock = null;
        ASTNode finallyBlock = null;

        skipNewlines();
        if (check(TokenType.CATCH)) {
            advance(); // consume 'catch'
            skipNewlines();
            if (match(TokenType.LPAREN)) {
                skipNewlines();
                catchVar = expect(TokenType.IDENTIFIER).getValue();
                skipNewlines();
                expect(TokenType.RPAREN);
            }
            skipNewlines();
            catchBlock = parseBlockStmt();
        }
        skipNewlines();
        if (check(TokenType.FINALLY)) {
            advance(); // consume 'finally'
            skipNewlines();
            finallyBlock = parseBlockStmt();
        }
        consumeStmtEnd();
        return new TryCatchStmt(loc(start), tryBlock, catchVar, catchBlock, finallyBlock);
    }

    private ASTNode parseClassDecl() throws CompileException {
        return parseClassDeclWithAnnotations(Collections.emptyList());
    }

    private ASTNode parseClassDeclWithAnnotations(List<AnnotationExpr> classAnnotations) throws CompileException {
        Token start = advance(); // consume 'class'
        skipNewlines();
        String name = expect(TokenType.IDENTIFIER).getValue();
        skipNewlines();

        String parentName = null;
        if (match(TokenType.EXTENDS)) {
            skipNewlines();
            parentName = expect(TokenType.IDENTIFIER).getValue();
            skipNewlines();
        }

        expect(TokenType.LBRACE);
        skipNewlines();

        List<ClassDeclStmt.ClassFieldDecl> fields = new ArrayList<>();
        List<ClassDeclStmt.ClassMethodDecl> methods = new ArrayList<>();
        ASTNode constructor = null;

        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            skipNewlines();

            // 成员注解
            List<AnnotationExpr> memberAnnotations = Collections.emptyList();
            if (check(TokenType.AT)) {
                memberAnnotations = parseAnnotations();
                skipNewlines();
            }

            boolean isStatic = false;
            if (check(TokenType.STATIC)) {
                advance();
                skipNewlines();
                isStatic = true;
            }

            // 方法名或 constructor
            String memberName = current.getValue();
            Token memberToken = advance();
            skipNewlines();

            // getter/setter: get name() { ... } / set name(val) { ... }
            if (("get".equals(memberName) || "set".equals(memberName))
                    && check(TokenType.IDENTIFIER)) {
                String propName = advance().getValue();
                skipNewlines();
                List<ParamInfo> params = parseParamListWithDefaults();
                skipNewlines();
                ASTNode body = parseFunctionBody(params, loc(memberToken));
                ASTNode lambda = new LambdaExpr(loc(memberToken), body);
                // 编码为 __get_propName / __set_propName 方法
                String methodName = "__" + memberName + "_" + propName;
                methods.add(new ClassDeclStmt.ClassMethodDecl(methodName, lambda, memberAnnotations));
            } else if (check(TokenType.LPAREN)) {
                // 方法或构造函数
                List<ParamInfo> params = parseParamListWithDefaults();
                skipNewlines();
                ASTNode body = parseFunctionBody(params, loc(memberToken));
                ASTNode lambda = new LambdaExpr(loc(memberToken), body);

                if ("constructor".equals(memberName)) {
                    constructor = lambda;
                } else {
                    methods.add(new ClassDeclStmt.ClassMethodDecl(memberName, lambda, memberAnnotations));
                }
            } else if (check(TokenType.ASSIGN)) {
                // 字段: name = value
                advance();
                skipNewlines();
                ASTNode defaultValue = parseAssignExpr();
                fields.add(new ClassDeclStmt.ClassFieldDecl(memberName, true, defaultValue, memberAnnotations));
                consumeStmtEnd();
            } else {
                // 字段无默认值
                fields.add(new ClassDeclStmt.ClassFieldDecl(memberName, true, null, memberAnnotations));
                consumeStmtEnd();
            }
            skipNewlines();
        }
        expect(TokenType.RBRACE);
        consumeStmtEnd();
        return new ClassDeclStmt(loc(start), name, parentName, classAnnotations, fields, methods, constructor);
    }


    private ASTNode parseImportStmt() throws CompileException {
        Token start = advance(); // consume 'import'
        skipNewlines();

        // import { a, b } from 'module'
        if (check(TokenType.LBRACE)) {
            advance();
            skipNewlines();
            List<String> names = new ArrayList<>();
            while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                names.add(expect(TokenType.IDENTIFIER).getValue());
                skipNewlines();
                if (!check(TokenType.RBRACE)) {
                    expect(TokenType.COMMA);
                    skipNewlines();
                }
            }
            expect(TokenType.RBRACE);
            skipNewlines();
            expect(TokenType.FROM);
            skipNewlines();
            String source = parseStringValue();
            consumeStmtEnd();
            return new ImportStmt(loc(start), names, source, true);
        }

        // import * as name from 'module'
        if (check(TokenType.STAR)) {
            advance();
            skipNewlines();
            expect(TokenType.AS);
            skipNewlines();
            String alias = expect(TokenType.IDENTIFIER).getValue();
            skipNewlines();
            expect(TokenType.FROM);
            skipNewlines();
            String source = parseStringValue();
            consumeStmtEnd();
            return new ImportStmt(loc(start), List.of(source), alias);
        }

        // import name from 'module' (default import)
        if (check(TokenType.IDENTIFIER)) {
            String defaultName = advance().getValue();
            skipNewlines();

            // import name, { a, b } from 'module'
            if (check(TokenType.COMMA)) {
                advance();
                skipNewlines();
                if (check(TokenType.LBRACE)) {
                    advance();
                    skipNewlines();
                    List<String> names = new ArrayList<>();
                    names.add(defaultName);
                    while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                        names.add(expect(TokenType.IDENTIFIER).getValue());
                        skipNewlines();
                        if (!check(TokenType.RBRACE)) {
                            expect(TokenType.COMMA);
                            skipNewlines();
                        }
                    }
                    expect(TokenType.RBRACE);
                    skipNewlines();
                    expect(TokenType.FROM);
                    skipNewlines();
                    String source = parseStringValue();
                    consumeStmtEnd();
                    return new ImportStmt(loc(start), names, source, true);
                }
            }

            expect(TokenType.FROM);
            skipNewlines();
            String source = parseStringValue();
            consumeStmtEnd();
            return new ImportStmt(loc(start), List.of(source), defaultName);
        }

        // import 'module' (side-effect import)
        String source = parseStringValue();
        consumeStmtEnd();
        return new ImportStmt(loc(start), List.of(source), null);
    }

    private String parseStringValue() throws CompileException {
        TokenType t = current.getType();
        if (t == TokenType.TEXT || t == TokenType.TEXTPLUS || t == TokenType.TEXT_BLOCK) {
            return advance().getValue();
        }
        throw error("期望字符串字面量");
    }

    private ASTNode parseExportStmt() throws CompileException {
        Token start = advance(); // consume 'export'
        skipNewlines();

        // export default ...
        if (check(TokenType.DEFAULT)) {
            advance();
            skipNewlines();
            ASTNode expr;
            if (check(TokenType.FUNCTION)) {
                expr = parseFunctionDecl();
            } else if (check(TokenType.CLASS)) {
                expr = parseClassDecl();
            } else {
                expr = parseExpression();
                consumeStmtEnd();
            }
            return new ExportStmt(loc(start), expr);
        }

        // export function / class / var / let / const
        ASTNode stmt;
        if (check(TokenType.FUNCTION)) {
            stmt = parseFunctionDecl();
        } else if (check(TokenType.CLASS)) {
            stmt = parseClassDecl();
        } else if (check(TokenType.VAR_KW) || check(TokenType.LET)) {
            stmt = parseVarDecl(true);
        } else if (check(TokenType.CONST)) {
            stmt = parseVarDecl(false);
        } else {
            // export { ... } — 简化为表达式
            stmt = parseExpression();
            consumeStmtEnd();
        }
        return new ExportStmt(loc(start), stmt);
    }


    private ASTNode parseExpression() throws CompileException {
        skipNewlines();
        ASTNode expr = parseAssignExpr();
        // Comma operator: evaluate all, return last
        while (check(TokenType.COMMA)) {
            advance();
            skipNewlines();
            ASTNode right = parseAssignExpr();
            expr = new BinaryExpr(loc(expr), expr, BinaryExpr.BinaryOp.COMMA, right);
        }
        return expr;
    }


    private ASTNode parseAssignExpr() throws CompileException {
        skipNewlines();

        // 检测箭头函数: ident => ...
        if (check(TokenType.IDENTIFIER) && peek().getType() == TokenType.FAT_ARROW) {
            return parseArrowFunction();
        }

        // 检测箭头函数: () => ... 或 (params) => ...
        if (check(TokenType.LPAREN) && isArrowFunction()) {
            return parseArrowFunction();
        }

        // function 表达式
        if (check(TokenType.FUNCTION)) {
            return parseFunctionExpr();
        }

        ASTNode left = parseTernaryExpr();

        // 赋值运算符
        AssignmentExpr.AssignOp assignOp = matchAssignOp();
        if (assignOp != null) {
            skipNewlines();
            ASTNode right = parseAssignExpr();
            // JS 裸标识符赋值 → var.xxx（与变量声明一致，确保跨 scope 可见）
            ASTNode target = left;
            if (target instanceof IdentifierExpr id) {
                target = new DotExpr(loc(left), new IdentifierExpr(loc(left), "var"), id.getName());
            }
            return new AssignmentExpr(loc(left), target, assignOp, right);
        }

        return left;
    }

    private AssignmentExpr.AssignOp matchAssignOp() throws CompileException {
        return switch (current.getType()) {
            case ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.ASSIGN; }
            case PLUS_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.PLUS_ASSIGN; }
            case MINUS_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.MINUS_ASSIGN; }
            case STAR_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.STAR_ASSIGN; }
            case SLASH_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.SLASH_ASSIGN; }
            case PERCENT_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.PERCENT_ASSIGN; }
            case TILDE_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.INIT_OR_GET; }
            case BIT_AND_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.BIT_AND_ASSIGN; }
            case BIT_OR_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.BIT_OR_ASSIGN; }
            case BIT_XOR_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.BIT_XOR_ASSIGN; }
            case SHL_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.SHL_ASSIGN; }
            case SHR_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.SHR_ASSIGN; }
            case USHR_ASSIGN -> { advance(); yield AssignmentExpr.AssignOp.USHR_ASSIGN; }
            default -> null;
        };
    }

    private boolean isArrowFunction() throws CompileException {
        // 简单前瞻: 扫描匹配的括号，看后面是否跟 =>
        // 利用 Lexer 的 peek 只能看一个 token，所以用计数法
        // 这里用一个保守策略：如果是 () => 或 (ident, ...) => 模式
        if (!check(TokenType.LPAREN)) return false;
        Token peeked = peek();
        // () => ...
        if (peeked.getType() == TokenType.RPAREN) return true;
        // (ident => 不可能，(ident) => 或 (ident, ...) =>
        // 无法用单 token peek 完全判断，采用试探法
        if (peeked.getType() == TokenType.IDENTIFIER || peeked.getType() == TokenType.SPREAD) return true;
        return false;
    }

    private ASTNode parseArrowFunction() throws CompileException {
        Token start = current;
        List<ParamInfo> params;

        if (check(TokenType.IDENTIFIER)) {
            // single param: x => ...
            params = List.of(new ParamInfo(advance().getValue(), null));
        } else {
            // (params) => ...
            params = parseParamListWithDefaults();
        }
        skipNewlines();
        expect(TokenType.FAT_ARROW);
        skipNewlines();
        SourceLocation loc = loc(start);
        ASTNode body = parseFunctionBody(params, loc);
        return new LambdaExpr(loc, body);
    }

    private ASTNode parseFunctionExpr() throws CompileException {
        Token start = advance(); // consume 'function'
        skipNewlines();
        // 可选的函数名
        String name = null;
        if (check(TokenType.IDENTIFIER)) {
            name = advance().getValue();
            skipNewlines();
        }
        List<ParamInfo> params = parseParamListWithDefaults();
        skipNewlines();
        ASTNode body = parseFunctionBody(params, loc(start));
        ASTNode lambda = new LambdaExpr(loc(start), body);

        if (name != null) {
            // 命名函数表达式：包装为立即赋值
            ASTNode target = new DotExpr(loc(start), new IdentifierExpr(loc(start), "var"), name);
            return new AssignmentExpr(loc(start), target, AssignmentExpr.AssignOp.ASSIGN, lambda);
        }
        return lambda;
    }


    private ASTNode parseTernaryExpr() throws CompileException {
        ASTNode expr = parseOrExpr();
        if (check(TokenType.QUESTION)) {
            advance();
            skipNewlines();
            ASTNode thenExpr = parseAssignExpr();
            skipNewlines();
            expect(TokenType.COLON);
            skipNewlines();
            ASTNode elseExpr = parseAssignExpr();
            return new TernaryExpr(loc(expr), expr, thenExpr, elseExpr);
        }
        return expr;
    }

    private ASTNode parseOrExpr() throws CompileException {
        ASTNode left = parseAndExpr();
        while (check(TokenType.OR)) {
            advance();
            skipNewlines();
            ASTNode right = parseAndExpr();
            left = new BinaryExpr(loc(left), left, BinaryExpr.BinaryOp.OR, right);
        }
        return left;
    }

    private ASTNode parseAndExpr() throws CompileException {
        ASTNode left = parseNullishExpr();
        while (check(TokenType.AND)) {
            advance();
            skipNewlines();
            ASTNode right = parseNullishExpr();
            left = new BinaryExpr(loc(left), left, BinaryExpr.BinaryOp.AND, right);
        }
        return left;
    }

    private ASTNode parseNullishExpr() throws CompileException {
        ASTNode left = parseBitOrExpr();
        while (check(TokenType.NULLISH_COALESCE)) {
            advance();
            skipNewlines();
            ASTNode right = parseBitOrExpr();
            left = new BinaryExpr(loc(left), left, BinaryExpr.BinaryOp.NULLISH_COALESCE, right);
        }
        return left;
    }

    private ASTNode parseBitOrExpr() throws CompileException {
        ASTNode left = parseBitXorExpr();
        while (check(TokenType.BIT_OR)) {
            advance();
            skipNewlines();
            ASTNode right = parseBitXorExpr();
            left = new BinaryExpr(loc(left), left, BinaryExpr.BinaryOp.BIT_OR, right);
        }
        return left;
    }

    private ASTNode parseBitXorExpr() throws CompileException {
        ASTNode left = parseBitAndExpr();
        while (check(TokenType.BIT_XOR)) {
            advance();
            skipNewlines();
            ASTNode right = parseBitAndExpr();
            left = new BinaryExpr(loc(left), left, BinaryExpr.BinaryOp.BIT_XOR, right);
        }
        return left;
    }

    private ASTNode parseBitAndExpr() throws CompileException {
        ASTNode left = parseEqExpr();
        while (check(TokenType.BIT_AND)) {
            advance();
            skipNewlines();
            ASTNode right = parseEqExpr();
            left = new BinaryExpr(loc(left), left, BinaryExpr.BinaryOp.BIT_AND, right);
        }
        return left;
    }

    private ASTNode parseEqExpr() throws CompileException {
        ASTNode left = parseRelExpr();
        while (true) {
            BinaryExpr.BinaryOp op = switch (current.getType()) {
                case EQ, STRICT_EQ -> BinaryExpr.BinaryOp.EQ;
                case NE, STRICT_NE -> BinaryExpr.BinaryOp.NE;
                default -> null;
            };
            if (op == null) break;
            advance();
            skipNewlines();
            ASTNode right = parseRelExpr();
            left = new BinaryExpr(loc(left), left, op, right);
        }
        return left;
    }

    private ASTNode parseRelExpr() throws CompileException {
        ASTNode left = parseShiftExpr();
        while (true) {
            BinaryExpr.BinaryOp op = switch (current.getType()) {
                case LT -> BinaryExpr.BinaryOp.LT;
                case GT -> BinaryExpr.BinaryOp.GT;
                case LE -> BinaryExpr.BinaryOp.LE;
                case GE -> BinaryExpr.BinaryOp.GE;
                case IN_RANGE -> BinaryExpr.BinaryOp.IN_RANGE;
                case INSTANCEOF -> BinaryExpr.BinaryOp.INSTANCEOF;
                case IN -> BinaryExpr.BinaryOp.IN_OBJ;
                default -> null;
            };
            if (op == null) break;
            advance();
            skipNewlines();
            ASTNode right = parseShiftExpr();
            left = new BinaryExpr(loc(left), left, op, right);
        }
        return left;
    }


    private ASTNode parseShiftExpr() throws CompileException {
        ASTNode left = parseAddExpr();
        while (true) {
            BinaryExpr.BinaryOp op = switch (current.getType()) {
                case SHL -> BinaryExpr.BinaryOp.SHL;
                case SHR -> BinaryExpr.BinaryOp.SHR;
                case USHR -> BinaryExpr.BinaryOp.USHR;
                default -> null;
            };
            if (op == null) break;
            advance();
            skipNewlines();
            ASTNode right = parseAddExpr();
            left = new BinaryExpr(loc(left), left, op, right);
        }
        return left;
    }

    private ASTNode parseAddExpr() throws CompileException {
        ASTNode left = parseMulExpr();
        while (true) {
            BinaryExpr.BinaryOp op = switch (current.getType()) {
                case PLUS -> BinaryExpr.BinaryOp.ADD;
                case MINUS -> BinaryExpr.BinaryOp.SUB;
                default -> null;
            };
            if (op == null) break;
            advance();
            skipNewlines();
            ASTNode right = parseMulExpr();
            left = new BinaryExpr(loc(left), left, op, right);
        }
        return left;
    }

    private ASTNode parseMulExpr() throws CompileException {
        ASTNode left = parseUnaryExpr();
        while (true) {
            BinaryExpr.BinaryOp op = switch (current.getType()) {
                case STAR -> BinaryExpr.BinaryOp.MUL;
                case SLASH -> BinaryExpr.BinaryOp.DIV;
                case PERCENT -> BinaryExpr.BinaryOp.MOD;
                default -> null;
            };
            if (op == null) break;
            advance();
            skipNewlines();
            ASTNode right = parseUnaryExpr();
            left = new BinaryExpr(loc(left), left, op, right);
        }
        return left;
    }


    private ASTNode parseUnaryExpr() throws CompileException {
        Token t = current;
        switch (t.getType()) {
            case NOT: {
                advance();
                skipNewlines();
                ASTNode operand = parseUnaryExpr();
                return new UnaryExpr(loc(t), operand, UnaryExpr.UnaryOp.NOT, true);
            }
            case MINUS: {
                advance();
                skipNewlines();
                ASTNode operand = parseUnaryExpr();
                return new UnaryExpr(loc(t), operand, UnaryExpr.UnaryOp.NEG, true);
            }
            case BIT_NOT: {
                advance();
                skipNewlines();
                ASTNode operand = parseUnaryExpr();
                return new UnaryExpr(loc(t), operand, UnaryExpr.UnaryOp.BIT_NOT, true);
            }
            case INCREMENT: {
                advance();
                skipNewlines();
                ASTNode operand = parseDotExpr();
                return new UnaryExpr(loc(t), operand, UnaryExpr.UnaryOp.INCREMENT, true);
            }
            case DECREMENT: {
                advance();
                skipNewlines();
                ASTNode operand = parseDotExpr();
                return new UnaryExpr(loc(t), operand, UnaryExpr.UnaryOp.DECREMENT, true);
            }
            case TYPEOF: {
                advance();
                skipNewlines();
                ASTNode operand = parseUnaryExpr();
                // typeof x → type.typeof(x) — 映射为 Aria 内置函数调用
                ASTNode callee = new DotExpr(loc(t), new IdentifierExpr(loc(t), "type"), "typeof");
                return new CallExpr(loc(t), callee, List.of(operand));
            }
            case DELETE: {
                advance();
                skipNewlines();
                ASTNode operand = parseUnaryExpr();
                return new UnaryExpr(loc(t), operand, UnaryExpr.UnaryOp.DELETE, true);
            }
            case VOID: {
                advance();
                skipNewlines();
                ASTNode operand = parseUnaryExpr();
                return new UnaryExpr(loc(t), operand, UnaryExpr.UnaryOp.VOID, true);
            }
            case SPREAD: {
                advance();
                skipNewlines();
                ASTNode operand = parseAssignExpr();
                return new SpreadExpr(loc(t), operand);
            }
            default: break;
        }

        ASTNode expr = parseDotExpr();

        // 后缀 ++ --
        if (check(TokenType.INCREMENT)) {
            advance();
            return new UnaryExpr(loc(expr), expr, UnaryExpr.UnaryOp.INCREMENT, false);
        }
        if (check(TokenType.DECREMENT)) {
            advance();
            return new UnaryExpr(loc(expr), expr, UnaryExpr.UnaryOp.DECREMENT, false);
        }
        return expr;
    }


    private ASTNode parseDotExpr() throws CompileException {
        return parseElemPostfix();
    }

    private ASTNode parseElemPostfix() throws CompileException {
        ASTNode expr = parseElement();
        while (true) {
            if (check(TokenType.LBRACKET)) {
                advance();
                skipNewlines();
                ASTNode index = parseExpression();
                skipNewlines();
                expect(TokenType.RBRACKET);
                expr = new IndexExpr(loc(expr), expr, index);
            } else if (check(TokenType.LPAREN)) {
                // 函数调用
                expr = parseCallArgs(expr);
            } else if (check(TokenType.DOT)) {
                advance();
                skipNewlines();
                // Nashorn 兼容：.static 直接吞掉
                if (check(TokenType.STATIC)) {
                    advance();
                } else {
                    String prop = expect(TokenType.IDENTIFIER).getValue();
                    expr = new DotExpr(loc(expr), expr, prop);
                }
            } else if (check(TokenType.OPTIONAL_CHAIN)) {
                advance();
                skipNewlines();
                String prop = expect(TokenType.IDENTIFIER).getValue();
                expr = new OptionalChainExpr(loc(expr), expr, prop);
            } else {
                break;
            }
        }
        return expr;
    }

    private ASTNode parseCallArgs(ASTNode callee) throws CompileException {
        expect(TokenType.LPAREN);
        skipNewlines();
        List<ASTNode> args = new ArrayList<>();
        while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
            args.add(parseAssignExpr());
            skipNewlines();
            if (!check(TokenType.RPAREN)) {
                expect(TokenType.COMMA);
                skipNewlines();
            }
        }
        expect(TokenType.RPAREN);
        return new CallExpr(loc(callee), callee, args);
    }


    private ASTNode parseElement() throws CompileException {
        skipNewlines();
        Token t = current;

        switch (t.getType()) {
            case NUMBER: {
                advance();
                String val = t.getValue();
                double num;
                if (val.startsWith("0x") || val.startsWith("0X")) {
                    num = Long.parseLong(val.substring(2), 16);
                } else if (val.startsWith("0b") || val.startsWith("0B")) {
                    num = Long.parseLong(val.substring(2), 2);
                } else if (val.startsWith("0o") || val.startsWith("0O")) {
                    num = Long.parseLong(val.substring(2), 8);
                } else {
                    num = Double.parseDouble(val);
                }
                return new LiteralExpr(loc(t), new NumberValue(num));
            }
            case TEXT:
            case TEXTPLUS:
            case TEXT_BLOCK: {
                advance();
                return new LiteralExpr(loc(t), new StringValue(t.getValue()));
            }
            case TEMPLATE_STRING: {
                advance();
                return parseTemplateString(t);
            }
            case TRUE: {
                advance();
                return new LiteralExpr(loc(t), BooleanValue.TRUE);
            }
            case FALSE: {
                advance();
                return new LiteralExpr(loc(t), BooleanValue.FALSE);
            }
            case NONE:
            case NULL:
            case UNDEFINED: {
                advance();
                return new LiteralExpr(loc(t), NoneValue.NONE);
            }
            case THIS: {
                advance();
                return new IdentifierExpr(loc(t), "self");
            }
            case SUPER: {
                advance();
                return new IdentifierExpr(loc(t), "super");
            }
            case IDENTIFIER: {
                advance();
                return new IdentifierExpr(loc(t), t.getValue());
            }
            case LPAREN: {
                advance();
                skipNewlines();
                ASTNode expr = parseExpression();
                skipNewlines();
                expect(TokenType.RPAREN);
                return expr;
            }
            case LBRACKET: {
                return parseArrayLiteral();
            }
            case LBRACE: {
                return parseObjectLiteral();
            }
            case NEW: {
                return parseNewExpr();
            }
            case FUNCTION: {
                return parseFunctionExpr();
            }
            default:
                throw error("意外的 token: " + t.getType() + " (" + t.getValue() + ")");
        }
    }

    private ASTNode parseArrayLiteral() throws CompileException {
        Token start = expect(TokenType.LBRACKET);
        skipNewlines();
        List<ASTNode> elements = new ArrayList<>();
        while (!check(TokenType.RBRACKET) && !check(TokenType.EOF)) {
            elements.add(parseAssignExpr());
            skipNewlines();
            if (!check(TokenType.RBRACKET)) {
                expect(TokenType.COMMA);
                skipNewlines();
            }
        }
        expect(TokenType.RBRACKET);
        return new ListExpr(loc(start), elements);
    }

    private ASTNode parseObjectLiteral() throws CompileException {
        Token start = expect(TokenType.LBRACE);
        skipNewlines();
        List<MapExpr.MapEntry> entries = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            skipNewlines();

            // spread: { ...obj }
            if (check(TokenType.SPREAD)) {
                Token spreadTok = advance();
                ASTNode operand = parseAssignExpr();
                // 将 spread 作为特殊 entry: key=null 表示 spread
                entries.add(new MapExpr.MapEntry(new SpreadExpr(loc(spreadTok), operand), operand));
                skipNewlines();
                if (!check(TokenType.RBRACE)) {
                    expect(TokenType.COMMA);
                    skipNewlines();
                }
                continue;
            }

            // 计算属性名: [expr]: value
            ASTNode key;
            if (check(TokenType.LBRACKET)) {
                advance();
                skipNewlines();
                key = parseExpression();
                skipNewlines();
                expect(TokenType.RBRACKET);
            } else if (check(TokenType.NUMBER)) {
                Token numTok = advance();
                key = new LiteralExpr(loc(numTok), new StringValue(numTok.getValue()));
            } else {
                // 标识符或字符串作为 key
                Token keyToken = current;
                if (check(TokenType.TEXT) || check(TokenType.TEXTPLUS)) {
                    advance();
                    key = new LiteralExpr(loc(keyToken), new StringValue(keyToken.getValue()));
                } else {
                    // 标识符 key（包括关键字作为属性名）
                    String keyName = current.getValue();
                    advance();
                    key = new LiteralExpr(loc(keyToken), new StringValue(keyName));

                    // 简写属性: { name } 等价于 { name: name }
                    if (check(TokenType.COMMA) || check(TokenType.RBRACE) || check(TokenType.NEWLINE)) {
                        ASTNode value = new IdentifierExpr(loc(keyToken), keyName);
                        entries.add(new MapExpr.MapEntry(key, value));
                        skipNewlines();
                        if (check(TokenType.COMMA)) {
                            advance();
                            skipNewlines();
                        }
                        continue;
                    }

                    // 方法简写: { method() { ... } }
                    if (check(TokenType.LPAREN)) {
                        List<ParamInfo> params = parseParamListWithDefaults();
                        skipNewlines();
                        ASTNode body = parseFunctionBody(params, loc(keyToken));
                        ASTNode lambda = new LambdaExpr(loc(keyToken), body);
                        entries.add(new MapExpr.MapEntry(key, lambda));
                        skipNewlines();
                        if (check(TokenType.COMMA)) {
                            advance();
                            skipNewlines();
                        }
                        continue;
                    }
                }
            }

            skipNewlines();
            expect(TokenType.COLON);
            skipNewlines();
            ASTNode value = parseAssignExpr();
            entries.add(new MapExpr.MapEntry(key, value));
            skipNewlines();
            if (!check(TokenType.RBRACE)) {
                expect(TokenType.COMMA);
                skipNewlines();
            }
        }
        expect(TokenType.RBRACE);
        return new MapExpr(loc(start), entries);
    }

    private ASTNode parseNewExpr() throws CompileException {
        Token start = advance(); // consume 'new'
        skipNewlines();
        String className = expect(TokenType.IDENTIFIER).getValue();
        // 支持 new a.B.C() 形式 — 取最后一段作为类名
        while (check(TokenType.DOT)) {
            advance();
            className = expect(TokenType.IDENTIFIER).getValue();
        }
        skipNewlines();
        List<ASTNode> args = new ArrayList<>();
        if (check(TokenType.LPAREN)) {
            advance();
            skipNewlines();
            while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
                args.add(parseAssignExpr());
                skipNewlines();
                if (!check(TokenType.RPAREN)) {
                    expect(TokenType.COMMA);
                    skipNewlines();
                }
            }
            expect(TokenType.RPAREN);
        }
        return new NewExpr(loc(start), className, args);
    }


    private ASTNode parseTemplateString(Token token) throws CompileException {
        String raw = token.getValue();
        SourceLocation loc = loc(token);
        List<Object> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '$' && i + 1 < raw.length() && raw.charAt(i + 1) == '{') {
                // 将之前的文本部分加入
                if (!sb.isEmpty()) {
                    parts.add(sb.toString());
                    sb.setLength(0);
                }
                // 找到匹配的 }
                i += 2; // 跳过 ${
                int braceDepth = 1;
                int exprStart = i;
                while (i < raw.length() && braceDepth > 0) {
                    if (raw.charAt(i) == '{') braceDepth++;
                    else if (raw.charAt(i) == '}') braceDepth--;
                    if (braceDepth > 0) i++;
                }
                String exprStr = raw.substring(exprStart, i);
                i++; // 跳过 }
                // 解析插值表达式
                Lexer exprLexer = new Lexer(exprStr, true);
                JavaScriptParser exprParser = new JavaScriptParser(exprLexer);
                ASTNode exprNode = exprParser.parseExpression();
                parts.add(exprNode);
            } else {
                sb.append(c);
                i++;
            }
        }
        if (!sb.isEmpty()) {
            parts.add(sb.toString());
        }
        // 如果没有插值，返回普通字符串
        if (parts.size() == 1 && parts.get(0) instanceof String s) {
            return new LiteralExpr(loc, new StringValue(s));
        }
        return new InterpolatedStringExpr(loc, parts);
    }
}
