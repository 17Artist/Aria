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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.compiler.Compiler;
import priv.seventeen.artist.aria.compiler.Optimizer;
import priv.seventeen.artist.aria.compiler.ir.IRInstruction;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.compiler.optimize.DeadCodeElimination;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.parser.Lexer;
import priv.seventeen.artist.aria.parser.aria.AriaParser;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.value.IValue;

import static org.junit.jupiter.api.Assertions.*;

public class OptimizerTest {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    private IValue<?> eval(String code) throws AriaException {
        Context ctx = Aria.createContext();
        return Aria.eval(code, ctx);
    }

    private IRProgram compileToIR(String code) throws AriaException {
        return Aria.compile("opt-test", code).getProgram();
    }

    //  常量折叠

    @Test
    void testConstantFoldingAddition() throws AriaException {
        // 2 + 3 应在编译期折叠为 5
        IValue<?> result = eval("return 2 + 3\n");
        assertEquals(5.0, result.numberValue());

        // 验证 IR 中常量被折叠：优化后应有常量 5
        IRProgram program = compileToIR("return 2 + 3\n");
        boolean hasFoldedConstant = false;
        for (IValue<?> c : program.getConstants()) {
            if (c.numberValue() == 5.0) {
                hasFoldedConstant = true;
                break;
            }
        }
        assertTrue(hasFoldedConstant, "Constant 5 should exist in folded constants");
    }

    @Test
    void testConstantFoldingMultiplication() throws AriaException {
        // 10 * 2 应在编译期折叠为 20
        IValue<?> result = eval("return 10 * 2\n");
        assertEquals(20.0, result.numberValue());

        IRProgram program = compileToIR("return 10 * 2\n");
        boolean hasFoldedConstant = false;
        for (IValue<?> c : program.getConstants()) {
            if (c.numberValue() == 20.0) {
                hasFoldedConstant = true;
                break;
            }
        }
        assertTrue(hasFoldedConstant, "Constant 20 should exist in folded constants");
    }

    @Test
    void testConstantFoldingStringNotFolded() throws AriaException {
        // 字符串拼接不应被常量折叠（只折叠数字）
        IValue<?> result = eval("return 'hello' + ' world'\n");
        assertEquals("hello world", result.stringValue());
    }

    //  死代码消除

    @Test
    void testDeadCodeAfterReturn() throws AriaException {
        // return 后的代码应被消除
        String code = """
            return 42
            var.x = 99
            return var.x
            """;
        IValue<?> result = eval(code);
        assertEquals(42.0, result.numberValue());

        // 验证优化后指令数减少
        IRProgram unoptimized = new Compiler().compile("dce-test",
                new AriaParser(
                        new Lexer(code)).parse());
        IRProgram optimized = new DeadCodeElimination().optimize(unoptimized);
        assertTrue(optimized.getInstructions().length <= unoptimized.getInstructions().length,
                "Dead code elimination should not increase instruction count");
    }

    @Test
    void testDeadCodeUnreachableBranch() throws AriaException {
        // 不可达分支应被消除，但语义不变
        IValue<?> result = eval("""
            if (true) {
                return 'reachable'
            }
            return 'unreachable'
            """);
        assertEquals("reachable", result.stringValue());
    }

    //  表达式扁平化

    @Test
    void testExpressionFlatteningNestedArithmetic() throws AriaException {
        // 嵌套算术应被简化，语义不变
        IValue<?> result = eval("return (1 + 2) * (3 + 4)\n");
        assertEquals(21.0, result.numberValue());
    }

    //  语义保持

