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

    private final Map<String, Integer> registerAliases = new HashMap<>();


    private final Set<String> knownVarFunctions = new HashSet<>();


    private final Deque<int[]> loopTargetStack = new ArrayDeque<>();

    private final List<Integer> pendingBreaks = new ArrayList<>();
    private final List<Integer> pendingNexts = new ArrayList<>();


    public IRProgram compile(String name, ASTNode root) {
        // 重置状态
        instructions.clear();
        sourceMap.clear();
        constants.clear();
        variableKeys.clear();
        subPrograms.clear();
        registerCounter = 0;
        labelCounter = 0;

        compileNode(root, -1);
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
            compileAsync(stmt);
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
                // 检查 for-in 循环变量的寄存器别名
                Integer aliasReg = registerAliases.get(name);
                if (aliasReg != null) {
                    // 直接返回别名寄存器，避免 MOVE
                    return aliasReg;
                } else {
                    int ki = addVariableKey(name);
                    emit(IRInstruction.of(IROpCode.LOAD_SCOPE, dst, ki), expr.getLocation());
                }
            }
        }
        return dst;
    }

    private int compileBinary(BinaryExpr expr, int dst) {
        if (expr.getOperator() == BinaryExpr.BinaryOp.COMMA) {
            compileNode(expr.getLeft(), -1); // side effects only
            return compileNode(expr.getRight(), dst);
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
            case COMMA -> IROpCode.NOP; // handled above
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
            knownVarFunctions.add(dot.getProperty());
        }
        emitStore(target, valueReg, expr.getLocation());
        // 返回 valueReg 作为表达式结果，避免多余 MOVE
        return valueReg;
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
            if (obj instanceof IdentifierExpr id) {
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
        int calleeReg = compileNode(callee, -1);
        IRInstruction inst = IRInstruction.of(IROpCode.CALL, dst, calleeReg, args.size());
        inst.c = argBase;
        emit(inst, expr.getLocation());
        return dst;
    }

    private int compileIndex(IndexExpr expr, int dst) {
        if (expr.getObject() instanceof IdentifierExpr id && "args".equals(id.getName())
                && expr.getIndex() instanceof LiteralExpr lit && lit.getValue() instanceof NumberValue nv) {
            int argIdx = (int) nv.numberValue();
            emit(IRInstruction.of(IROpCode.LOAD_ARG, dst, argIdx), expr.getLocation());
            return dst;
        }

        int objReg = compileNode(expr.getObject(), -1);
        if (expr.getIndex() != null) {
            int idxReg = compileNode(expr.getIndex(), -1);
            emit(IRInstruction.of(IROpCode.GET_INDEX, dst, objReg, idxReg), expr.getLocation());
        } else {
            emit(IRInstruction.of(IROpCode.GET_INDEX, dst, objReg, -1), expr.getLocation());
        }
        return dst;
    }

    private int compileLambda(LambdaExpr expr, int dst) {
        // 编译函数体为子程序
        Compiler subCompiler = new Compiler();
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
        int baseReg = registerCounter;
        for (MapExpr.MapEntry entry : entries) {
            compileNode(entry.key(), -1);
            compileNode(entry.value(), -1);
        }
        emit(IRInstruction.of(IROpCode.NEW_MAP, dst, baseReg, entries.size()), expr.getLocation());
        return dst;
    }

    private int compileInterpolatedString(InterpolatedStringExpr expr, int dst) {
        List<Object> parts = expr.getParts();
        int baseReg = registerCounter;
        int count = 0;
        for (Object part : parts) {
            if (part instanceof String s) {
                int r = nextRegister();
                int ci = addConstant(new StringValue(s));
                emit(IRInstruction.of(IROpCode.LOAD_CONST, r, ci), expr.getLocation());
                count++;
            } else if (part instanceof ASTNode node) {
                compileNode(node, -1);
                count++;
            }
        }
        emit(IRInstruction.of(IROpCode.CONCAT, dst, baseReg, count), expr.getLocation());
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
        int argBase = registerCounter;
        for (ASTNode arg : args) {
            compileNode(arg, -1);
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
        compileNode(stmt.getExpression(), dst);
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
        int loopStart = currentPC();
        int condReg = compileNode(stmt.getCondition(), -1);
        int exitJump = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP_IF_FALSE, condReg, 0), stmt.getLocation());

        // 保存外层 break/next 列表，创建本层
        List<Integer> outerBreaks = new ArrayList<>(pendingBreaks);
        List<Integer> outerNexts = new ArrayList<>(pendingNexts);
        pendingBreaks.clear();
        pendingNexts.clear();

        compileNode(stmt.getBody(), -1);

        int continueTarget = currentPC(); // next 跳转到这里（重新检查条件）
        emit(IRInstruction.of(IROpCode.JUMP, 0, loopStart), stmt.getLocation());

        int breakTarget = currentPC(); // break 跳转到这里（循环出口）
        patchJump(exitJump, breakTarget);

        // 修补本层 break/next
        for (int pc : pendingBreaks) patchJump(pc, breakTarget);
        for (int pc : pendingNexts) patchJump(pc, continueTarget);

        // 恢复外层
        pendingBreaks.clear(); pendingBreaks.addAll(outerBreaks);
        pendingNexts.clear(); pendingNexts.addAll(outerNexts);
    }

    private void compileForIn(ForInStmt stmt) {
        // 编译可迭代对象
        int iterReg = compileNode(stmt.getIterable(), -1);

        List<String> vars = stmt.getVariables();
        boolean singleVar = (vars.size() == 1);

        // 单变量 for-in 优化：不用 PUSH_SCOPE/STORE_SCOPE/LOAD_SCOPE/POP_SCOPE
        // 循环变量直接用寄存器，通过 registerAliases 让循环体中的引用直接用寄存器
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

        // 第一次取元素
        emit(IRInstruction.of(IROpCode.GET_INDEX, iterVarReg, iterReg, counterReg), stmt.getLocation());

        int loopStart = currentPC();

        // 如果为 none 则退出
        int exitJump = currentPC();
        emit(IRInstruction.of(IROpCode.JUMP_IF_NONE, iterVarReg, 0), stmt.getLocation());

        if (singleVar) {
            // 单变量：注册寄存器别名，循环体中直接用寄存器
            registerAliases.put(vars.get(0), iterVarReg);
        } else {
            // 多变量解构：仍用 scope
            for (int i = 0; i < vars.size(); i++) {
                int ki = addVariableKey(vars.get(i));
                int partReg = nextRegister();
                int idxConst = addConstant(new NumberValue(i));
                int idxReg = nextRegister();
                emit(IRInstruction.of(IROpCode.LOAD_CONST, idxReg, idxConst), stmt.getLocation());
                emit(IRInstruction.of(IROpCode.GET_INDEX, partReg, iterVarReg, idxReg), stmt.getLocation());
                emit(IRInstruction.of(IROpCode.STORE_SCOPE, partReg, ki), stmt.getLocation());
            }
        }

        // 保存外层 break/next
        List<Integer> outerBreaks = new ArrayList<>(pendingBreaks);
        List<Integer> outerNexts = new ArrayList<>(pendingNexts);
        pendingBreaks.clear();
        pendingNexts.clear();

        // 循环体
        compileNode(stmt.getBody(), -1);

        // 移除寄存器别名
        if (singleVar) {
            registerAliases.remove(vars.get(0));
        }

        // next 跳转到这里（递增计数器 + 下一次迭代）
        int continueTarget = currentPC();
        emit(IRInstruction.of(IROpCode.INC, counterReg, counterReg), stmt.getLocation());
        emit(IRInstruction.of(IROpCode.GET_INDEX, iterVarReg, iterReg, counterReg), stmt.getLocation());
        emit(IRInstruction.of(IROpCode.JUMP, 0, loopStart), stmt.getLocation());

        // break 跳转到这里
        int breakTarget = currentPC();
        patchJump(exitJump, breakTarget);

        // 修补本层 break/next
        for (int pc : pendingBreaks) patchJump(pc, breakTarget);
        for (int pc : pendingNexts) patchJump(pc, continueTarget);

        // 恢复外层
        pendingBreaks.clear(); pendingBreaks.addAll(outerBreaks);
        pendingNexts.clear(); pendingNexts.addAll(outerNexts);

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

        // 保存外层 break/next
        List<Integer> outerBreaks = new ArrayList<>(pendingBreaks);
        List<Integer> outerNexts = new ArrayList<>(pendingNexts);
        pendingBreaks.clear();
        pendingNexts.clear();

        // body
        compileNode(stmt.getBody(), -1);

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
        for (int pc : pendingBreaks) patchJump(pc, breakTarget);
        for (int pc : pendingNexts) patchJump(pc, continueTarget);

        // 恢复外层
        pendingBreaks.clear(); pendingBreaks.addAll(outerBreaks);
        pendingNexts.clear(); pendingNexts.addAll(outerNexts);

        emit(IRInstruction.of(IROpCode.POP_SCOPE), stmt.getLocation());
    }

    private void compileSwitch(SwitchStmt stmt) {
        int condReg = compileNode(stmt.getCondition(), -1);

        if (stmt.isFallthrough()) {
            // 编译成：检查每个 case，匹配则跳到对应 body，不匹配跳到下一个 case 检查
            // 所有 body 顺序排列，不加 break 会穿透到下一个 body
            List<Integer> bodyLabels = new ArrayList<>(); // 每个 case body 的起始 PC
            List<Integer> checkJumps = new ArrayList<>(); // 不匹配时跳到下一个 case 检查

            // 第一阶段：生成所有 case 条件检查，匹配则跳到对应 body
            for (int i = 0; i < stmt.getCases().size(); i++) {
                CaseStmt caseStmt = stmt.getCases().get(i);
                int caseValReg = compileNode(caseStmt.getCondition(), -1);
                int eqReg = nextRegister();
                emit(IRInstruction.of(IROpCode.EQ, eqReg, condReg, caseValReg), caseStmt.getLocation());
                // 匹配则跳到 body（延迟修补）
                bodyLabels.add(-1); // 占位，后面修补
                int matchJump = currentPC();
                emit(IRInstruction.of(IROpCode.JUMP_IF_TRUE, eqReg, 0), caseStmt.getLocation());
                checkJumps.add(matchJump);
            }
            // 所有 case 都不匹配，跳到 else 或 end
            int elseJump = currentPC();
            emit(IRInstruction.of(IROpCode.JUMP, 0, 0), null);

            // 保存外层 break（switch 中的 break 跳出 switch）
            List<Integer> outerBreaks = new ArrayList<>(pendingBreaks);
            pendingBreaks.clear();

            // 第二阶段：生成所有 case body（顺序排列，穿透）
            for (int i = 0; i < stmt.getCases().size(); i++) {
                CaseStmt caseStmt = stmt.getCases().get(i);
                int bodyPC = currentPC();
                // 修补匹配跳转
                patchJump(checkJumps.get(i), bodyPC);
                compileNode(caseStmt.getBody(), -1);
                // 不加 JUMP — 穿透到下一个 body
            }

            int breakTarget = currentPC();

            // else 块
            patchJump(elseJump, stmt.getElseBlock() != null ? breakTarget : breakTarget);
            if (stmt.getElseBlock() != null) {
                int elsePC = currentPC();
                patchJump(elseJump, elsePC);
                compileNode(stmt.getElseBlock(), -1);
                breakTarget = currentPC();
            }

            // 修补 break
            for (int pc : pendingBreaks) patchJump(pc, breakTarget);
            pendingBreaks.clear();
            pendingBreaks.addAll(outerBreaks);
        } else {
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

    private void compileAsync(AsyncStmt stmt) {
        emit(IRInstruction.of(IROpCode.ASYNC_BEGIN), stmt.getLocation());
        compileNode(stmt.getBody(), -1);
        emit(IRInstruction.of(IROpCode.ASYNC_END), stmt.getLocation());
    }

    private void compileTryCatch(TryCatchStmt stmt) {
        int catchTarget = 0; // 将被修补
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
                // 异常值由解释器写入寄存器 0（约定）
                int ki = addVariableKey(stmt.getCatchVar());
                emit(IRInstruction.of(IROpCode.STORE_SCOPE, 0, ki), stmt.getLocation());
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
        Compiler fieldInitCompiler = new Compiler();
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
            Compiler staticInitCompiler = new Compiler();
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
            Compiler methodCompiler = new Compiler();
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
            Compiler ctorCompiler = new Compiler();
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
            // 编译为: var.rest = value.subList(startIdx)
            // 通过 CALL_STATIC 调用内部辅助函数
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
            subListCall.name = "subList";
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
        // 先编译内部语句
        compileNode(stmt.getStatement(), -1);

        // 提取导出的变量名
        String exportName = extractExportName(stmt.getStatement());
        if (exportName != null) {
            // 从 scope/var 加载变量值
            int valReg = nextRegister();
            int ki = addVariableKey(exportName);
            emit(IRInstruction.of(IROpCode.LOAD_SCOPE, valReg, ki), stmt.getLocation());

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
            emit(IRInstruction.of(IROpCode.STORE_SCOPE, reg, ki), stmt.getLocation());
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
                emit(IRInstruction.of(IROpCode.STORE_SCOPE, propReg, ki), stmt.getLocation());
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
                // 编译成 JUMP → 循环出口（延迟修补）
                pendingBreaks.add(currentPC());
                emit(IRInstruction.of(IROpCode.JUMP, 0, 0), stmt.getLocation());
            }
            case NEXT -> {
                // 编译成 JUMP → 循环更新（延迟修补）
                pendingNexts.add(currentPC());
                emit(IRInstruction.of(IROpCode.JUMP, 0, 0), stmt.getLocation());
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
                    case "global" -> IROpCode.STORE_GLOBAL;
                    case "server" -> IROpCode.STORE_SERVER;
                    case "client" -> IROpCode.STORE_CLIENT;
                    default -> IROpCode.STORE_SCOPE; // val 不可写，但编译期不报错
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
