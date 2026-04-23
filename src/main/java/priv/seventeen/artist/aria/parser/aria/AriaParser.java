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

package priv.seventeen.artist.aria.parser.aria;

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

public class AriaParser {

    private final Lexer lexer;
    private Token current;
    private final List<CompileException> errors = new ArrayList<>();

    public AriaParser(Lexer lexer) throws CompileException {
        this.lexer = lexer;
        this.current = lexer.nextToken();
    }

    public List<CompileException> getErrors() { return errors; }
    public boolean hasErrors() { return !errors.isEmpty(); }


    private boolean check(TokenType type) {
        return current.getType() == type;
    }

    private boolean match(TokenType... types) throws CompileException {
        for (TokenType type : types) {
            if (current.getType() == type) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token advance() throws CompileException {
        Token prev = current;
        current = lexer.nextToken();
        return prev;
    }

    private Token expect(TokenType type) throws CompileException {
        if (current.getType() != type) {
            throw error("Expected " + type + ", got " + current.getType() + " ('" + current.getValue() + "')");
        }
        return advance();
    }

    private void skipNewlines() throws CompileException {
        while (current.getType() == TokenType.NEWLINE) {
            advance();
        }
    }

    private void consumeStmtEnd() throws CompileException {
        if (current.getType() == TokenType.NEWLINE || current.getType() == TokenType.SEMICOLON) {
            while (current.getType() == TokenType.NEWLINE || current.getType() == TokenType.SEMICOLON) {
                advance();
            }
        }
        // EOF 和 RBRACE 不需要消费，它们是自然终结
    }

    private CompileException error(String message) {
        return new CompileException(message, current.getLine(), current.getColumn());
    }

    private void synchronize() {
        while (!check(TokenType.EOF)) {
            if (check(TokenType.NEWLINE) || check(TokenType.SEMICOLON)) {
                try { advance(); } catch (CompileException ignored) {}
                return;
            }
            if (check(TokenType.RBRACE)) {
                return;
            }
            // Next statement start keywords
            if (check(TokenType.IF) || check(TokenType.FOR) || check(TokenType.WHILE)
                    || check(TokenType.CLASS) || check(TokenType.IMPORT) || check(TokenType.RETURN)
                    || check(TokenType.TRY) || check(TokenType.EXPORT) || check(TokenType.SWITCH)
                    || check(TokenType.MATCH) || check(TokenType.THROW) || check(TokenType.BREAK)
                    || check(TokenType.NEXT) || check(TokenType.ASYNC)) {
                return;
            }
            try { advance(); } catch (CompileException ignored) {}
        }
    }

    private SourceLocation loc(Token token) {
        return new SourceLocation(token.getLine(), token.getColumn());
    }

    private SourceLocation loc(Token start, Token end) {
        return new SourceLocation(start.getLine(), start.getColumn(), end.getLine(), end.getColumn());
    }

    private List<ASTNode> parseExprList() throws CompileException {
        List<ASTNode> list = new ArrayList<>();
        list.add(parseExprOrSpread());
        while (check(TokenType.COMMA)) {
            advance();
            skipNewlines();
            list.add(parseExprOrSpread());
        }
        return list;
    }

    private ASTNode parseExprOrSpread() throws CompileException {
        if (check(TokenType.SPREAD)) {
            Token start = advance();
            ASTNode operand = parseExpression();
            return new SpreadExpr(loc(start), operand);
        }
        return parseExpression();
    }


    /**
     * 解析整个编译单元。
     * compilationUnit → skipNewlines (importStmt skipNewlines)* statement EOF
     */
    public ASTNode parse() throws CompileException {
        List<ASTNode> statements = new ArrayList<>();
        skipNewlines();

        // 解析 import 语句
        while (check(TokenType.IMPORT)) {
            statements.add(parseImportStmt());
            skipNewlines();
        }

        // 解析语句直到 EOF
        while (!check(TokenType.EOF)) {
            skipNewlines();
            if (check(TokenType.EOF)) break;
            Token before = current;
            try {
                statements.add(parseStatement());
            } catch (CompileException e) {
                errors.add(e);
                synchronize();
                // 防止无限循环：如果 synchronize 后 token 没变，强制前进
                if (current == before) {
                    try { advance(); } catch (CompileException ignored) {}
                }
                // 错误上限防止 OOM
                if (errors.size() > 100) break;
            }
            skipNewlines();
        }

        if (errors.size() > 0 && statements.isEmpty()) {
            throw errors.get(0);
        }

        if (statements.isEmpty()) {
            return new BlockStmt(SourceLocation.UNKNOWN, statements);
        }
        if (statements.size() == 1) {
            return statements.get(0);
        }
        SourceLocation location = statements.get(0).getLocation();
        return new BlockStmt(location, statements);
    }


    private ASTNode parseStatement() throws CompileException {
        skipNewlines();
        return switch (current.getType()) {
            case IF -> parseIfStmt();
            case FOR -> parseForStmt();
            case WHILE -> parseWhileStmt();
            case SWITCH -> parseSwitchStmt();
            case MATCH -> parseMatchStmt();
            case ASYNC -> parseAsyncStmt();
            case LBRACE -> parseBlockStmt();
            case CLASS -> parseClassDecl(Collections.emptyList());
            case TRY -> parseTryCatchStmt();
            case IMPORT -> parseImportStmt();
            case EXPORT -> parseExportStmt();
            case AT -> {
                // 注解可能修饰 class
                List<AnnotationExpr> annotations = parseAnnotations();
                skipNewlines();
                if (check(TokenType.CLASS)) {
                    yield parseClassDecl(annotations);
                }
                // 独立注解作为表达式语句
                if (annotations.size() == 1) {
                    consumeStmtEnd();
                    yield new ExpressionStmt(annotations.get(0).getLocation(), annotations.get(0));
                }
                throw error("Unexpected annotations without class declaration");
            }
            default -> parseExprStmtOrDestructure();
        };
    }

    private ASTNode parseExprStmtOrDestructure() throws CompileException {
        Token start = current;

        // 检测 var.[ 或 val.[ 模式
        if (check(TokenType.IDENTIFIER)) {
            String prefix = current.getValue();
            if ("var".equals(prefix) || "val".equals(prefix)) {
                Token peeked = lexer.peek();
                if (peeked.getType() == TokenType.DOT) {
                    // 消费 var/val
                    advance();
                    // 消费 .
                    advance();
                    // 检查是否是 [
                    if (check(TokenType.LBRACKET)) {
                        return parseDestructure(start, "var".equals(prefix));
                    }
                    // 不是解构，回退——但我们已经消费了 var 和 .
                    // 这其实就是普通的 var.xxx 赋值，构造 DotExpr 继续
                    Token prop = current;
                    if (check(TokenType.IDENTIFIER) || isKeywordToken()) {
                        advance();
                    } else {
                        throw error("Expected property name after '" + prefix + ".'");
                    }
                    ASTNode left = new DotExpr(loc(start), new IdentifierExpr(loc(start), prefix), prop.getValue());
                    left = parsePostfixOps(left);
                    // 继续解析可能的 . 链
                    while (check(TokenType.DOT) || check(TokenType.OPTIONAL_CHAIN)) {
                        boolean optional = check(TokenType.OPTIONAL_CHAIN);
                        advance();
                        Token p2 = current;
                        if (check(TokenType.IDENTIFIER) || isKeywordToken()) {
                            advance();
                        } else {
                            throw error("Expected property name");
                        }
                        if (optional) {
                            left = new OptionalChainExpr(left.getLocation(), left, p2.getValue());
                        } else {
                            left = new DotExpr(left.getLocation(), left, p2.getValue());
                        }
                        left = parsePostfixOps(left);
                    }
                    // 检查赋值
                    AssignmentExpr.AssignOp op = matchAssignOp();
                    if (op != null) {
                        skipNewlines();
                        ASTNode right = parseFuncExpr();
                        ASTNode assign = new AssignmentExpr(left.getLocation(), left, op, right);
                        consumeStmtEnd();
                        return new ExpressionStmt(loc(start), assign);
                    }
                    consumeStmtEnd();
                    return new ExpressionStmt(loc(start), left);
                }
            }
        }

        // 普通表达式语句
        ASTNode expr = parseExpression();
        consumeStmtEnd();
        return new ExpressionStmt(loc(start), expr);
    }

    private ASTNode parseDestructure(Token start, boolean mutable) throws CompileException {
        expect(TokenType.LBRACKET);
        skipNewlines();
        List<String> names = new ArrayList<>();
        String restName = null;

        while (!check(TokenType.RBRACKET) && !check(TokenType.EOF)) {
            skipNewlines();
            if (check(TokenType.SPREAD)) {
                advance();
                Token restToken = expect(TokenType.IDENTIFIER);
                restName = restToken.getValue();
                skipNewlines();
                break; // ...rest 必须是最后一个
            }
            Token nameToken = expect(TokenType.IDENTIFIER);
            names.add(nameToken.getValue());
            skipNewlines();
            if (check(TokenType.COMMA)) {
                advance();
                skipNewlines();
            }
        }
        expect(TokenType.RBRACKET);
        skipNewlines();
        expect(TokenType.ASSIGN);
        skipNewlines();
        ASTNode value = parseExpression();
        consumeStmtEnd();
        return new DestructureStmt(loc(start), mutable, names, restName, value);
    }

    private boolean isKeywordToken() {
        return check(TokenType.FROM) || check(TokenType.AS) || check(TokenType.IN)
                || check(TokenType.NEW) || check(TokenType.CLASS) || check(TokenType.SUPER)
                || check(TokenType.IMPORT) || check(TokenType.EXTENDS) || check(TokenType.RETURN)
                || check(TokenType.BREAK) || check(TokenType.NEXT) || check(TokenType.TRUE)
                || check(TokenType.FALSE) || check(TokenType.NONE) || check(TokenType.THROW)
                || check(TokenType.TRY) || check(TokenType.CATCH) || check(TokenType.FINALLY)
                || check(TokenType.MATCH) || check(TokenType.CASE) || check(TokenType.ASYNC);
    }

    private ASTNode parseExprStmt() throws CompileException {
        Token start = current;
        ASTNode expr = parseExpression();
        consumeStmtEnd();
        return new ExpressionStmt(loc(start), expr);
    }

    private ASTNode parseIfStmt() throws CompileException {
        Token start = expect(TokenType.IF);
        expect(TokenType.LPAREN);
        skipNewlines();
        ASTNode condition = parseExpression();
        skipNewlines();
        expect(TokenType.RPAREN);
        skipNewlines();
        expect(TokenType.LBRACE);
        ASTNode thenBlock = parseStatementsUntilBrace();
        expect(TokenType.RBRACE);

        // elif 分支
        List<IfStmt> elifBlocks = new ArrayList<>();
        skipNewlines();
        while (check(TokenType.ELIF)) {
            Token elifStart = advance();
            expect(TokenType.LPAREN);
            skipNewlines();
            ASTNode elifCond = parseExpression();
            skipNewlines();
            expect(TokenType.RPAREN);
            skipNewlines();
            expect(TokenType.LBRACE);
            ASTNode elifBody = parseStatementsUntilBrace();
            expect(TokenType.RBRACE);
            elifBlocks.add(new IfStmt(loc(elifStart), elifCond, elifBody, Collections.emptyList(), null));
            skipNewlines();
        }

        // else 分支
        ASTNode elseBlock = null;
        if (check(TokenType.ELSE)) {
            advance();
            skipNewlines();
            expect(TokenType.LBRACE);
            elseBlock = parseStatementsUntilBrace();
            expect(TokenType.RBRACE);
        }

        return new IfStmt(loc(start), condition, thenBlock, elifBlocks, elseBlock);
    }

    private ASTNode parseForStmt() throws CompileException {
        Token start = expect(TokenType.FOR);
        expect(TokenType.LPAREN);
        skipNewlines();

        // 尝试判断是 for-in 还是 for(;;)
        // for-in: for (x in expr) 或 for (x, y in expr)
        // for(;;): for (init; cond; update)
        if (check(TokenType.SEMICOLON)) {
            // for (; ...)  — 经典 for，init 为空
            return parseForClassic(start, null);
        }

        // 先解析第一个表达式，然后看后面是 IN、COMMA+...IN 还是 SEMICOLON
        // 保存位置以便回溯判断
        ASTNode firstExpr = parseExpression();

        if (check(TokenType.IN)) {
            // for (x in expr) — 单变量 for-in
            advance();
            skipNewlines();
            List<String> vars = new ArrayList<>();
            vars.add(extractIdentifierName(firstExpr));
            ASTNode iterable = parseExpression();
            skipNewlines();
            expect(TokenType.RPAREN);
            skipNewlines();
            expect(TokenType.LBRACE);
            ASTNode body = parseStatementsUntilBrace();
            expect(TokenType.RBRACE);
            return new ForInStmt(loc(start), vars, iterable, body);
        }

        if (check(TokenType.COMMA)) {
            // for (x, y in expr) — 多变量 for-in
            List<String> vars = new ArrayList<>();
            vars.add(extractIdentifierName(firstExpr));
            while (check(TokenType.COMMA)) {
                advance();
                skipNewlines();
                ASTNode varExpr = parseExpression();
                if (check(TokenType.IN)) {
                    vars.add(extractIdentifierName(varExpr));
                    break;
                }
                vars.add(extractIdentifierName(varExpr));
            }
            expect(TokenType.IN);
            skipNewlines();
            ASTNode iterable = parseExpression();
            skipNewlines();
            expect(TokenType.RPAREN);
            skipNewlines();
            expect(TokenType.LBRACE);
            ASTNode body = parseStatementsUntilBrace();
            expect(TokenType.RBRACE);
            return new ForInStmt(loc(start), vars, iterable, body);
        }

        // 否则是经典 for(init; cond; update)
        return parseForClassic(start, firstExpr);
    }

    private ASTNode parseForClassic(Token start, ASTNode init) throws CompileException {
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
        expect(TokenType.LBRACE);
        ASTNode body = parseStatementsUntilBrace();
        expect(TokenType.RBRACE);

        return new ForStmt(loc(start), init, condition, update, body);
    }

    private String extractIdentifierName(ASTNode expr) throws CompileException {
        if (expr instanceof IdentifierExpr id) {
            return id.getName();
        }
        throw new CompileException("Expected identifier in for-in variable list",
                expr.getLocation().startLine(), expr.getLocation().startColumn());
    }

    private ASTNode parseWhileStmt() throws CompileException {
        Token start = expect(TokenType.WHILE);
        expect(TokenType.LPAREN);
        skipNewlines();
        ASTNode condition = parseExpression();
        skipNewlines();
        expect(TokenType.RPAREN);
        skipNewlines();
        expect(TokenType.LBRACE);
        ASTNode body = parseStatementsUntilBrace();
        expect(TokenType.RBRACE);
        return new WhileStmt(loc(start), condition, body);
    }

    private ASTNode parseSwitchStmt() throws CompileException {
        Token start = expect(TokenType.SWITCH);
        expect(TokenType.LPAREN);
        skipNewlines();
        ASTNode condition = parseExpression();
        skipNewlines();
        expect(TokenType.RPAREN);
        skipNewlines();
        expect(TokenType.LBRACE);
        skipNewlines();

        List<CaseStmt> cases = new ArrayList<>();
        ASTNode elseBlock = null;

        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            skipNewlines();
            if (check(TokenType.CASE)) {
                Token caseStart = advance();
                skipNewlines();
                ASTNode caseExpr = parseExpression();
                skipNewlines();
                expect(TokenType.LBRACE);
                ASTNode caseBody = parseStatementsUntilBrace();
                expect(TokenType.RBRACE);
                cases.add(new CaseStmt(loc(caseStart), caseExpr, caseBody));
                skipNewlines();
            } else if (check(TokenType.ELSE)) {
                advance();
                skipNewlines();
                expect(TokenType.LBRACE);
                elseBlock = parseStatementsUntilBrace();
                expect(TokenType.RBRACE);
                skipNewlines();
            } else {
                break;
            }
        }

        expect(TokenType.RBRACE);
        return new SwitchStmt(loc(start), condition, cases, elseBlock, true); // Aria: 穿透
    }

    private ASTNode parseMatchStmt() throws CompileException {
        Token start = expect(TokenType.MATCH);
        expect(TokenType.LPAREN);
        skipNewlines();
        ASTNode condition = parseExpression();
        skipNewlines();
        expect(TokenType.RPAREN);
        skipNewlines();
        expect(TokenType.LBRACE);
        skipNewlines();

        List<CaseStmt> cases = new ArrayList<>();
        ASTNode elseBlock = null;

        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            skipNewlines();
            if (check(TokenType.CASE)) {
                Token caseStart = advance();
                skipNewlines();
                ASTNode caseExpr = parseExpression();
                skipNewlines();
                expect(TokenType.LBRACE);
                ASTNode caseBody = parseStatementsUntilBrace();
                expect(TokenType.RBRACE);
                cases.add(new CaseStmt(loc(caseStart), caseExpr, caseBody));
                skipNewlines();
            } else if (check(TokenType.ELSE)) {
                advance();
                skipNewlines();
                expect(TokenType.LBRACE);
                elseBlock = parseStatementsUntilBrace();
                expect(TokenType.RBRACE);
                skipNewlines();
            } else {
                break;
            }
        }

        expect(TokenType.RBRACE);
        return new SwitchStmt(loc(start), condition, cases, elseBlock, false); // match: 不穿透
    }

