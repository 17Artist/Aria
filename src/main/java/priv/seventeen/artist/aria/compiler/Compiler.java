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

package priv.seventeen.artist.aria.compiler;

import priv.seventeen.artist.aria.ast.ASTNode;
import priv.seventeen.artist.aria.ast.expression.*;
import priv.seventeen.artist.aria.ast.statement.*;
import priv.seventeen.artist.aria.compiler.ir.IRInstruction;
import priv.seventeen.artist.aria.compiler.ir.IROpCode;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.parser.SourceLocation;
import priv.seventeen.artist.aria.annotation.AnnotationProcessor;
import priv.seventeen.artist.aria.annotation.AriaAnnotation;
import priv.seventeen.artist.aria.value.*;

import java.util.*;

public class Compiler {

    private static final Set<String> DOT_NAMESPACES = Set.of(
            "var", "val", "global", "server", "client"
    );

    private final List<IRInstruction> instructions = new ArrayList<>();
    private final List<SourceLocation> sourceMap = new ArrayList<>();
    private final List<IValue<?>> constants = new ArrayList<>();
    private final List<VariableKey> variableKeys = new ArrayList<>();
    private final List<IRProgram> subPrograms = new ArrayList<>();
    private int registerCounter = 0;
    private int labelCounter = 0;


    private final Set<String> importAliases = new HashSet<>();

    private Compiler forSubProgram() {
        Compiler sub = new Compiler();
        sub.importAliases.addAll(this.importAliases);
        return sub;
    }


    private static final class LoopFrame {
        static final int FOR = 0;
        static final int WHILE = 1;
        static final int SWITCH = 2;

        final int type;
        final List<Integer> breakJumps = new ArrayList<>();  // break 占位 JUMP 的指令位置，帧关闭时 patchJump 回填到消耗点
        final List<Integer> nextJumps = new ArrayList<>();   // next 占位 JUMP 的指令位置（FOR 帧），帧关闭时回填到 continue 点
        int whileCondStart = -1;    // WHILE：条件检查起始 PC（next 回跳目标）
        int whileLeakFlagReg = -1;  // WHILE：尾部 next 泄漏标志寄存器
        boolean whileLeakUsed = false; // WHILE：体内是否出现过指向本帧的 next（含内层 while 泄漏链）

        LoopFrame(int type) { this.type = type; }
    }

    private final Deque<LoopFrame> loopFrames = new ArrayDeque<>();


    public IRProgram compile(String name, ASTNode root) {
        // 重置状态
        instructions.clear();
        sourceMap.clear();
        constants.clear();
        variableKeys.clear();
        subPrograms.clear();
        loopFrames.clear();
        registerCounter = 0;
        labelCounter = 0;

        // Shimmer 对齐(controlflow-08)：隐式返回按 BlockStatement"末语句结果值"语义——
        // 末语句为 if/else 时两分支值汇入统一寄存器、while 为末次循环体值(0 次为 none)、
        // for-in/switch/语句形 async(async-6) 为 none、直线表达式为其值。
        int resultReg = compileStatementValue(root);
        if (!instructions.isEmpty() && instructions.get(instructions.size() - 1).opcode != IROpCode.RETURN) {
            emit(IRInstruction.of(IROpCode.RETURN, resultReg));
        }
        IRProgram program = new IRProgram(name);
        program.setInstructions(instructions.toArray(new IRInstruction[0]));
        program.setConstants(constants.toArray(new IValue<?>[0]));
        program.setVariableKeys(variableKeys.toArray(new VariableKey[0]));
        program.setRegisterCount(registerCounter);
        program.setSourceMap(sourceMap.toArray(new SourceLocation[0]));
        program.setSubPrograms(subPrograms.toArray(new IRProgram[0]));
        return program;
    }


    private int nextRegister() {
        return registerCounter++;
    }


    private int addConstant(IValue<?> value) {
        for (int i = 0; i < constants.size(); i++) {
            IValue<?> existing = constants.get(i);
            if (existing.typeID() == value.typeID()
                    && existing.stringValue().equals(value.stringValue())) {
                return i;
            }
        }
        constants.add(value);
        return constants.size() - 1;
    }


    private int addVariableKey(VariableKey key) {
        for (int i = 0; i < variableKeys.size(); i++) {
            if (variableKeys.get(i).equals(key)) return i;
        }
        variableKeys.add(key);
        return variableKeys.size() - 1;
    }

    private int addVariableKey(String name) {
        return addVariableKey(VariableKey.of(name));
    }


    private void emit(IRInstruction inst) {
        instructions.add(inst);
        sourceMap.add(SourceLocation.UNKNOWN);
    }

    private void emit(IRInstruction inst, SourceLocation loc) {
        instructions.add(inst);
        sourceMap.add(loc != null ? loc : SourceLocation.UNKNOWN);
    }

    private int currentPC() {
        return instructions.size();
    }

    private void patchJump(int pc, int target) {
        instructions.get(pc).a = target;
    }


    private int compileNode(ASTNode node, int dstHint) {
        if (node == null) return -1;
        int dst = dstHint >= 0 ? dstHint : nextRegister();

        if (node instanceof LiteralExpr expr) {
            return compileLiteral(expr, dst);
        } else if (node instanceof IdentifierExpr expr) {
            return compileIdentifier(expr, dst);
        } else if (node instanceof BinaryExpr expr) {
            return compileBinary(expr, dst);
        } else if (node instanceof UnaryExpr expr) {
            return compileUnary(expr, dst);
        } else if (node instanceof TernaryExpr expr) {
            return compileTernary(expr, dst);
        } else if (node instanceof AssignmentExpr expr) {
            return compileAssignment(expr, dst);
        } else if (node instanceof DotExpr expr) {
            return compileDot(expr, dst);
        } else if (node instanceof CallExpr expr) {
            return compileCall(expr, dst);
        } else if (node instanceof IndexExpr expr) {
            return compileIndex(expr, dst);
        } else if (node instanceof LambdaExpr expr) {
            return compileLambda(expr, dst);
        } else if (node instanceof ListExpr expr) {
            return compileList(expr, dst);
        } else if (node instanceof MapExpr expr) {
            return compileMap(expr, dst);
        } else if (node instanceof InterpolatedStringExpr expr) {
            return compileInterpolatedString(expr, dst);
        } else if (node instanceof NewExpr expr) {
            return compileNew(expr, dst);
        } else if (node instanceof OptionalChainExpr expr) {
            return compileOptionalChain(expr, dst);
        } else if (node instanceof AwaitExpr expr) {
            int opReg = compileNode(expr.getOperand(), -1);
            emit(IRInstruction.of(IROpCode.AWAIT, dst, opReg), expr.getLocation());
            return dst;
        } else if (node instanceof SpreadExpr) {
            // SpreadExpr 在 compileList/compileCall 中特殊处理，不应单独出现
            return dst;
        } else if (node instanceof AnnotationExpr) {
            // 注解在编译期处理，不生成运行时指令
            return dst;
        }
        else if (node instanceof BlockStmt stmt) {
            compileBlock(stmt);
        } else if (node instanceof ExpressionStmt stmt) {
            compileExpressionStmt(stmt, dst);
        } else if (node instanceof IfStmt stmt) {
            compileIf(stmt);
        } else if (node instanceof WhileStmt stmt) {
            compileWhile(stmt);
        } else if (node instanceof ForInStmt stmt) {
            compileForIn(stmt);
        } else if (node instanceof ForStmt stmt) {
            compileFor(stmt);
        } else if (node instanceof SwitchStmt stmt) {
            compileSwitch(stmt);
        } else if (node instanceof AsyncStmt stmt) {
            return compileAsync(stmt, dstHint);
        } else if (node instanceof TryCatchStmt stmt) {
            compileTryCatch(stmt);
        } else if (node instanceof ClassDeclStmt stmt) {
            compileClassDecl(stmt);
        } else if (node instanceof ImportStmt stmt) {
            compileImport(stmt);
        } else if (node instanceof DestructureStmt stmt) {
            compileDestructure(stmt);
        } else if (node instanceof ExportStmt stmt) {
            compileExport(stmt);
        } else if (node instanceof ReturnStmt stmt) {
            compileReturn(stmt, dst);
        }

        return dst;
    }


    private int compileLiteral(LiteralExpr expr, int dst) {
        IValue<?> value = expr.getValue();
        if (value instanceof NoneValue) {
            emit(IRInstruction.of(IROpCode.LOAD_NONE, dst), expr.getLocation());
        } else if (value instanceof BooleanValue bv) {
            emit(IRInstruction.of(bv.booleanValue() ? IROpCode.LOAD_TRUE : IROpCode.LOAD_FALSE, dst),
                    expr.getLocation());
        } else {
            int ci = addConstant(value);
            emit(IRInstruction.of(IROpCode.LOAD_CONST, dst, ci), expr.getLocation());
        }
        return dst;
    }

    private int compileIdentifier(IdentifierExpr expr, int dst) {
        String name = expr.getName();
        switch (name) {
            case "self" -> emit(IRInstruction.of(IROpCode.LOAD_SELF, dst), expr.getLocation());
            case "args" -> emit(IRInstruction.of(IROpCode.LOAD_ARGS, dst), expr.getLocation());
            case "true" -> emit(IRInstruction.of(IROpCode.LOAD_TRUE, dst), expr.getLocation());
            case "false" -> emit(IRInstruction.of(IROpCode.LOAD_FALSE, dst), expr.getLocation());
            case "none" -> emit(IRInstruction.of(IROpCode.LOAD_NONE, dst), expr.getLocation());
            default -> {
                // Shimmer 对齐(controlflow-11)：for-in 循环变量已改真实 scope 存储，
                // 寄存器别名机制(registerAliases)废弃——裸名一律 LOAD_SCOPE。
                // 例外：import 别名(文件级词法绑定,存 var 存储)编译为 LOAD_VAR——
                // lambda 体隔离(R2)后仍可在嵌套体内引用同文件 import 的符号。
                int ki = addVariableKey(name);
                emit(IRInstruction.of(importAliases.contains(name) ? IROpCode.LOAD_VAR : IROpCode.LOAD_SCOPE,
                        dst, ki), expr.getLocation());
            }
        }
        return dst;
    }

