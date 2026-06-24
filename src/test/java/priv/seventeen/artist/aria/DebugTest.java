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

import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.ast.ASTNode;
import priv.seventeen.artist.aria.compiler.Compiler;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.parser.Lexer;
import priv.seventeen.artist.aria.parser.aria.AriaParser;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.Result;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DebugTest {

    @Test
    void testParseAndCompileReturn42() throws Exception {
        Aria.getEngine().initialize();

        // 测试1: 简单 return
        testScript("return 42\n", 42.0);

        // 测试2: 裸标识符赋值 + return（ScopeStack）
        testScript("x = 10\nreturn x\n", 10.0);

        // 测试3: 简单加法
        testScript("x = 10\ny = 20\nreturn x + y\n", 30.0);

        // 测试4: var.x 赋值 + var.x 读取
        testScript("var.x = 10\nreturn var.x\n", 10.0);

        // 测试5: if 语句
        testScript("if (true) {\nreturn 99\n}\n", 99.0);
    }

    private void testScript(String code, double expected) throws Exception {
        System.out.println("\n=== Testing: " + code.replace("\n", " | ") + " ===");
        Lexer lexer = new Lexer(code);
        AriaParser parser = new AriaParser(lexer);
        ASTNode ast = parser.parse();
        System.out.println("AST type: " + ast.getClass().getSimpleName());

        Compiler compiler = new Compiler();
        IRProgram program = compiler.compile("test", ast);
        System.out.println("Instructions: " + program.getInstructions().length);
        for (int i = 0; i < program.getInstructions().length; i++) {
            System.out.println("  [" + i + "] " + program.getInstructions()[i]);
        }
        for (int i = 0; i < program.getConstants().length; i++) {
            System.out.println("  const[" + i + "] = " + program.getConstants()[i]);
        }

        Context ctx = new Context(new GlobalStorage());
        Interpreter interpreter = new Interpreter();
        Result result = interpreter.execute(program, ctx);
        System.out.println("Result: " + result.getValue().numberValue() + " (expected: " + expected + ")");
        assertEquals(expected, result.getValue().numberValue(), 0.001);
    }
}