    private ASTNode parseAsyncStmt() throws CompileException {
        Token start = expect(TokenType.ASYNC);
        skipNewlines();
        expect(TokenType.LBRACE);
        ASTNode body = parseStatementsUntilBrace();
        expect(TokenType.RBRACE);
        return new AsyncStmt(loc(start), body);
    }

    private ASTNode parseBlockStmt() throws CompileException {
        Token start = expect(TokenType.LBRACE);
        ASTNode body = parseStatementsUntilBrace();
        expect(TokenType.RBRACE);
        return body;
    }

    private ASTNode parseTryCatchStmt() throws CompileException {
        Token start = expect(TokenType.TRY);
        skipNewlines();
        expect(TokenType.LBRACE);
        ASTNode tryBlock = parseStatementsUntilBrace();
        expect(TokenType.RBRACE);
        skipNewlines();

        String catchVar = null;
        ASTNode catchBlock = null;
        if (check(TokenType.CATCH)) {
            advance();
            // catch 可以带变量名: catch(e) { ... } 或不带: catch { ... }
            if (check(TokenType.LPAREN)) {
                advance();
                Token varToken = expect(TokenType.IDENTIFIER);
                catchVar = varToken.getValue();
                expect(TokenType.RPAREN);
            }
            skipNewlines();
            expect(TokenType.LBRACE);
            catchBlock = parseStatementsUntilBrace();
            expect(TokenType.RBRACE);
            skipNewlines();
        }

        ASTNode finallyBlock = null;
        if (check(TokenType.FINALLY)) {
            advance();
            skipNewlines();
            expect(TokenType.LBRACE);
            finallyBlock = parseStatementsUntilBrace();
            expect(TokenType.RBRACE);
        }

        return new TryCatchStmt(loc(start), tryBlock, catchVar, catchBlock, finallyBlock);
    }

