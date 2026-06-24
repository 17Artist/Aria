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

package priv.seventeen.artist.aria;

import priv.seventeen.artist.aria.api.AriaCompilationUnit;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.api.AriaEngine;
import priv.seventeen.artist.aria.ast.ASTNode;
import priv.seventeen.artist.aria.compiler.Compiler;
import priv.seventeen.artist.aria.compiler.Optimizer;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.CompileException;
import priv.seventeen.artist.aria.parser.ContentTracker;
import priv.seventeen.artist.aria.parser.Lexer;
import priv.seventeen.artist.aria.parser.aria.AriaParser;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.SandboxConfig;
import priv.seventeen.artist.aria.value.IValue;

public final class Aria {

    private static final AriaEngine DEFAULT_ENGINE = new AriaEngine();
    private static final Optimizer OPTIMIZER = new Optimizer();

    static {
        DEFAULT_ENGINE.initialize();
    }

    private Aria() {}

    public static AriaCompilationUnit compile(String name, Context context, String code) throws CompileException {
        ContentTracker tracker = new ContentTracker(code);
        ASTNode ast = parse(code);
        IRProgram program = new Compiler().compile(name, ast);
        IRProgram optimized = OPTIMIZER.optimize(program);
        return new AriaCompilationUnit(name, optimized, context, tracker);
    }

    public static AriaCompiledRoutine compile(String name, String code) throws CompileException {
        ContentTracker tracker = new ContentTracker(code);
        ASTNode ast = parse(code);
        IRProgram program = new Compiler().compile(name, ast);
        IRProgram optimized = OPTIMIZER.optimize(program);
        return new AriaCompiledRoutine(name, optimized, tracker);
    }

    public static IValue<?> eval(String code, Context context) throws AriaException {
        AriaCompilationUnit unit = compile("eval", context, code);
        try {
            return unit.execute();
        } finally {
            Interpreter.resetCallDepth();
        }
    }

    public static IValue<?> eval(String code, Context context, SandboxConfig sandbox) throws AriaException {
        Interpreter.setSandbox(sandbox);
        try {
            return eval(code, context);
        } finally {
            Interpreter.clearSandbox();
        }
    }

    public static Context createContext() {
        return DEFAULT_ENGINE.createContext();
    }

    public static AriaEngine getEngine() {
        return DEFAULT_ENGINE;
    }


    private static ASTNode parse(String code) throws CompileException {
        Lexer lexer = new Lexer(code);
        AriaParser parser = new AriaParser(lexer);
        ASTNode ast = parser.parse();
        // fail-fast：任何解析错误立即抛出，不静默丢弃出错的语句。
        // （LSP 等需要部分 AST 的消费方直接调用 AriaParser.parse()，不经此入口，故不受影响。）
        if (parser.hasErrors()) {
            throw parser.getErrors().get(0);
        }
        return ast;
    }
}
