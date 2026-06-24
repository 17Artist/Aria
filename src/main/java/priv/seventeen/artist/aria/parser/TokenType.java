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

public enum TokenType {
    NUMBER, TEXT, TEXTPLUS, TEXT_BLOCK,
    TRUE, FALSE, NONE,
    
    IDENTIFIER,
    
    PLUS, MINUS, STAR, SLASH, PERCENT,
    
    ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN,
    TILDE_ASSIGN,  // ~= 初始化或获取
    
    EQ, NE, LT, GT, LE, GE,
    IN_RANGE,  // ~~
    
    AND, OR, NOT,
    
    BIT_AND, BIT_OR, BIT_XOR, BIT_NOT,
    SHL, SHR, USHR,  // << >> >>>
    BIT_AND_ASSIGN, BIT_OR_ASSIGN, BIT_XOR_ASSIGN,
    SHL_ASSIGN, SHR_ASSIGN, USHR_ASSIGN,
    
    INCREMENT, DECREMENT,
    
    ARROW,       // ->
    
    LPAREN, RPAREN,     // ( )
    LBRACKET, RBRACKET, // [ ]
    LBRACE, RBRACE,     // { }
    DOT, COMMA, SEMICOLON, COLON,
    OPTIONAL_CHAIN,     // ?.
    NULLISH_COALESCE,   // ??
    SPREAD,             // ...
    QUESTION,           // ?
    AT,                 // @
    
    NEWLINE,
    
    IF, ELIF, ELSE, WHILE, FOR, IN, INSTANCEOF, SWITCH, CASE, MATCH,
    BREAK, RETURN, NEXT,
    ASYNC, AWAIT,
    
    CLASS, EXTENDS, SUPER,
    TRY, CATCH, FINALLY, THROW,
    IMPORT, FROM, AS, EXPORT,

    EOF
}