    private ASTNode parseExportStmt() throws CompileException {
        Token start = expect(TokenType.EXPORT);
        skipNewlines();
        // export 后面跟一个普通语句
        ASTNode inner = parseStatement();
        return new ExportStmt(loc(start), inner);
    }

    private ASTNode parseImportStmt() throws CompileException {
        Token start = expect(TokenType.IMPORT);
        skipNewlines();

        // Named import: import { a, b, c } from 'source'
        if (check(TokenType.LBRACE)) {
            advance();
            skipNewlines();
            List<String> names = new ArrayList<>();
            names.add(expect(TokenType.IDENTIFIER).getValue());
            while (check(TokenType.COMMA)) {
                advance();
                skipNewlines();
                names.add(expect(TokenType.IDENTIFIER).getValue());
                skipNewlines();
            }
            skipNewlines();
            expect(TokenType.RBRACE);
            skipNewlines();
            expect(TokenType.FROM);
            skipNewlines();
            // Source can be a string literal or identifier path
            String source;
            if (check(TokenType.TEXT) || check(TokenType.TEXTPLUS)) {
                source = advance().getValue();
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(expect(TokenType.IDENTIFIER).getValue());
                while (check(TokenType.DOT)) {
                    advance();
                    sb.append('.').append(expect(TokenType.IDENTIFIER).getValue());
                }
                source = sb.toString();
            }
            consumeStmtEnd();
            return new ImportStmt(loc(start), names, source, true);
        }

        List<String> path = new ArrayList<>();
        Token id = expect(TokenType.IDENTIFIER);
        path.add(id.getValue());

        while (check(TokenType.DOT)) {
            advance();
            Token part = expect(TokenType.IDENTIFIER);
            path.add(part.getValue());
        }

        String alias = null;
        if (check(TokenType.AS)) {
            advance();
            Token aliasToken = expect(TokenType.IDENTIFIER);
            alias = aliasToken.getValue();
        }

        consumeStmtEnd();
        return new ImportStmt(loc(start), path, alias);
    }

