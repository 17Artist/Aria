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

package priv.seventeen.artist.aria.parser;

import priv.seventeen.artist.aria.exception.CompileException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class Lexer {

    private static final Map<String, TokenType> ARIA_KEYWORDS = new HashMap<>();

    static {
        ARIA_KEYWORDS.put("if",       TokenType.IF);
        ARIA_KEYWORDS.put("elif",     TokenType.ELIF);
        ARIA_KEYWORDS.put("else",     TokenType.ELSE);
        ARIA_KEYWORDS.put("while",    TokenType.WHILE);
        ARIA_KEYWORDS.put("for",      TokenType.FOR);
        ARIA_KEYWORDS.put("in",       TokenType.IN);
        ARIA_KEYWORDS.put("instanceof", TokenType.INSTANCEOF);
        ARIA_KEYWORDS.put("switch",   TokenType.SWITCH);
        ARIA_KEYWORDS.put("match",    TokenType.MATCH);
        ARIA_KEYWORDS.put("case",     TokenType.CASE);
        ARIA_KEYWORDS.put("break",    TokenType.BREAK);
        ARIA_KEYWORDS.put("return",   TokenType.RETURN);
        ARIA_KEYWORDS.put("next",     TokenType.NEXT);
        ARIA_KEYWORDS.put("async",    TokenType.ASYNC);
        ARIA_KEYWORDS.put("await",    TokenType.AWAIT);
        ARIA_KEYWORDS.put("true",     TokenType.TRUE);
        ARIA_KEYWORDS.put("false",    TokenType.FALSE);
        ARIA_KEYWORDS.put("none",     TokenType.NONE);
        ARIA_KEYWORDS.put("class",    TokenType.CLASS);
        ARIA_KEYWORDS.put("extends",  TokenType.EXTENDS);
        ARIA_KEYWORDS.put("super",    TokenType.SUPER);
        ARIA_KEYWORDS.put("try",      TokenType.TRY);
        ARIA_KEYWORDS.put("catch",    TokenType.CATCH);
        ARIA_KEYWORDS.put("finally",  TokenType.FINALLY);
        ARIA_KEYWORDS.put("throw",    TokenType.THROW);
        ARIA_KEYWORDS.put("import",   TokenType.IMPORT);
        ARIA_KEYWORDS.put("from",     TokenType.FROM);
        ARIA_KEYWORDS.put("as",       TokenType.AS);
        ARIA_KEYWORDS.put("export",   TokenType.EXPORT);
    }

    /**
     * 所有保留关键字与字面量（true/false/none）的词形集合。
     * 供 LSP 等工具读取，避免关键字列表在多处重复维护而产生漂移。
     */
    public static Set<String> keywords() {
        return Collections.unmodifiableSet(ARIA_KEYWORDS.keySet());
    }


    private final String source;
    private final int length;
    private int pos;
    private int line;
    private int column;

    private Token peeked;
    private int savedPos;
    private int savedLine;
    private int savedColumn;

    public Lexer(String source) {
        this.source = source;
        this.length = source.length();
        this.pos = 0;
        this.line = 1;
        this.column = 1;
    }


    public Token nextToken() throws CompileException {
        if (peeked != null) {
            Token t = peeked;
            pos = savedPos;
            line = savedLine;
            column = savedColumn;
            peeked = null;
            return t;
        }
        return readToken();
    }

    public Token peek() throws CompileException {
        if (peeked != null) return peeked;
        int oldPos = pos;
        int oldLine = line;
        int oldCol = column;
        peeked = readToken();
        savedPos = pos;
        savedLine = line;
        savedColumn = column;
        pos = oldPos;
        line = oldLine;
        column = oldCol;
        return peeked;
    }

    public int getLine() { return line; }
    public int getColumn() { return column; }

    /**
     * 记录当前词法位置(供解析器语句级回退,F4/R4)。peek() 不前移 pos,故无论是否有
     * 待取的 peeked token,返回的都是"下一个未消费 token 的起点"。
     */
    public int[] mark() {
        return new int[]{ pos, line, column };
    }

    /** 回退到 mark() 记录的位置并清空 peek 缓存(F4/R4)。 */
    public void reset(int[] mark) {
        this.pos = mark[0];
        this.line = mark[1];
        this.column = mark[2];
        this.peeked = null;
    }


    private Token readToken() throws CompileException {

        skipWhitespace();

        if (pos >= length) {
            return makeToken(TokenType.EOF, "", line, column);
        }

        char c = current();


        if (c == '\n' || c == '\r') {
            return readNewline();
        }


        if (c == '/') {
            if (pos + 1 < length) {
                char next = source.charAt(pos + 1);
                if (next == '/') { skipLineComment(); return readToken(); }
                if (next == '*') { skipBlockComment(); return readToken(); }
            }
        }


        if (isDigit(c)) {
            return readNumber();
        }

        if (c == '\'') {
            return readSingleQuoteString();
        }
        if (c == '"') {
            return readDoubleQuoteString();
        }

        if (isIdentifierStart(c)) {
            return readIdentifierOrKeyword();
        }

        return readOperator();
    }


    private void skipWhitespace() {
        while (pos < length) {
            char c = source.charAt(pos);
            if (c == ' ' || c == '\t') {
                advance();
            } else {
                break;
            }
        }
    }

    private Token readNewline() {
        int startLine = line;
        int startCol = column;
        char c = current();
        if (c == '\r' && pos + 1 < length && source.charAt(pos + 1) == '\n') {
            pos += 2;
        } else {
            pos++;
        }
        line++;
        column = 1;
        return makeToken(TokenType.NEWLINE, "\\n", startLine, startCol);
    }



    private void skipLineComment() {
        advance();
        do {
            advance();
        } while (pos < length && current() != '\n' && current() != '\r');
        // 不消费换行，让 readToken 把它作为 NEWLINE 返回
    }

    private void skipBlockComment() throws CompileException {
        int startLine = line;
        int startCol = column;
        // 跳过 /* 或 /**
        advance(); // /
        advance(); // *
        while (pos < length) {
            if (current() == '*' && pos + 1 < length && source.charAt(pos + 1) == '/') {
                advance(); // *
                advance(); // /
                return;
            }
            if (current() == '\n' || current() == '\r') {
                consumeNewlineChars();
            } else {
                advance();
            }
        }
        throw new CompileException("未闭合的块注释", startLine, startCol);
    }

    private void consumeNewlineChars() {
        char c = source.charAt(pos);
        if (c == '\r' && pos + 1 < length && source.charAt(pos + 1) == '\n') {
            pos += 2;
        } else {
            pos++;
        }
        line++;
        column = 1;
    }

    //  数字

    private Token readNumber() throws CompileException {
        int startLine = line;
        int startCol = column;
        int startPos = pos;

        if (current() == '0' && pos + 1 < length) {
            char prefix = source.charAt(pos + 1);
            if (prefix == 'x' || prefix == 'X') return readHexNumber(startLine, startCol);
            if (prefix == 'b' || prefix == 'B') return readBinaryNumber(startLine, startCol);
            if (prefix == 'o' || prefix == 'O') return readOctalNumber(startLine, startCol);
        }

        // 十进制（含浮点）
        while (pos < length && isDigit(current())) {
            advance();
        }
        if (pos < length && current() == '.' && pos + 1 < length && isDigit(source.charAt(pos + 1))) {
            advance(); // .
            while (pos < length && isDigit(current())) {
                advance();
            }
        }
        // 科学计数法
        if (pos < length && (current() == 'e' || current() == 'E')) {
            advance();
            if (pos < length && (current() == '+' || current() == '-')) {
                advance();
            }
            if (pos >= length || !isDigit(current())) {
                throw new CompileException("无效的科学计数法数字", startLine, startCol);
            }
            while (pos < length && isDigit(current())) {
                advance();
            }
        }

        return makeToken(TokenType.NUMBER, source.substring(startPos, pos), startLine, startCol);
    }

    private Token readHexNumber(int startLine, int startCol) throws CompileException {
        int startPos = pos;
        advance(); // 0
        advance(); // x/X
        if (pos >= length || !isHexDigit(current())) {
            throw new CompileException("无效的十六进制数字", startLine, startCol);
        }
        while (pos < length && isHexDigit(current())) {
            advance();
        }
        return makeToken(TokenType.NUMBER, source.substring(startPos, pos), startLine, startCol);
    }

    private Token readBinaryNumber(int startLine, int startCol) throws CompileException {
        int startPos = pos;
        advance(); // 0
        advance(); // b/B
        if (pos >= length || (current() != '0' && current() != '1')) {
            throw new CompileException("无效的二进制数字", startLine, startCol);
        }
        while (pos < length && (current() == '0' || current() == '1')) {
            advance();
        }
        return makeToken(TokenType.NUMBER, source.substring(startPos, pos), startLine, startCol);
    }

    private Token readOctalNumber(int startLine, int startCol) throws CompileException {
        int startPos = pos;
        advance(); // 0
        advance(); // o/O
        if (pos >= length || current() < '0' || current() > '7') {
            throw new CompileException("无效的八进制数字", startLine, startCol);
        }
        while (pos < length && current() >= '0' && current() <= '7') {
            advance();
        }
        return makeToken(TokenType.NUMBER, source.substring(startPos, pos), startLine, startCol);
    }

    //  字符串

    private Token readSingleQuoteString() throws CompileException {
        int startLine = line;
        int startCol = column;
        advance(); // 跳过开头 '
        StringBuilder sb = new StringBuilder();
        while (pos < length) {
            char c = current();
            if (c == '\'') {
                advance(); // 跳过结尾 '
                return makeToken(TokenType.TEXT, sb.toString(), startLine, startCol);
            }
            if (c == '\\') {
                sb.append(readEscapeSequence());
            } else if (c == '\n' || c == '\r') {
                throw new CompileException("单引号字符串不允许换行", line, column);
            } else {
                sb.append(c);
                advance();
            }
        }
        throw new CompileException("未闭合的单引号字符串", startLine, startCol);
    }

    private Token readDoubleQuoteString() throws CompileException {
        int startLine = line;
        int startCol = column;

        // 检查三引号
        if (pos + 2 < length && source.charAt(pos + 1) == '"' && source.charAt(pos + 2) == '"') {
            return readTextBlock(startLine, startCol);
        }

        advance(); // 跳过开头 "
        StringBuilder sb = new StringBuilder();
        while (pos < length) {
            char c = current();
            if (c == '"') {
                advance(); // 跳过结尾 "
                return makeToken(TokenType.TEXTPLUS, sb.toString(), startLine, startCol);
            }
            if (c == '\\') {
                sb.append(readEscapeSequence());
            } else if (c == '\n' || c == '\r') {
                throw new CompileException("双引号字符串不允许换行", line, column);
            } else {
                sb.append(c);
                advance();
            }
        }
        throw new CompileException("未闭合的双引号字符串", startLine, startCol);
    }

    private Token readTextBlock(int startLine, int startCol) throws CompileException {
        advance(); // "
        advance(); // "
        advance(); // "
        StringBuilder sb = new StringBuilder();
        while (pos < length) {
            if (current() == '"' && pos + 2 < length
                    && source.charAt(pos + 1) == '"'
                    && source.charAt(pos + 2) == '"') {
                advance(); // "
                advance(); // "
                advance(); // "
                return makeToken(TokenType.TEXT_BLOCK, sb.toString(), startLine, startCol);
            }
            char c = current();
            if (c == '\r' || c == '\n') {
                sb.append('\n');
                consumeNewlineChars();
            } else if (c == '\\') {
                sb.append(readEscapeSequence());
            } else {
                sb.append(c);
                advance();
            }
        }
        throw new CompileException("未闭合的三引号文本块", startLine, startCol);
    }

    /**
     * Shimmer 对齐(syntax-05)：字符串字面量<b>不展开转义</b>——值保留引号内原文。
     * Shimmer 词法的转义白名单仅用于"反斜杠+后随字符不结束字符串"的扫描判定，
     * 值取 substring 原文（如 {@code 'a\nb'} 是 4 个字符 a \ n b；{@code 'a\'b'} 是
     * a \ ' b 且 {@code \'} 不终止字符串）。此处照抄：消费反斜杠+后随字符、原样进值。
     * 未知转义保持宽容（Aria 自由区，Shimmer 是编译错），串尾裸反斜杠原样保留。
     */
    private String readEscapeSequence() {
        advance(); // 跳过 '\'
        if (pos >= length) {
            return "\\";
        }
        char c = current();
        advance();
        return "\\" + c;
    }

    private Token readIdentifierOrKeyword() {
        int startLine = line;
        int startCol = column;
        int startPos = pos;
        advance();
        while (pos < length && isIdentifierPart(current())) {
            advance();
        }
        String text = source.substring(startPos, pos);
        TokenType type = ARIA_KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);
        return makeToken(type, text, startLine, startCol);
    }



    private Token readOperator() throws CompileException {
        int startLine = line;
        int startCol = column;
        char c = current();
        char c1 = peek(1);
        char c2 = peek(2);

        switch (c) {

            case '(' -> { advance(); return makeToken(TokenType.LPAREN,    "(", startLine, startCol); }
            case ')' -> { advance(); return makeToken(TokenType.RPAREN,    ")", startLine, startCol); }
            case '[' -> { advance(); return makeToken(TokenType.LBRACKET,  "[", startLine, startCol); }
            case ']' -> { advance(); return makeToken(TokenType.RBRACKET,  "]", startLine, startCol); }
            case '{' -> { advance(); return makeToken(TokenType.LBRACE,    "{", startLine, startCol); }
            case '}' -> { advance(); return makeToken(TokenType.RBRACE,    "}", startLine, startCol); }
            case ',' -> { advance(); return makeToken(TokenType.COMMA,     ",", startLine, startCol); }
            case ';' -> { advance(); return makeToken(TokenType.SEMICOLON, ";", startLine, startCol); }
            case ':' -> { advance(); return makeToken(TokenType.COLON,     ":", startLine, startCol); }
            case '@' -> { advance(); return makeToken(TokenType.AT,        "@", startLine, startCol); }


            case '.' -> {
                if (c1 == '.' && c2 == '.') {
                    advance(); advance(); advance();
                    return makeToken(TokenType.SPREAD, "...", startLine, startCol);
                }
                if (c1 == '.') {
                    // Shimmer 对齐：`..` 区间运算符（此前抛错并导致解析器错误恢复死循环）
                    advance(); advance();
                    return makeToken(TokenType.RANGE, "..", startLine, startCol);
                }
                advance();
                return makeToken(TokenType.DOT, ".", startLine, startCol);
            }


            case '+' -> {
                if (c1 == '=') { advance(); advance(); return makeToken(TokenType.PLUS_ASSIGN, "+=", startLine, startCol); }
                if (c1 == '+') { advance(); advance(); return makeToken(TokenType.INCREMENT,   "++", startLine, startCol); }
                advance();
                return makeToken(TokenType.PLUS, "+", startLine, startCol);
            }

            case '-' -> {
                if (c1 == '>') { advance(); advance(); return makeToken(TokenType.ARROW,        "->", startLine, startCol); }
                if (c1 == '=') { advance(); advance(); return makeToken(TokenType.MINUS_ASSIGN, "-=", startLine, startCol); }
                if (c1 == '-') { advance(); advance(); return makeToken(TokenType.DECREMENT,    "--", startLine, startCol); }
                advance();
                return makeToken(TokenType.MINUS, "-", startLine, startCol);
            }


            case '*' -> {
                if (c1 == '=') { advance(); advance(); return makeToken(TokenType.STAR_ASSIGN, "*=", startLine, startCol); }
                advance();
                return makeToken(TokenType.STAR, "*", startLine, startCol);
            }


            case '/' -> {
                if (c1 == '=') { advance(); advance(); return makeToken(TokenType.SLASH_ASSIGN, "/=", startLine, startCol); }
                advance();
                return makeToken(TokenType.SLASH, "/", startLine, startCol);
            }

            case '%' -> {
                if (c1 == '=') { advance(); advance(); return makeToken(TokenType.PERCENT_ASSIGN, "%=", startLine, startCol); }
                advance();
                return makeToken(TokenType.PERCENT, "%", startLine, startCol);
            }


            case '=' -> {
                if (c1 == '=') { advance(); advance(); return makeToken(TokenType.EQ, "==", startLine, startCol); }
                advance();
                return makeToken(TokenType.ASSIGN, "=", startLine, startCol);
            }


            case '!' -> {
                if (c1 == '=') { advance(); advance(); return makeToken(TokenType.NE, "!=", startLine, startCol); }
                advance();
                return makeToken(TokenType.NOT, "!", startLine, startCol);
            }


            case '<' -> {
                if (c1 == '<' && c2 == '=') { advance(); advance(); advance(); return makeToken(TokenType.SHL_ASSIGN, "<<=", startLine, startCol); }
                if (c1 == '<')              { advance(); advance();             return makeToken(TokenType.SHL,        "<<",  startLine, startCol); }
                if (c1 == '=')              { advance(); advance();             return makeToken(TokenType.LE,         "<=",  startLine, startCol); }
                advance();
                return makeToken(TokenType.LT, "<", startLine, startCol);
            }

            case '>' -> {
                if (c1 == '>' && c2 == '>') {
                    // 可能是 >>>=
                    if (pos + 3 < length && source.charAt(pos + 3) == '=') {
                        advance(); advance(); advance(); advance();
                        return makeToken(TokenType.USHR_ASSIGN, ">>>=", startLine, startCol);
                    }
                    advance(); advance(); advance();
                    return makeToken(TokenType.USHR, ">>>", startLine, startCol);
                }
                if (c1 == '>' && c2 == '=') { advance(); advance(); advance(); return makeToken(TokenType.SHR_ASSIGN, ">>=", startLine, startCol); }
                if (c1 == '>')              { advance(); advance();             return makeToken(TokenType.SHR,        ">>",  startLine, startCol); }
                if (c1 == '=')              { advance(); advance();             return makeToken(TokenType.GE,         ">=",  startLine, startCol); }
                advance();
                return makeToken(TokenType.GT, ">", startLine, startCol);
            }

            case '&' -> {
                if (c1 == '&') { advance(); advance(); return makeToken(TokenType.AND,            "&&", startLine, startCol); }
                if (c1 == '=') { advance(); advance(); return makeToken(TokenType.BIT_AND_ASSIGN, "&=", startLine, startCol); }
                advance();
                return makeToken(TokenType.BIT_AND, "&", startLine, startCol);
            }

            case '|' -> {
                if (c1 == '|') { advance(); advance(); return makeToken(TokenType.OR,             "||", startLine, startCol); }
                if (c1 == '=') { advance(); advance(); return makeToken(TokenType.BIT_OR_ASSIGN,  "|=", startLine, startCol); }
                advance();
                return makeToken(TokenType.BIT_OR, "|", startLine, startCol);
            }

            case '^' -> {
                if (c1 == '=') { advance(); advance(); return makeToken(TokenType.BIT_XOR_ASSIGN, "^=", startLine, startCol); }
                advance();
                return makeToken(TokenType.BIT_XOR, "^", startLine, startCol);
            }

            case '~' -> {
                if (c1 == '~') { advance(); advance(); return makeToken(TokenType.IN_RANGE,     "~~", startLine, startCol); }
                if (c1 == '=') { advance(); advance(); return makeToken(TokenType.TILDE_ASSIGN, "~=", startLine, startCol); }
                advance();
                return makeToken(TokenType.BIT_NOT, "~", startLine, startCol);
            }

            case '?' -> {
                if (c1 == '.') { advance(); advance(); return makeToken(TokenType.OPTIONAL_CHAIN,   "?.", startLine, startCol); }
                if (c1 == '?') { advance(); advance(); return makeToken(TokenType.NULLISH_COALESCE, "??", startLine, startCol); }
                advance();
                return makeToken(TokenType.QUESTION, "?", startLine, startCol);
            }

            default -> throw new CompileException(
                    "意外的字符: '" + c + "' (U+" + String.format("%04X", (int) c) + ")",
                    startLine, startCol);
        }
    }


    private char current() {
        return source.charAt(pos);
    }

    private char peek(int offset) {
        int idx = pos + offset;
        return idx < length ? source.charAt(idx) : '\0';
    }

    private void advance() {
        pos++;
        column++;
    }

    private Token makeToken(TokenType type, String value, int line, int column) {
        return new Token(type, value, line, column);
    }


    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fa5';
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_'
                || isChinese(c);
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }
}
