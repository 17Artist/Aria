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
import priv.seventeen.artist.aria.parser.javascript.JavaScriptParser;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.SandboxConfig;
import priv.seventeen.artist.aria.value.IValue;

public final class Aria {

    public enum Mode { ARIA, JAVASCRIPT }

    private static final AriaEngine DEFAULT_ENGINE = new AriaEngine();
    private static final Optimizer OPTIMIZER = new Optimizer();

    static {
        DEFAULT_ENGINE.initialize();
    }

    private Aria() {}

        public static AriaCompilationUnit compile(String name, Context context, String code) throws CompileException {
        return compile(name, context, code, detectMode(name));
    }

    public static AriaCompilationUnit compile(String name, Context context, String code, Mode mode) throws CompileException {
        ContentTracker tracker = new ContentTracker(code);
        ASTNode ast = parse(code, mode);
        IRProgram program = new Compiler().compile(name, ast);
        IRProgram optimized = OPTIMIZER.optimize(program);
        return new AriaCompilationUnit(name, optimized, context, tracker);
    }

        public static AriaCompiledRoutine compile(String name, String code) throws CompileException {
        return compile(name, code, detectMode(name));
    }

    public static AriaCompiledRoutine compile(String name, String code, Mode mode) throws CompileException {
        ContentTracker tracker = new ContentTracker(code);
        ASTNode ast = parse(code, mode);
        IRProgram program = new Compiler().compile(name, ast);
        IRProgram optimized = OPTIMIZER.optimize(program);
        return new AriaCompiledRoutine(name, optimized, tracker);
    }

        public static IValue<?> eval(String code, Context context) throws AriaException {
        AriaCompilationUnit unit = compile("eval", context, code, Mode.ARIA);
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


    private static ASTNode parse(String code, Mode mode) throws CompileException {
        if (mode == Mode.JAVASCRIPT) {
            Lexer lexer = new Lexer(code, true);
            JavaScriptParser parser = new JavaScriptParser(lexer);
            return parser.parse();
        }
        Lexer lexer = new Lexer(code);
        AriaParser parser = new AriaParser(lexer);
        return parser.parse();
    }

    public static Mode detectMode(String filename) {
        if (filename != null && filename.endsWith(".js")) return Mode.JAVASCRIPT;
        return Mode.ARIA;
    }
}