    private ASTNode parseStatementsUntilBrace() throws CompileException {
        List<ASTNode> statements = new ArrayList<>();
        skipNewlines();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            statements.add(parseStatement());
            skipNewlines();
        }
        if (statements.isEmpty()) {
            return new BlockStmt(current.getType() == TokenType.EOF ? SourceLocation.UNKNOWN : loc(current), statements);
        }
        if (statements.size() == 1) {
            return new BlockStmt(statements.get(0).getLocation(), statements);
        }
        return new BlockStmt(statements.get(0).getLocation(), statements);
    }

    private List<AnnotationExpr> parseAnnotations() throws CompileException {
        List<AnnotationExpr> annotations = new ArrayList<>();
        while (check(TokenType.AT)) {
            Token start = advance();
            Token name = expect(TokenType.IDENTIFIER);
            List<ASTNode> args = Collections.emptyList();
            if (check(TokenType.LPAREN)) {
                advance();
                skipNewlines();
                if (!check(TokenType.RPAREN)) {
                    args = parseExprList();
                }
                skipNewlines();
                expect(TokenType.RPAREN);
            }
            annotations.add(new AnnotationExpr(loc(start), name.getValue(), args));
            skipNewlines();
        }
        return annotations;
    }


    private ASTNode parseExpression() throws CompileException {
        Token start = current;

        if (check(TokenType.RETURN)) {
            advance();
            ASTNode value = null;
            if (!isStmtEnd()) {
                value = parseAssignExpr();
            }
            return new ReturnStmt(loc(start), value, ExpressionStmt.StmtControl.RETURN);
        }

        if (check(TokenType.BREAK)) {
            advance();
            return new ReturnStmt(loc(start), null, ExpressionStmt.StmtControl.BREAK);
        }

        if (check(TokenType.NEXT)) {
            advance();
            return new ReturnStmt(loc(start), null, ExpressionStmt.StmtControl.NEXT);
        }

        if (check(TokenType.THROW)) {
            advance();
            ASTNode value = parseAssignExpr();
            return new ReturnStmt(loc(start), value, ExpressionStmt.StmtControl.THROW);
        }

        return parseAssignExpr();
    }

    private boolean isStmtEnd() {
        return current.getType() == TokenType.NEWLINE
                || current.getType() == TokenType.SEMICOLON
                || current.getType() == TokenType.EOF
                || current.getType() == TokenType.RBRACE;
    }


    private ASTNode parseAssignExpr() throws CompileException {
        ASTNode left = parseFuncExpr();

        AssignmentExpr.AssignOp op = matchAssignOp();
        if (op != null) {
            skipNewlines();
            ASTNode right = parseFuncExpr();
            return new AssignmentExpr(left.getLocation(), left, op, right);
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

    private ASTNode parseFuncExpr() throws CompileException {
        if (check(TokenType.ARROW)) {
            Token start = advance();
            skipNewlines();
            expect(TokenType.LBRACE);
            ASTNode body = parseStatementsUntilBrace();
            expect(TokenType.RBRACE);
            return new LambdaExpr(loc(start), body);
        }
        return parseTernaryExpr();
    }

    private ASTNode parseTernaryExpr() throws CompileException {
        ASTNode condition = parseOrExpr();

        if (check(TokenType.QUESTION)) {
            advance();
            skipNewlines();

            // a ?: c 形式（Elvis 运算符）
            if (check(TokenType.COLON)) {
                advance();
                skipNewlines();
                ASTNode elseExpr = parseOrExpr();
                return new TernaryExpr(condition.getLocation(), condition, null, elseExpr);
            }

            // a ? b : c 或 a ? b : 形式
            ASTNode thenExpr = parseOrExpr();
            skipNewlines();

            if (check(TokenType.COLON)) {
                advance();
                skipNewlines();
                // a ? b : 形式（仅 true 分支）
                if (isStmtEnd() || check(TokenType.RPAREN) || check(TokenType.RBRACKET)
                        || check(TokenType.COMMA) || check(TokenType.RBRACE)) {
                    return new TernaryExpr(condition.getLocation(), condition, thenExpr, null);
                }
                // a ? b : c 完整形式
                ASTNode elseExpr = parseOrExpr();
                return new TernaryExpr(condition.getLocation(), condition, thenExpr, elseExpr);
            }

            // a ? b 形式（缺少冒号，视为仅 true 分支）
            return new TernaryExpr(condition.getLocation(), condition, thenExpr, null);
        }

        return condition;
    }


    private ASTNode parseOrExpr() throws CompileException {
        ASTNode left = parseAndExpr();
        while (check(TokenType.OR)) {
            advance();
            skipNewlines();
            ASTNode right = parseAndExpr();
            left = new BinaryExpr(left.getLocation(), left, BinaryExpr.BinaryOp.OR, right);
        }
        return left;
    }


    private ASTNode parseAndExpr() throws CompileException {
        ASTNode left = parseNullishExpr();
        while (check(TokenType.AND)) {
            advance();
            skipNewlines();
            ASTNode right = parseNullishExpr();
            left = new BinaryExpr(left.getLocation(), left, BinaryExpr.BinaryOp.AND, right);
        }
        return left;
    }


    private ASTNode parseNullishExpr() throws CompileException {
        ASTNode left = parseBitOrExpr();
        while (check(TokenType.NULLISH_COALESCE)) {
            advance();
            skipNewlines();
            ASTNode right = parseBitOrExpr();
            left = new BinaryExpr(left.getLocation(), left, BinaryExpr.BinaryOp.NULLISH_COALESCE, right);
        }
        return left;
    }

    private ASTNode parseBitOrExpr() throws CompileException {
        ASTNode left = parseBitXorExpr();
        while (check(TokenType.BIT_OR)) {
            advance();
            skipNewlines();
            ASTNode right = parseBitXorExpr();
            left = new BinaryExpr(left.getLocation(), left, BinaryExpr.BinaryOp.BIT_OR, right);
        }
        return left;
    }

    private ASTNode parseBitXorExpr() throws CompileException {
        ASTNode left = parseBitAndExpr();
        while (check(TokenType.BIT_XOR)) {
            advance();
            skipNewlines();
            ASTNode right = parseBitAndExpr();
            left = new BinaryExpr(left.getLocation(), left, BinaryExpr.BinaryOp.BIT_XOR, right);
        }
        return left;
    }

    private ASTNode parseBitAndExpr() throws CompileException {
        ASTNode left = parseRelExpr();
        while (check(TokenType.BIT_AND)) {
            advance();
            skipNewlines();
            ASTNode right = parseRelExpr();
            left = new BinaryExpr(left.getLocation(), left, BinaryExpr.BinaryOp.BIT_AND, right);
        }
        return left;
    }

    private ASTNode parseRelExpr() throws CompileException {
        ASTNode left = parseShiftExpr();

        BinaryExpr.BinaryOp op = switch (current.getType()) {
            case EQ -> BinaryExpr.BinaryOp.EQ;
            case NE -> BinaryExpr.BinaryOp.NE;
            case LT -> BinaryExpr.BinaryOp.LT;
            case GT -> BinaryExpr.BinaryOp.GT;
            case LE -> BinaryExpr.BinaryOp.LE;
            case GE -> BinaryExpr.BinaryOp.GE;
            case IN_RANGE -> BinaryExpr.BinaryOp.IN_RANGE;
            default -> null;
        };

        if (op != null) {
            advance();
            skipNewlines();
            ASTNode right = parseShiftExpr();
            return new BinaryExpr(left.getLocation(), left, op, right);
        }

        return left;
    }

    private ASTNode parseShiftExpr() throws CompileException {
        ASTNode left = parseAddExpr();
        while (check(TokenType.SHL) || check(TokenType.SHR) || check(TokenType.USHR)) {
            BinaryExpr.BinaryOp op = switch (current.getType()) {
                case SHL -> BinaryExpr.BinaryOp.SHL;
                case SHR -> BinaryExpr.BinaryOp.SHR;
                case USHR -> BinaryExpr.BinaryOp.USHR;
                default -> throw error("Unexpected shift operator");
            };
            advance();
            skipNewlines();
            ASTNode right = parseAddExpr();
            left = new BinaryExpr(left.getLocation(), left, op, right);
        }
        return left;
    }


    private ASTNode parseAddExpr() throws CompileException {
        ASTNode left = parseMulExpr();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            BinaryExpr.BinaryOp op = current.getType() == TokenType.PLUS
                    ? BinaryExpr.BinaryOp.ADD : BinaryExpr.BinaryOp.SUB;
            advance();
            skipNewlines();
            ASTNode right = parseMulExpr();
            left = new BinaryExpr(left.getLocation(), left, op, right);
        }
        return left;
    }


    private ASTNode parseMulExpr() throws CompileException {
        ASTNode left = parseUnaryExpr();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
            BinaryExpr.BinaryOp op = switch (current.getType()) {
                case STAR -> BinaryExpr.BinaryOp.MUL;
                case SLASH -> BinaryExpr.BinaryOp.DIV;
                case PERCENT -> BinaryExpr.BinaryOp.MOD;
                default -> throw error("Unexpected multiplicative operator");
            };
            advance();
            skipNewlines();
            ASTNode right = parseUnaryExpr();
            left = new BinaryExpr(left.getLocation(), left, op, right);
        }
        return left;
    }

    private ASTNode parseUnaryExpr() throws CompileException {
        Token start = current;

        // await expr
        if (check(TokenType.AWAIT)) {
            advance();
            ASTNode operand = parseUnaryExpr();
            return new priv.seventeen.artist.aria.ast.expression.AwaitExpr(loc(start), operand);
        }

        // 前缀 !
        if (check(TokenType.NOT)) {
            advance();
            ASTNode operand = parseDotExpr();
            return new UnaryExpr(loc(start), operand, UnaryExpr.UnaryOp.NOT, true);
        }

        // 前缀 ++
        if (check(TokenType.INCREMENT)) {
            advance();
            ASTNode operand = parseDotExpr();
            return new UnaryExpr(loc(start), operand, UnaryExpr.UnaryOp.INCREMENT, true);
        }

        // 前缀 --
        if (check(TokenType.DECREMENT)) {
            advance();
            ASTNode operand = parseDotExpr();
            return new UnaryExpr(loc(start), operand, UnaryExpr.UnaryOp.DECREMENT, true);
        }

        // 前缀 - (取负)
        if (check(TokenType.MINUS)) {
            advance();
            ASTNode operand = parseDotExpr();
            return new UnaryExpr(loc(start), operand, UnaryExpr.UnaryOp.NEG, true);
        }

        // 前缀 ~ (位取反)
        if (check(TokenType.BIT_NOT)) {
            advance();
            ASTNode operand = parseDotExpr();
            return new UnaryExpr(loc(start), operand, UnaryExpr.UnaryOp.BIT_NOT, true);
        }

        ASTNode operand = parseDotExpr();

        // 后缀 ++
        if (check(TokenType.INCREMENT)) {
            advance();
            return new UnaryExpr(loc(start), operand, UnaryExpr.UnaryOp.INCREMENT, false);
        }

        // 后缀 --
        if (check(TokenType.DECREMENT)) {
            advance();
            return new UnaryExpr(loc(start), operand, UnaryExpr.UnaryOp.DECREMENT, false);
        }

        return operand;
    }


    private ASTNode parseDotExpr() throws CompileException {
        ASTNode left = parseElemPostfix();

        while (check(TokenType.DOT) || check(TokenType.OPTIONAL_CHAIN)) {
            boolean optional = check(TokenType.OPTIONAL_CHAIN);
            advance();
            Token prop = current;
            if (check(TokenType.IDENTIFIER) || check(TokenType.FROM) || check(TokenType.AS)
                    || check(TokenType.IN) || check(TokenType.NEW) || check(TokenType.CLASS)
                    || check(TokenType.SUPER) || check(TokenType.IMPORT) || check(TokenType.EXTENDS)
                    || check(TokenType.RETURN) || check(TokenType.BREAK) || check(TokenType.NEXT)
                    || check(TokenType.TRUE) || check(TokenType.FALSE) || check(TokenType.NONE)
                    || check(TokenType.THROW) || check(TokenType.TRY) || check(TokenType.CATCH)
                    || check(TokenType.FINALLY) || check(TokenType.MATCH) || check(TokenType.CASE)
                    || check(TokenType.ASYNC)) {
                advance();
            } else {
                throw error("Expected property name after '" + (optional ? "?." : ".") + "', got " + current.getType() + " ('" + current.getValue() + "')");
            }

            if (optional) {
                left = new OptionalChainExpr(left.getLocation(), left, prop.getValue());
                left = parsePostfixOps(left);
            } else {
                left = new DotExpr(left.getLocation(), left, prop.getValue());
                left = parsePostfixOps(left);
            }
        }

        return left;
    }


    private ASTNode parseElemPostfix() throws CompileException {
        ASTNode element = parseElement();
        return parsePostfixOps(element);
    }

    private ASTNode parsePostfixOps(ASTNode element) throws CompileException {
        while (true) {
            if (check(TokenType.LBRACKET)) {
                advance();
                skipNewlines();
                ASTNode index = null;
                if (!check(TokenType.RBRACKET)) {
                    index = parseExpression();
                }
                skipNewlines();
                expect(TokenType.RBRACKET);
                element = new IndexExpr(element.getLocation(), element, index);
            } else if (check(TokenType.LPAREN)) {
                advance();
                skipNewlines();
                List<ASTNode> args = Collections.emptyList();
                if (!check(TokenType.RPAREN)) {
                    args = parseExprList();
                }
                skipNewlines();
                expect(TokenType.RPAREN);
                element = new CallExpr(element.getLocation(), element, args);
            } else {
                break;
            }
        }
        return element;
    }


    private ASTNode parseElement() throws CompileException {
        Token start = current;

        // 数字字面量
        if (check(TokenType.NUMBER)) {
            Token token = advance();
            double value;
            try {
                value = Double.parseDouble(token.getValue());
            } catch (NumberFormatException e) {
                throw error("Invalid number: " + token.getValue());
            }
            return new LiteralExpr(loc(start), new NumberValue(value));
        }

        // 字符串字面量（可能含插值）
        if (check(TokenType.TEXT) || check(TokenType.TEXTPLUS) || check(TokenType.TEXT_BLOCK)) {
            Token token = advance();
            return parseStringLiteral(token);
        }

        // 布尔字面量
        if (check(TokenType.TRUE)) {
            advance();
            return new LiteralExpr(loc(start), BooleanValue.TRUE);
        }
        if (check(TokenType.FALSE)) {
            advance();
            return new LiteralExpr(loc(start), BooleanValue.FALSE);
        }

        // none 字面量
        if (check(TokenType.NONE)) {
            advance();
            return new LiteralExpr(loc(start), NoneValue.NONE);
        }

        // 括号表达式
        if (check(TokenType.LPAREN)) {
            advance();
            skipNewlines();
            ASTNode expr = parseExpression();
            skipNewlines();
            expect(TokenType.RPAREN);
            return expr;
        }

        // 列表字面量
        if (check(TokenType.LBRACKET)) {
            advance();
            skipNewlines();
            List<ASTNode> elements = new ArrayList<>();
            if (!check(TokenType.RBRACKET)) {
                elements = parseExprList();
                skipNewlines();
            }
            expect(TokenType.RBRACKET);
            return new ListExpr(loc(start), elements);
        }

        // 字典字面量
        if (check(TokenType.LBRACE)) {
            advance();
            skipNewlines();
            List<MapExpr.MapEntry> entries = new ArrayList<>();
            if (!check(TokenType.RBRACE)) {
                entries = parseKvPairList();
                skipNewlines();
            }
            expect(TokenType.RBRACE);
            return new MapExpr(loc(start), entries);
        }

        // 注解表达式
        if (check(TokenType.AT)) {
            advance();
            Token name = expect(TokenType.IDENTIFIER);
            List<ASTNode> args = Collections.emptyList();
            if (check(TokenType.LPAREN)) {
                advance();
                skipNewlines();
                if (!check(TokenType.RPAREN)) {
                    args = parseExprList();
                }
                skipNewlines();
                expect(TokenType.RPAREN);
            }
            return new AnnotationExpr(loc(start), name.getValue(), args);
        }

        // 标识符
        if (check(TokenType.IDENTIFIER)) {
            Token token = advance();
            return new IdentifierExpr(loc(start), token.getValue());
        }

        // self / super 作为标识符
        if (check(TokenType.THIS)) {
            advance();
            return new IdentifierExpr(loc(start), "self");
        }
        if (check(TokenType.SUPER)) {
            advance();
            return new IdentifierExpr(loc(start), "super");
        }

        throw error("Unexpected token: " + current.getType() + " ('" + current.getValue() + "')");
    }

    private List<MapExpr.MapEntry> parseKvPairList() throws CompileException {
        List<MapExpr.MapEntry> entries = new ArrayList<>();
        entries.add(parseKvPair());
        while (check(TokenType.COMMA)) {
            advance();
            skipNewlines();
            if (check(TokenType.RBRACE)) break; // 允许尾逗号
            entries.add(parseKvPair());
        }
        return entries;
    }

    private MapExpr.MapEntry parseKvPair() throws CompileException {
        skipNewlines();
        ASTNode key;
        // 键可以是标识符或表达式
        if (check(TokenType.IDENTIFIER) || check(TokenType.TEXT) || check(TokenType.NUMBER)) {
            Token keyToken = current;
            ASTNode keyExpr = parseExpression();
            if (check(TokenType.COLON)) {
                advance();
                skipNewlines();
                ASTNode value = parseExpression();
                return new MapExpr.MapEntry(keyExpr, value);
            }
            // 简写形式: { name } 等价于 { name: name }
            return new MapExpr.MapEntry(keyExpr, keyExpr);
        }
        key = parseExpression();
        expect(TokenType.COLON);
        skipNewlines();
        ASTNode value = parseExpression();
        return new MapExpr.MapEntry(key, value);
    }


    private ASTNode parseStringLiteral(Token token) throws CompileException {
        String raw = token.getValue();

        // 单引号字符串（TEXT）不解析插值，直接返回纯文本
        if (token.getType() == TokenType.TEXT || !raw.contains("{")) {
            return new LiteralExpr(loc(token), new StringValue(raw));
        }

        List<Object> parts = new ArrayList<>();
        StringBuilder segment = new StringBuilder();
        int i = 0;
        int depth = 0;

        while (i < raw.length()) {
            char c = raw.charAt(i);

            if (c == '\\' && i + 1 < raw.length()) {
                // 转义字符，跳过
                segment.append(c);
                segment.append(raw.charAt(i + 1));
                i += 2;
                continue;
            }

            if (c == '{' && depth == 0) {
                // 开始插值表达式
                if (!segment.isEmpty()) {
                    parts.add(segment.toString());
                    segment.setLength(0);
                }
                depth = 1;
                i++;
                StringBuilder exprStr = new StringBuilder();
                while (i < raw.length()) {
                    char ec = raw.charAt(i);
                    if (ec == '{') {
                        depth++;
                        exprStr.append(ec);
                    } else if (ec == '}') {
                        depth--;
                        if (depth == 0) {
                            i++;
                            break;
                        }
                        exprStr.append(ec);
                    } else {
                        exprStr.append(ec);
                    }
                    i++;
                }
                // 解析插值表达式
                String exprSource = exprStr.toString().trim();
                if (!exprSource.isEmpty()) {
                    try {
                        Lexer subLexer = new Lexer(exprSource);
                        AriaParser subParser = new AriaParser(subLexer);
                        ASTNode exprNode = subParser.parseExpression();
                        parts.add(exprNode);
                    } catch (CompileException e) {
                        throw new CompileException(
                                "Error in string interpolation: " + e.getMessage(),
                                token.getLine(), token.getColumn());
                    }
                }
                continue;
            }

            segment.append(c);
            i++;
        }

        if (!segment.isEmpty()) {
            parts.add(segment.toString());
        }

        // 如果只有一个字符串部分，返回简单字面量
        if (parts.size() == 1 && parts.get(0) instanceof String s) {
            return new LiteralExpr(loc(token), new StringValue(s));
        }

        return new InterpolatedStringExpr(loc(token), parts);
    }



    private ASTNode parseClassDecl(List<AnnotationExpr> annotations) throws CompileException {
        Token start = expect(TokenType.CLASS);
        Token name = expect(TokenType.IDENTIFIER);

        String parentName = null;
        if (check(TokenType.EXTENDS)) {
            advance();
            Token parent = expect(TokenType.IDENTIFIER);
            parentName = parent.getValue();
        }

        skipNewlines();
        expect(TokenType.LBRACE);
        skipNewlines();

        List<ClassDeclStmt.ClassFieldDecl> fields = new ArrayList<>();
        List<ClassDeclStmt.ClassMethodDecl> methods = new ArrayList<>();
        ASTNode constructor = null;

        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            skipNewlines();
            if (check(TokenType.RBRACE)) break;

            // 收集成员注解
            List<AnnotationExpr> memberAnnotations = Collections.emptyList();
            if (check(TokenType.AT)) {
                memberAnnotations = parseAnnotations();
                skipNewlines();
            }

            // 构造函数: new = -> { ... }
            if (check(TokenType.NEW) || (check(TokenType.IDENTIFIER) && "new".equals(current.getValue()))) {
                advance();
                expect(TokenType.ASSIGN);
                skipNewlines();
                expect(TokenType.ARROW);
                skipNewlines();
                expect(TokenType.LBRACE);
                constructor = parseStatementsUntilBrace();
                expect(TokenType.RBRACE);
                skipNewlines();
                consumeStmtEnd();
                continue;
            }

            // 字段或方法: 需要判断是 var.name / val.name 还是 name = -> { ... }
            if (check(TokenType.IDENTIFIER)) {
                Token firstToken = current;
                String firstName = firstToken.getValue();

                // var.name = expr 或 val.name = expr
                if ("var".equals(firstName) || "val".equals(firstName)) {
                    advance();
                    expect(TokenType.DOT);
                    Token fieldName = expect(TokenType.IDENTIFIER);
                    boolean mutable = "var".equals(firstName);
                    ASTNode defaultValue = null;
                    if (check(TokenType.ASSIGN)) {
                        advance();
                        skipNewlines();
                        defaultValue = parseExpression();
                    }
                    fields.add(new ClassDeclStmt.ClassFieldDecl(
                            fieldName.getValue(), mutable, defaultValue, memberAnnotations));
                    consumeStmtEnd();
                    continue;
                }

                // name = -> { ... } (方法) 或 name = expr (字段简写)
                advance();
                if (check(TokenType.ASSIGN)) {
                    advance();
                    skipNewlines();
                    if (check(TokenType.ARROW)) {
                        // 方法定义
                        Token arrowToken = advance();
                        skipNewlines();
                        expect(TokenType.LBRACE);
                        ASTNode body = parseStatementsUntilBrace();
                        expect(TokenType.RBRACE);
                        ASTNode lambda = new LambdaExpr(loc(arrowToken), body);
                        methods.add(new ClassDeclStmt.ClassMethodDecl(
                                firstName, lambda, memberAnnotations));
                    } else {
                        // 字段简写: name = expr
                        ASTNode defaultValue = parseExpression();
                        fields.add(new ClassDeclStmt.ClassFieldDecl(
                                firstName, true, defaultValue, memberAnnotations));
                    }
                    consumeStmtEnd();
                    continue;
                }

                // 无赋值的字段声明
                fields.add(new ClassDeclStmt.ClassFieldDecl(
                        firstName, true, null, memberAnnotations));
                consumeStmtEnd();
                continue;
            }

            throw error("Unexpected token in class body: " + current.getType());
        }

        expect(TokenType.RBRACE);
        return new ClassDeclStmt(loc(start), name.getValue(), parentName,
                annotations, fields, methods, constructor);
    }
}