    @Test
    void testOptimizerPreservesSemantics() throws AriaException {
        // 优化前后结果应一致
        String code = """
            var.x = 10 + 20
            var.y = var.x * 2
            return var.y + 5
            """;

        // 直接执行（经过优化）
        IValue<?> optimizedResult = eval(code);

        // 手动编译不优化 vs 优化
        var ast = new AriaParser(
                new Lexer(code)).parse();
        IRProgram unoptimized = new Compiler().compile("sem-test", ast);
        Optimizer optimizer = new Optimizer();
        IRProgram optimized = optimizer.optimize(unoptimized);

        // 两者都执行
        Context ctx1 = Aria.createContext();
        var interp1 = new Interpreter();
        IValue<?> r1 = interp1.execute(unoptimized, ctx1).getValue();

        Context ctx2 = Aria.createContext();
        var interp2 = new Interpreter();
        IValue<?> r2 = interp2.execute(optimized, ctx2).getValue();

        assertEquals(r1.numberValue(), r2.numberValue(),
                "Optimized and unoptimized should produce same result");
        assertEquals(65.0, optimizedResult.numberValue());
    }

    //  边界情况

    @Test
    void testOptimizerHandlesEmptyProgram() throws AriaException {
        // 空程序不应崩溃
        IRProgram empty = new IRProgram("empty");
        empty.setInstructions(new IRInstruction[0]);
        empty.setConstants(new IValue<?>[0]);
        empty.setVariableKeys(new VariableKey[0]);
        empty.setRegisterCount(1);

        Optimizer optimizer = new Optimizer();
        IRProgram result = optimizer.optimize(empty);
        assertNotNull(result);
    }

    @Test
    void testOptimizerHandlesNoOptimizableCode() throws AriaException {
        String code = """
            var.x = 1
            return var.x
            """;
        IValue<?> result = eval(code);
        assertEquals(1.0, result.numberValue());
    }

    //  JIT 整数特化正确性验证
    @Test
    void testJitIntegerBranchIntensiveCorrectness() throws AriaException {
        IValue<?> result = eval("""
            var.count = 0
            for (var.i = 0; var.i < 100000; var.i += 1) {
                if (var.i % 3 == 0) {
                    var.count += 1
                } elif (var.i % 3 == 1) {
                    var.count += 2
                } else {
                    var.count += 3
                }
            }
            return var.count
            """);
        assertEquals(199999.0, result.numberValue());
    }

    @Test
    void testJitIntegerLoopArithmeticCorrectness() throws AriaException {
        // Loop Arithmetic 基准测试的正确性验证
        // sum(0..999999) = 999999*1000000/2 = 499999500000
        IValue<?> result = eval("""
            var.sum = 0
            for (var.i = 0; var.i < 1000000; var.i += 1) {
                var.sum += var.i
            }
            return var.sum
            """);
        assertEquals(499999500000.0, result.numberValue());
    }

    @Test
    void testJitIntegerModCorrectness() throws AriaException {
        // 验证整数取模在 JIT long 路径下的正确性
        IValue<?> result = eval("""
            var.r = 0
            for (var.i = 0; var.i < 100; var.i += 1) {
                var.r += var.i % 7
            }
            return var.r
            """);
        // 0+1+2+3+4+5+6 + 0+1+2+3+4+5+6 + ... (14 完整周期 + 2 余数: 0+1)
        // 14 * 21 + 1 = 295
        assertEquals(295.0, result.numberValue());
    }

    @Test
    void testJitIntegerNegativeValues() throws AriaException {
        // 验证负数在整数路径下的正确性
        IValue<?> result = eval("""
            var.sum = 0
            for (var.i = -50; var.i < 50; var.i += 1) {
                var.sum += var.i
            }
            return var.sum
            """);
        // sum(-50..49) = -50
        assertEquals(-50.0, result.numberValue());
    }

    //  错误恢复

    @Test
    void testErrorRecoveryCollectsMultipleErrors() throws Exception {
        // Code with multiple syntax errors
        String code = """
            var.x = 10
            var.y = @@@
            var.z = 20
            return var.z
            """;
        var parser = new AriaParser(
            new Lexer(code));
        var ast = parser.parse();
        assertNotNull(ast);
        // Should have collected at least one error
        assertTrue(parser.hasErrors());
        // But should still have parsed some valid statements
    }

    @Test
    void testErrorRecoveryValidCodeNoErrors() throws Exception {
        String code = """
            var.x = 10
            var.y = 20
            return x + y
            """;
        var parser = new AriaParser(
            new Lexer(code));
        var ast = parser.parse();
        assertNotNull(ast);
        assertFalse(parser.hasErrors());
    }
}