    private int compileBinary(BinaryExpr expr, int dst) {
        // AND/OR：短路求值 + 结果规整为布尔（Shimmer 对齐）。必须在编译右操作数前拦截，
        // 否则右操作数指令会被无条件发射→丧失短路。
        BinaryExpr.BinaryOp binOp = expr.getOperator();
        if (binOp == BinaryExpr.BinaryOp.AND || binOp == BinaryExpr.BinaryOp.OR) {
            return compileLogical(expr, dst);
        }
        int leftReg = compileNode(expr.getLeft(), -1);
        int rightReg = compileNode(expr.getRight(), -1);
        IROpCode op = switch (expr.getOperator()) {
            case ADD -> IROpCode.ADD;
            case SUB -> IROpCode.SUB;
            case MUL -> IROpCode.MUL;
            case DIV -> IROpCode.DIV;
            case MOD -> IROpCode.MOD;
            case EQ -> IROpCode.EQ;
            case NE -> IROpCode.NE;
            case LT -> IROpCode.LT;
            case GT -> IROpCode.GT;
            case LE -> IROpCode.LE;
            case GE -> IROpCode.GE;
            case IN_RANGE -> IROpCode.IN_RANGE;
            case AND -> IROpCode.AND;
            case OR -> IROpCode.OR;
            case BIT_AND -> IROpCode.BIT_AND;
            case BIT_OR -> IROpCode.BIT_OR;
            case BIT_XOR -> IROpCode.BIT_XOR;
            case SHL -> IROpCode.SHL;
            case SHR -> IROpCode.SHR;
            case USHR -> IROpCode.USHR;
            case NULLISH_COALESCE -> IROpCode.JUMP_IF_NONE;
            case IN_OBJ -> IROpCode.IN_CHECK;
            case INSTANCEOF -> IROpCode.INSTANCEOF_CHECK;
        };
        if (expr.getOperator() == BinaryExpr.BinaryOp.NULLISH_COALESCE) {
            // a ?? b: 如果a为none则用b
            int jumpPC = currentPC();
            emit(IRInstruction.of(IROpCode.JUMP_IF_NONE, leftReg, 0), expr.getLocation());
            emit(IRInstruction.of(IROpCode.MOVE, dst, leftReg), expr.getLocation());
            int skipPC = currentPC();
            emit(IRInstruction.of(IROpCode.JUMP, 0), expr.getLocation());
            patchJump(jumpPC, currentPC());
            emit(IRInstruction.of(IROpCode.MOVE, dst, rightReg), expr.getLocation());
            patchJump(skipPC, currentPC());
        } else {
            emit(IRInstruction.of(op, dst, leftReg, rightReg), expr.getLocation());
        }
        return dst;
    }

    /**
     * 编译 {@code &&} / {@code ||}：<b>短路求值 + 结果规整为布尔</b>（Shimmer 对齐）。
     * <pre>
     *   a && b:  eval a; if(!a) -> dst=FALSE; else eval b; dst=bool(b)
     *   a || b:  eval a; if( a) -> dst=TRUE ; else eval b; dst=bool(b)
     * </pre>
     * 右操作数指令在跳转之后才发射，故左侧决定结果时不会执行右侧（真短路）。
     * 用双 NOT 把右操作数值规整为 BooleanValue（NOT 产 {@code BooleanValue.of(!x)}，再 NOT 得 {@code bool(x)}）。
     */
    private int compileLogical(BinaryExpr expr, int dst) {
        boolean isAnd = expr.getOperator() == BinaryExpr.BinaryOp.AND;
        if (dst < 0) dst = nextRegister();
        int leftReg = compileNode(expr.getLeft(), -1);
        int jShort = currentPC();
        emit(IRInstruction.of(isAnd ? IROpCode.JUMP_IF_FALSE : IROpCode.JUMP_IF_TRUE, leftReg, 0), expr.getLocation());
        // 未短路：dst = 右操作数的布尔值
        int rightReg = compileNode(expr.getRight(), -1);
        emit(IRInstruction.of(IROpCode.NOT, dst, rightReg), expr.getLocation());
        emit(IRInstruction.of(IROpCode.NOT, dst, dst), expr.getLocation());
        int jEnd = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP, 0), expr.getLocation());
        // 短路：AND->FALSE, OR->TRUE
        patchJump(jShort, currentPC());
        emit(IRInstruction.of(isAnd ? IROpCode.LOAD_FALSE : IROpCode.LOAD_TRUE, dst), expr.getLocation());
        patchJump(jEnd, currentPC());
        return dst;
    }

    private int compileUnary(UnaryExpr expr, int dst) {
        int operandReg = compileNode(expr.getOperand(), -1);
        switch (expr.getOperator()) {
            case NEG -> emit(IRInstruction.of(IROpCode.NEG, dst, operandReg), expr.getLocation());
            case NOT -> emit(IRInstruction.of(IROpCode.NOT, dst, operandReg), expr.getLocation());
            case BIT_NOT -> emit(IRInstruction.of(IROpCode.BIT_NOT, dst, operandReg), expr.getLocation());
            case INCREMENT -> {
                if (expr.isPrefix()) {
                    emit(IRInstruction.of(IROpCode.INC, operandReg, operandReg), expr.getLocation());
                    emit(IRInstruction.of(IROpCode.MOVE, dst, operandReg), expr.getLocation());
                    emitStoreBack(expr.getOperand(), operandReg, expr.getLocation());
                } else {
                    emit(IRInstruction.of(IROpCode.MOVE, dst, operandReg), expr.getLocation());
                    emit(IRInstruction.of(IROpCode.INC, operandReg, operandReg), expr.getLocation());
                    emitStoreBack(expr.getOperand(), operandReg, expr.getLocation());
                }
            }
            case DECREMENT -> {
                if (expr.isPrefix()) {
                    emit(IRInstruction.of(IROpCode.DEC, operandReg, operandReg), expr.getLocation());
                    emit(IRInstruction.of(IROpCode.MOVE, dst, operandReg), expr.getLocation());
                    emitStoreBack(expr.getOperand(), operandReg, expr.getLocation());
                } else {
                    emit(IRInstruction.of(IROpCode.MOVE, dst, operandReg), expr.getLocation());
                    emit(IRInstruction.of(IROpCode.DEC, operandReg, operandReg), expr.getLocation());
                    emitStoreBack(expr.getOperand(), operandReg, expr.getLocation());
                }
            }
            default -> emit(IRInstruction.of(IROpCode.NOP, dst), expr.getLocation());
        }
        return dst;
    }

    private int compileTernary(TernaryExpr expr, int dst) {
        int condReg = compileNode(expr.getCondition(), -1);
        int falseJump = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP_IF_FALSE, condReg, 0), expr.getLocation());

        // then 分支
        if (expr.getThenExpr() != null) {
            compileNode(expr.getThenExpr(), dst);
        } else {
            // a ?: c 形式 — then 分支返回条件值本身
            emit(IRInstruction.of(IROpCode.MOVE, dst, condReg), expr.getLocation());
        }
        int endJump = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP, 0), expr.getLocation());

        // else 分支
        patchJump(falseJump, currentPC());
        if (expr.getElseExpr() != null) {
            compileNode(expr.getElseExpr(), dst);
        } else {
            emit(IRInstruction.of(IROpCode.LOAD_NONE, dst), expr.getLocation());
        }
        patchJump(endJump, currentPC());
        return dst;
    }

    private int compileAssignment(AssignmentExpr expr, int dst) {
        ASTNode target = expr.getTarget();

        // ~= 运算符
        if (expr.getOperator() == AssignmentExpr.AssignOp.INIT_OR_GET) {
            int valueReg = compileNode(expr.getValue(), -1);
            if (target instanceof DotExpr dot && isDotNamespace(dot)) {
                String ns = ((IdentifierExpr) dot.getObject()).getName();
                int ki = addVariableKey(dot.getProperty());
                IRInstruction inst = IRInstruction.of(IROpCode.INIT_OR_GET, dst, ki, valueReg);
                inst.name = ns;
                emit(inst, expr.getLocation());
            } else if (target instanceof IdentifierExpr id) {
                int ki = addVariableKey(id.getName());
                emit(IRInstruction.of(IROpCode.INIT_OR_GET, dst, ki, valueReg), expr.getLocation());
            }
            return dst;
        }

        // 复合赋值
        if (expr.getOperator() != AssignmentExpr.AssignOp.ASSIGN) {
            if (expr.getOperator() == AssignmentExpr.AssignOp.PLUS_ASSIGN
                    && target instanceof DotExpr dot && isDotNamespace(dot)) {
                String ns = ((IdentifierExpr) dot.getObject()).getName();
                if ("var".equals(ns)) {
                    int ki = addVariableKey(dot.getProperty());
                    ASTNode valueNode = expr.getValue();
                    // var.x += 1 → VAR_INC
                    if (valueNode instanceof LiteralExpr lit && lit.getValue() instanceof NumberValue nv && nv.value == 1.0) {
                        emit(IRInstruction.of(IROpCode.VAR_INC, dst, ki), expr.getLocation());
                        return dst;
                    }
                    // var.x += const → VAR_ADD_CONST
                    if (valueNode instanceof LiteralExpr lit2 && lit2.getValue() instanceof NumberValue) {
                        int ci = addConstant(lit2.getValue());
                        emit(IRInstruction.of(IROpCode.VAR_ADD_CONST, dst, ki, ci), expr.getLocation());
                        return dst;
                    }
                    // var.x += expr → VAR_ADD_REG
                    int valueReg = compileNode(valueNode, -1);
                    emit(IRInstruction.of(IROpCode.VAR_ADD_REG, dst, ki, valueReg), expr.getLocation());
                    return dst;
                }
            }

            int currentReg = compileNode(target, -1);
            int valueReg = compileNode(expr.getValue(), -1);
            IROpCode op = switch (expr.getOperator()) {
                case PLUS_ASSIGN -> IROpCode.ADD;
                case MINUS_ASSIGN -> IROpCode.SUB;
                case STAR_ASSIGN -> IROpCode.MUL;
                case SLASH_ASSIGN -> IROpCode.DIV;
                case PERCENT_ASSIGN -> IROpCode.MOD;
                case BIT_AND_ASSIGN -> IROpCode.BIT_AND;
                case BIT_OR_ASSIGN -> IROpCode.BIT_OR;
                case BIT_XOR_ASSIGN -> IROpCode.BIT_XOR;
                case SHL_ASSIGN -> IROpCode.SHL;
                case SHR_ASSIGN -> IROpCode.SHR;
                case USHR_ASSIGN -> IROpCode.USHR;
                default -> IROpCode.NOP;
            };
            int resultReg = nextRegister();
            emit(IRInstruction.of(op, resultReg, currentReg, valueReg), expr.getLocation());
            emitStore(target, resultReg, expr.getLocation());
            return resultReg;
        }

        // 简单赋值
        if (target instanceof DotExpr dot && isDotNamespace(dot)) {
            String ns = ((IdentifierExpr) dot.getObject()).getName();
            if ("var".equals(ns) && expr.getValue() instanceof BinaryExpr bin
                    && bin.getOperator() == BinaryExpr.BinaryOp.ADD) {
                // 检测 var.x = var.x + expr 或 var.x = expr + var.x
                String prop = dot.getProperty();
                if (isSameVarDot(bin.getLeft(), "var", prop)) {
                    int ki = addVariableKey(prop);
                    int valueReg = compileNode(bin.getRight(), -1);
                    emit(IRInstruction.of(IROpCode.VAR_ADD_REG, dst, ki, valueReg), expr.getLocation());
                    return dst;
                }
            }
        }
        int valueReg = compileNode(expr.getValue(), -1);
        // 追踪 var.xxx = -> {} 定义的函数名
        if (target instanceof DotExpr dot && isDotNamespace(dot)
                && "var".equals(((IdentifierExpr) dot.getObject()).getName())
                && expr.getValue() instanceof LambdaExpr) {
            // 把变量名传给 lambda 的 subProgram，便于 JIT 识别 CALL_STATIC 形式的自递归
            if (!subPrograms.isEmpty()) {
                subPrograms.get(subPrograms.size() - 1).setName(dot.getProperty());
            }
        }
        // Shimmer 对齐(R2 系, Assignment.getResult)：`x = f` / `x = var.f` 等"RHS 为裸变量读"
        // 的简单赋值——若读到可调用值则零参自动调用后再存(Shimmer 中具名 lambda 不作为一等值
        // 流动,实测 X13/Y06/Y20/cases3#7)。lambda 字面量 RHS 不发射(直接存函数)；其余 RHS 形态
        // (调用结果/字面量/运算)保持 Aria 一等函数值语义(自由区超集)。
        if (isBareVariableRead(expr.getValue())) {
            emit(IRInstruction.of(IROpCode.AUTO_INVOKE, valueReg), expr.getLocation());
        }
        emitStore(target, valueReg, expr.getLocation());
        // 返回 valueReg 作为表达式结果，避免多余 MOVE
        return valueReg;
    }

    /** RHS 是否为"裸变量读"：标识符(scope 读)或 var./val./global./server./client. 命名空间点读。 */
    private boolean isBareVariableRead(ASTNode node) {
        if (node instanceof IdentifierExpr) return true;
        return node instanceof DotExpr dot && isDotNamespace(dot);
    }

    /** 检查节点是否为 DotExpr(Identifier(ns), prop) */
    private boolean isSameVarDot(ASTNode node, String ns, String prop) {
        return node instanceof DotExpr d
                && d.getObject() instanceof IdentifierExpr id
                && ns.equals(id.getName())
                && prop.equals(d.getProperty());
    }

    private int compileDot(DotExpr expr, int dst) {
        ASTNode obj = expr.getObject();
        String prop = expr.getProperty();

        // Dot 命名空间系统: var.x, val.x, global.x, server.x, client.x
        if (obj instanceof IdentifierExpr id) {
            String ns = id.getName();
            int ki = addVariableKey(prop);
            switch (ns) {
                case "var" -> {
                    emit(IRInstruction.of(IROpCode.LOAD_VAR, dst, ki), expr.getLocation());
                    return dst;
                }
                case "val" -> {
                    emit(IRInstruction.of(IROpCode.LOAD_VAL, dst, ki), expr.getLocation());
                    return dst;
                }
                case "global" -> {
                    emit(IRInstruction.of(IROpCode.LOAD_GLOBAL, dst, ki), expr.getLocation());
                    return dst;
                }
                case "server" -> {
                    emit(IRInstruction.of(IROpCode.LOAD_SERVER, dst, ki), expr.getLocation());
                    return dst;
                }
                case "client" -> {
                    emit(IRInstruction.of(IROpCode.LOAD_CLIENT, dst, ki), expr.getLocation());
                    return dst;
                }
                case "args" -> {
                    // args.0, args.1 等 — 按索引加载参数
                    try {
                        int argIndex = Integer.parseInt(prop);
                        emit(IRInstruction.of(IROpCode.LOAD_ARG, dst, argIndex), expr.getLocation());
                    } catch (NumberFormatException e) {
                        // args.length 等属性访问
                        int argsReg = nextRegister();
                        emit(IRInstruction.of(IROpCode.LOAD_ARGS, argsReg), expr.getLocation());
                        emit(IRInstruction.of(IROpCode.GET_PROP, dst, argsReg).withName(prop), expr.getLocation());
                    }
                    return dst;
                }
            }
        }

        // 普通属性访问
        int objReg = compileNode(obj, -1);
        emit(IRInstruction.of(IROpCode.GET_PROP, dst, objReg).withName(prop), expr.getLocation());
        return dst;
    }

    private int compileCall(CallExpr expr, int dst) {
        ASTNode callee = expr.getCallee();
        List<ASTNode> args = expr.getArguments();

        // 编译参数到连续寄存器
        int argBase = registerCounter;
        int[] argDstRegs = new int[args.size()];
        for (int i = 0; i < args.size(); i++) {
            argDstRegs[i] = nextRegister(); // 预分配连续寄存器
        }
        for (int i = 0; i < args.size(); i++) {
            int resultReg = compileNode(args.get(i), -1);
            if (resultReg != argDstRegs[i]) {
                emit(IRInstruction.of(IROpCode.MOVE, argDstRegs[i], resultReg), expr.getLocation());
            }
        }

        // 方法调用: obj.method(args)
        if (callee instanceof DotExpr dot) {
            ASTNode obj = dot.getObject();
            String method = dot.getProperty();

            // var.fib(x), val.func(x) — 从命名空间存储加载函数值后调用
            if (obj instanceof IdentifierExpr id && DOT_NAMESPACES.contains(id.getName())) {
                int funcReg = nextRegister();
                compileDot(dot, funcReg); // 加载 var.fib 的值到 funcReg
                IRInstruction inst = IRInstruction.of(IROpCode.CALL, dst, funcReg, args.size());
                inst.c = argBase;
                emit(inst, expr.getLocation());
                return dst;
            }

            // super.method(args) — 父类方法调用
            if (obj instanceof IdentifierExpr id && "super".equals(id.getName())) {
                IRInstruction inst = IRInstruction.of(IROpCode.INVOKE_SUPER, dst, argBase, args.size());
                inst.name = method;
                inst.c = argBase;
                emit(inst, expr.getLocation());
                return dst;
            }

            // 命名空间静态调用: math.sin(x), console.log(x)
            // 例外："self" 是自身对象(LOAD_SELF)、不是命名空间 → self.method() 必须走下方的普通方法调用
            // (否则被编成 CALL_STATIC "self.method" → 查不到命名空间 → NONE，所有 self.xxx() 皆失效)。
            if (obj instanceof IdentifierExpr id && !"self".equals(id.getName())) {
                String ns = id.getName();
                IRInstruction inst = IRInstruction.of(IROpCode.CALL_STATIC, dst, argBase, args.size());
                inst.name = ns + "." + method;
                emit(inst, expr.getLocation());
                return dst;
            }

            // 普通方法调用
            int objReg = compileNode(obj, -1);
            IRInstruction inst = IRInstruction.of(IROpCode.CALL_METHOD, dst, objReg, args.size());
            inst.name = method;
            inst.c = argBase;
            emit(inst, expr.getLocation());
            return dst;
        }

        // 构造器调用: Range(1, 5), UUID() 等
        if (callee instanceof IdentifierExpr id) {
            String name = id.getName();
            // 首字母大写 → 构造器
            if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
                IRInstruction inst = IRInstruction.of(IROpCode.CALL_CONSTRUCTOR, dst, argBase, args.size());
                inst.name = name;
                emit(inst, expr.getLocation());
                return dst;
            }
            // 裸函数调用: print(x), println(x) 等 → CALL_STATIC
            IRInstruction inst = IRInstruction.of(IROpCode.CALL_STATIC, dst, argBase, args.size());
            inst.name = name;
            emit(inst, expr.getLocation());
            return dst;
        }

        // 普通函数调用（callee 是表达式）
        // Shimmer 对齐(A8)：callee 是索引表达式(m['k'])时,以 no-resolve 模式发射 GET_INDEX
        // (c=3)——取出**原始** StoreOnlyValue<CWI> 交给 CALL 带参调用,而非在取值点无参 auto-invoke。
        // 无后续调用的 m['k'] 仍走普通 compileIndex(c=0/resolve),消费点 auto-invoke 保持不变。
        int calleeReg;
        if (callee instanceof IndexExpr idxCallee) {
            calleeReg = compileIndex(idxCallee, -1, true);
        } else {
            calleeReg = compileNode(callee, -1);
        }
        IRInstruction inst = IRInstruction.of(IROpCode.CALL, dst, calleeReg, args.size());
        inst.c = argBase;
        emit(inst, expr.getLocation());
        return dst;
    }

    private int compileIndex(IndexExpr expr, int dst) {
        return compileIndex(expr, dst, false);
    }

    /**
     * @param rawCallee true 表示该索引取值作为调用目标(callee)——GET_INDEX 以 no-resolve 模式(c=3)
     *                  发射,取出原始值(如 StoreOnlyValue&lt;CWI&gt;)供 CALL 带参调用;不在取值点 auto-invoke。
     *                  (Shimmer 对齐 A8：后缀链先接完再于消费点求值。)
     */
    private int compileIndex(IndexExpr expr, int dst, boolean rawCallee) {
        // rawCallee 路径可能以 dst=-1 直接进入(compileCall 未过 compileNode 分配)——就地分配寄存器。
        if (dst < 0) dst = nextRegister();
        boolean argsAccess = expr.getObject() instanceof IdentifierExpr aid && "args".equals(aid.getName());
        if (argsAccess && expr.getIndex() instanceof LiteralExpr lit && lit.getValue() instanceof NumberValue nv) {
            int argIdx = (int) nv.numberValue();
            emit(IRInstruction.of(IROpCode.LOAD_ARG, dst, argIdx), expr.getLocation());
            return dst;
        }

        int objReg = compileNode(expr.getObject(), -1);
        if (expr.getIndex() != null) {
            int idxReg = compileNode(expr.getIndex(), -1);
            IRInstruction gi = IRInstruction.of(IROpCode.GET_INDEX, dst, objReg, idxReg);
            // Shimmer 对齐(controlflow-07, ArgsDot)：args[动态下标] 越界返回 none 而非抛
            // "列表索引越界"——c=2 标记 args 索引模式(与 LOAD_ARG 常量形一致)。
            // Shimmer 对齐(A8)：rawCallee(索引取值当 callee) → c=3 no-resolve,取原始值(不 auto-invoke CWI),
            // 供 CALL 带参调用。argsAccess 优先(args[i] 不会是 CWI map,二者互斥)。
            if (argsAccess) gi.withC(2);
            else if (rawCallee) gi.withC(3);
            emit(gi, expr.getLocation());
        } else {
            // 空索引 m[] 作为 callee 无意义;rawCallee 时也不 resolve(返回对象本身,与现状一致)。
            emit(IRInstruction.of(IROpCode.GET_INDEX, dst, objReg, -1), expr.getLocation());
        }
        return dst;
    }

    private int compileLambda(LambdaExpr expr, int dst) {
        // 编译函数体为子程序
        Compiler subCompiler = forSubProgram();
        IRProgram subProg = subCompiler.compile("<lambda>", expr.getBody());

        // 隐式返回：如果最后一条指令不是 RETURN，插入 RETURN
        IRInstruction[] subCode = subProg.getInstructions();
        if (subCode.length > 0) {
            IROpCode lastOp = subCode[subCode.length - 1].opcode;
            if (lastOp != IROpCode.RETURN) {
                // 找到最后一个有意义的指令的 dst 寄存器作为返回值
                int lastDst = -1;
                for (int i = subCode.length - 1; i >= 0; i--) {
                    IROpCode op = subCode[i].opcode;
                    if (op != IROpCode.NOP && op != IROpCode.POP_SCOPE && op != IROpCode.PUSH_SCOPE) {
                        lastDst = subCode[i].dst;
                        break;
                    }
                }
                // 追加 RETURN 指令
                IRInstruction[] newCode = new IRInstruction[subCode.length + 1];
                System.arraycopy(subCode, 0, newCode, 0, subCode.length);
                newCode[subCode.length] = IRInstruction.of(IROpCode.RETURN, lastDst);
                subProg.setInstructions(newCode);
                // 同步 sourceMap
                SourceLocation[] srcMap = subProg.getSourceMap();
                if (srcMap != null) {
                    SourceLocation[] newSrcMap = new SourceLocation[srcMap.length + 1];
                    System.arraycopy(srcMap, 0, newSrcMap, 0, srcMap.length);
                    subProg.setSourceMap(newSrcMap);
                }
            }
        }

        int subIdx = subPrograms.size();
        subPrograms.add(subProg);
        emit(IRInstruction.of(IROpCode.NEW_FUNCTION, dst, subIdx), expr.getLocation());
        return dst;
    }

    private int compileList(ListExpr expr, int dst) {
        List<ASTNode> elements = expr.getElements();
        boolean hasSpread = elements.stream().anyMatch(e -> e instanceof SpreadExpr);

        if (!hasSpread) {
            // 快速路径：无 spread，预分配连续寄存器
            int baseReg = registerCounter;
            int[] dstRegs = new int[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                dstRegs[i] = nextRegister();
            }
            for (int i = 0; i < elements.size(); i++) {
                int resultReg = compileNode(elements.get(i), -1);
                if (resultReg != dstRegs[i]) {
                    emit(IRInstruction.of(IROpCode.MOVE, dstRegs[i], resultReg), expr.getLocation());
                }
            }
            emit(IRInstruction.of(IROpCode.NEW_LIST, dst, baseReg, elements.size()), expr.getLocation());
        } else {
            // 有 spread：先创建空列表，然后逐个 add 或 addAll
            emit(IRInstruction.of(IROpCode.NEW_LIST, dst, registerCounter, 0), expr.getLocation());
            for (ASTNode elem : elements) {
                int valReg = compileNode(elem instanceof SpreadExpr se ? se.getOperand() : elem, -1);
                if (elem instanceof SpreadExpr) {
                    // CALL_METHOD dst.addAll(valReg)
                    int argBase = registerCounter;
                    int argReg = nextRegister();
                    emit(IRInstruction.of(IROpCode.MOVE, argReg, valReg), expr.getLocation());
                    IRInstruction addAll = IRInstruction.of(IROpCode.CALL_METHOD, nextRegister(), dst, 1);
                    addAll.name = "addAll";
                    addAll.c = argBase;
                    emit(addAll, expr.getLocation());
                } else {
                    // CALL_METHOD dst.add(valReg)
                    int argBase = registerCounter;
                    int argReg = nextRegister();
                    emit(IRInstruction.of(IROpCode.MOVE, argReg, valReg), expr.getLocation());
                    IRInstruction add = IRInstruction.of(IROpCode.CALL_METHOD, nextRegister(), dst, 1);
                    add.name = "add";
                    add.c = argBase;
                    emit(add, expr.getLocation());
                }
            }
        }
        return dst;
    }

    private int compileMap(MapExpr expr, int dst) {
        List<MapExpr.MapEntry> entries = expr.getEntries();
        boolean hasSpread = entries.stream().anyMatch(e -> e.key() == null);
        if (!hasSpread) {
            // 快速路径：无展开，连续寄存器窗口 + 单条 NEW_MAP。与 compileList/CONCAT 相同：
            // 先预分配整个 2n 窗口，再把键/值编译到自由寄存器后显式 MOVE 进窗口——
            // 键/值表达式(IndexExpr/BinaryExpr 等)的中间操作数寄存器不再混进窗口挤掉真实键值。
            int n = entries.size();
            int baseReg = registerCounter;
            int[] window = new int[n * 2];
            for (int i = 0; i < n * 2; i++) {
                window[i] = nextRegister();
            }
            for (int i = 0; i < n; i++) {
                MapExpr.MapEntry entry = entries.get(i);
                int kReg = compileNode(entry.key(), -1);
                if (kReg != window[i * 2]) {
                    emit(IRInstruction.of(IROpCode.MOVE, window[i * 2], kReg), expr.getLocation());
                }
                int vReg = compileNode(entry.value(), -1);
                if (vReg != window[i * 2 + 1]) {
                    emit(IRInstruction.of(IROpCode.MOVE, window[i * 2 + 1], vReg), expr.getLocation());
                }
            }
            emit(IRInstruction.of(IROpCode.NEW_MAP, dst, baseReg, n), expr.getLocation());
            return dst;
        }
        // 有展开：先建空 map，按顺序逐条 put（普通项）或 merge（展开项，复用 map+map 合并语义）
        int base = nextRegister();
        emit(IRInstruction.of(IROpCode.NEW_MAP, dst, base, 0), expr.getLocation());
        for (MapExpr.MapEntry entry : entries) {
            if (entry.key() == null) {
                // {...operand}：dst = dst 合并 operand（右覆盖左）；MAP_MERGE 对非 map 操作数报错
                int opReg = compileNode(entry.value(), -1);
                emit(IRInstruction.of(IROpCode.MAP_MERGE, dst, opReg), expr.getLocation());
            } else {
                int kReg = compileNode(entry.key(), -1);
                int vReg = compileNode(entry.value(), -1);
                emit(IRInstruction.of(IROpCode.SET_INDEX, dst, kReg, vReg), expr.getLocation());
            }
        }
        return dst;
    }

    private int compileInterpolatedString(InterpolatedStringExpr expr, int dst) {
        // Shimmer 对齐(syntax-01)：CONCAT 按 baseReg..baseReg+n-1 连续窗口取段。先预分配
        // 整个窗口，再把每段编译到自由寄存器后显式 MOVE 进窗口——嵌入表达式(BinaryExpr/
        // IndexExpr/DotExpr 等)的中间操作数寄存器不再混进窗口挤掉真实段。
        List<Object> parts = expr.getParts();
        int n = parts.size();
        int baseReg = registerCounter;
        int[] window = new int[n];
        for (int i = 0; i < n; i++) {
            window[i] = nextRegister();
        }
        for (int i = 0; i < n; i++) {
            Object part = parts.get(i);
            if (part instanceof String s) {
                int ci = addConstant(new StringValue(s));
                emit(IRInstruction.of(IROpCode.LOAD_CONST, window[i], ci), expr.getLocation());
            } else if (part instanceof ASTNode node) {
                int r = compileNode(node, -1);
                if (r != window[i]) {
                    emit(IRInstruction.of(IROpCode.MOVE, window[i], r), expr.getLocation());
                }
            }
        }
        emit(IRInstruction.of(IROpCode.CONCAT, dst, baseReg, n), expr.getLocation());
        return dst;
    }

    private int compileOptionalChain(OptionalChainExpr expr, int dst) {
        // obj?.field → 编译 obj，如果 none 则跳过属性访问
        int objReg = compileNode(expr.getObject(), -1);
        // JUMP_IF_NONE objReg → skipTarget
        int jumpPC = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP_IF_NONE, objReg, 0), expr.getLocation());
        // obj 不是 none，访问属性
        emit(IRInstruction.of(IROpCode.GET_PROP, dst, objReg).withName(expr.getProperty()), expr.getLocation());
        int endJump = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP, 0), expr.getLocation());
        // obj 是 none，返回 none
        patchJump(jumpPC, currentPC());
        emit(IRInstruction.of(IROpCode.LOAD_NONE, dst), expr.getLocation());
        patchJump(endJump, currentPC());
        return dst;
    }

    private int compileNew(NewExpr expr, int dst) {
        List<ASTNode> args = expr.getArguments();
        // 与 compileCall 相同：预分配连续参数窗口后 MOVE 进入，
        // 复杂参数表达式的中间寄存器不会挤进 NEW_INSTANCE 的 argBase 窗口。
        int argBase = registerCounter;
        int[] argDstRegs = new int[args.size()];
        for (int i = 0; i < args.size(); i++) {
            argDstRegs[i] = nextRegister();
        }
        for (int i = 0; i < args.size(); i++) {
            int resultReg = compileNode(args.get(i), -1);
            if (resultReg != argDstRegs[i]) {
                emit(IRInstruction.of(IROpCode.MOVE, argDstRegs[i], resultReg), expr.getLocation());
            }
        }
        int ci = addConstant(new StringValue(expr.getClassName()));
        IRInstruction inst = IRInstruction.of(IROpCode.NEW_INSTANCE, dst, ci, args.size());
        inst.c = argBase;
        emit(inst, expr.getLocation());
        return dst;
    }


    private void compileBlock(BlockStmt stmt) {
        // 先记录当前指令位置，生成 PUSH_SCOPE 占位
        int pushPC = currentPC();
        emit(IRInstruction.of(IROpCode.PUSH_SCOPE), stmt.getLocation());

        // 记录编译前的指令数，用于检测是否生成了 scope 操作
        int beforeCount = instructions.size();
        boolean[] usedScope = {false};

        for (ASTNode child : stmt.getStatements()) {
            compileNode(child, -1);
        }

        // 扫描新生成的指令，检查是否有 LOAD_SCOPE/STORE_SCOPE
        for (int i = beforeCount; i < instructions.size(); i++) {
            IROpCode op = instructions.get(i).opcode;
            if (op == IROpCode.LOAD_SCOPE || op == IROpCode.STORE_SCOPE) {
                usedScope[0] = true;
                break;
            }
        }

        if (usedScope[0]) {
            emit(IRInstruction.of(IROpCode.POP_SCOPE), stmt.getLocation());
        } else {
            // 没有 scope 操作，把 PUSH_SCOPE 替换为 NOP
            instructions.set(pushPC, IRInstruction.of(IROpCode.NOP));
            // 不生成 POP_SCOPE
        }
    }

    private void compileExpressionStmt(ExpressionStmt stmt, int dst) {
        int reg = compileNode(stmt.getExpression(), dst);
        // Shimmer 对齐(R2 系, Expression.needCall)：裸变量读作为独立语句(如语句 `f`)——
        // 值可调用则零参自动调用(副作用发生,语句值=调用结果),与赋值 RHS 同一受限节点集。
        if (isBareVariableRead(stmt.getExpression())) {
            emit(IRInstruction.of(IROpCode.AUTO_INVOKE, reg), stmt.getLocation());
        }
    }

    // ==================== Shimmer 块结果值语义(controlflow-08) ====================

    /**
     * 按 Shimmer BlockStatement"末语句结果值"语义编译语句，返回持有该语句结果值的寄存器；
     * {@code -1} 表示 none。仅用于顶层程序/lambda/async 体的末语句链（含递归的 if 分支、
     * while 体、嵌套块）。
     * <ul>
     *   <li>if/else：被选中分支块的结果（无分支命中 → none），递归汇入统一寄存器。</li>
     *   <li>while：末次循环体的结果（0 次执行 → none）。</li>
     *   <li>for-in / 经典 for / switch（源码 line 61 恒 Result.NONE）/ 语句形 async（async-6，
     *       AsyncStatement 恒 Result.NONE）→ none。</li>
     *   <li>表达式语句：其值寄存器。</li>
     * </ul>
     */
    private int compileStatementValue(ASTNode node) {
        if (node == null) return -1;
        if (node instanceof BlockStmt block) {
            return compileBlockValue(block);
        }
        if (node instanceof ExpressionStmt es) {
            int r = compileNode(es.getExpression(), -1);
            // Shimmer 对齐(R2 系, Expression.needCall)：末语句裸变量读同样自动调用(与 compileExpressionStmt 一致)
            if (isBareVariableRead(es.getExpression())) {
                emit(IRInstruction.of(IROpCode.AUTO_INVOKE, r), es.getLocation());
            }
            return r;
        }
        if (node instanceof IfStmt ifs) {
            return compileIfValue(ifs);
        }
        if (node instanceof WhileStmt ws) {
            int resultReg = nextRegister();
            compileWhileValue(ws, resultReg);
            return resultReg;
        }
        if (node instanceof ForInStmt || node instanceof ForStmt || node instanceof SwitchStmt
                || node instanceof AsyncStmt || node instanceof ImportStmt || node instanceof ExportStmt
                || node instanceof ClassDeclStmt || node instanceof DestructureStmt
                || node instanceof TryCatchStmt || node instanceof ReturnStmt) {
            compileNode(node, -1);
            return -1;
        }
        // 裸表达式节点（root 可能不是语句包装）
        return compileNode(node, -1);
    }

    /** 与 {@link #compileBlock} 同构，但末语句按 Shimmer 块结果值语义编译（controlflow-08）。 */
    private int compileBlockValue(BlockStmt stmt) {
        int pushPC = currentPC();
        emit(IRInstruction.of(IROpCode.PUSH_SCOPE), stmt.getLocation());
        int beforeCount = instructions.size();

        List<ASTNode> children = stmt.getStatements();
        int resultReg = -1;
        for (int i = 0; i < children.size(); i++) {
            if (i == children.size() - 1) {
                resultReg = compileStatementValue(children.get(i));
            } else {
                compileNode(children.get(i), -1);
            }
        }

        boolean usedScope = false;
        for (int i = beforeCount; i < instructions.size(); i++) {
            IROpCode op = instructions.get(i).opcode;
            if (op == IROpCode.LOAD_SCOPE || op == IROpCode.STORE_SCOPE) {
                usedScope = true;
                break;
            }
        }
        if (usedScope) {
            emit(IRInstruction.of(IROpCode.POP_SCOPE), stmt.getLocation());
        } else {
            instructions.set(pushPC, IRInstruction.of(IROpCode.NOP));
        }
        return resultReg;
    }

    /**
     * 值语义 if/elif/else（controlflow-08）：所有分支块结果 MOVE 汇入统一结果寄存器；
     * 无分支命中（含无 else）时为 none（对齐 Shimmer IfStatement 落空返回 Result.NONE）。
     */
    private int compileIfValue(IfStmt stmt) {
        int resultReg = nextRegister();
        emit(IRInstruction.of(IROpCode.LOAD_NONE, resultReg), stmt.getLocation());

        int condReg = compileNode(stmt.getCondition(), -1);
        int falseJump = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP_IF_FALSE, condReg, 0), stmt.getLocation());

        int thenVal = compileStatementValue(stmt.getThenBlock());
        if (thenVal >= 0) {
            emit(IRInstruction.of(IROpCode.MOVE, resultReg, thenVal), stmt.getLocation());
        }
        List<Integer> endJumps = new ArrayList<>();
        endJumps.add(currentPC());
        emit(IRInstruction.of(IROpCode.JUMP, 0), stmt.getLocation());

        patchJump(falseJump, currentPC());
        if (stmt.getElifBlocks() != null) {
            for (IfStmt elif : stmt.getElifBlocks()) {
                int elifCondReg = compileNode(elif.getCondition(), -1);
                int elifFalseJump = currentPC();
                emit(IRInstruction.of(IROpCode.JUMP_IF_FALSE, elifCondReg, 0), elif.getLocation());

                int elifVal = compileStatementValue(elif.getThenBlock());
                if (elifVal >= 0) {
                    emit(IRInstruction.of(IROpCode.MOVE, resultReg, elifVal), elif.getLocation());
                }
                endJumps.add(currentPC());
                emit(IRInstruction.of(IROpCode.JUMP, 0), elif.getLocation());

                patchJump(elifFalseJump, currentPC());
            }
        }
        if (stmt.getElseBlock() != null) {
            int elseVal = compileStatementValue(stmt.getElseBlock());
            if (elseVal >= 0) {
                emit(IRInstruction.of(IROpCode.MOVE, resultReg, elseVal), stmt.getLocation());
            }
        }

        int endPC = currentPC();
        for (int jumpPC : endJumps) {
            patchJump(jumpPC, endPC);
        }
        return resultReg;
    }

    // ==================== break/next 泄漏语义(syntax-06/controlflow-04/05) ====================

    /**
     * 发射一次 NEXT 控制流传播（Shimmer 对齐）：从 {@code frames}（内→外）找第一个非 SWITCH 帧——
     * FOR → 跳其 continue 点；WHILE → 置其泄漏标志并回跳其条件检查（continue，泄漏由该帧
     * 出口检查续传）；全部穿透（仅 SWITCH/空栈）→ RETURN none（脚本优雅终止）。
     */
    private void emitNextPropagation(Iterator<LoopFrame> frames, SourceLocation loc) {
        while (frames.hasNext()) {
            LoopFrame f = frames.next();
            if (f.type == LoopFrame.SWITCH) continue; // switch 不消耗 NEXT(源码 line 43 上抛)
            if (f.type == LoopFrame.FOR) {
                f.nextJumps.add(currentPC());
                emit(IRInstruction.of(IROpCode.JUMP, 0, 0), loc);
                return;
            }
            // WHILE：consume 为 continue；若随后条件为假退出，泄漏标志使 NEXT 继续外传
            f.whileLeakUsed = true;
            emit(IRInstruction.of(IROpCode.LOAD_TRUE, f.whileLeakFlagReg), loc);
            emit(IRInstruction.of(IROpCode.JUMP, 0, f.whileCondStart), loc);
            return;
        }
        emit(IRInstruction.of(IROpCode.RETURN, -1), loc);
    }

    private void compileIf(IfStmt stmt) {
        int condReg = compileNode(stmt.getCondition(), -1);
        int falseJump = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP_IF_FALSE, condReg, 0), stmt.getLocation());

        // then 块
        compileNode(stmt.getThenBlock(), -1);

        List<Integer> endJumps = new ArrayList<>();
        endJumps.add(currentPC());
        emit(IRInstruction.of(IROpCode.JUMP, 0), stmt.getLocation());

        // elif 块
        patchJump(falseJump, currentPC());
        if (stmt.getElifBlocks() != null) {
            for (int i = 0; i < stmt.getElifBlocks().size(); i++) {
                IfStmt elif = stmt.getElifBlocks().get(i);
                int elifCondReg = compileNode(elif.getCondition(), -1);
                int elifFalseJump = currentPC();
                emit(IRInstruction.of(IROpCode.JUMP_IF_FALSE, elifCondReg, 0), elif.getLocation());

                compileNode(elif.getThenBlock(), -1);
                endJumps.add(currentPC());
                emit(IRInstruction.of(IROpCode.JUMP, 0), elif.getLocation());

                patchJump(elifFalseJump, currentPC());
            }
        }

        // else 块
        if (stmt.getElseBlock() != null) {
            compileNode(stmt.getElseBlock(), -1);
        }

        // 修补所有结束跳转
        int endPC = currentPC();
        for (int jumpPC : endJumps) {
            patchJump(jumpPC, endPC);
        }
    }

    private void compileWhile(WhileStmt stmt) {
        compileWhileValue(stmt, -1);
    }

    /**
     * Shimmer 对齐(syntax-06/controlflow-04/05)的 while 编译：
     * <ul>
     *   <li>体内 break 不指向本循环——由 {@link #compileReturn} 跳到最近外层 FOR/SWITCH 的
     *       消耗点，无消耗者则 RETURN none（BREAK 泄漏语义）。</li>
     *   <li>体内 next：置泄漏标志后回跳条件检查；体正常完成一轮清标志。循环因条件为假退出时
     *       检查标志——为真则把 NEXT 继续向外层传播（对齐 WhileStatement 末轮 next 后
     *       {@code return result} 残留 NEXT 型 Result 的泄漏）。</li>
     *   <li>{@code resultReg >= 0} 时（controlflow-08 值语义）：每轮体结果 MOVE 入
     *       resultReg，0 次执行为 none。</li>
     * </ul>
     */
    private void compileWhileValue(WhileStmt stmt, int resultReg) {
        LoopFrame frame = new LoopFrame(LoopFrame.WHILE);
        frame.whileLeakFlagReg = nextRegister();
        emit(IRInstruction.of(IROpCode.LOAD_FALSE, frame.whileLeakFlagReg), stmt.getLocation());
        if (resultReg >= 0) {
            emit(IRInstruction.of(IROpCode.LOAD_NONE, resultReg), stmt.getLocation());
        }

        int loopStart = currentPC();
        frame.whileCondStart = loopStart;
        int condReg = compileNode(stmt.getCondition(), -1);
        int exitJump = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP_IF_FALSE, condReg, 0), stmt.getLocation());

        loopFrames.push(frame);
        if (resultReg >= 0) {
            int bodyVal = compileStatementValue(stmt.getBody());
            if (bodyVal >= 0) {
                emit(IRInstruction.of(IROpCode.MOVE, resultReg, bodyVal), stmt.getLocation());
            } else {
                emit(IRInstruction.of(IROpCode.LOAD_NONE, resultReg), stmt.getLocation());
            }
        } else {
            compileNode(stmt.getBody(), -1);
        }
        loopFrames.pop();

        if (frame.whileLeakUsed) {
            // 体正常完成一轮：清泄漏标志再回条件
            emit(IRInstruction.of(IROpCode.LOAD_FALSE, frame.whileLeakFlagReg), stmt.getLocation());
            emit(IRInstruction.of(IROpCode.JUMP, 0, loopStart), stmt.getLocation());

            // 条件为假出口：检查残留 NEXT 泄漏
            patchJump(exitJump, currentPC());
            int noLeakJump = currentPC();
            emit(IRInstruction.of(IROpCode.JUMP_IF_FALSE, frame.whileLeakFlagReg, 0), stmt.getLocation());
            emitNextPropagation(loopFrames.iterator(), stmt.getLocation());
            patchJump(noLeakJump, currentPC());
        } else {
            emit(IRInstruction.of(IROpCode.JUMP, 0, loopStart), stmt.getLocation());
            patchJump(exitJump, currentPC());
        }
        // 注：break 从不指向 while 自身(泄漏语义)，frame.breakJumps/nextJumps 恒为空。
    }

    private void compileForIn(ForInStmt stmt) {
        // 编译可迭代对象
        int iterReg = compileNode(stmt.getIterable(), -1);

        List<String> vars = stmt.getVariables();
        boolean singleVar = (vars.size() == 1);

        // Shimmer 对齐(controlflow-11)：单变量循环变量不再用寄存器别名(registerAliases)——
        // 改为每轮 STORE_SCOPE 写真实作用域：循环后可见=末次迭代值、覆盖同名外层变量，
        // 且体内读(LOAD_SCOPE)写(STORE_SCOPE)同一绑定(别名方案读寄存器写 scope 不一致)。
        if (!singleVar) {
            emit(IRInstruction.of(IROpCode.PUSH_SCOPE), stmt.getLocation());
        }

        // 循环变量寄存器
        int iterVarReg = nextRegister();
        emit(IRInstruction.of(IROpCode.LOAD_NONE, iterVarReg), stmt.getLocation());

        // 计数器寄存器
        int counterReg = nextRegister();
        int zeroConst = addConstant(new NumberValue(0));
        emit(IRInstruction.of(IROpCode.LOAD_CONST, counterReg, zeroConst), stmt.getLocation());

        // 第一次取元素（c=1 标记迭代模式:Map 返回第 i 个 [key,value] 对而非按键查找,
        // 使 for-in 能遍历 Map;迭代终结返回 ITER_END 哨兵而非 none——含 none 元素的列表可完整遍历）
        emit(IRInstruction.of(IROpCode.GET_INDEX, iterVarReg, iterReg, counterReg).withC(1), stmt.getLocation());

        int loopStart = currentPC();

        // 迭代终结则退出(c=1：只认 ITER_END 哨兵,Shimmer 对齐 controlflow-09)
        int exitJump = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP_IF_NONE, iterVarReg, 0).withC(1), stmt.getLocation());

        if (singleVar) {
            // 单变量：每轮把元素写入真实作用域绑定(controlflow-11)
            int ki = addVariableKey(vars.get(0));
            emit(IRInstruction.of(IROpCode.STORE_SCOPE, iterVarReg, ki), stmt.getLocation());
        } else {
            // 多变量解构：仍用 scope。GET_INDEX c=2：解构缺位(如 map 遍历的第 3 个变量)→ none
            // 而非抛"列表索引越界"(Shimmer 对齐 controlflow-10(b))。
            for (int i = 0; i < vars.size(); i++) {
                int ki = addVariableKey(vars.get(i));
                int partReg = nextRegister();
                int idxConst = addConstant(new NumberValue(i));
                int idxReg = nextRegister();
                emit(IRInstruction.of(IROpCode.LOAD_CONST, idxReg, idxConst), stmt.getLocation());
                emit(IRInstruction.of(IROpCode.GET_INDEX, partReg, iterVarReg, idxReg).withC(2), stmt.getLocation());
                emit(IRInstruction.of(IROpCode.STORE_SCOPE, partReg, ki), stmt.getLocation());
            }
        }

        // FOR 帧：完整消耗 break/next(Shimmer ForInStatement 语义,syntax-06)
        LoopFrame frame = new LoopFrame(LoopFrame.FOR);
        loopFrames.push(frame);

        // 循环体
        compileNode(stmt.getBody(), -1);

        loopFrames.pop();

        // next 跳转到这里（递增计数器 + 下一次迭代）
        int continueTarget = currentPC();
        emit(IRInstruction.of(IROpCode.INC, counterReg, counterReg), stmt.getLocation());
        emit(IRInstruction.of(IROpCode.GET_INDEX, iterVarReg, iterReg, counterReg).withC(1), stmt.getLocation());
        emit(IRInstruction.of(IROpCode.JUMP, 0, loopStart), stmt.getLocation());

        // break 跳转到这里
        int breakTarget = currentPC();
        patchJump(exitJump, breakTarget);

        // 修补本层 break/next
        for (int pc : frame.breakJumps) patchJump(pc, breakTarget);
        for (int pc : frame.nextJumps) patchJump(pc, continueTarget);

        if (!singleVar) {
            emit(IRInstruction.of(IROpCode.POP_SCOPE), stmt.getLocation());
        }
    }

    private void compileFor(ForStmt stmt) {
        emit(IRInstruction.of(IROpCode.PUSH_SCOPE), stmt.getLocation());

        // init
        if (stmt.getInit() != null) {
            compileNode(stmt.getInit(), -1);
        }

        int loopStart = currentPC();

        // condition
        int exitJump = -1;
        if (stmt.getCondition() != null) {
            int condReg = compileNode(stmt.getCondition(), -1);
            exitJump = currentPC();
            emit(IRInstruction.of(IROpCode.JUMP_IF_FALSE, condReg, 0), stmt.getLocation());
        }

        // FOR 帧：经典 for 是 Aria 自由区构造,按 for-in 同语义完整消耗 break/next
        LoopFrame frame = new LoopFrame(LoopFrame.FOR);
        loopFrames.push(frame);

        // body
        compileNode(stmt.getBody(), -1);

        loopFrames.pop();

        // next 跳转到这里（update）
        int continueTarget = currentPC();
        if (stmt.getUpdate() != null) {
            compileNode(stmt.getUpdate(), -1);
        }

        emit(IRInstruction.of(IROpCode.JUMP, 0, loopStart), stmt.getLocation());

        int breakTarget = currentPC();
        if (exitJump >= 0) {
            patchJump(exitJump, breakTarget);
        }

        // 修补本层 break/next
        for (int pc : frame.breakJumps) patchJump(pc, breakTarget);
        for (int pc : frame.nextJumps) patchJump(pc, continueTarget);

        emit(IRInstruction.of(IROpCode.POP_SCOPE), stmt.getLocation());
    }

    private void compileSwitch(SwitchStmt stmt) {
        if (stmt.isFallthrough()) {
            // Shimmer 对齐(controlflow-03,bug-for-bug 按 SwitchStatement 源码)：
            //   顺序逐 case——每个 case 条件都求值并与"当前比对值"eq 比较(匹配后仍继续比对后续
            //   case)；匹配 → 执行 case 块并把比对值替换为该块的结果值；全部 case 过完后 else
            //   总是执行；case/else 块内 break 被 switch 消耗(跳过 else,switch 结果 NONE)；
            //   next/return 穿透。不存在 C 式无条件穿透。
            int condVal = compileNode(stmt.getCondition(), -1);
            int compareReg = nextRegister();
            emit(IRInstruction.of(IROpCode.MOVE, compareReg, condVal), stmt.getLocation());

            LoopFrame frame = new LoopFrame(LoopFrame.SWITCH);
            loopFrames.push(frame);

            for (CaseStmt caseStmt : stmt.getCases()) {
                int caseValReg = compileNode(caseStmt.getCondition(), -1);
                int eqReg = nextRegister();
                emit(IRInstruction.of(IROpCode.EQ, eqReg, compareReg, caseValReg), caseStmt.getLocation());
                int skipJump = currentPC();
                emit(IRInstruction.of(IROpCode.JUMP_IF_FALSE, eqReg, 0), caseStmt.getLocation());

                // 匹配：执行块并把比对值替换为块结果(Shimmer 怪癖,bug-for-bug)
                int bodyVal = compileStatementValue(caseStmt.getBody());
                if (bodyVal >= 0) {
                    emit(IRInstruction.of(IROpCode.MOVE, compareReg, bodyVal), caseStmt.getLocation());
                } else {
                    emit(IRInstruction.of(IROpCode.LOAD_NONE, compareReg), caseStmt.getLocation());
                }
                patchJump(skipJump, currentPC());
            }

            // else 总是执行(除非某个已执行块内 break)
            if (stmt.getElseBlock() != null) {
                compileNode(stmt.getElseBlock(), -1);
            }
            loopFrames.pop();

            // break 消耗点：跳过 else、结束 switch
            int breakTarget = currentPC();
            for (int pc : frame.breakJumps) patchJump(pc, breakTarget);
        } else {
            int condReg = compileNode(stmt.getCondition(), -1);
            List<Integer> endJumps = new ArrayList<>();

            for (CaseStmt caseStmt : stmt.getCases()) {
                int caseValReg = compileNode(caseStmt.getCondition(), -1);
                int eqReg = nextRegister();
                emit(IRInstruction.of(IROpCode.EQ, eqReg, condReg, caseValReg), caseStmt.getLocation());
                int skipJump = currentPC();
                emit(IRInstruction.of(IROpCode.JUMP_IF_FALSE, eqReg, 0), caseStmt.getLocation());

                compileNode(caseStmt.getBody(), -1);
                endJumps.add(currentPC());
                emit(IRInstruction.of(IROpCode.JUMP, 0), caseStmt.getLocation());

                patchJump(skipJump, currentPC());
            }

            if (stmt.getElseBlock() != null) {
                compileNode(stmt.getElseBlock(), -1);
            }

            int endPC = currentPC();
            for (int jumpPC : endJumps) {
                patchJump(jumpPC, endPC);
            }
        }
    }

    /**
     * async { body } 作为表达式：把 body 编成独立子程序（与 lambda 同构，含隐式 return），
     * emit NEW_ASYNC 让运行时把子程序提交线程池执行，产出真 PromiseValue。
     */
    private int compileAsync(AsyncStmt stmt, int dst) {
        if (dst < 0) dst = nextRegister();
        Compiler subCompiler = forSubProgram();
        IRProgram subProg = subCompiler.compile("<async>", stmt.getBody());

        // 隐式返回：若末尾不是 RETURN，补一条返回最后求值结果（与 compileLambda 一致）
        IRInstruction[] subCode = subProg.getInstructions();
        if (subCode.length > 0 && subCode[subCode.length - 1].opcode != IROpCode.RETURN) {
            int lastDst = -1;
            for (int i = subCode.length - 1; i >= 0; i--) {
                IROpCode op = subCode[i].opcode;
                if (op != IROpCode.NOP && op != IROpCode.POP_SCOPE && op != IROpCode.PUSH_SCOPE) {
                    lastDst = subCode[i].dst;
                    break;
                }
            }
            IRInstruction[] newCode = new IRInstruction[subCode.length + 1];
            System.arraycopy(subCode, 0, newCode, 0, subCode.length);
            newCode[subCode.length] = IRInstruction.of(IROpCode.RETURN, lastDst);
            subProg.setInstructions(newCode);
        }

        int subIdx = subPrograms.size();
        subPrograms.add(subProg);
        emit(IRInstruction.of(IROpCode.NEW_ASYNC, dst, subIdx), stmt.getLocation());
        return dst;
    }

    private void compileTryCatch(TryCatchStmt stmt) {
        int tryBeginPC = currentPC();
        emit(IRInstruction.of(IROpCode.TRY_BEGIN, 0, 0), stmt.getLocation());

        compileNode(stmt.getTryBlock(), -1);
        emit(IRInstruction.of(IROpCode.TRY_END), stmt.getLocation());
        int skipCatchJump = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP, 0), stmt.getLocation());

        // catch 块
        patchJump(tryBeginPC, currentPC());
        if (stmt.getCatchBlock() != null) {
            emit(IRInstruction.of(IROpCode.PUSH_SCOPE), stmt.getLocation());
            if (stmt.getCatchVar() != null) {
                // 异常值由解释器写入寄存器 0（约定）。用 DECLARE_SCOPE 在 catch 作用域内声明，
                // 强制 shadow 外层同名变量（避免污染外层 var.e）。
                int ki = addVariableKey(stmt.getCatchVar());
                emit(IRInstruction.of(IROpCode.DECLARE_SCOPE, 0, ki), stmt.getLocation());
            }
            compileNode(stmt.getCatchBlock(), -1);
            emit(IRInstruction.of(IROpCode.POP_SCOPE), stmt.getLocation());
        }

        patchJump(skipCatchJump, currentPC());

        // finally 块
        if (stmt.getFinallyBlock() != null) {
            compileNode(stmt.getFinallyBlock(), -1);
        }
    }

    private void compileClassDecl(ClassDeclStmt stmt) {
        int classReg = nextRegister();

        // 编译字段初始化子程序：对每个有默认值的实例字段，直接内联编译默认值表达式
        Compiler fieldInitCompiler = forSubProgram();
        // 手动构建字段初始化程序：LOAD_SELF → 对每个字段 SET_PROP self.field = defaultValue → RETURN
        // 使用一个子编译器来编译整个字段初始化体
        List<ASTNode> fieldInitStmts = new ArrayList<>();
        List<ASTNode> staticInitStmts = new ArrayList<>();
        for (ClassDeclStmt.ClassFieldDecl field : stmt.getFields()) {
            if (field.defaultValue() != null) {
                // 构造 self.fieldName = defaultValue 的赋值表达式
                IdentifierExpr selfExpr = new IdentifierExpr(stmt.getLocation(), "self");
                DotExpr target = new DotExpr(stmt.getLocation(), selfExpr, field.name());
                AssignmentExpr assign = new AssignmentExpr(stmt.getLocation(), target, AssignmentExpr.AssignOp.ASSIGN, field.defaultValue());
                ExpressionStmt es = new ExpressionStmt(stmt.getLocation(), assign);
                if (field.isStatic()) staticInitStmts.add(es); else fieldInitStmts.add(es);
            }
        }
        IRProgram fieldInitProg;
        if (!fieldInitStmts.isEmpty()) {
            BlockStmt fieldInitBlock = new BlockStmt(stmt.getLocation(), fieldInitStmts);
            fieldInitProg = fieldInitCompiler.compile(stmt.getName() + ".<field-init>", fieldInitBlock);
        } else {
            fieldInitProg = null;
        }
        int fieldInitSubIdx = -1;
        if (fieldInitProg != null) {
            fieldInitSubIdx = subPrograms.size();
            subPrograms.add(fieldInitProg);
        }

        // 静态字段初始化子程序：self = ObjectValue<ClassDefinition>，SET_PROP 写入静态字段
        IRProgram staticInitProg = null;
        if (!staticInitStmts.isEmpty()) {
            Compiler staticInitCompiler = forSubProgram();
            BlockStmt staticInitBlock = new BlockStmt(stmt.getLocation(), staticInitStmts);
            staticInitProg = staticInitCompiler.compile(stmt.getName() + ".<static-init>", staticInitBlock);
        }
        int staticInitSubIdx = -1;
        if (staticInitProg != null) {
            staticInitSubIdx = subPrograms.size();
            subPrograms.add(staticInitProg);
        }

        // 编译方法为子程序（实例 + 静态）
        List<String> methodNames = new ArrayList<>();
        List<Integer> methodSubIndices = new ArrayList<>();
        List<String> staticMethodNames = new ArrayList<>();
        List<Integer> staticMethodSubIndices = new ArrayList<>();
        for (ClassDeclStmt.ClassMethodDecl method : stmt.getMethods()) {
            Compiler methodCompiler = forSubProgram();
            // method.body() 是 LambdaExpr，需要提取其内部 body
            ASTNode methodBody = method.body();
            if (methodBody instanceof LambdaExpr lambda) {
                methodBody = lambda.getBody();
            }
            IRProgram methodProg = methodCompiler.compile(stmt.getName() + "." + method.name(), methodBody);
            int subIdx = subPrograms.size();
            subPrograms.add(methodProg);
            if (method.isStatic()) {
                staticMethodNames.add(method.name());
                staticMethodSubIndices.add(subIdx);
            } else {
                methodNames.add(method.name());
                methodSubIndices.add(subIdx);
            }
        }

        // 编译构造函数为子程序
        int ctorSubIdx = -1;
        if (stmt.getConstructor() != null) {
            Compiler ctorCompiler = forSubProgram();
            ASTNode ctorBody = stmt.getConstructor();
            // JS 模式的 constructor 被包装为 LambdaExpr，需要提取其内部 body
            if (ctorBody instanceof LambdaExpr lambda) {
                ctorBody = lambda.getBody();
            }
            IRProgram ctorProg = ctorCompiler.compile(stmt.getName() + ".<init>", ctorBody);
            ctorSubIdx = subPrograms.size();
            subPrograms.add(ctorProg);
        }

        // 收集字段元数据（名称+是否可变），区分实例与静态
        StringBuilder fieldMeta = new StringBuilder();
        StringBuilder staticFieldMeta = new StringBuilder();
        for (ClassDeclStmt.ClassFieldDecl field : stmt.getFields()) {
            StringBuilder target = field.isStatic() ? staticFieldMeta : fieldMeta;
            if (target.length() > 0) target.append(",");
            target.append(field.mutable() ? "var." : "val.").append(field.name());
        }

        // 收集方法元数据
        StringBuilder methodMeta = new StringBuilder();
        for (int i = 0; i < methodNames.size(); i++) {
            if (methodMeta.length() > 0) methodMeta.append(",");
            methodMeta.append(methodNames.get(i)).append(":").append(methodSubIndices.get(i));
        }
        StringBuilder staticMethodMeta = new StringBuilder();
        for (int i = 0; i < staticMethodNames.size(); i++) {
            if (staticMethodMeta.length() > 0) staticMethodMeta.append(",");
            staticMethodMeta.append(staticMethodNames.get(i)).append(":").append(staticMethodSubIndices.get(i));
        }

        // DEFINE_CLASS: dst=classReg, a=fieldInitSubIdx, b=ctorSubIdx, c=staticInitSubIdx
        // name = "className|parentName|fieldMeta|methodMeta|staticFieldMeta|staticMethodMeta"
        String parentName = stmt.getParentName() != null ? stmt.getParentName() : "";
        IRInstruction defineClass = IRInstruction.of(IROpCode.DEFINE_CLASS, classReg, fieldInitSubIdx, ctorSubIdx);
        defineClass.c = staticInitSubIdx;
        defineClass.name = stmt.getName() + "|" + parentName + "|" + fieldMeta + "|" + methodMeta
                + "|" + staticFieldMeta + "|" + staticMethodMeta;

        // 注解元数据：存入 metadata 字段，Interpreter 在 DEFINE_CLASS 时读取
        defineClass.metadata = buildClassAnnotationMeta(stmt);
        emit(defineClass, stmt.getLocation());

        // 将类定义存入作用域
        int ki = addVariableKey(stmt.getName());
        emit(IRInstruction.of(IROpCode.STORE_SCOPE, classReg, ki), stmt.getLocation());
    }

    private Object buildClassAnnotationMeta(ClassDeclStmt stmt) {
        var classAnns = AnnotationProcessor.convert(stmt.getAnnotations());
        Map<String, List<AriaAnnotation>> fieldAnns = new HashMap<>();
        Map<String, List<AriaAnnotation>> methodAnns = new HashMap<>();
        for (ClassDeclStmt.ClassFieldDecl field : stmt.getFields()) {
            var fa = AnnotationProcessor.convert(field.annotations());
            if (!fa.isEmpty()) fieldAnns.put(field.name(), fa);
        }
        for (ClassDeclStmt.ClassMethodDecl method : stmt.getMethods()) {
            var ma = AnnotationProcessor.convert(method.annotations());
            if (!ma.isEmpty()) methodAnns.put(method.name(), ma);
        }
        if (classAnns.isEmpty() && fieldAnns.isEmpty() && methodAnns.isEmpty()) return null;
        return new Object[]{ classAnns, fieldAnns, methodAnns };
    }

    private void compileDestructure(DestructureStmt stmt) {
        // 编译右侧值
        int valueReg = compileNode(stmt.getValue(), -1);
        boolean objectPattern = stmt.isObjectPattern();

        // 对每个命名变量，GET_INDEX 取值并 STORE_VAR/STORE_SCOPE
        for (int i = 0; i < stmt.getNames().size(); i++) {
            String name = stmt.getNames().get(i);
            int idxConst = objectPattern
                    ? addConstant(new StringValue(name))
                    : addConstant(new NumberValue(i));
            int idxReg = nextRegister();
            emit(IRInstruction.of(IROpCode.LOAD_CONST, idxReg, idxConst), stmt.getLocation());
            int elemReg = nextRegister();
            emit(IRInstruction.of(IROpCode.GET_INDEX, elemReg, valueReg, idxReg), stmt.getLocation());

            int ki = addVariableKey(name);
            if (stmt.isMutable()) {
                emit(IRInstruction.of(IROpCode.STORE_VAR, elemReg, ki), stmt.getLocation());
            } else {
                // val 存入 scope（与 val.xxx 行为一致）
                emit(IRInstruction.of(IROpCode.STORE_SCOPE, elemReg, ki), stmt.getLocation());
            }
        }

        // ...rest 收集剩余元素（仅数组模式支持）
        if (stmt.getRestName() != null && !objectPattern) {
            // 编译为: var.rest = value.#rest(startIdx)
            // A5：不再借道脚本可见的 subList(其单参形态已按 Shimmer 对齐为 none)——
            // 走 ListFunctions 注册的内部辅助 "#rest"(名字含 '#'，脚本方法语法不可达)。
            int startConst = addConstant(new NumberValue(stmt.getNames().size()));
            int startReg = nextRegister();
            emit(IRInstruction.of(IROpCode.LOAD_CONST, startReg, startConst), stmt.getLocation());

            // 构建参数: [self=value, from=startIdx]
            int argBase = registerCounter;
            int arg0 = nextRegister(); // self
            emit(IRInstruction.of(IROpCode.MOVE, arg0, valueReg), stmt.getLocation());
            int arg1 = nextRegister(); // from
            emit(IRInstruction.of(IROpCode.MOVE, arg1, startReg), stmt.getLocation());

            int restReg = nextRegister();
            IRInstruction subListCall = IRInstruction.of(IROpCode.CALL_METHOD, restReg, valueReg, 1);
            subListCall.name = "#rest";
            subListCall.c = argBase + 1; // 跳过 self，只传 from
            emit(subListCall, stmt.getLocation());

            int ki = addVariableKey(stmt.getRestName());
            if (stmt.isMutable()) {
                emit(IRInstruction.of(IROpCode.STORE_VAR, restReg, ki), stmt.getLocation());
            } else {
                emit(IRInstruction.of(IROpCode.STORE_SCOPE, restReg, ki), stmt.getLocation());
            }
        }
    }

    private void compileExport(ExportStmt stmt) {
        // Shimmer 对齐(variables-7/8 连带修复)：裸名与 var./val. 隔离后，LOAD_SCOPE 再读
        // `export var.x = ...` 会得 none(val 脚本写更是 no-op)。赋值形导出直接复用赋值表达式的
        // 值寄存器；其余(class 声明等仍写 scope)保持 LOAD_SCOPE 再读。
        int valReg;
        if (stmt.getStatement() instanceof ExpressionStmt es && es.getExpression() instanceof AssignmentExpr) {
            valReg = compileNode(es.getExpression(), -1); // compileAssignment 返回真实值寄存器
        } else {
            compileNode(stmt.getStatement(), -1);
            valReg = -1;
        }

        // 提取导出的变量名
        String exportName = extractExportName(stmt.getStatement());
        if (exportName != null) {
            if (valReg < 0) {
                // 从 scope 加载变量值(class 声明等)
                valReg = nextRegister();
                int ki = addVariableKey(exportName);
                emit(IRInstruction.of(IROpCode.LOAD_SCOPE, valReg, ki), stmt.getLocation());
            }

            // 存入 __exports__ map
            int exportsKi = addVariableKey("__exports__");
            int exportsReg = nextRegister();
            // INIT_OR_GET: 如果 __exports__ 不存在则创建空 map
            int emptyMapReg = nextRegister();
            emit(IRInstruction.of(IROpCode.NEW_MAP, emptyMapReg, registerCounter, 0), stmt.getLocation());
            IRInstruction initExports = IRInstruction.of(IROpCode.INIT_OR_GET, exportsReg, exportsKi, emptyMapReg);
            initExports.name = "var";
            emit(initExports, stmt.getLocation());

            // SET_PROP __exports__[exportName] = value
            int nameConst = addConstant(new StringValue(exportName));
            int nameReg = nextRegister();
            emit(IRInstruction.of(IROpCode.LOAD_CONST, nameReg, nameConst), stmt.getLocation());
            emit(IRInstruction.of(IROpCode.SET_INDEX, exportsReg, nameReg, valReg), stmt.getLocation());
        }
    }

    private String extractExportName(ASTNode stmt) {
        if (stmt instanceof ExpressionStmt es && es.getExpression() instanceof AssignmentExpr ae) {
            ASTNode target = ae.getTarget();
            if (target instanceof DotExpr dot && dot.getObject() instanceof IdentifierExpr id) {
                String ns = id.getName();
                if ("var".equals(ns) || "val".equals(ns)) {
                    return dot.getProperty();
                }
            }
        }
        if (stmt instanceof ClassDeclStmt cls) {
            return cls.getName();
        }
        return null;
    }

    private void compileImport(ImportStmt stmt) {
        // Shimmer 对齐(R2)副作用修复：lambda 体隔离后闭包不再捕获外层 scope，而模块函数普遍引用
        // 同文件 import 的符号——import 绑定改存 var 存储(STORE_VAR；模块用独立 LocalStorage 执行，
        // 不外泄到导入方)，别名记入 importAliases，同文件(含嵌套体)的裸名读经 compileIdentifier
        // 编译为 LOAD_VAR、裸名调用经 resolveVariable 的 var 回退命中。
        if (stmt.getPath() != null) {
            // import a.b.c 或 import a.b.c as alias
            String fullPath = String.join(".", stmt.getPath());
            int pathConst = addConstant(new StringValue(fullPath));
            int reg = nextRegister();
            emit(IRInstruction.of(IROpCode.LOAD_CONST, reg, pathConst), stmt.getLocation());
            emit(IRInstruction.of(IROpCode.CALL, reg, reg, 0).withName("__import__"), stmt.getLocation());

            String alias = stmt.getAlias() != null ? stmt.getAlias()
                    : stmt.getPath().get(stmt.getPath().size() - 1);
            int ki = addVariableKey(alias);
            importAliases.add(alias);
            emit(IRInstruction.of(IROpCode.STORE_VAR, reg, ki), stmt.getLocation());
        } else if (stmt.getNames() != null && stmt.getSource() != null) {
            // import {a, b} from 'source'
            int srcConst = addConstant(new StringValue(stmt.getSource()));
            int modReg = nextRegister();
            emit(IRInstruction.of(IROpCode.LOAD_CONST, modReg, srcConst), stmt.getLocation());
            emit(IRInstruction.of(IROpCode.CALL, modReg, modReg, 0).withName("__import__"), stmt.getLocation());

            for (String name : stmt.getNames()) {
                int propReg = nextRegister();
                emit(IRInstruction.of(IROpCode.GET_PROP, propReg, modReg).withName(name), stmt.getLocation());
                int ki = addVariableKey(name);
                importAliases.add(name);
                emit(IRInstruction.of(IROpCode.STORE_VAR, propReg, ki), stmt.getLocation());
            }
        }
    }

    private void compileReturn(ReturnStmt stmt, int dst) {
        switch (stmt.getType()) {
            case RETURN -> {
                if (stmt.getValue() != null) {
                    int valReg = compileNode(stmt.getValue(), -1);
                    emit(IRInstruction.of(IROpCode.RETURN, valReg), stmt.getLocation());
                } else {
                    emit(IRInstruction.of(IROpCode.RETURN, -1), stmt.getLocation());
                }
            }
            case BREAK -> {
                // Shimmer 对齐(syntax-06/controlflow-04/05)：BREAK 穿透所有 WHILE(泄漏),
                // 被最近的 FOR(循环出口)或 SWITCH(跳过 else)消耗；无消耗者 → RETURN none
                // (整脚本优雅终止,修 Aria 旧 JUMP 0,0 悬空挂死)。
                LoopFrame target = null;
                for (LoopFrame f : loopFrames) {
                    if (f.type != LoopFrame.WHILE) {
                        target = f;
                        break;
                    }
                }
                if (target != null) {
                    target.breakJumps.add(currentPC());
                    emit(IRInstruction.of(IROpCode.JUMP, 0, 0), stmt.getLocation());
                } else {
                    emit(IRInstruction.of(IROpCode.RETURN, -1), stmt.getLocation());
                }
            }
            case NEXT -> {
                // Shimmer 对齐：NEXT 被最近的 FOR/WHILE 消耗(SWITCH 穿透)；WHILE 消耗时置
                // 泄漏标志(末轮 next 后条件为假退出则继续外传)；无消耗者 → RETURN none。
                emitNextPropagation(loopFrames.iterator(), stmt.getLocation());
            }
            case THROW -> {
                if (stmt.getValue() != null) {
                    int valReg = compileNode(stmt.getValue(), -1);
                    emit(IRInstruction.of(IROpCode.THROW, valReg), stmt.getLocation());
                } else {
                    emit(IRInstruction.of(IROpCode.THROW, -1), stmt.getLocation());
                }
            }
            default -> {}
        }
    }


    private boolean isDotNamespace(DotExpr dot) {
        return dot.getObject() instanceof IdentifierExpr id
                && DOT_NAMESPACES.contains(id.getName());
    }

    private void emitStore(ASTNode target, int valueReg, SourceLocation loc) {
        if (target instanceof DotExpr dot) {
            if (isDotNamespace(dot)) {
                String ns = ((IdentifierExpr) dot.getObject()).getName();
                int ki = addVariableKey(dot.getProperty());
                IROpCode storeOp = switch (ns) {
                    case "var" -> IROpCode.STORE_VAR;
                    case "val" -> IROpCode.STORE_VAL;   // val 写入不可变存储,初始化一次后重赋报错
                    case "global" -> IROpCode.STORE_GLOBAL;
                    case "server" -> IROpCode.STORE_SERVER;
                    case "client" -> IROpCode.STORE_CLIENT;
                    default -> IROpCode.STORE_SCOPE;
                };
                emit(IRInstruction.of(storeOp, valueReg, ki), loc);
            } else {
                int objReg = compileNode(dot.getObject(), -1);
                emit(IRInstruction.of(IROpCode.SET_PROP, objReg, valueReg).withName(dot.getProperty()), loc);
            }
        } else if (target instanceof IdentifierExpr id) {
            int ki = addVariableKey(id.getName());
            emit(IRInstruction.of(IROpCode.STORE_SCOPE, valueReg, ki), loc);
        } else if (target instanceof IndexExpr idx) {
            int objReg = compileNode(idx.getObject(), -1);
            int idxReg = idx.getIndex() != null ? compileNode(idx.getIndex(), -1) : -1;
            emit(IRInstruction.of(IROpCode.SET_INDEX, objReg, idxReg, valueReg), loc);
        }
    }

    private void emitStoreBack(ASTNode target, int valueReg, SourceLocation loc) {
        emitStore(target, valueReg, loc);
    }
}
