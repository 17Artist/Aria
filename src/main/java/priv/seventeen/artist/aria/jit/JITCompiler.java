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

package priv.seventeen.artist.aria.jit;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.compiler.ir.IRInstruction;
import priv.seventeen.artist.aria.compiler.ir.IROpCode;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.object.ClassDefinition;
import priv.seventeen.artist.aria.object.ClassInstance;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.object.RangeObject;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.Result;
import priv.seventeen.artist.aria.value.*;
import priv.seventeen.artist.aria.value.reference.IReference;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.objectweb.asm.Opcodes.*;

public class JITCompiler {

    private static final int JIT_THRESHOLD = 5;
    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger(0);

    // ASM 类型描述符常量
    private static final String IVALUE = "priv/seventeen/artist/aria/value/IValue";
    private static final String IVALUE_DESC = "Lpriv/seventeen/artist/aria/value/IValue;";
    private static final String NUMVAL = "priv/seventeen/artist/aria/value/NumberValue";
    private static final String NUMVAL_DESC = "Lpriv/seventeen/artist/aria/value/NumberValue;";
    private static final String BOOLVAL = "priv/seventeen/artist/aria/value/BooleanValue";
    private static final String BOOLVAL_DESC = "Lpriv/seventeen/artist/aria/value/BooleanValue;";
    private static final String NONEVAL = "priv/seventeen/artist/aria/value/NoneValue";
    private static final String STRVAL = "priv/seventeen/artist/aria/value/StringValue";
    private static final String CONTEXT = "priv/seventeen/artist/aria/context/Context";
    private static final String CONTEXT_DESC = "Lpriv/seventeen/artist/aria/context/Context;";
    private static final String VKEY = "priv/seventeen/artist/aria/context/VariableKey";
    private static final String VKEY_DESC = "L" + VKEY + ";";
    private static final String VREF = "priv/seventeen/artist/aria/value/reference/VariableReference";
    private static final String VREF_DESC = "L" + VREF + ";";
    private static final String LOCAL_STORAGE = "priv/seventeen/artist/aria/context/LocalStorage";
    private static final String SCOPE_STACK = "priv/seventeen/artist/aria/context/ScopeStack";
    private static final String CALLABLE_MGR = "priv/seventeen/artist/aria/callable/CallableManager";
    private static final String ICALLABLE = "priv/seventeen/artist/aria/callable/ICallable";
    private static final String ICALLABLE_DESC = "L" + ICALLABLE + ";";
    private static final String INVOC_DATA = "priv/seventeen/artist/aria/callable/InvocationData";
    private static final String FUNCTION_VALUE = "priv/seventeen/artist/aria/value/FunctionValue";
    private static final String FUNCTION_CALLABLE = "priv/seventeen/artist/aria/callable/FunctionCallable";
    private static final String LIST_VALUE = "priv/seventeen/artist/aria/value/ListValue";
    private static final String IRPROGRAM = "priv/seventeen/artist/aria/compiler/ir/IRProgram";
    private static final String IRPROGRAM_DESC = "L" + IRPROGRAM + ";";

    public static int getThreshold() { return JIT_THRESHOLD; }

    public boolean canCompile(IRProgram program) {
        IRInstruction[] code = program.getInstructions();
        if (code == null || code.length == 0) return false;

        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                // 支持的指令
                case LOAD_ARG, LOAD_CONST, LOAD_NONE, LOAD_TRUE, LOAD_FALSE:
                case ADD, SUB, MUL, DIV, MOD, NEG, INC, DEC:
                case ADD_NUM, SUB_NUM, MUL_NUM, DIV_NUM, MOD_NUM:
                case EQ, NE, LT, GT, LE, GE, NOT:
                case AND, OR:
                case JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE, JUMP_IF_NONE:
                case RETURN:
                case MOVE, NOP:
                case PUSH_SCOPE, POP_SCOPE:
                    // 变量与作用域
                case STORE_VAR:
                case LOAD_SCOPE, STORE_SCOPE:
                    // 命名空间变量读取（写入暂走解释器）
                case LOAD_GLOBAL, LOAD_SERVER, LOAD_CLIENT:
                    // 索引访问 / 属性访问
                case GET_INDEX:
                case GET_PROP:
                    // 复合优化指令
                case VAR_INC, VAR_ADD_CONST, VAR_ADD_REG:
                    // self / args
                case LOAD_ARGS, LOAD_SELF:
                    // 字符串拼接
                case CONCAT:
                    // 函数定义
                case NEW_FUNCTION:
                    // 构造器调用
                case CALL_CONSTRUCTOR:
                    // 方法调用、集合创建、索引写入
                case CALL_METHOD:
                case NEW_LIST:
                case NEW_MAP:
                case SET_INDEX:
                    // for-range（Compiler 当前未 emit，留作未来扩展防御）
                case FOR_RANGE_INIT, FOR_RANGE_NEXT:
                    // BREAK 当前 Compiler 编译为 JUMP，未实际 emit BREAK，防御性放行
                case BREAK:
                    break;
                case LOAD_VAR:
                    // LOAD_VAR 用于加载递归函数引用 — 允许
                    break;
                case CALL:
                    // CALL 只允许自递归调用（callee 来自 LOAD_VAR）
                    break;
                case CALL_STATIC:
                    if (isSupportedStaticCall(inst)) break;
                    if (inst.name != null && inst.name.contains(".")) break;
                    if (inst.name != null && inst.name.equals(program.getName())) break;
                    if (inst.name != null) {
                        boolean canInline = false;
                        for (int j = 0; j < code.length - 1; j++) {
                            if (code[j].opcode == IROpCode.NEW_FUNCTION
                                    && code[j + 1].opcode == IROpCode.STORE_VAR
                                    && code[j + 1].a < program.getVariableKeys().length
                                    && inst.name.equals(program.getVariableKeys()[code[j + 1].a].getName())) {
                                int subIdx = code[j].a;
                                if (subIdx < program.getSubPrograms().length) {
                                    canInline = detectSimpleUnaryLambda(program.getSubPrograms()[subIdx]) != null;
                                }
                                break;
                            }
                        }
                        if (!canInline) return false;
                        break;
                    }
                    return false;
                default:
                    return false;
            }
        }
        return true;
    }

    private boolean isSupportedStaticCall(IRInstruction inst) {
        String fn = inst.name;
        if (fn == null) return false;
        return switch (fn) {
            case "math.sin", "math.cos", "math.tan", "math.abs",
                 "math.floor", "math.ceil", "math.round", "math.sqrt",
                 "math.log", "math.pow", "math.min", "math.max",
                 "math.random", "math.PI", "math.E",
                 "io.print", "io.println" -> true;
            default -> false;
        };
    }

    public ICallable compile(IRProgram subProgram, Context context) {
        try {
            return doCompile(subProgram);
        } catch (Exception e) {
            return null;
        }
    }

    private ICallable doCompile(IRProgram program) throws Exception {
        IRInstruction[] code = program.getInstructions();
        IValue<?>[] constants = program.getConstants();
        VariableKey[] keys = program.getVariableKeys();
        IRProgram[] subPrograms = program.getSubPrograms();

        int maxArgIndex = detectMaxArgIndex(code);
        int argCount = maxArgIndex + 1;

        Set<Integer> selfRecursiveCallPCs = detectSelfRecursion(code, keys, program);

        boolean numericOnly = isNumericOnly(code) && constantsAreNumeric(constants);

        Map<String, IRProgram> varFunctionMap = new HashMap<>();
        for (int i = 0; i < code.length - 1; i++) {
            if (code[i].opcode == IROpCode.NEW_FUNCTION && code[i + 1].opcode == IROpCode.STORE_VAR) {
                int subIdx = code[i].a;
                int keyIdx = code[i + 1].a;
                if (subIdx < subPrograms.length && keyIdx < keys.length) {
                    varFunctionMap.put(keys[keyIdx].getName(), subPrograms[subIdx]);
                }
            }
        }

        boolean fastDoubleRecursion = numericOnly && !selfRecursiveCallPCs.isEmpty();

        boolean hasVarOps = hasVarOperations(code);
        boolean fastDoubleVars = numericOnly && selfRecursiveCallPCs.isEmpty() && hasVarOps && argCount == 0;

        boolean fastLongVars = fastDoubleVars && isIntegerSafe(code, constants);

        int regCount = program.getRegisterCount();
        boolean[] usedRegs = analyzeUsedRegisters(code, regCount);

        int ctxLocal = argCount;
        int regBase = argCount + 1;
        int[] regToLocal = new int[regCount];
        int nextLocal = regBase;
        for (int i = 0; i < regCount; i++) {
            if (usedRegs[i]) {
                regToLocal[i] = nextLocal++;
            } else {
                regToLocal[i] = -1;
            }
        }

        // 5. 生成类
        int classId = CLASS_COUNTER.incrementAndGet();
        String className = "priv/seventeen/artist/aria/jit/CompiledFunc_" + classId;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String icallable = "priv/seventeen/artist/aria/callable/ICallable";
        cw.visit(V17, ACC_PUBLIC | ACC_FINAL, className, null, "java/lang/Object",
                new String[]{icallable});

        // 静态字段：常量池和变量键
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "CONSTANTS", "[Lpriv/seventeen/artist/aria/value/IValue;", null, null).visitEnd();
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "KEYS", "[Lpriv/seventeen/artist/aria/context/VariableKey;", null, null).visitEnd();
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "SUB_PROGRAMS", "[" + IRPROGRAM_DESC, null, null).visitEnd();

        if (fastDoubleRecursion) {
            // 生成 callFast(double, double, ...) → double
            String fastDesc = buildFastDescriptor(argCount);
            MethodVisitor fmv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "callFast", fastDesc, null, null);
            fmv.visitCode();
            emitFastDoubleBytecode(fmv, code, constants, argCount, regToLocal, regCount,
                    usedRegs, selfRecursiveCallPCs, className, fastDesc);
            fmv.visitMaxs(0, 0);
            fmv.visitEnd();

            // 生成 call(IValue, ..., Context) → IValue 作为入口包装
            String callDesc = buildCallDescriptor(argCount);
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "call", callDesc, null, null);
            mv.visitCode();
            // 提取 double 参数
            for (int i = 0; i < argCount; i++) {
                mv.visitVarInsn(ALOAD, i);
                mv.visitTypeInsn(CHECKCAST, NUMVAL);
                mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
            }
            mv.visitMethodInsn(INVOKESTATIC, className, "callFast", fastDesc, false);
            // 包装返回值
            emitNewNumberValue(mv);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // invoke 方法 — 直接调用 callFast，跳过 call 中间层
            {
                String INVDATA = "priv/seventeen/artist/aria/callable/InvocationData";
                String INVDATA_DESC = "Lpriv/seventeen/artist/aria/callable/InvocationData;";
                MethodVisitor imv = cw.visitMethod(ACC_PUBLIC, "invoke",
                        "(" + INVDATA_DESC + ")" + IVALUE_DESC, null,
                        new String[]{"priv/seventeen/artist/aria/exception/AriaException"});
                imv.visitCode();
                // 从 InvocationData 提取参数并直接拆箱为 double
                for (int i = 0; i < argCount; i++) {
                    imv.visitVarInsn(ALOAD, 1); // data
                    emitIntConst(imv, i);
                    imv.visitMethodInsn(INVOKEVIRTUAL, INVDATA, "get", "(I)" + IVALUE_DESC, false);
                    imv.visitTypeInsn(CHECKCAST, NUMVAL);
                    imv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
                }
                // invokestatic callFast(double, ...) → double
                imv.visitMethodInsn(INVOKESTATIC, className, "callFast", fastDesc, false);
                // 包装返回值为 NumberValue
                emitNewNumberValue(imv);
                imv.visitInsn(ARETURN);
                imv.visitMaxs(0, 0);
                imv.visitEnd();
            }
        } else if (fastLongVars) {
            // 生成 callFast(Context) → double（返回 double 保持兼容）
            String fastVarDesc = "(" + CONTEXT_DESC + ")D";
            MethodVisitor fmv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "callFast", fastVarDesc, null, null);
            fmv.visitCode();
            emitFastLongVarBytecode(fmv, code, constants, keys, argCount, regCount,
                    usedRegs, className, fastVarDesc, varFunctionMap);
            fmv.visitMaxs(0, 0);
            fmv.visitEnd();

            // 生成 call(IValue, ..., Context) → IValue 作为入口包装
            String callDesc = buildCallDescriptor(argCount);
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "call", callDesc, null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, argCount); // ctx 在参数列表最后
            mv.visitMethodInsn(INVOKESTATIC, className, "callFast", fastVarDesc, false);
            emitNewNumberValue(mv);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // invoke 方法
            emitInvokeMethod(cw, className, callDesc, argCount);
        } else if (fastDoubleVars) {
            // 生成 callFast(Context) → double
            String fastVarDesc = "(" + CONTEXT_DESC + ")D";
            MethodVisitor fmv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "callFast", fastVarDesc, null, null);
            fmv.visitCode();
            emitFastDoubleVarBytecode(fmv, code, constants, keys, argCount, regCount,
                    usedRegs, className, fastVarDesc, varFunctionMap);
            fmv.visitMaxs(0, 0);
            fmv.visitEnd();

            // 生成 call(IValue, ..., Context) → IValue 作为入口包装
            String callDesc = buildCallDescriptor(argCount);
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "call", callDesc, null, null);
            mv.visitCode();
            // 传递 Context 参数
            mv.visitVarInsn(ALOAD, argCount); // ctx 在参数列表最后
            mv.visitMethodInsn(INVOKESTATIC, className, "callFast", fastVarDesc, false);
            // 包装返回值
            emitNewNumberValue(mv);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // invoke 方法
            emitInvokeMethod(cw, className, callDesc, argCount);
        } else {
            String callDesc = buildCallDescriptor(argCount);
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "call", callDesc, null, null);
            mv.visitCode();
            emitBytecode(mv, code, constants, argCount, ctxLocal, regToLocal, regCount,
                    usedRegs, selfRecursiveCallPCs, className, callDesc, numericOnly, program);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            emitInvokeMethod(cw, className, callDesc, argCount);
        }

        // 默认构造器
        MethodVisitor ctor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        cw.visitEnd();

        // 6. 加载类
        byte[] bytecode = cw.toByteArray();
        String dumpDir = System.getProperty("aria.jit.dump");
        if (dumpDir != null) {
            try {
                java.io.File dir = new java.io.File(dumpDir);
                dir.mkdirs();
                java.nio.file.Files.write(new java.io.File(dir, className.replace('/', '_') + ".class").toPath(), bytecode);
            } catch (java.io.IOException ignored) {}
        }
        Class<?> clazz = loadClass(className.replace('/', '.'), bytecode);

        // 7. 设置静态字段
        clazz.getField("CONSTANTS").set(null, constants);
        clazz.getField("KEYS").set(null, keys);
        clazz.getField("SUB_PROGRAMS").set(null, subPrograms);
        // 标记纯数值 JIT 路径不依赖 Context — NEW_FUNCTION 包装可省一层 lambda + InvocationData
        if (fastDoubleRecursion || fastDoubleVars || fastLongVars) {
            program.setJitContextFree(true);
        }
        // 8. 实例化为 ICallable
        return (ICallable) clazz.getDeclaredConstructor().newInstance();
    }


    private int detectMaxArgIndex(IRInstruction[] code) {
        int max = -1;
        for (IRInstruction inst : code) {
            if (inst.opcode == IROpCode.LOAD_ARG) {
                max = Math.max(max, inst.a);
            }
        }
        return max;
    }

    private Set<Integer> detectSelfRecursion(IRInstruction[] code, VariableKey[] keys, IRProgram program) {
        Set<Integer> result = new HashSet<>();
        // 只有函数（有 LOAD_ARG）才可能自递归。主程序不可能自递归。
        boolean isFunction = false;
        for (IRInstruction inst : code) {
            if (inst.opcode == IROpCode.LOAD_ARG) { isFunction = true; break; }
        }
        if (!isFunction) return result;

        String selfName = program.getName();
        Map<Integer, Integer> loadVarRegs = new HashMap<>();
        for (int pc = 0; pc < code.length; pc++) {
            IRInstruction inst = code[pc];
            if (inst.opcode == IROpCode.LOAD_VAR) {
                loadVarRegs.put(inst.dst, inst.a);
            }
            if (inst.opcode == IROpCode.CALL) {
                Integer keyIdx = loadVarRegs.get(inst.a);
                if (keyIdx != null) {
                    result.add(pc);
                }
            }
            // CALL_STATIC name=<self> 也是自递归 — 编译器把 var.fib = -> 时 fib 的名字传给了 subProg.name
            if (inst.opcode == IROpCode.CALL_STATIC && selfName != null && selfName.equals(inst.name)) {
                result.add(pc);
            }
        }
        return result;
    }

    private boolean isNumericOnly(IRInstruction[] code) {
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case CONCAT:
                case LOAD_SCOPE, STORE_SCOPE:
                case GET_INDEX, CALL_CONSTRUCTOR:
                case LOAD_ARGS, LOAD_SELF:
                case NEW_LIST, NEW_MAP:
                case CALL_METHOD, SET_INDEX:
                    return false;
                // NEW_FUNCTION 产出一个函数对象（非数值），后续 STORE_VAR 会把它写到 var.x。
                // fastDoubleVars / fastLongVars 把寄存器当作 double / long 处理，NEW_FUNCTION
                // 被跳过后寄存器保持 0，STORE_VAR 会把 var.x 写成数字 0，函数值丢失。
                // 因此包含 NEW_FUNCTION 的程序必须走通用 JIT 路径，不能走纯数值路径。
                case NEW_FUNCTION:
                    return false;
                // CALL 不阻止 numericOnly — 自递归调用在 fastDouble 路径中被特殊处理
                default:
                    break;
            }
        }
        return true;
    }

    private boolean isIntegerSafe(IRInstruction[] code, IValue<?>[] constants) {
        // 检查所有常量是否为整数
        if (constants != null) {
            for (IValue<?> c : constants) {
                if (c instanceof NumberValue nv) {
                    if (nv.value != Math.floor(nv.value) || nv.value > Long.MAX_VALUE || nv.value < Long.MIN_VALUE) return false;
                }
            }
        }
        // 除法可能产生小数，不安全
        for (IRInstruction inst : code) {
            if (inst.opcode == IROpCode.DIV || inst.opcode == IROpCode.DIV_NUM) return false;
        }
        return true;
    }

    private boolean constantsAreNumeric(IValue<?>[] constants) {
        if (constants == null) return true;
        for (IValue<?> c : constants) {
            if (c instanceof StringValue) return false;
        }
        return true;
    }

    private boolean hasVarOperations(IRInstruction[] code) {
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case STORE_VAR, VAR_INC, VAR_ADD_CONST, VAR_ADD_REG:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    private boolean[] analyzeUsedRegisters(IRInstruction[] code, int regCount) {
        boolean[] used = new boolean[regCount];
        for (IRInstruction inst : code) {
            if (inst.dst >= 0 && inst.dst < regCount) used[inst.dst] = true;
            if (inst.a >= 0 && inst.a < regCount) {
                switch (inst.opcode) {
                    case ADD, SUB, MUL, DIV, MOD, ADD_NUM, SUB_NUM, MUL_NUM, DIV_NUM, MOD_NUM:
                    case EQ, NE, LT, GT, LE, GE:
                    case AND, OR:
                    case NEG, NOT, INC, DEC, MOVE:
                    case GET_INDEX:
                        used[inst.a] = true;
                        break;
                    case CALL:
                        used[inst.a] = true;
                        break;
                    default:
                        break;
                }
            }
            if (inst.b >= 0 && inst.b < regCount) {
                switch (inst.opcode) {
                    case ADD, SUB, MUL, DIV, MOD, ADD_NUM, SUB_NUM, MUL_NUM, DIV_NUM, MOD_NUM:
                    case EQ, NE, LT, GT, LE, GE:
                    case AND, OR:
                    case GET_INDEX:
                    case VAR_ADD_REG:
                        used[inst.b] = true;
                        break;
                    default:
                        break;
                }
            }
            // CALL 的参数寄存器
            if (inst.opcode == IROpCode.CALL) {
                int argBase = inst.c;
                int argCnt = inst.b;
                for (int i = 0; i < argCnt; i++) {
                    int r = argBase + i;
                    if (r >= 0 && r < regCount) used[r] = true;
                }
            }
            // CALL_STATIC 的参数寄存器
            if (inst.opcode == IROpCode.CALL_STATIC) {
                int argBase = inst.a;
                int argCnt = inst.b;
                for (int i = 0; i < argCnt; i++) {
                    int r = argBase + i;
                    if (r >= 0 && r < regCount) used[r] = true;
                }
            }
            // CALL_CONSTRUCTOR 的参数寄存器
            if (inst.opcode == IROpCode.CALL_CONSTRUCTOR) {
                int argBase = inst.a;
                int argCnt = inst.b;
                for (int i = 0; i < argCnt; i++) {
                    int r = argBase + i;
                    if (r >= 0 && r < regCount) used[r] = true;
                }
            }
            // CONCAT 的参数寄存器
            if (inst.opcode == IROpCode.CONCAT) {
                int baseReg = inst.a;
                int count = inst.b;
                for (int i = 0; i < count; i++) {
                    int r = baseReg + i;
                    if (r >= 0 && r < regCount) used[r] = true;
                }
            }
            // CALL_METHOD: a=objReg, b=argCount, c=argBase
            if (inst.opcode == IROpCode.CALL_METHOD) {
                if (inst.a >= 0 && inst.a < regCount) used[inst.a] = true;
                int argBase = inst.c;
                int argCnt = inst.b;
                for (int i = 0; i < argCnt; i++) {
                    int r = argBase + i;
                    if (r >= 0 && r < regCount) used[r] = true;
                }
            }
            // NEW_LIST: a=baseReg, b=count
            if (inst.opcode == IROpCode.NEW_LIST) {
                int baseReg = inst.a;
                int count = inst.b;
                for (int i = 0; i < count; i++) {
                    int r = baseReg + i;
                    if (r >= 0 && r < regCount) used[r] = true;
                }
            }
            // NEW_MAP: a=baseReg, b=entryCount (key-value pairs)
            if (inst.opcode == IROpCode.NEW_MAP) {
                int baseReg = inst.a;
                int entryCount = inst.b;
                for (int i = 0; i < entryCount * 2; i++) {
                    int r = baseReg + i;
                    if (r >= 0 && r < regCount) used[r] = true;
                }
            }
            // SET_INDEX: dst=objReg, a=idxReg, b=valueReg
            if (inst.opcode == IROpCode.SET_INDEX) {
                if (inst.dst >= 0 && inst.dst < regCount) used[inst.dst] = true;
                if (inst.a >= 0 && inst.a < regCount) used[inst.a] = true;
                if (inst.b >= 0 && inst.b < regCount) used[inst.b] = true;
            }
        }
        return used;
    }

    private String buildFastDescriptor(int argCount) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < argCount; i++) sb.append("D");
        sb.append(")D");
        return sb.toString();
    }


    private String buildCallDescriptor(int argCount) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < argCount; i++) sb.append(IVALUE_DESC);
        sb.append(CONTEXT_DESC);
        sb.append(")").append(IVALUE_DESC);
        return sb.toString();
    }

    private Class<?>[] buildCallParamTypes(int argCount) {
        Class<?>[] types = new Class<?>[argCount + 1];
        for (int i = 0; i < argCount; i++) types[i] = IValue.class;
        types[argCount] = Context.class;
        return types;
    }


    private void emitFastDoubleBytecode(MethodVisitor mv, IRInstruction[] code, IValue<?>[] constants,
                                        int argCount, int[] regToLocal, int regCount,
                                        boolean[] usedRegs, Set<Integer> selfRecursivePCs,
                                        String className, String fastDesc) {
        int[] fastRegToLocal = new int[regCount];
        int nextLocal = argCount * 2;
        for (int i = 0; i < regCount; i++) {
            if (usedRegs[i]) {
                fastRegToLocal[i] = nextLocal;
                nextLocal += 2;
            } else {
                fastRegToLocal[i] = -1;
            }
        }

        // 标签
        Label[] labels = new Label[code.length + 1];
        for (int i = 0; i <= code.length; i++) labels[i] = new Label();

        for (int i = 0; i < regCount; i++) {
            if (usedRegs[i] && fastRegToLocal[i] >= 0) {
                mv.visitInsn(DCONST_0);
                mv.visitVarInsn(DSTORE, fastRegToLocal[i]);
            }
        }

        for (int pc = 0; pc < code.length; pc++) {
            mv.visitLabel(labels[pc]);
            IRInstruction inst = code[pc];

            switch (inst.opcode) {
                case LOAD_CONST -> {
                    IValue<?> c = constants[inst.a];
                    double d = (c instanceof NumberValue nv) ? nv.value : c.numberValue();
                    emitDoubleConst(mv, d);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case LOAD_NONE, LOAD_FALSE, LOAD_VAR -> {
                    mv.visitInsn(DCONST_0);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case LOAD_TRUE -> {
                    mv.visitInsn(DCONST_1);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case LOAD_ARG -> {
                    if (inst.a < argCount) {
                        mv.visitVarInsn(DLOAD, inst.a * 2);
                    } else {
                        mv.visitInsn(DCONST_0);
                    }
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }

                case ADD, ADD_NUM -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DADD);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case SUB, SUB_NUM -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DSUB);
                    // 窥孔：SUB→CALL 融合（仅 CALL 形式；CALL_STATIC 形式由非融合路径处理以确保正确性）
                    boolean fusedSub = false;
                    if (pc + 1 < code.length) {
                        IRInstruction nx = code[pc + 1];
                        if (nx.opcode == IROpCode.CALL && selfRecursivePCs.contains(pc + 1)
                                && nx.b == 1 && nx.c == inst.dst) {
                            for (int i = 1; i < argCount; i++) mv.visitInsn(DCONST_0);
                            mv.visitMethodInsn(INVOKESTATIC, className, "callFast", fastDesc, false);
                            mv.visitVarInsn(DSTORE, fastRegToLocal[nx.dst]);
                            pc++;
                            fusedSub = true;
                        }
                    }
                    if (!fusedSub) {
                        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                    }
                }
                case MUL, MUL_NUM -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DMUL);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case DIV, DIV_NUM -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DDIV);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case MOD, MOD_NUM -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DREM);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case NEG -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitInsn(DNEG);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case INC -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitInsn(DCONST_1);
                    mv.visitInsn(DADD);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case DEC -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitInsn(DCONST_1);
                    mv.visitInsn(DSUB);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }

                case LE, LT, GT, GE, EQ, NE, NOT, AND, OR,
                     JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE, JUMP_IF_NONE -> {
                    pc = emitDoubleCmpAndControl(mv, code, pc, inst, fastRegToLocal, labels);
                }

                case RETURN -> {
                    if (inst.dst >= 0 && fastRegToLocal[inst.dst] >= 0) {
                        mv.visitVarInsn(DLOAD, fastRegToLocal[inst.dst]);
                    } else {
                        mv.visitInsn(DCONST_0);
                    }
                    mv.visitInsn(DRETURN);
                }

                case CALL -> {
                    if (selfRecursivePCs.contains(pc)) {
                        int callArgCount = inst.b;
                        int callArgBase = inst.c;
                        for (int i = 0; i < callArgCount; i++) {
                            int r = callArgBase + i;
                            if (fastRegToLocal[r] >= 0) {
                                mv.visitVarInsn(DLOAD, fastRegToLocal[r]);
                            } else {
                                mv.visitInsn(DCONST_0);
                            }
                        }
                        for (int i = callArgCount; i < argCount; i++) {
                            mv.visitInsn(DCONST_0);
                        }
                        mv.visitMethodInsn(INVOKESTATIC, className, "callFast", fastDesc, false);
                        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                    } else {
                        mv.visitInsn(DCONST_0);
                        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                    }
                }
                case CALL_STATIC -> {
                    if (selfRecursivePCs.contains(pc)) {
                        // CALL_STATIC 自递归：a=argBase, b=argCount，调用方保证寄存器都是 double 局部
                        int callArgBase = inst.a;
                        int callArgCount = inst.b;
                        for (int i = 0; i < callArgCount; i++) {
                            int r = callArgBase + i;
                            if (fastRegToLocal[r] >= 0) {
                                mv.visitVarInsn(DLOAD, fastRegToLocal[r]);
                            } else {
                                mv.visitInsn(DCONST_0);
                            }
                        }
                        for (int i = callArgCount; i < argCount; i++) {
                            mv.visitInsn(DCONST_0);
                        }
                        mv.visitMethodInsn(INVOKESTATIC, className, "callFast", fastDesc, false);
                        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                    } else {
                        emitFastStaticCall(mv, fastRegToLocal, inst, Collections.emptyMap());
                    }
                }

                case MOVE -> {
                    if (fastRegToLocal[inst.a] != fastRegToLocal[inst.dst]) {
                        mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                    }
                }
                case PUSH_SCOPE, POP_SCOPE, NOP -> {}
            }
        }

        mv.visitLabel(labels[code.length]);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DRETURN);
    }


    private int emitDoubleCmpAndControl(MethodVisitor mv, IRInstruction[] code, int pc,
                                        IRInstruction inst, int[] fastRegToLocal, Label[] labels) {
        switch (inst.opcode) {
            case LE, LT, GT, GE -> {
                IRInstruction next = (pc + 1 < code.length) ? code[pc + 1] : null;
                if (next != null && (next.opcode == IROpCode.JUMP_IF_TRUE || next.opcode == IROpCode.JUMP_IF_FALSE) && next.dst == inst.dst) {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    int cmpInsn = (inst.opcode == IROpCode.LE || inst.opcode == IROpCode.LT) ? DCMPG : DCMPL;
                    mv.visitInsn(cmpInsn);
                    boolean jumpIfTrue = next.opcode == IROpCode.JUMP_IF_TRUE;
                    int fusedOp = switch (inst.opcode) {
                        case LE -> jumpIfTrue ? IFLE : IFGT;
                        case LT -> jumpIfTrue ? IFLT : IFGE;
                        case GT -> jumpIfTrue ? IFGT : IFLE;
                        case GE -> jumpIfTrue ? IFGE : IFLT;
                        default -> IFEQ;
                    };
                    mv.visitJumpInsn(fusedOp, labels[next.a]);
                    pc++;
                } else {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    int cmpInsn = (inst.opcode == IROpCode.LE || inst.opcode == IROpCode.LT) ? DCMPG : DCMPL;
                    mv.visitInsn(cmpInsn);
                    int ifZeroOp = switch (inst.opcode) {
                        case LE -> IFLE;
                        case LT -> IFLT;
                        case GE -> IFGE;
                        case GT -> IFGT;
                        default -> IFEQ;
                    };
                    Label trueL = new Label();
                    Label endL = new Label();
                    mv.visitJumpInsn(ifZeroOp, trueL);
                    mv.visitInsn(DCONST_0);
                    mv.visitJumpInsn(GOTO, endL);
                    mv.visitLabel(trueL);
                    mv.visitInsn(DCONST_1);
                    mv.visitLabel(endL);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
            }
            case EQ -> {
                IRInstruction next = (pc + 1 < code.length) ? code[pc + 1] : null;
                if (next != null && (next.opcode == IROpCode.JUMP_IF_TRUE || next.opcode == IROpCode.JUMP_IF_FALSE) && next.dst == inst.dst) {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DCMPL);
                    int fusedOp = (next.opcode == IROpCode.JUMP_IF_TRUE) ? IFEQ : IFNE;
                    mv.visitJumpInsn(fusedOp, labels[next.a]);
                    pc++;
                } else {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DCMPL);
                    Label trueL = new Label();
                    Label endL = new Label();
                    mv.visitJumpInsn(IFEQ, trueL);
                    mv.visitInsn(DCONST_0);
                    mv.visitJumpInsn(GOTO, endL);
                    mv.visitLabel(trueL);
                    mv.visitInsn(DCONST_1);
                    mv.visitLabel(endL);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
            }
            case NE -> {
                IRInstruction next = (pc + 1 < code.length) ? code[pc + 1] : null;
                if (next != null && (next.opcode == IROpCode.JUMP_IF_TRUE || next.opcode == IROpCode.JUMP_IF_FALSE) && next.dst == inst.dst) {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DCMPL);
                    int fusedOp = (next.opcode == IROpCode.JUMP_IF_TRUE) ? IFNE : IFEQ;
                    mv.visitJumpInsn(fusedOp, labels[next.a]);
                    pc++;
                } else {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DCMPL);
                    Label trueL = new Label();
                    Label endL = new Label();
                    mv.visitJumpInsn(IFNE, trueL);
                    mv.visitInsn(DCONST_0);
                    mv.visitJumpInsn(GOTO, endL);
                    mv.visitLabel(trueL);
                    mv.visitInsn(DCONST_1);
                    mv.visitLabel(endL);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
            }
            case NOT -> {
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                Label trueL = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFEQ, trueL);
                mv.visitInsn(DCONST_0);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(trueL);
                mv.visitInsn(DCONST_1);
                mv.visitLabel(endL);
                mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
            }
            case AND -> {
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                Label useRight = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFNE, useRight);
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(useRight);
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                mv.visitLabel(endL);
                mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
            }
            case OR -> {
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                Label useRight = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFEQ, useRight);
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(useRight);
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                mv.visitLabel(endL);
                mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
            }
            case JUMP -> mv.visitJumpInsn(GOTO, labels[inst.a]);
            case JUMP_IF_TRUE -> {
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.dst]);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                mv.visitJumpInsn(IFNE, labels[inst.a]);
            }
            case JUMP_IF_FALSE -> {
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.dst]);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                mv.visitJumpInsn(IFEQ, labels[inst.a]);
            }
            case JUMP_IF_NONE -> {
                // 在 double 路径中，NONE 映射为 0.0 — 不跳转
            }
            default -> {}
        }
        return pc;
    }



    private void emitFastDoubleVarBytecode(MethodVisitor mv, IRInstruction[] code, IValue<?>[] constants,
                                           VariableKey[] keys, int argCount, int regCount,
                                           boolean[] usedRegs, String className, String fastVarDesc,
                                           Map<String, IRProgram> varFunctionMap) {
        // 局部变量布局: [ctx(1 slot), reg0(2), reg1(2), ..., var0(2), var1(2), ...]
        int ctxSlot = 0; // Context 参数在 slot 0
        int[] fastRegToLocal = new int[regCount];
        int nextLocal = 1; // ctx 之后
        for (int i = 0; i < regCount; i++) {
            if (usedRegs[i]) {
                fastRegToLocal[i] = nextLocal;
                nextLocal += 2; // double 占 2 slots
            } else {
                fastRegToLocal[i] = -1;
            }
        }

        // 收集所有 var 变量的 key 索引
        Set<Integer> varKeyIndices = new LinkedHashSet<>();
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case LOAD_VAR, STORE_VAR, VAR_INC, VAR_ADD_CONST, VAR_ADD_REG -> varKeyIndices.add(inst.a);
                default -> {}
            }
        }

        // 为每个 var 变量分配 double 局部变量 slot
        Map<Integer, Integer> varDoubleSlots = new HashMap<>();
        for (int keyIdx : varKeyIndices) {
            varDoubleSlots.put(keyIdx, nextLocal);
            nextLocal += 2; // double 占 2 slots
        }

        // 标签
        Label[] labels = new Label[code.length + 1];
        for (int i = 0; i <= code.length; i++) labels[i] = new Label();

        // 初始化寄存器为 0.0
        for (int i = 0; i < regCount; i++) {
            if (usedRegs[i] && fastRegToLocal[i] >= 0) {
                mv.visitInsn(DCONST_0);
                mv.visitVarInsn(DSTORE, fastRegToLocal[i]);
            }
        }

        // 从 Context 读取 var 变量初始值到 double 局部变量
        // ctx.getLocalStorage().getVarVariable(KEYS[keyIdx]).getValue() → checkcast NumberValue → .value
        for (int keyIdx : varKeyIndices) {
            int varSlot = varDoubleSlots.get(keyIdx);
            mv.visitVarInsn(ALOAD, ctxSlot);
            mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getLocalStorage", "()L" + LOCAL_STORAGE + ";", false);
            mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
            emitIntConst(mv, keyIdx);
            mv.visitInsn(AALOAD);
            mv.visitMethodInsn(INVOKEVIRTUAL, LOCAL_STORAGE, "getVarVariable", "(" + VKEY_DESC + ")" + VREF_DESC, false);
            mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "getValue", "()" + IVALUE_DESC, false);
            // 如果是 NumberValue 取 value，否则用 0.0
            Label isNum = new Label(), done = new Label();
            mv.visitInsn(DUP);
            mv.visitTypeInsn(INSTANCEOF, NUMVAL);
            mv.visitJumpInsn(IFNE, isNum);
            mv.visitInsn(POP);
            mv.visitInsn(DCONST_0);
            mv.visitJumpInsn(GOTO, done);
            mv.visitLabel(isNum);
            mv.visitTypeInsn(CHECKCAST, NUMVAL);
            mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
            mv.visitLabel(done);
            mv.visitVarInsn(DSTORE, varSlot);
        }

        // 生成字节码
        for (int pc = 0; pc < code.length; pc++) {
            mv.visitLabel(labels[pc]);
            IRInstruction inst = code[pc];

            switch (inst.opcode) {
                case LOAD_CONST -> {
                    IValue<?> c = constants[inst.a];
                    double d = (c instanceof NumberValue nv) ? nv.value : c.numberValue();
                    emitDoubleConst(mv, d);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case LOAD_NONE -> {
                    mv.visitInsn(DCONST_0);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case LOAD_TRUE -> {
                    mv.visitInsn(DCONST_1);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case LOAD_FALSE -> {
                    mv.visitInsn(DCONST_0);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case LOAD_ARG -> {
                    // 主程序通常没有参数，默认 0.0
                    mv.visitInsn(DCONST_0);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }

                case LOAD_VAR -> {
                    Integer varSlot = varDoubleSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(DLOAD, varSlot);
                        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                    } else {
                        mv.visitInsn(DCONST_0);
                        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                    }
                }
                case STORE_VAR -> {
                    Integer varSlot = varDoubleSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(DLOAD, fastRegToLocal[inst.dst]);
                        mv.visitVarInsn(DSTORE, varSlot);
                    }
                }
                case VAR_INC -> {
                    // var[a] += 1
                    Integer varSlot = varDoubleSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(DLOAD, varSlot);
                        mv.visitInsn(DCONST_1);
                        mv.visitInsn(DADD);
                        mv.visitVarInsn(DSTORE, varSlot);
                    }
                }
                case VAR_ADD_CONST -> {
                    // var[a] += constants[b]
                    Integer varSlot = varDoubleSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(DLOAD, varSlot);
                        IValue<?> c = constants[inst.b];
                        double d = (c instanceof NumberValue nv) ? nv.value : c.numberValue();
                        emitDoubleConst(mv, d);
                        mv.visitInsn(DADD);
                        mv.visitVarInsn(DSTORE, varSlot);
                    }
                }
                case VAR_ADD_REG -> {
                    // var[a] += reg[b]
                    Integer varSlot = varDoubleSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(DLOAD, varSlot);
                        mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                        mv.visitInsn(DADD);
                        mv.visitVarInsn(DSTORE, varSlot);
                    }
                }

                case ADD, ADD_NUM -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DADD);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case SUB, SUB_NUM -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DSUB);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case MUL, MUL_NUM -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DMUL);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case DIV, DIV_NUM -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DDIV);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case MOD, MOD_NUM -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(DREM);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case NEG -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitInsn(DNEG);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case INC -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitInsn(DCONST_1);
                    mv.visitInsn(DADD);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case DEC -> {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitInsn(DCONST_1);
                    mv.visitInsn(DSUB);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }

                case LE, LT, GT, GE, EQ, NE, NOT, AND, OR,
                     JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE, JUMP_IF_NONE -> {
                    pc = emitDoubleCmpAndControl(mv, code, pc, inst, fastRegToLocal, labels);
                }

                case RETURN -> {
                    // 写回 var 变量到 Context
                    emitVarWriteBack(mv, varDoubleSlots, ctxSlot, className);
                    if (inst.dst >= 0 && fastRegToLocal[inst.dst] >= 0) {
                        mv.visitVarInsn(DLOAD, fastRegToLocal[inst.dst]);
                    } else {
                        mv.visitInsn(DCONST_0);
                    }
                    mv.visitInsn(DRETURN);
                }

                case CALL_STATIC -> emitFastStaticCall(mv, fastRegToLocal, inst, varFunctionMap);

                // NEW_FUNCTION: 在 fastDoubleVars 路径中跳过（函数定义不产生 double 值）
                // 函数会在解释器预热阶段被定义并存到 var 变量
                case NEW_FUNCTION -> {}

                case MOVE -> {
                    if (fastRegToLocal[inst.a] != fastRegToLocal[inst.dst]) {
                        mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                    }
                }
                case PUSH_SCOPE, POP_SCOPE, NOP -> {}
            }
        }

        // 尾部安全返回（写回 var 变量）
        mv.visitLabel(labels[code.length]);
        emitVarWriteBack(mv, varDoubleSlots, ctxSlot, className);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DRETURN);
    }

    private void emitVarWriteBack(MethodVisitor mv, Map<Integer, Integer> varDoubleSlots,
                                  int ctxSlot, String className) {
        for (var entry : varDoubleSlots.entrySet()) {
            int keyIdx = entry.getKey();
            int varSlot = entry.getValue();
            // ctx.getLocalStorage().getVarVariable(KEYS[keyIdx]).setValue(new NumberValue(doubleVal))
            mv.visitVarInsn(ALOAD, ctxSlot);
            mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getLocalStorage", "()L" + LOCAL_STORAGE + ";", false);
            mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
            emitIntConst(mv, keyIdx);
            mv.visitInsn(AALOAD);
            mv.visitMethodInsn(INVOKEVIRTUAL, LOCAL_STORAGE, "getVarVariable", "(" + VKEY_DESC + ")" + VREF_DESC, false);
            // new NumberValue(doubleVal)
            mv.visitVarInsn(DLOAD, varSlot);
            emitNewNumberValue(mv);
            mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "setValue", "(" + IVALUE_DESC + ")" + IVALUE_DESC, false);
            mv.visitInsn(POP); // setValue 返回 IValue，丢弃
        }
    }

    private void emitLongVarWriteBack(MethodVisitor mv, Map<Integer, Integer> varLongSlots,
                                      int ctxSlot, String className) {
        for (var entry : varLongSlots.entrySet()) {
            int keyIdx = entry.getKey();
            int varSlot = entry.getValue();
            mv.visitVarInsn(ALOAD, ctxSlot);
            mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getLocalStorage", "()L" + LOCAL_STORAGE + ";", false);
            mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
            emitIntConst(mv, keyIdx);
            mv.visitInsn(AALOAD);
            mv.visitMethodInsn(INVOKEVIRTUAL, LOCAL_STORAGE, "getVarVariable", "(" + VKEY_DESC + ")" + VREF_DESC, false);
            // long → double → new NumberValue(double)
            mv.visitVarInsn(LLOAD, varSlot);
            mv.visitInsn(L2D);
            emitNewNumberValue(mv);
            mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "setValue", "(" + IVALUE_DESC + ")" + IVALUE_DESC, false);
            mv.visitInsn(POP);
        }
    }


    private int emitLongCmpAndControl(MethodVisitor mv, IRInstruction[] code, int pc,
                                      IRInstruction inst, int[] fastRegToLocal, Label[] labels) {
        switch (inst.opcode) {
            case LE, LT, GT, GE -> {
                IRInstruction next = (pc + 1 < code.length) ? code[pc + 1] : null;
                if (next != null && (next.opcode == IROpCode.JUMP_IF_TRUE || next.opcode == IROpCode.JUMP_IF_FALSE) && next.dst == inst.dst) {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(LCMP);
                    boolean jumpIfTrue = next.opcode == IROpCode.JUMP_IF_TRUE;
                    int fusedOp = switch (inst.opcode) {
                        case LE -> jumpIfTrue ? IFLE : IFGT;
                        case LT -> jumpIfTrue ? IFLT : IFGE;
                        case GT -> jumpIfTrue ? IFGT : IFLE;
                        case GE -> jumpIfTrue ? IFGE : IFLT;
                        default -> IFEQ;
                    };
                    mv.visitJumpInsn(fusedOp, labels[next.a]);
                    pc++;
                } else {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(LCMP);
                    int ifZeroOp = switch (inst.opcode) {
                        case LE -> IFLE;
                        case LT -> IFLT;
                        case GE -> IFGE;
                        case GT -> IFGT;
                        default -> IFEQ;
                    };
                    Label trueL = new Label();
                    Label endL = new Label();
                    mv.visitJumpInsn(ifZeroOp, trueL);
                    mv.visitInsn(LCONST_0);
                    mv.visitJumpInsn(GOTO, endL);
                    mv.visitLabel(trueL);
                    mv.visitInsn(LCONST_1);
                    mv.visitLabel(endL);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
            }
            case EQ -> {
                IRInstruction next = (pc + 1 < code.length) ? code[pc + 1] : null;
                if (next != null && (next.opcode == IROpCode.JUMP_IF_TRUE || next.opcode == IROpCode.JUMP_IF_FALSE) && next.dst == inst.dst) {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(LCMP);
                    int fusedOp = (next.opcode == IROpCode.JUMP_IF_TRUE) ? IFEQ : IFNE;
                    mv.visitJumpInsn(fusedOp, labels[next.a]);
                    pc++;
                } else {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(LCMP);
                    Label trueL = new Label();
                    Label endL = new Label();
                    mv.visitJumpInsn(IFEQ, trueL);
                    mv.visitInsn(LCONST_0);
                    mv.visitJumpInsn(GOTO, endL);
                    mv.visitLabel(trueL);
                    mv.visitInsn(LCONST_1);
                    mv.visitLabel(endL);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
            }
            case NE -> {
                IRInstruction next = (pc + 1 < code.length) ? code[pc + 1] : null;
                if (next != null && (next.opcode == IROpCode.JUMP_IF_TRUE || next.opcode == IROpCode.JUMP_IF_FALSE) && next.dst == inst.dst) {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(LCMP);
                    int fusedOp = (next.opcode == IROpCode.JUMP_IF_TRUE) ? IFNE : IFEQ;
                    mv.visitJumpInsn(fusedOp, labels[next.a]);
                    pc++;
                } else {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(LCMP);
                    Label trueL = new Label();
                    Label endL = new Label();
                    mv.visitJumpInsn(IFNE, trueL);
                    mv.visitInsn(LCONST_0);
                    mv.visitJumpInsn(GOTO, endL);
                    mv.visitLabel(trueL);
                    mv.visitInsn(LCONST_1);
                    mv.visitLabel(endL);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
            }
            case NOT -> {
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LCMP);
                Label trueL = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFEQ, trueL);
                mv.visitInsn(LCONST_0);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(trueL);
                mv.visitInsn(LCONST_1);
                mv.visitLabel(endL);
                mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
            }
            case AND -> {
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LCMP);
                Label useRight = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFNE, useRight);
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(useRight);
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                mv.visitLabel(endL);
                mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
            }
            case OR -> {
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LCMP);
                Label useRight = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFEQ, useRight);
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(useRight);
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                mv.visitLabel(endL);
                mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
            }
            case JUMP -> mv.visitJumpInsn(GOTO, labels[inst.a]);
            case JUMP_IF_TRUE -> {
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.dst]);
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LCMP);
                mv.visitJumpInsn(IFNE, labels[inst.a]);
            }
            case JUMP_IF_FALSE -> {
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.dst]);
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LCMP);
                mv.visitJumpInsn(IFEQ, labels[inst.a]);
            }
            case JUMP_IF_NONE -> {
                // 在 long 路径中，NONE 映射为 0L — 不跳转
            }
            default -> {}
        }
        return pc;
    }



    private void emitFastLongVarBytecode(MethodVisitor mv, IRInstruction[] code, IValue<?>[] constants,
                                         VariableKey[] keys, int argCount, int regCount,
                                         boolean[] usedRegs, String className, String fastVarDesc,
                                         Map<String, IRProgram> varFunctionMap) {
        // 局部变量布局: [ctx(1 slot), reg0(2), reg1(2), ..., var0(2), var1(2), ...]
        int ctxSlot = 0;
        int[] fastRegToLocal = new int[regCount];
        int nextLocal = 1; // ctx 之后
        for (int i = 0; i < regCount; i++) {
            if (usedRegs[i]) {
                fastRegToLocal[i] = nextLocal;
                nextLocal += 2; // long 占 2 slots
            } else {
                fastRegToLocal[i] = -1;
            }
        }

        // 收集所有 var 变量的 key 索引
        Set<Integer> varKeyIndices = new LinkedHashSet<>();
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case LOAD_VAR, STORE_VAR, VAR_INC, VAR_ADD_CONST, VAR_ADD_REG -> varKeyIndices.add(inst.a);
                default -> {}
            }
        }

        // 为每个 var 变量分配 long 局部变量 slot
        Map<Integer, Integer> varLongSlots = new HashMap<>();
        for (int keyIdx : varKeyIndices) {
            varLongSlots.put(keyIdx, nextLocal);
            nextLocal += 2; // long 占 2 slots
        }

        // 标签
        Label[] labels = new Label[code.length + 1];
        for (int i = 0; i <= code.length; i++) labels[i] = new Label();

        // 初始化寄存器为 0L
        for (int i = 0; i < regCount; i++) {
            if (usedRegs[i] && fastRegToLocal[i] >= 0) {
                mv.visitInsn(LCONST_0);
                mv.visitVarInsn(LSTORE, fastRegToLocal[i]);
            }
        }

        // 从 Context 读取 var 变量初始值到 long 局部变量
        // 读取 NumberValue.value (double) 然后 D2L 转为 long
        for (int keyIdx : varKeyIndices) {
            int varSlot = varLongSlots.get(keyIdx);
            mv.visitVarInsn(ALOAD, ctxSlot);
            mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getLocalStorage", "()L" + LOCAL_STORAGE + ";", false);
            mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
            emitIntConst(mv, keyIdx);
            mv.visitInsn(AALOAD);
            mv.visitMethodInsn(INVOKEVIRTUAL, LOCAL_STORAGE, "getVarVariable", "(" + VKEY_DESC + ")" + VREF_DESC, false);
            mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "getValue", "()" + IVALUE_DESC, false);
            Label isNum = new Label(), done = new Label();
            mv.visitInsn(DUP);
            mv.visitTypeInsn(INSTANCEOF, NUMVAL);
            mv.visitJumpInsn(IFNE, isNum);
            mv.visitInsn(POP);
            mv.visitInsn(LCONST_0);
            mv.visitJumpInsn(GOTO, done);
            mv.visitLabel(isNum);
            mv.visitTypeInsn(CHECKCAST, NUMVAL);
            mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
            mv.visitInsn(D2L); // double → long
            mv.visitLabel(done);
            mv.visitVarInsn(LSTORE, varSlot);
        }

        // 生成字节码
        for (int pc = 0; pc < code.length; pc++) {
            mv.visitLabel(labels[pc]);
            IRInstruction inst = code[pc];

            switch (inst.opcode) {
                case LOAD_CONST -> {
                    IValue<?> c = constants[inst.a];
                    double d = (c instanceof NumberValue nv) ? nv.value : c.numberValue();
                    emitLongConst(mv, (long) d);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
                case LOAD_NONE -> {
                    mv.visitInsn(LCONST_0);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
                case LOAD_TRUE -> {
                    mv.visitInsn(LCONST_1);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
                case LOAD_FALSE -> {
                    mv.visitInsn(LCONST_0);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
                case LOAD_ARG -> {
                    // 主程序通常没有参数，默认 0L
                    mv.visitInsn(LCONST_0);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }

                case LOAD_VAR -> {
                    Integer varSlot = varLongSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(LLOAD, varSlot);
                        mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                    } else {
                        mv.visitInsn(LCONST_0);
                        mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                    }
                }
                case STORE_VAR -> {
                    Integer varSlot = varLongSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(LLOAD, fastRegToLocal[inst.dst]);
                        mv.visitVarInsn(LSTORE, varSlot);
                    }
                }
                case VAR_INC -> {
                    // var[a] += 1L
                    Integer varSlot = varLongSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(LLOAD, varSlot);
                        mv.visitInsn(LCONST_1);
                        mv.visitInsn(LADD);
                        mv.visitVarInsn(LSTORE, varSlot);
                    }
                }
                case VAR_ADD_CONST -> {
                    // var[a] += (long) constants[b]
                    Integer varSlot = varLongSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(LLOAD, varSlot);
                        IValue<?> c = constants[inst.b];
                        double d = (c instanceof NumberValue nv) ? nv.value : c.numberValue();
                        emitLongConst(mv, (long) d);
                        mv.visitInsn(LADD);
                        mv.visitVarInsn(LSTORE, varSlot);
                    }
                }
                case VAR_ADD_REG -> {
                    // var[a] += reg[b]
                    Integer varSlot = varLongSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(LLOAD, varSlot);
                        mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                        mv.visitInsn(LADD);
                        mv.visitVarInsn(LSTORE, varSlot);
                    }
                }

                case ADD, ADD_NUM -> {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(LADD);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
                case SUB, SUB_NUM -> {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(LSUB);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
                case MUL, MUL_NUM -> {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(LMUL);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
                case MOD, MOD_NUM -> {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                    mv.visitInsn(LREM);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
                case NEG -> {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitInsn(LNEG);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
                case INC -> {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitInsn(LCONST_1);
                    mv.visitInsn(LADD);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }
                case DEC -> {
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitInsn(LCONST_1);
                    mv.visitInsn(LSUB);
                    mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                }

                case LE, LT, GT, GE, EQ, NE, NOT, AND, OR,
                     JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE, JUMP_IF_NONE -> {
                    pc = emitLongCmpAndControl(mv, code, pc, inst, fastRegToLocal, labels);
                }

                case RETURN -> {
                    // 写回 var 变量到 Context
                    emitLongVarWriteBack(mv, varLongSlots, ctxSlot, className);
                    if (inst.dst >= 0 && fastRegToLocal[inst.dst] >= 0) {
                        mv.visitVarInsn(LLOAD, fastRegToLocal[inst.dst]);
                    } else {
                        mv.visitInsn(LCONST_0);
                    }
                    mv.visitInsn(L2D);
                    mv.visitInsn(DRETURN);
                }

                case CALL_STATIC -> emitFastLongStaticCall(mv, fastRegToLocal, inst, varFunctionMap);

                // NEW_FUNCTION: 在 fastLongVars 路径中跳过
                case NEW_FUNCTION -> {}

                case MOVE -> {
                    if (fastRegToLocal[inst.a] != fastRegToLocal[inst.dst]) {
                        mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                        mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                    }
                }
                case PUSH_SCOPE, POP_SCOPE, NOP -> {}
            }
        }

        // 尾部安全返回（写回 var 变量）
        mv.visitLabel(labels[code.length]);
        emitLongVarWriteBack(mv, varLongSlots, ctxSlot, className);
        mv.visitInsn(LCONST_0);
        mv.visitInsn(L2D);
        mv.visitInsn(DRETURN);
    }

    private void emitFastLongStaticCall(MethodVisitor mv, int[] fastRegToLocal, IRInstruction inst,
                                        Map<String, IRProgram> varFunctionMap) {
        String fn = inst.name;
        int argBase = inst.a;
        int argCnt = inst.b;

        // math.* 函数需要 double 参数，所以 long → L2D → 调用 → D2L → long
        if (argCnt == 1) {
            String mathMethod = switch (fn) {
                case "math.sin" -> "sin";
                case "math.cos" -> "cos";
                case "math.tan" -> "tan";
                case "math.abs" -> "abs";
                case "math.floor" -> "floor";
                case "math.ceil" -> "ceil";
                case "math.sqrt" -> "sqrt";
                case "math.log" -> "log";
                default -> null;
            };
            if (mathMethod != null) {
                mv.visitVarInsn(LLOAD, fastRegToLocal[argBase]);
                mv.visitInsn(L2D);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", mathMethod, "(D)D", false);
                mv.visitInsn(D2L);
                mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                return;
            }
            if ("math.round".equals(fn)) {
                mv.visitVarInsn(LLOAD, fastRegToLocal[argBase]);
                mv.visitInsn(L2D);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "round", "(D)J", false);
                mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                return;
            }
        }
        if (argCnt == 2) {
            String mathMethod = switch (fn) {
                case "math.pow" -> "pow";
                case "math.min" -> "min";
                case "math.max" -> "max";
                default -> null;
            };
            if (mathMethod != null) {
                mv.visitVarInsn(LLOAD, fastRegToLocal[argBase]);
                mv.visitInsn(L2D);
                mv.visitVarInsn(LLOAD, fastRegToLocal[argBase + 1]);
                mv.visitInsn(L2D);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", mathMethod, "(DD)D", false);
                mv.visitInsn(D2L);
                mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                return;
            }
        }
        if ("math.random".equals(fn)) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "random", "()D", false);
            mv.visitInsn(D2L);
            mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
            return;
        }
        if ("math.PI".equals(fn)) {
            mv.visitLdcInsn((long) Math.PI);
            mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
            return;
        }
        if ("math.E".equals(fn)) {
            mv.visitLdcInsn((long) Math.E);
            mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
            return;
        }
        // 尝试内联 var 函数（简单运算 lambda）
        IRProgram varFunc = varFunctionMap.get(fn);
        if (varFunc != null && argCnt == 1) {
            double[] inlineResult = detectSimpleUnaryLambda(varFunc);
            if (inlineResult != null) {
                int op = (int) inlineResult[0];
                long constant = (long) inlineResult[1];
                boolean argFirst = inlineResult[2] != 0;
                mv.visitVarInsn(LLOAD, fastRegToLocal[argBase]);
                if (argFirst) {
                    emitLongConst(mv, constant);
                } else {
                    emitLongConst(mv, constant);
                    // long swap: 使用临时变量不可行，用 DUP2_X2 + POP2
                    mv.visitInsn(DUP2_X2);
                    mv.visitInsn(POP2);
                }
                switch (op) {
                    case 0 -> mv.visitInsn(LADD);
                    case 1 -> mv.visitInsn(LSUB);
                    case 2 -> mv.visitInsn(LMUL);
                    default -> mv.visitInsn(LADD);
                }
                mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                return;
            }
        }
        mv.visitInsn(LCONST_0);
        mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
    }

    private void emitFastStaticCall(MethodVisitor mv, int[] fastRegToLocal, IRInstruction inst,
                                    Map<String, IRProgram> varFunctionMap) {
        String fn = inst.name;
        int argBase = inst.a;
        int argCnt = inst.b;

        if (argCnt == 1) {
            String mathMethod = switch (fn) {
                case "math.sin" -> "sin";
                case "math.cos" -> "cos";
                case "math.tan" -> "tan";
                case "math.abs" -> "abs";
                case "math.floor" -> "floor";
                case "math.ceil" -> "ceil";
                case "math.sqrt" -> "sqrt";
                case "math.log" -> "log";
                default -> null;
            };
            if (mathMethod != null) {
                mv.visitVarInsn(DLOAD, fastRegToLocal[argBase]);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", mathMethod, "(D)D", false);
                mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                return;
            }
            if ("math.round".equals(fn)) {
                mv.visitVarInsn(DLOAD, fastRegToLocal[argBase]);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "round", "(D)J", false);
                mv.visitInsn(L2D);
                mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                return;
            }
        }
        if (argCnt == 2) {
            String mathMethod = switch (fn) {
                case "math.pow" -> "pow";
                case "math.min" -> "min";
                case "math.max" -> "max";
                default -> null;
            };
            if (mathMethod != null) {
                mv.visitVarInsn(DLOAD, fastRegToLocal[argBase]);
                mv.visitVarInsn(DLOAD, fastRegToLocal[argBase + 1]);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", mathMethod, "(DD)D", false);
                mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                return;
            }
        }
        if ("math.random".equals(fn)) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "random", "()D", false);
            mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
            return;
        }
        if ("math.PI".equals(fn)) {
            mv.visitLdcInsn(Math.PI);
            mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
            return;
        }
        if ("math.E".equals(fn)) {
            mv.visitLdcInsn(Math.E);
            mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
            return;
        }
        // 尝试内联 var 函数（简单运算 lambda）
        IRProgram varFunc = varFunctionMap.get(fn);
        if (varFunc != null && argCnt == 1) {
            // 检查是否是 args[0] op const 模式
            double[] inlineResult = detectSimpleUnaryLambda(varFunc);
            if (inlineResult != null) {
                // inlineResult = [opcode, constant, argFirst]
                int op = (int) inlineResult[0];
                double constant = inlineResult[1];
                boolean argFirst = inlineResult[2] != 0;
                mv.visitVarInsn(DLOAD, fastRegToLocal[argBase]);
                if (argFirst) {
                    emitDoubleConst(mv, constant);
                } else {
                    // const op arg → 需要交换
                    emitDoubleConst(mv, constant);
                    mv.visitInsn(DUP2_X2);
                    mv.visitInsn(POP2);
                }
                switch (op) {
                    case 0 -> mv.visitInsn(DADD);
                    case 1 -> mv.visitInsn(DSUB);
                    case 2 -> mv.visitInsn(DMUL);
                    case 3 -> mv.visitInsn(DDIV);
                    default -> mv.visitInsn(DADD);
                }
                if (!argFirst) {
                    // 如果是 const op arg，上面的交换已经处理了
                }
                mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                return;
            }
        }
        mv.visitInsn(DCONST_0);
        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
    }


    private void emitDoubleConst(MethodVisitor mv, double d) {
        if (d == 0.0) {
            mv.visitInsn(DCONST_0);
        } else if (d == 1.0) {
            mv.visitInsn(DCONST_1);
        } else {
            mv.visitLdcInsn(d);
        }
    }


    private void emitLongConst(MethodVisitor mv, long value) {
        if (value == 0) mv.visitInsn(LCONST_0);
        else if (value == 1) mv.visitInsn(LCONST_1);
        else mv.visitLdcInsn(value);
    }

    private double[] detectSimpleUnaryLambda(IRProgram sub) {
        IRInstruction[] c = sub.getInstructions();
        if (c == null) return null;
        int loadArgCount = 0, loadConstCount = 0;
        int argReg = -1, constReg = -1;
        double constValue = 0;
        IROpCode arithOp = null;
        int arithA = -1;
        for (IRInstruction inst : c) {
            switch (inst.opcode) {
                case PUSH_SCOPE, POP_SCOPE, NOP, MOVE -> {}
                case LOAD_ARG -> { loadArgCount++; argReg = inst.dst; }
                case LOAD_CONST -> {
                    loadConstCount++;
                    constReg = inst.dst;
                    if (sub.getConstants() != null && inst.a < sub.getConstants().length
                            && sub.getConstants()[inst.a] instanceof NumberValue nv) {
                        constValue = nv.value;
                    }
                }
                case ADD, SUB, MUL, DIV -> {
                    if (arithOp != null) return null;
                    arithOp = inst.opcode;
                    arithA = inst.a;
                }
                case RETURN -> {}
                default -> { return null; }
            }
        }
        if (arithOp == null || loadArgCount != 1 || loadConstCount != 1) return null;
        int opIdx = switch (arithOp) {
            case ADD -> 0; case SUB -> 1; case MUL -> 2; case DIV -> 3; default -> -1;
        };
        if (opIdx < 0) return null;
        boolean argFirst = (arithA == argReg);
        return new double[]{ opIdx, constValue, argFirst ? 1 : 0 };
    }


    private void emitBytecode(MethodVisitor mv, IRInstruction[] code, IValue<?>[] constants,
                              int argCount, int ctxLocal, int[] regToLocal, int regCount,
                              boolean[] usedRegs, Set<Integer> selfRecursivePCs,
                              String className, String callDesc, boolean numericOnly,
                              IRProgram program) {

        // 为每条 IR 指令创建 Label（用于跳转目标）
        Label[] labels = new Label[code.length + 1];
        for (int i = 0; i <= code.length; i++) labels[i] = new Label();

        // 初始化使用的寄存器为 NoneValue.NONE
        for (int i = 0; i < regCount; i++) {
            if (usedRegs[i] && regToLocal[i] >= 0) {
                mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                mv.visitVarInsn(ASTORE, regToLocal[i]);
            }
        }

        // 收集所有 LOAD_VAR/STORE_VAR/VAR_INC/VAR_ADD_CONST/VAR_ADD_REG 用到的 key 索引
        Set<Integer> varKeyIndices = new HashSet<>();
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case LOAD_VAR, STORE_VAR, VAR_INC, VAR_ADD_CONST, VAR_ADD_REG -> varKeyIndices.add(inst.a);
                default -> {}
            }
        }
        // 为每个 key 分配局部变量 slot 并预解析
        int nextVarRefSlot = ctxLocal + 1; // 从 ctx 之后开始
        for (int i = 0; i < regCount; i++) {
            if (regToLocal[i] >= nextVarRefSlot) nextVarRefSlot = regToLocal[i] + 1;
        }
        Map<Integer, Integer> varRefSlots = new HashMap<>();
        for (int keyIdx : varKeyIndices) {
            int slot = nextVarRefSlot++;
            varRefSlots.put(keyIdx, slot);
            // ctx.getLocalStorage().getVarVariable(KEYS[keyIdx])
            mv.visitVarInsn(ALOAD, ctxLocal);
            mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getLocalStorage", "()L" + LOCAL_STORAGE + ";", false);
            mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
            emitIntConst(mv, keyIdx);
            mv.visitInsn(AALOAD);
            mv.visitMethodInsn(INVOKEVIRTUAL, LOCAL_STORAGE, "getVarVariable", "(" + VKEY_DESC + ")" + VREF_DESC, false);
            mv.visitVarInsn(ASTORE, slot);
        }

        for (int pc = 0; pc < code.length; pc++) {
            mv.visitLabel(labels[pc]);
            IRInstruction inst = code[pc];

            switch (inst.opcode) {
                case LOAD_CONST -> {
                    IValue<?> c = constants[inst.a];
                    emitLoadConstant(mv, c, className, inst.a);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case LOAD_NONE -> {
                    mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case LOAD_TRUE -> {
                    mv.visitFieldInsn(GETSTATIC, BOOLVAL, "TRUE", BOOLVAL_DESC);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case LOAD_FALSE -> {
                    mv.visitFieldInsn(GETSTATIC, BOOLVAL, "FALSE", BOOLVAL_DESC);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case LOAD_ARG -> {
                    // 参数直接从方法参数加载
                    if (inst.a < argCount) {
                        mv.visitVarInsn(ALOAD, inst.a);
                    } else {
                        // 超出范围的参数返回 NONE
                        mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                    }
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case LOAD_VAR -> {
                    Integer vrSlot = varRefSlots.get(inst.a);
                    if (vrSlot != null) {
                        mv.visitVarInsn(ALOAD, vrSlot);
                        mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "getValue", "()" + IVALUE_DESC, false);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    }
                }
                case STORE_VAR -> {
                    Integer vrSlot = varRefSlots.get(inst.a);
                    if (vrSlot != null) {
                        mv.visitVarInsn(ALOAD, vrSlot);
                        mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                        mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "setValue", "(" + IVALUE_DESC + ")" + IVALUE_DESC, false);
                        mv.visitInsn(POP);
                    }
                }
                case LOAD_SCOPE -> {
                    // ctx.getScopeStack().get(KEYS[inst.a]).getValue()
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getScopeStack",
                            "()L" + SCOPE_STACK + ";", false);
                    mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
                    emitIntConst(mv, inst.a);
                    mv.visitInsn(AALOAD);
                    mv.visitMethodInsn(INVOKEVIRTUAL, SCOPE_STACK, "get",
                            "(" + VKEY_DESC + ")" + VREF_DESC, false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "getValue", "()" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case STORE_SCOPE -> {
                    // ctx.getScopeStack().get(KEYS[inst.a]).setValue(reg[inst.dst])
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getScopeStack",
                            "()L" + SCOPE_STACK + ";", false);
                    mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
                    emitIntConst(mv, inst.a);
                    mv.visitInsn(AALOAD);
                    mv.visitMethodInsn(INVOKEVIRTUAL, SCOPE_STACK, "get",
                            "(" + VKEY_DESC + ")" + VREF_DESC, false);
                    mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                    mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "setValue",
                            "(" + IVALUE_DESC + ")" + IVALUE_DESC, false);
                    mv.visitInsn(POP);
                }
                case LOAD_SELF -> {
                    // ctx.getSelf()
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getSelf", "()" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case LOAD_ARGS -> {
                    // ListValue from ctx.getArgs()
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getArgs", "()[" + IVALUE_DESC, false);
                    mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "asList",
                            "([Ljava/lang/Object;)Ljava/util/List;", false);
                    mv.visitTypeInsn(NEW, "java/util/ArrayList");
                    mv.visitInsn(DUP_X1);
                    mv.visitInsn(SWAP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>",
                            "(Ljava/util/Collection;)V", false);
                    mv.visitTypeInsn(NEW, LIST_VALUE);
                    mv.visitInsn(DUP_X1);
                    mv.visitInsn(SWAP);
                    mv.visitMethodInsn(INVOKESPECIAL, LIST_VALUE, "<init>", "(Ljava/util/List;)V", false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case GET_INDEX -> {
                    // 内联快速路径：SmallMapValue.get(idx)
                    mv.visitVarInsn(ALOAD, regToLocal[inst.a]); // obj
                    mv.visitInsn(DUP);
                    mv.visitTypeInsn(INSTANCEOF, "priv/seventeen/artist/aria/value/SmallMapValue");
                    Label notSmallMap = new Label();
                    mv.visitJumpInsn(IFEQ, notSmallMap);
                    // 快速路径：直接调用 SmallMapValue.get(IValue)
                    mv.visitTypeInsn(CHECKCAST, "priv/seventeen/artist/aria/value/SmallMapValue");
                    mv.visitVarInsn(ALOAD, regToLocal[inst.b]); // idx
                    mv.visitMethodInsn(INVOKEVIRTUAL, "priv/seventeen/artist/aria/value/SmallMapValue",
                            "get", "(" + IVALUE_DESC + ")" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    Label endGetIndex = new Label();
                    mv.visitJumpInsn(GOTO, endGetIndex);
                    mv.visitLabel(notSmallMap);
                    // 慢速路径：rtGetIndex
                    mv.visitVarInsn(ALOAD, regToLocal[inst.b]); // idx
                    mv.visitMethodInsn(INVOKESTATIC,
                            "priv/seventeen/artist/aria/jit/JITCompiler", "rtGetIndex",
                            "(" + IVALUE_DESC + IVALUE_DESC + ")" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    mv.visitLabel(endGetIndex);
                }
                case GET_PROP -> {
                    // dst = obj.propName  via rtGetProp(obj, propName, ctx)
                    mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                    mv.visitLdcInsn(inst.name == null ? "" : inst.name);
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKESTATIC,
                            "priv/seventeen/artist/aria/jit/JITCompiler", "rtGetProp",
                            "(" + IVALUE_DESC + "Ljava/lang/String;" + CONTEXT_DESC + ")" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case LOAD_GLOBAL -> {
                    // dst = ctx.getGlobalStorage().getGlobalVariable(KEYS[a]).getValue()
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getGlobalStorage",
                            "()Lpriv/seventeen/artist/aria/context/GlobalStorage;", false);
                    mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
                    emitIntConst(mv, inst.a);
                    mv.visitInsn(AALOAD);
                    mv.visitMethodInsn(INVOKEVIRTUAL,
                            "priv/seventeen/artist/aria/context/GlobalStorage", "getGlobalVariable",
                            "(" + VKEY_DESC + ")" + VREF_DESC, false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "getValue", "()" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case LOAD_SERVER -> {
                    // dst = ctx.getServerVariable(KEYS[a]).ariaValue()
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
                    emitIntConst(mv, inst.a);
                    mv.visitInsn(AALOAD);
                    mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getServerVariable",
                            "(" + VKEY_DESC + ")Lpriv/seventeen/artist/aria/value/Variable$Normal;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL,
                            "priv/seventeen/artist/aria/value/Variable$Normal", "ariaValue",
                            "()" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case LOAD_CLIENT -> {
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
                    emitIntConst(mv, inst.a);
                    mv.visitInsn(AALOAD);
                    mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getClientVariable",
                            "(" + VKEY_DESC + ")Lpriv/seventeen/artist/aria/value/Variable$Normal;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL,
                            "priv/seventeen/artist/aria/value/Variable$Normal", "ariaValue",
                            "()" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                // FOR_RANGE_INIT/NEXT/BREAK: Compiler 当前不 emit，留空 case 防御 IR 演进时崩溃
                case FOR_RANGE_INIT, FOR_RANGE_NEXT, BREAK -> {}
                case VAR_INC -> {
                    Integer vrSlot = varRefSlots.get(inst.a);
                    if (vrSlot != null) {
                        mv.visitVarInsn(ALOAD, vrSlot);
                        mv.visitInsn(DUP);
                        mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "getValue", "()" + IVALUE_DESC, false);
                        mv.visitTypeInsn(CHECKCAST, NUMVAL);
                        mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
                        mv.visitInsn(DCONST_1);
                        mv.visitInsn(DADD);
                        emitNewNumberValue(mv);
                        mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "setValue", "(" + IVALUE_DESC + ")" + IVALUE_DESC, false);
                        mv.visitInsn(POP);
                    }
                }
                case VAR_ADD_CONST -> {
                    Integer vrSlot = varRefSlots.get(inst.a);
                    if (vrSlot != null) {
                        mv.visitVarInsn(ALOAD, vrSlot);
                        mv.visitInsn(DUP);
                        mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "getValue", "()" + IVALUE_DESC, false);
                        mv.visitTypeInsn(CHECKCAST, NUMVAL);
                        mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
                        mv.visitFieldInsn(GETSTATIC, className, "CONSTANTS", "[" + IVALUE_DESC);
                        emitIntConst(mv, inst.b);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, NUMVAL);
                        mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
                        mv.visitInsn(DADD);
                        emitNewNumberValue(mv);
                        mv.visitMethodInsn(INVOKEVIRTUAL, VREF, "setValue", "(" + IVALUE_DESC + ")" + IVALUE_DESC, false);
                        mv.visitInsn(POP);
                    }
                }
                case VAR_ADD_REG -> {
                    Integer vrSlot = varRefSlots.get(inst.a);
                    if (vrSlot != null) {
                        // 通用路径：支持 NumberValue 和字符串
                        mv.visitVarInsn(ALOAD, vrSlot); // VariableReference
                        mv.visitVarInsn(ALOAD, regToLocal[inst.b]); // val
                        mv.visitMethodInsn(INVOKESTATIC,
                                "priv/seventeen/artist/aria/jit/JITCompiler",
                                "rtVarAddReg", "(" + VREF_DESC + IVALUE_DESC + ")V", false);
                    }
                }
                case CONCAT -> {
                    // StringBuilder 拼接
                    int baseReg = inst.a;
                    int count = inst.b;
                    mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
                    mv.visitInsn(DUP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                    for (int i = 0; i < count; i++) {
                        mv.visitVarInsn(ALOAD, regToLocal[baseReg + i]);
                        mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "stringValue",
                                "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                    }
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                            "()Ljava/lang/String;", false);
                    // new StringValue(str)
                    mv.visitTypeInsn(NEW, STRVAL);
                    mv.visitInsn(DUP_X1);
                    mv.visitInsn(SWAP);
                    mv.visitMethodInsn(INVOKESPECIAL, STRVAL, "<init>", "(Ljava/lang/String;)V", false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case CALL_CONSTRUCTOR -> {
                    // CallableManager.INSTANCE.getConstructor(name).invoke(new InvocationData(ctx, null, args))
                    mv.visitFieldInsn(GETSTATIC, CALLABLE_MGR, "INSTANCE", "L" + CALLABLE_MGR + ";");
                    mv.visitLdcInsn(inst.name);
                    mv.visitMethodInsn(INVOKEVIRTUAL, CALLABLE_MGR, "getConstructor",
                            "(Ljava/lang/String;)Lpriv/seventeen/artist/aria/callable/IObjectConstructor;", false);
                    // 构建 InvocationData
                    mv.visitTypeInsn(NEW, INVOC_DATA);
                    mv.visitInsn(DUP);
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitInsn(ACONST_NULL);
                    // 创建参数数组
                    int ctorArgCnt = inst.b;
                    int ctorArgBase = inst.a;
                    emitIntConst(mv, ctorArgCnt);
                    mv.visitTypeInsn(ANEWARRAY, IVALUE);
                    for (int i = 0; i < ctorArgCnt; i++) {
                        mv.visitInsn(DUP);
                        emitIntConst(mv, i);
                        int r = ctorArgBase + i;
                        if (regToLocal[r] >= 0) {
                            mv.visitVarInsn(ALOAD, regToLocal[r]);
                        } else {
                            mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                        }
                        mv.visitInsn(AASTORE);
                    }
                    mv.visitMethodInsn(INVOKESPECIAL, INVOC_DATA, "<init>",
                            "(" + CONTEXT_DESC + "Ljava/lang/Object;[" + IVALUE_DESC + ")V", false);
                    mv.visitMethodInsn(INVOKEINTERFACE, ICALLABLE, "invoke",
                            "(L" + INVOC_DATA + ";)" + IVALUE_DESC, true);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case CALL_METHOD -> {
                    // ── 内联快速路径：list.add (单参数) ──
                    if ("add".equals(inst.name) && inst.b == 1) {
                        mv.visitVarInsn(ALOAD, regToLocal[inst.a]); // obj
                        mv.visitInsn(DUP);
                        mv.visitTypeInsn(INSTANCEOF, LIST_VALUE);
                        Label slowPathAdd = new Label();
                        mv.visitJumpInsn(IFEQ, slowPathAdd);
                        // 快速路径：栈上已有 obj，直接 CHECKCAST
                        mv.visitTypeInsn(CHECKCAST, LIST_VALUE);
                        mv.visitMethodInsn(INVOKEVIRTUAL, LIST_VALUE, "jvmValue", "()Ljava/util/List;", false);
                        mv.visitVarInsn(ALOAD, regToLocal[inst.c]); // 第一个参数
                        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
                        mv.visitInsn(POP); // 丢弃 boolean
                        mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                        Label endAdd = new Label();
                        mv.visitJumpInsn(GOTO, endAdd);
                        mv.visitLabel(slowPathAdd);
                        // 慢速路径：栈上有 DUP 的 obj，先弹出
                        mv.visitInsn(POP);
                        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                        mv.visitLdcInsn(inst.name);
                        emitIntConst(mv, inst.b);
                        mv.visitTypeInsn(ANEWARRAY, IVALUE);
                        mv.visitInsn(DUP);
                        emitIntConst(mv, 0);
                        if (inst.c < regToLocal.length && regToLocal[inst.c] >= 0) {
                            mv.visitVarInsn(ALOAD, regToLocal[inst.c]);
                        } else {
                            mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                        }
                        mv.visitInsn(AASTORE);
                        mv.visitVarInsn(ALOAD, ctxLocal);
                        mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                                "rtCallMethod", "(" + IVALUE_DESC + "Ljava/lang/String;[" + IVALUE_DESC + CONTEXT_DESC + ")" + IVALUE_DESC, false);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                        mv.visitLabel(endAdd);
                    }
                    // ── 内联快速路径：list.size (无参数) ──
                    else if ("size".equals(inst.name) && inst.b == 0) {
                        mv.visitVarInsn(ALOAD, regToLocal[inst.a]); // obj
                        mv.visitInsn(DUP);
                        mv.visitTypeInsn(INSTANCEOF, LIST_VALUE);
                        Label slowPathSize = new Label();
                        mv.visitJumpInsn(IFEQ, slowPathSize);
                        // 快速路径：栈上已有 obj
                        mv.visitTypeInsn(CHECKCAST, LIST_VALUE);
                        mv.visitMethodInsn(INVOKEVIRTUAL, LIST_VALUE, "jvmValue", "()Ljava/util/List;", false);
                        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
                        mv.visitInsn(I2D);
                        emitNewNumberValue(mv);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                        Label endSize = new Label();
                        mv.visitJumpInsn(GOTO, endSize);
                        mv.visitLabel(slowPathSize);
                        // 慢速路径：弹出 DUP 的 obj
                        mv.visitInsn(POP);
                        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                        mv.visitLdcInsn(inst.name);
                        emitIntConst(mv, 0);
                        mv.visitTypeInsn(ANEWARRAY, IVALUE);
                        mv.visitVarInsn(ALOAD, ctxLocal);
                        mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                                "rtCallMethod", "(" + IVALUE_DESC + "Ljava/lang/String;[" + IVALUE_DESC + CONTEXT_DESC + ")" + IVALUE_DESC, false);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                        mv.visitLabel(endSize);
                    }
                    // ── 通用路径 ──
                    else {
                        // rtCallMethod(obj, methodName, args[], ctx)
                        mv.visitVarInsn(ALOAD, regToLocal[inst.a]); // obj
                        mv.visitLdcInsn(inst.name); // methodName
                        // 构建参数数组
                        int cmArgCnt = inst.b;
                        int cmArgBase = inst.c;
                        emitIntConst(mv, cmArgCnt);
                        mv.visitTypeInsn(ANEWARRAY, IVALUE);
                        for (int i = 0; i < cmArgCnt; i++) {
                            mv.visitInsn(DUP);
                            emitIntConst(mv, i);
                            int r = cmArgBase + i;
                            if (r < regToLocal.length && regToLocal[r] >= 0) {
                                mv.visitVarInsn(ALOAD, regToLocal[r]);
                            } else {
                                mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                            }
                            mv.visitInsn(AASTORE);
                        }
                        mv.visitVarInsn(ALOAD, ctxLocal); // ctx
                        mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                                "rtCallMethod", "(" + IVALUE_DESC + "Ljava/lang/String;[" + IVALUE_DESC + CONTEXT_DESC + ")" + IVALUE_DESC, false);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    }
                }
                case NEW_LIST -> {
                    // rtNewList(elements[])
                    int nlCount = inst.b;
                    int nlBase = inst.a;
                    emitIntConst(mv, nlCount);
                    mv.visitTypeInsn(ANEWARRAY, IVALUE);
                    for (int i = 0; i < nlCount; i++) {
                        mv.visitInsn(DUP);
                        emitIntConst(mv, i);
                        int r = nlBase + i;
                        if (r < regToLocal.length && regToLocal[r] >= 0) {
                            mv.visitVarInsn(ALOAD, regToLocal[r]);
                        } else {
                            mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                        }
                        mv.visitInsn(AASTORE);
                    }
                    mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                            "rtNewList", "([" + IVALUE_DESC + ")" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case NEW_MAP -> {
                    int nmCount = inst.b;
                    int nmBase = inst.a;
                    if (nmCount <= 4) {
                        // 小 map 直接内联构造 SmallMapValue，省掉 kvPairs 中间数组
                        mv.visitTypeInsn(NEW, "priv/seventeen/artist/aria/value/SmallMapValue");
                        mv.visitInsn(DUP);
                        // new String[nmCount] — keys
                        emitIntConst(mv, nmCount);
                        mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
                        for (int i = 0; i < nmCount; i++) {
                            mv.visitInsn(DUP);
                            emitIntConst(mv, i);
                            int keyReg = nmBase + i * 2;
                            if (keyReg < regToLocal.length && regToLocal[keyReg] >= 0) {
                                mv.visitVarInsn(ALOAD, regToLocal[keyReg]);
                            } else {
                                mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                            }
                            mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "stringValue", "()Ljava/lang/String;", false);
                            mv.visitInsn(AASTORE);
                        }
                        // new IValue[nmCount] — values
                        emitIntConst(mv, nmCount);
                        mv.visitTypeInsn(ANEWARRAY, IVALUE);
                        for (int i = 0; i < nmCount; i++) {
                            mv.visitInsn(DUP);
                            emitIntConst(mv, i);
                            int valReg = nmBase + i * 2 + 1;
                            if (valReg < regToLocal.length && regToLocal[valReg] >= 0) {
                                mv.visitVarInsn(ALOAD, regToLocal[valReg]);
                            } else {
                                mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                            }
                            mv.visitInsn(AASTORE);
                        }
                        // SmallMapValue(String[], IValue[], int)
                        emitIntConst(mv, nmCount);
                        mv.visitMethodInsn(INVOKESPECIAL, "priv/seventeen/artist/aria/value/SmallMapValue",
                                "<init>", "([Ljava/lang/String;[" + IVALUE_DESC + "I)V", false);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    } else {
                        // 大 map 走 rtNewMap
                        emitIntConst(mv, nmCount * 2);
                        mv.visitTypeInsn(ANEWARRAY, IVALUE);
                        for (int i = 0; i < nmCount * 2; i++) {
                            mv.visitInsn(DUP);
                            emitIntConst(mv, i);
                            int r = nmBase + i;
                            if (r < regToLocal.length && regToLocal[r] >= 0) {
                                mv.visitVarInsn(ALOAD, regToLocal[r]);
                            } else {
                                mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                            }
                            mv.visitInsn(AASTORE);
                        }
                        emitIntConst(mv, nmCount);
                        mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                                "rtNewMap", "([" + IVALUE_DESC + "I)" + IVALUE_DESC, false);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    }
                }
                case SET_INDEX -> {
                    // rtSetIndex(obj, idxReg, value, isAppend)
                    // SET_INDEX: dst=objReg, a=idxReg (-1 means append), b=valueReg
                    mv.visitVarInsn(ALOAD, regToLocal[inst.dst]); // obj
                    if (inst.a == -1) {
                        mv.visitInsn(ACONST_NULL); // null index means append
                    } else {
                        mv.visitVarInsn(ALOAD, regToLocal[inst.a]); // index
                    }
                    mv.visitVarInsn(ALOAD, regToLocal[inst.b]); // value
                    mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                            "rtSetIndex", "(" + IVALUE_DESC + IVALUE_DESC + IVALUE_DESC + ")V", false);
                }
                case NEW_FUNCTION -> {
                    // new FunctionValue(new FunctionCallable(SUB_PROGRAMS[inst.a], ctx))
                    mv.visitTypeInsn(NEW, FUNCTION_VALUE);
                    mv.visitInsn(DUP);
                    mv.visitTypeInsn(NEW, FUNCTION_CALLABLE);
                    mv.visitInsn(DUP);
                    mv.visitFieldInsn(GETSTATIC, className, "SUB_PROGRAMS", "[" + IRPROGRAM_DESC);
                    emitIntConst(mv, inst.a);
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKESPECIAL, FUNCTION_CALLABLE, "<init>",
                            "(" + IRPROGRAM_DESC + CONTEXT_DESC + ")V", false);
                    mv.visitMethodInsn(INVOKESPECIAL, FUNCTION_VALUE, "<init>",
                            "(" + ICALLABLE_DESC + ")V", false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }

                case ADD -> {
                    if (numericOnly) {
                        emitGetNumber(mv, regToLocal, inst.a);
                        emitGetNumber(mv, regToLocal, inst.b);
                        mv.visitInsn(DADD);
                        emitNewNumberValue(mv);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    } else {
                        emitBinaryArith(mv, regToLocal, inst, DADD);
                    }
                }
                case SUB -> {
                    if (numericOnly) {
                        emitGetNumber(mv, regToLocal, inst.a);
                        emitGetNumber(mv, regToLocal, inst.b);
                        mv.visitInsn(DSUB);
                        emitNewNumberValue(mv);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    } else {
                        emitBinaryArith(mv, regToLocal, inst, DSUB);
                    }
                }
                case MUL -> {
                    if (numericOnly) {
                        emitGetNumber(mv, regToLocal, inst.a);
                        emitGetNumber(mv, regToLocal, inst.b);
                        mv.visitInsn(DMUL);
                        emitNewNumberValue(mv);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    } else {
                        emitBinaryArith(mv, regToLocal, inst, DMUL);
                    }
                }
                case DIV -> {
                    if (numericOnly) {
                        emitGetNumber(mv, regToLocal, inst.a);
                        emitGetNumber(mv, regToLocal, inst.b);
                        // 除零检查
                        mv.visitInsn(DUP2);
                        mv.visitInsn(DCONST_0);
                        mv.visitInsn(DCMPL);
                        Label nonZero = new Label();
                        Label end = new Label();
                        mv.visitJumpInsn(IFNE, nonZero);
                        mv.visitInsn(POP2);
                        mv.visitInsn(POP2);
                        mv.visitInsn(DCONST_0);
                        emitNewNumberValue(mv);
                        mv.visitJumpInsn(GOTO, end);
                        mv.visitLabel(nonZero);
                        mv.visitInsn(DDIV);
                        emitNewNumberValue(mv);
                        mv.visitLabel(end);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    } else {
                        emitBinaryDiv(mv, regToLocal, inst);
                    }
                }
                case MOD -> {
                    if (numericOnly) {
                        emitGetNumber(mv, regToLocal, inst.a);
                        emitGetNumber(mv, regToLocal, inst.b);
                        mv.visitInsn(DUP2);
                        mv.visitInsn(DCONST_0);
                        mv.visitInsn(DCMPL);
                        Label nonZero = new Label();
                        Label end = new Label();
                        mv.visitJumpInsn(IFNE, nonZero);
                        mv.visitInsn(POP2);
                        mv.visitInsn(POP2);
                        mv.visitInsn(DCONST_0);
                        emitNewNumberValue(mv);
                        mv.visitJumpInsn(GOTO, end);
                        mv.visitLabel(nonZero);
                        mv.visitInsn(DREM);
                        emitNewNumberValue(mv);
                        mv.visitLabel(end);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    } else {
                        emitBinaryMod(mv, regToLocal, inst);
                    }
                }
                case ADD_NUM, SUB_NUM, MUL_NUM, DIV_NUM, MOD_NUM ->
                        emitNumericBinary(mv, regToLocal, inst);
                case NEG -> {
                    emitGetNumber(mv, regToLocal, inst.a);
                    mv.visitInsn(DNEG);
                    emitNewNumberValue(mv);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case INC -> {
                    emitGetNumber(mv, regToLocal, inst.a);
                    mv.visitInsn(DCONST_1);
                    mv.visitInsn(DADD);
                    emitNewNumberValue(mv);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case DEC -> {
                    emitGetNumber(mv, regToLocal, inst.a);
                    mv.visitInsn(DCONST_1);
                    mv.visitInsn(DSUB);
                    emitNewNumberValue(mv);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }

                case LE -> emitComparison(mv, regToLocal, inst, IF_ICMPLE, numericOnly);
                case LT -> emitComparison(mv, regToLocal, inst, IF_ICMPLT, numericOnly);
                case GT -> emitComparison(mv, regToLocal, inst, IF_ICMPGT, numericOnly);
                case GE -> emitComparison(mv, regToLocal, inst, IF_ICMPGE, numericOnly);
                case EQ -> emitEquality(mv, regToLocal, inst, true);
                case NE -> emitEquality(mv, regToLocal, inst, false);
                case NOT -> {
                    mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                    mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "booleanValue", "()Z", false);
                    Label trueLabel = new Label();
                    Label endLabel = new Label();
                    mv.visitJumpInsn(IFNE, trueLabel);
                    mv.visitFieldInsn(GETSTATIC, BOOLVAL, "TRUE", BOOLVAL_DESC);
                    mv.visitJumpInsn(GOTO, endLabel);
                    mv.visitLabel(trueLabel);
                    mv.visitFieldInsn(GETSTATIC, BOOLVAL, "FALSE", BOOLVAL_DESC);
                    mv.visitLabel(endLabel);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case AND -> {
                    // 短路：左为 false 则结果为左，否则为右
                    mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                    mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "booleanValue", "()Z", false);
                    Label useRight = new Label();
                    Label endLabel = new Label();
                    mv.visitJumpInsn(IFNE, useRight);
                    mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                    mv.visitJumpInsn(GOTO, endLabel);
                    mv.visitLabel(useRight);
                    mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
                    mv.visitLabel(endLabel);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case OR -> {
                    // 短路：左为 true 则结果为左，否则为右
                    mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                    mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "booleanValue", "()Z", false);
                    Label useRight = new Label();
                    Label endLabel = new Label();
                    mv.visitJumpInsn(IFEQ, useRight);
                    mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                    mv.visitJumpInsn(GOTO, endLabel);
                    mv.visitLabel(useRight);
                    mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
                    mv.visitLabel(endLabel);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }

                case JUMP -> mv.visitJumpInsn(GOTO, labels[inst.a]);
                case JUMP_IF_TRUE -> {
                    mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                    mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "booleanValue", "()Z", false);
                    mv.visitJumpInsn(IFNE, labels[inst.a]);
                }
                case JUMP_IF_FALSE -> {
                    mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                    mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "booleanValue", "()Z", false);
                    mv.visitJumpInsn(IFEQ, labels[inst.a]);
                }
                case JUMP_IF_NONE -> {
                    mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                    mv.visitTypeInsn(INSTANCEOF, NONEVAL);
                    mv.visitJumpInsn(IFNE, labels[inst.a]);
                }

                case RETURN -> {
                    if (inst.dst >= 0 && regToLocal[inst.dst] >= 0) {
                        mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                    } else {
                        mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                    }
                    mv.visitInsn(ARETURN);
                }

                case CALL -> {
                    if (selfRecursivePCs.contains(pc)) {
                        // 自递归
                        int callArgCount = inst.b;
                        int callArgBase = inst.c;
                        for (int i = 0; i < callArgCount; i++) {
                            int r = callArgBase + i;
                            if (r < regToLocal.length && regToLocal[r] >= 0) {
                                mv.visitVarInsn(ALOAD, regToLocal[r]);
                            } else {
                                mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                            }
                        }
                        for (int i = callArgCount; i < argCount; i++) {
                            mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
                        }
                        mv.visitVarInsn(ALOAD, ctxLocal);
                        mv.visitMethodInsn(INVOKESTATIC, className, "call", callDesc, false);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    } else {
                        // 非递归 — 尝试内联简单 lambda
                        boolean inlined = false;
                        // 暂时禁用内联，直接走 generic
                        if (!inlined) {
                            // 追踪 callee 来自哪个 NEW_FUNCTION
                            for (int prev = pc - 1; prev >= 0; prev--) {
                                IRInstruction pi = code[prev];
                                if (pi.opcode == IROpCode.LOAD_VAR && pi.dst == inst.a) {
                                    // 找到 LOAD_VAR → 找对应的 STORE_VAR + NEW_FUNCTION
                                    for (int pp = 0; pp < code.length; pp++) {
                                        IRInstruction si = code[pp];
                                        if (si.opcode == IROpCode.NEW_FUNCTION) {
                                            // 检查下一条是否是 STORE_VAR 到同一个 key
                                            if (pp + 1 < code.length) {
                                                IRInstruction sv = code[pp + 1];
                                                if (sv.opcode == IROpCode.STORE_VAR && sv.a == pi.a) {
                                                    int subIdx = si.a;
                                                    if (subIdx < program.getSubPrograms().length) {
                                                        IRProgram sub = program.getSubPrograms()[subIdx];
                                                        if (isSimpleBinaryLambda(sub)) {
                                                            // 内联！直接执行 lambda 的算术运算
                                                            emitInlinedLambda(mv, regToLocal, inst, sub, numericOnly, ctxLocal);
                                                            inlined = true;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        if (!inlined) {
                            emitGenericCall(mv, regToLocal, inst, ctxLocal);
                        }
                    }
                }
                case CALL_STATIC -> emitStaticCall(mv, regToLocal, inst, ctxLocal, className);

                case MOVE -> {
                    if (regToLocal[inst.a] != regToLocal[inst.dst]) {
                        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    }
                }

                case PUSH_SCOPE -> {
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "pushScope", "()V", false);
                }
                case POP_SCOPE -> {
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "popScope", "()V", false);
                }
                case NOP -> {}
            }
        }

        // 尾部安全返回（如果没有显式 RETURN）
        mv.visitLabel(labels[code.length]);
        mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
        mv.visitInsn(ARETURN);
    }


    private void emitGetNumber(MethodVisitor mv, int[] regToLocal, int reg) {
        mv.visitVarInsn(ALOAD, regToLocal[reg]);
        mv.visitTypeInsn(CHECKCAST, NUMVAL);
        mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
    }


    private void emitNewNumberValue(MethodVisitor mv) {
        mv.visitTypeInsn(NEW, NUMVAL);
        mv.visitInsn(DUP_X2);
        mv.visitInsn(DUP_X2);
        mv.visitInsn(POP);
        mv.visitMethodInsn(INVOKESPECIAL, NUMVAL, "<init>", "(D)V", false);
    }

    /** 加载常量值 */
    private void emitLoadConstant(MethodVisitor mv, IValue<?> c, String className, int constIndex) {
        if (c instanceof NumberValue nv) {
            double d = nv.value;
            if (d == 0.0) {
                mv.visitInsn(DCONST_0);
            } else if (d == 1.0) {
                mv.visitInsn(DCONST_1);
            } else {
                mv.visitLdcInsn(d);
            }
            emitNewNumberValue(mv);
        } else if (c instanceof BooleanValue bv) {
            if (bv == BooleanValue.TRUE) {
                mv.visitFieldInsn(GETSTATIC, BOOLVAL, "TRUE", BOOLVAL_DESC);
            } else {
                mv.visitFieldInsn(GETSTATIC, BOOLVAL, "FALSE", BOOLVAL_DESC);
            }
        } else if (c instanceof NoneValue) {
            mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
        } else {
            // 其他类型（StringValue 等）从 CONSTANTS 数组加载
            mv.visitFieldInsn(GETSTATIC, className, "CONSTANTS", "[" + IVALUE_DESC);
            emitIntConst(mv, constIndex);
            mv.visitInsn(AALOAD);
        }
    }

    private void emitIntConst(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    private void emitBinaryArith(MethodVisitor mv, int[] regToLocal, IRInstruction inst, int dop) {
        Label slowPath = new Label();
        Label end = new Label();

        // 检查左操作数是否为 NumberValue
        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitTypeInsn(INSTANCEOF, NUMVAL);
        mv.visitJumpInsn(IFEQ, slowPath);

        // 检查右操作数是否为 NumberValue
        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        mv.visitTypeInsn(INSTANCEOF, NUMVAL);
        mv.visitJumpInsn(IFEQ, slowPath);

        // 快速路径：double 运算
        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitTypeInsn(CHECKCAST, NUMVAL);
        mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        mv.visitTypeInsn(CHECKCAST, NUMVAL);
        mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
        mv.visitInsn(dop);
        emitNewNumberValue(mv);
        mv.visitJumpInsn(GOTO, end);

        // 慢速路径：调用 IData 方法
        mv.visitLabel(slowPath);
        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        String methodName = switch (dop) {
            case DADD -> "add";
            case DSUB -> "sub";
            case DMUL -> "mul";
            default -> "add";
        };
        mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, methodName,
                "(Lpriv/seventeen/artist/aria/value/IData;)" + IVALUE_DESC, false);

        mv.visitLabel(end);
        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
    }

    private void emitBinaryDiv(MethodVisitor mv, int[] regToLocal, IRInstruction inst) {
        Label slowPath = new Label();
        Label end = new Label();

        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitTypeInsn(INSTANCEOF, NUMVAL);
        mv.visitJumpInsn(IFEQ, slowPath);
        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        mv.visitTypeInsn(INSTANCEOF, NUMVAL);
        mv.visitJumpInsn(IFEQ, slowPath);

        // 快速路径
        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        mv.visitTypeInsn(CHECKCAST, NUMVAL);
        mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
        mv.visitInsn(DUP2);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DCMPL);
        Label nonZero = new Label();
        mv.visitJumpInsn(IFNE, nonZero);
        // 除数为 0，返回 NumberValue(0)
        mv.visitInsn(POP2); // 弹出除数
        mv.visitInsn(DCONST_0);
        emitNewNumberValue(mv);
        mv.visitJumpInsn(GOTO, end);

        mv.visitLabel(nonZero);
        // divisor 已在栈顶
        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitTypeInsn(CHECKCAST, NUMVAL);
        mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
        mv.visitInsn(DUP2_X2);
        mv.visitInsn(POP2);
        mv.visitInsn(DDIV);
        emitNewNumberValue(mv);
        mv.visitJumpInsn(GOTO, end);

        mv.visitLabel(slowPath);
        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "div",
                "(Lpriv/seventeen/artist/aria/value/IData;)" + IVALUE_DESC, false);

        mv.visitLabel(end);
        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
    }

    private void emitBinaryMod(MethodVisitor mv, int[] regToLocal, IRInstruction inst) {
        Label slowPath = new Label();
        Label end = new Label();

        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitTypeInsn(INSTANCEOF, NUMVAL);
        mv.visitJumpInsn(IFEQ, slowPath);
        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        mv.visitTypeInsn(INSTANCEOF, NUMVAL);
        mv.visitJumpInsn(IFEQ, slowPath);

        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        mv.visitTypeInsn(CHECKCAST, NUMVAL);
        mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
        mv.visitInsn(DUP2);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DCMPL);
        Label nonZero = new Label();
        mv.visitJumpInsn(IFNE, nonZero);
        mv.visitInsn(POP2);
        mv.visitInsn(DCONST_0);
        emitNewNumberValue(mv);
        mv.visitJumpInsn(GOTO, end);

        mv.visitLabel(nonZero);
        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitTypeInsn(CHECKCAST, NUMVAL);
        mv.visitFieldInsn(GETFIELD, NUMVAL, "value", "D");
        mv.visitInsn(DUP2_X2);
        mv.visitInsn(POP2);
        mv.visitInsn(DREM);
        emitNewNumberValue(mv);
        mv.visitJumpInsn(GOTO, end);

        mv.visitLabel(slowPath);
        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "mod",
                "(Lpriv/seventeen/artist/aria/value/IData;)" + IVALUE_DESC, false);

        mv.visitLabel(end);
        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
    }

    private void emitNumericBinary(MethodVisitor mv, int[] regToLocal, IRInstruction inst) {
        emitGetNumber(mv, regToLocal, inst.a);
        emitGetNumber(mv, regToLocal, inst.b);
        int dop = switch (inst.opcode) {
            case ADD_NUM -> DADD;
            case SUB_NUM -> DSUB;
            case MUL_NUM -> DMUL;
            case DIV_NUM -> DDIV;
            case MOD_NUM -> DREM;
            default -> DADD;
        };
        // DIV_NUM/MOD_NUM 需要除零检查
        if (inst.opcode == IROpCode.DIV_NUM || inst.opcode == IROpCode.MOD_NUM) {
            Label nonZero = new Label();
            Label end = new Label();
            mv.visitInsn(DUP2);
            mv.visitInsn(DCONST_0);
            mv.visitInsn(DCMPL);
            mv.visitJumpInsn(IFNE, nonZero);
            mv.visitInsn(POP2); // 弹出除数
            mv.visitInsn(POP2); // 弹出被除数
            mv.visitInsn(DCONST_0);
            emitNewNumberValue(mv);
            mv.visitJumpInsn(GOTO, end);
            mv.visitLabel(nonZero);
            mv.visitInsn(dop);
            emitNewNumberValue(mv);
            mv.visitLabel(end);
        } else {
            mv.visitInsn(dop);
            emitNewNumberValue(mv);
        }
        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
    }

    private void emitComparison(MethodVisitor mv, int[] regToLocal, IRInstruction inst, int ifOp, boolean numericOnly) {
        if (numericOnly) {
            // 直接 checkcast + getfield，跳过 invokevirtual
            emitGetNumber(mv, regToLocal, inst.a);
            emitGetNumber(mv, regToLocal, inst.b);
        } else {
            mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
            mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "numberValue", "()D", false);
            mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
            mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "numberValue", "()D", false);
        }

        // dcmpg/dcmpl → int → 条件跳转
        Label trueLabel = new Label();
        Label endLabel = new Label();

        // 对于 LE/LT 用 dcmpg（NaN → 1 → 不满足条件）
        // 对于 GE/GT 用 dcmpl（NaN → -1 → 不满足条件）
        if (ifOp == IF_ICMPLE || ifOp == IF_ICMPLT) {
            mv.visitInsn(DCMPG);
        } else {
            mv.visitInsn(DCMPL);
        }

        // dcmpg/dcmpl 结果: -1, 0, 1
        // 转换 IF_ICMPxx 为 IFxx（单操作数与0比较）
        int ifZeroOp = switch (ifOp) {
            case IF_ICMPLE -> IFLE;
            case IF_ICMPLT -> IFLT;
            case IF_ICMPGE -> IFGE;
            case IF_ICMPGT -> IFGT;
            default -> IFEQ;
        };
        mv.visitJumpInsn(ifZeroOp, trueLabel);
        mv.visitFieldInsn(GETSTATIC, BOOLVAL, "FALSE", BOOLVAL_DESC);
        mv.visitJumpInsn(GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitFieldInsn(GETSTATIC, BOOLVAL, "TRUE", BOOLVAL_DESC);
        mv.visitLabel(endLabel);
        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
    }

    private void emitEquality(MethodVisitor mv, int[] regToLocal, IRInstruction inst, boolean isEq) {
        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "eq",
                "(Lpriv/seventeen/artist/aria/value/IData;)" + BOOLVAL_DESC, false);
        if (!isEq) {
            mv.visitMethodInsn(INVOKEVIRTUAL, BOOLVAL, "not", "()" + BOOLVAL_DESC, false);
        }
        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
    }


    private void emitStaticCall(MethodVisitor mv, int[] regToLocal, IRInstruction inst,
                                int ctxLocal, String className) {
        String fn = inst.name;
        int argBase = inst.a;
        int argCnt = inst.b;

        if ("io.println".equals(fn)) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            if (argCnt >= 1) {
                mv.visitVarInsn(ALOAD, regToLocal[argBase]);
                mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "stringValue", "()Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
                        "(Ljava/lang/String;)V", false);
            } else {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
            }
            mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
            mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
            return;
        }
        if ("io.print".equals(fn)) {
            if (argCnt >= 1) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitVarInsn(ALOAD, regToLocal[argBase]);
                mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "stringValue", "()Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print",
                        "(Ljava/lang/String;)V", false);
            }
            mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
            mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
            return;
        }

        // math.* 单参数函数 → 直接 invokestatic Math.xxx
        if (argCnt == 1) {
            String mathMethod = switch (fn) {
                case "math.sin" -> "sin";
                case "math.cos" -> "cos";
                case "math.tan" -> "tan";
                case "math.abs" -> "abs";
                case "math.floor" -> "floor";
                case "math.ceil" -> "ceil";
                case "math.sqrt" -> "sqrt";
                case "math.log" -> "log";
                default -> null;
            };
            if (mathMethod != null) {
                mv.visitVarInsn(ALOAD, regToLocal[argBase]);
                mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "numberValue", "()D", false);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", mathMethod, "(D)D", false);
                emitNewNumberValue(mv);
                mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                return;
            }
            if ("math.round".equals(fn)) {
                mv.visitVarInsn(ALOAD, regToLocal[argBase]);
                mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "numberValue", "()D", false);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "round", "(D)J", false);
                mv.visitInsn(L2D);
                emitNewNumberValue(mv);
                mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                return;
            }
        }

        // math.* 双参数函数
        if (argCnt == 2) {
            String mathMethod = switch (fn) {
                case "math.pow" -> "pow";
                case "math.min" -> "min";
                case "math.max" -> "max";
                default -> null;
            };
            if (mathMethod != null) {
                mv.visitVarInsn(ALOAD, regToLocal[argBase]);
                mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "numberValue", "()D", false);
                mv.visitVarInsn(ALOAD, regToLocal[argBase + 1]);
                mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "numberValue", "()D", false);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", mathMethod, "(DD)D", false);
                emitNewNumberValue(mv);
                mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                return;
            }
        }

        // math.random / math.PI / math.E（零参数）
        if (argCnt == 0 || "math.random".equals(fn)) {
            if ("math.random".equals(fn)) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "random", "()D", false);
                emitNewNumberValue(mv);
                mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                return;
            }
            if ("math.PI".equals(fn)) {
                mv.visitLdcInsn(Math.PI);
                emitNewNumberValue(mv);
                mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                return;
            }
            if ("math.E".equals(fn)) {
                mv.visitLdcInsn(Math.E);
                emitNewNumberValue(mv);
                mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                return;
            }
        }

        // 兜底：裸函数名 — 通过运行时 resolveVariable 查找并调用
        if (!fn.contains(".")) {
            // 生成: rtCallByName(fn, args, ctx)
            mv.visitLdcInsn(fn);
            // 构建参数数组
            emitIntConst(mv, argCnt);
            mv.visitTypeInsn(ANEWARRAY, IVALUE);
            for (int i = 0; i < argCnt; i++) {
                mv.visitInsn(DUP);
                emitIntConst(mv, i);
                mv.visitVarInsn(ALOAD, regToLocal[argBase + i]);
                mv.visitInsn(AASTORE);
            }
            mv.visitVarInsn(ALOAD, ctxLocal);
            mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                    "rtCallByName", "(Ljava/lang/String;[" + IVALUE_DESC + CONTEXT_DESC + ")" + IVALUE_DESC, false);
            mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
            return;
        }

        // 所有未知函数（含 ns.method 形式）通过 rtCallByName 调用
        mv.visitLdcInsn(fn);
        emitIntConst(mv, argCnt);
        mv.visitTypeInsn(ANEWARRAY, IVALUE);
        for (int i = 0; i < argCnt; i++) {
            mv.visitInsn(DUP);
            emitIntConst(mv, i);
            mv.visitVarInsn(ALOAD, regToLocal[argBase + i]);
            mv.visitInsn(AASTORE);
        }
        mv.visitVarInsn(ALOAD, ctxLocal);
        mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                "rtCallByName", "(Ljava/lang/String;[" + IVALUE_DESC + CONTEXT_DESC + ")" + IVALUE_DESC, false);
        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
    }

    public static IValue<?> rtCall(IValue<?> callee, IValue<?>[] args, Context ctx) {
        try {
            if (callee instanceof FunctionValue fv) {
                return fv.getCallable().invoke(
                        new InvocationData(ctx, null, args));
            }
        } catch (Exception e) { /* ignore */ }
        return NoneValue.NONE;
    }


    public static IValue<?> rtCallByName(String name, IValue<?>[] args, Context ctx) {
        try {
            // 先查 scope → var → global
            VariableKey key = VariableKey.of(name);
            // scope
            VariableReference scopeRef = ctx.getScopeStack().getExisting(key);
            if (scopeRef != null) {
                IValue<?> val = scopeRef.getValue();
                if (val instanceof FunctionValue fv) {
                    return fv.getCallable().invoke(
                            new InvocationData(ctx, null, args));
                }
            }
            // var
            VariableReference varRef = ctx.getLocalStorage().getVarVariableExisting(key);
            if (varRef != null) {
                IValue<?> val = varRef.getValue();
                if (val instanceof FunctionValue fv) {
                    return fv.getCallable().invoke(
                            new InvocationData(ctx, null, args));
                }
            }
            // CallableManager 全局函数
            ICallable globalFn =
                    CallableManager.INSTANCE.getStaticFunction("", name);
            if (globalFn != null) {
                return globalFn.invoke(
                        new InvocationData(ctx, null, args));
            }
            // bridge.method(args) 形式 — 闭包捕获对象的方法分派
            // 当 name 含 '.' 且没有匹配的命名空间静态函数时，按解释器逻辑：
            // 把第一段当变量解析，把第二段当方法调
            int dot = name.indexOf('.');
            if (dot > 0) {
                String baseName = name.substring(0, dot);
                String methodName = name.substring(dot + 1);
                // 命名空间静态函数（如 math.sin、user-registered "X.Y"）
                ICallable nsFn = CallableManager.INSTANCE.getStaticFunction(baseName, methodName);
                if (nsFn != null) {
                    return nsFn.invoke(new InvocationData(ctx, null, args));
                }
                // resolveVariable: scope → var → global
                IValue<?> obj = null;
                VariableKey baseKey = VariableKey.of(baseName);
                VariableReference baseScopeRef = ctx.getScopeStack().getExisting(baseKey);
                if (baseScopeRef != null) obj = baseScopeRef.getValue();
                if (obj == null || obj instanceof NoneValue) {
                    VariableReference baseVarRef = ctx.getLocalStorage().getVarVariableExisting(baseKey);
                    if (baseVarRef != null) obj = baseVarRef.getValue();
                }
                if (obj == null || obj instanceof NoneValue) {
                    VariableReference baseGlobalRef = ctx.getGlobalStorage().getGlobalVariable(baseKey);
                    if (baseGlobalRef != null) obj = baseGlobalRef.getValue();
                }
                if (obj != null && !(obj instanceof NoneValue)) {
                    return rtCallMethod(obj, methodName, args, ctx);
                }
            }
        } catch (Exception e) { /* ignore */ }
        return NoneValue.NONE;
    }

    private static boolean isSimpleBinaryLambda(IRProgram sub) {
        if (sub == null) return false;
        IRInstruction[] c = sub.getInstructions();
        // 模式：PUSH_SCOPE, LOAD_ARG 0, LOAD_ARG 1, ADD/SUB/MUL/DIV, RETURN, POP_SCOPE
        // 或更简单的变体
        int opCount = 0;
        boolean hasReturn = false;
        for (IRInstruction inst : c) {
            switch (inst.opcode) {
                case LOAD_ARG, LOAD_CONST, PUSH_SCOPE, POP_SCOPE, NOP, MOVE -> {}
                case ADD, SUB, MUL, DIV, MOD -> opCount++;
                case RETURN -> hasReturn = true;
                default -> { return false; }
            }
        }
        return hasReturn && opCount <= 3;
    }

    private void emitInlinedLambda(MethodVisitor mv, int[] regToLocal, IRInstruction callInst,
                                   IRProgram sub, boolean numericOnly, int ctxLocal) {
        IRInstruction[] subCode = sub.getInstructions();
        int argBase = callInst.c;
        // 遍历 lambda 的 IR，将 LOAD_ARG N 映射到 CALL 的参数寄存器
        // 简单实现：找到算术运算指令，直接用 CALL 的参数
        int resultReg = -1;
        for (IRInstruction si : subCode) {
            switch (si.opcode) {
                case PUSH_SCOPE, POP_SCOPE, NOP, MOVE -> {}
                case LOAD_ARG -> {
                    // args[N] → CALL 的第 N 个参数寄存器
                    int srcReg = argBase + si.a;
                    if (srcReg < regToLocal.length && regToLocal[srcReg] >= 0) {
                        // 复用 CALL 参数寄存器的值
                        // 需要一个临时映射：lambda 的寄存器 → 主程序的寄存器
                    }
                }
                default -> {}
            }
        }
        // 简化实现：对于 args[0] + args[1] 模式，直接生成 add
        // 找到 ADD/SUB/MUL/DIV 指令
        for (IRInstruction si : subCode) {
            if (si.opcode == IROpCode.ADD || si.opcode == IROpCode.SUB ||
                    si.opcode == IROpCode.MUL || si.opcode == IROpCode.DIV) {
                // 假设操作数是 args[0] 和 args[1]
                int arg0Reg = argBase;
                int arg1Reg = argBase + 1;
                if (numericOnly) {
                    emitGetNumber(mv, regToLocal, arg0Reg);
                    if (callInst.b > 1) {
                        emitGetNumber(mv, regToLocal, arg1Reg);
                    } else {
                        mv.visitInsn(DCONST_0);
                    }
                    int dop = switch (si.opcode) {
                        case ADD -> DADD;
                        case SUB -> DSUB;
                        case MUL -> DMUL;
                        case DIV -> DDIV;
                        default -> DADD;
                    };
                    mv.visitInsn(dop);
                    emitNewNumberValue(mv);
                } else {
                    mv.visitVarInsn(ALOAD, regToLocal[arg0Reg]);
                    mv.visitVarInsn(ALOAD, regToLocal[arg1Reg]);
                    String op = switch (si.opcode) {
                        case ADD -> "add";
                        case SUB -> "sub";
                        case MUL -> "mul";
                        case DIV -> "div";
                        default -> "add";
                    };
                    mv.visitMethodInsn(INVOKEVIRTUAL, "priv/seventeen/artist/aria/value/IData",
                            op, "(Lpriv/seventeen/artist/aria/value/IData;)" + IVALUE_DESC, false);
                }
                mv.visitVarInsn(ASTORE, regToLocal[callInst.dst]);
                return;
            }
        }
        // 回退
        emitGenericCall(mv, regToLocal, callInst, ctxLocal);
    }

    private void emitGenericCall(MethodVisitor mv, int[] regToLocal, IRInstruction inst, int ctxLocal) {
        int argCnt = inst.b;
        int argBase = inst.c;

        // 构建参数：callee, new IValue[]{arg0, arg1, ...}, ctx
        mv.visitVarInsn(ALOAD, regToLocal[inst.a]); // callee

        // 创建 IValue[] 参数数组
        emitIntConst(mv, argCnt);
        mv.visitTypeInsn(ANEWARRAY, IVALUE);
        for (int i = 0; i < argCnt; i++) {
            mv.visitInsn(DUP);
            emitIntConst(mv, i);
            int r = argBase + i;
            if (r < regToLocal.length && regToLocal[r] >= 0) {
                mv.visitVarInsn(ALOAD, regToLocal[r]);
            } else {
                mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
            }
            mv.visitInsn(AASTORE);
        }

        mv.visitVarInsn(ALOAD, ctxLocal); // ctx

        mv.visitMethodInsn(INVOKESTATIC,
                "priv/seventeen/artist/aria/jit/JITCompiler", "rtCall",
                "(" + IVALUE_DESC + "[" + IVALUE_DESC + CONTEXT_DESC + ")" + IVALUE_DESC, false);
        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
    }

    private void emitLoadVarRef(MethodVisitor mv, int ctxLocal, String className, int keyIdx) {
        mv.visitVarInsn(ALOAD, ctxLocal);
        mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getLocalStorage",
                "()L" + LOCAL_STORAGE + ";", false);
        mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
        emitIntConst(mv, keyIdx);
        mv.visitInsn(AALOAD);
        mv.visitMethodInsn(INVOKEVIRTUAL, LOCAL_STORAGE, "getVarVariable",
                "(" + VKEY_DESC + ")" + VREF_DESC, false);
    }

    /**
     * GET_PROP 运行时辅助：覆盖与 Interpreter.GET_PROP 相同的常用类型分支
     * （MapValue / SmallMapValue / AriaClassValue / ObjectValue / list.length / string.length）。
     * ClassDefinition 的静态方法 / __get_xxx getter 等少见路径走 Interpreter，本方法返回 NoneValue
     * 而不是抛异常 —— 不会破坏脚本，只是该 GET_PROP 退化为 NONE，调用方按 NONE 处理。
     */
    public static IValue<?> rtGetProp(IValue<?> obj, String propName, Context ctx) {
        if (obj == null) return NoneValue.NONE;
        if (obj instanceof MapValue mv) {
            IValue<?> val = mv.jvmValue().get(new StringValue(propName));
            return val != null ? val : NoneValue.NONE;
        }
        if (obj instanceof SmallMapValue sm) {
            IValue<?> v = sm.get(propName);
            return v != null ? v : NoneValue.NONE;
        }
        if (obj instanceof AriaClassValue cv && cv.jvmValue() != null) {
            ClassInstance ci = cv.jvmValue();
            IReference fieldRef = ci.getFields().get(propName);
            if (fieldRef != null) return fieldRef.getValue();
            // 类定义里的方法 → FunctionValue 包装
            ClassDefinition classDef = ci.getClassDefinition();
            if (classDef != null) {
                IRProgram methodProg = classDef.findMethod(propName);
                if (methodProg != null) {
                    final IValue<?> capturedObj = obj;
                    ICallable methodCallable = data -> {
                        Context callCtx = data.getContext().createCallContext(capturedObj, data.getArgs());
                        return new Interpreter().execute(methodProg, callCtx).getValue();
                    };
                    return new FunctionValue(methodCallable);
                }
            }
            return NoneValue.NONE;
        }
        if (obj instanceof ObjectValue<?> ov) {
            IAriaObject so = ov.jvmValue();
            Variable v = so.getVariable(propName);
            return v != null && v.ariaValue() != null ? v.ariaValue() : NoneValue.NONE;
        }
        if (obj instanceof ListValue lv && "length".equals(propName)) {
            return new NumberValue(lv.jvmValue().size());
        }
        if (obj instanceof StringValue sv && "length".equals(propName)) {
            return new NumberValue(sv.stringValue().length());
        }
        return NoneValue.NONE;
    }

    public static IValue<?> rtGetIndex(IValue<?> obj, IValue<?> idx) {
        if (obj instanceof ListValue lv) {
            int index = (int) idx.numberValue();
            List<IValue<?>> list = lv.jvmValue();
            if (index >= 0 && index < list.size()) {
                return list.get(index);
            }
            return NoneValue.NONE;
        }
        if (obj instanceof SmallMapValue sm) {
            return sm.get(idx);
        }
        if (obj instanceof MapValue mv) {
            Map<IValue<?>, IValue<?>> map = mv.jvmValue();
            // 先用原始 IValue key 查
            IValue<?> val = map.get(idx);
            if (val == null && !(idx instanceof StringValue)) {
                // idx 不是 StringValue 时才需要包装重试
                val = map.get(new StringValue(idx.stringValue()));
            }
            if (val == null) {
                return NoneValue.NONE;
            }
            return val;
        }
        if (obj instanceof ObjectValue<?> ov
                && ov.jvmValue() instanceof RangeObject range) {
            double val = range.getStart() + idx.numberValue() * range.getStep();
            if (range.getStep() > 0 ? val < range.getEnd() : val > range.getEnd()) {
                return new NumberValue(val);
            }
            return NoneValue.NONE;
        }
        return NoneValue.NONE;
    }

    public static IValue<?> rtCallMethod(IValue<?> obj, String methodName, IValue<?>[] args, Context ctx) {
        try {
            // 先查 CallableManager 对象函数
            ICallable objFunc = CallableManager.INSTANCE
                    .getObjectFunction(obj.getClass(), methodName);
            if (objFunc != null) {
                IValue<?>[] callArgs = new IValue<?>[args.length + 1];
                callArgs[0] = obj;
                System.arraycopy(args, 0, callArgs, 1, args.length);
                return objFunc.invoke(new InvocationData(ctx, obj, callArgs));
            }
            // 查 AriaClassValue 实例方法
            if (obj instanceof AriaClassValue cv && cv.jvmValue() != null) {
                ClassInstance ci = cv.jvmValue();
                // 先查实例字段中的函数值
                if (ci.getFields().containsKey(methodName)) {
                    IValue<?> method = ci.getFields().get(methodName).getValue();
                    if (method instanceof FunctionValue fv) {
                        Context callCtx = ctx.createCallContext(obj, args);
                        return fv.getCallable().invoke(
                                new InvocationData(callCtx, obj, args));
                    }
                }
                // 再查类定义中的方法
                ClassDefinition classDef = ci.getClassDefinition();
                if (classDef != null) {
                    IRProgram methodProg = classDef.findMethod(methodName);
                    if (methodProg != null) {
                        Context callCtx = ctx.createCallContext(obj, args);
                        Interpreter interpreter = new Interpreter();
                        Result result = interpreter.execute(methodProg, callCtx);
                        return result.getValue();
                    }
                }
            }
            // ObjectValue 方法查找
            if (obj instanceof ObjectValue<?> ov) {
                IAriaObject so = ov.jvmValue();
                Variable v = so.getVariable(methodName);
                IValue<?> method = v.ariaValue();
                if (method instanceof FunctionValue fv) {
                    Context callCtx = ctx.createCallContext(obj, args);
                    return fv.getCallable().invoke(
                            new InvocationData(callCtx, obj, args));
                }
            }
        } catch (Exception e) { /* ignore */ }
        return NoneValue.NONE;
    }

    public static IValue<?> rtNewList(IValue<?>[] elements) {
        List<IValue<?>> list = new ArrayList<>(elements.length);
        Collections.addAll(list, elements);
        return new ListValue(list);
    }

    public static IValue<?> rtNewMap(IValue<?>[] kvPairs, int entryCount) {
        if (entryCount <= 4) {
            // 直接构造 SmallMapValue，复用 kvPairs 中的值引用
            String[] keys = new String[entryCount];
            IValue<?>[] values = new IValue<?>[entryCount];
            for (int i = 0; i < entryCount; i++) {
                IValue<?> k = kvPairs[i * 2];
                keys[i] = k instanceof StringValue sv
                        ? sv.jvmValue() : k.stringValue();
                values[i] = kvPairs[i * 2 + 1];
            }
            return new SmallMapValue(keys, values, entryCount);
        }
        Map<IValue<?>, IValue<?>> map = new HashMap<>(entryCount * 2);
        for (int i = 0; i < entryCount; i++) {
            map.put(kvPairs[i * 2], kvPairs[i * 2 + 1]);
        }
        return new MapValue(map);
    }

    public static void rtSetIndex(IValue<?> obj, IValue<?> index, IValue<?> value) {
        if (index == null) {
            // 空索引 — list.add
            if (obj instanceof ListValue lv) {
                lv.jvmValue().add(value);
            }
        } else if (obj instanceof ListValue lv) {
            int idx = (int) index.numberValue();
            List<IValue<?>> list = lv.jvmValue();
            while (list.size() <= idx) list.add(NoneValue.NONE);
            list.set(idx, value);
        } else if (obj instanceof MapValue mv) {
            mv.jvmValue().put(index, value);
        } else if (obj instanceof ObjectValue<?> ov) {
            Variable elem = ov.jvmValue().getElement(index.stringValue());
            if (elem instanceof Variable.Normal nv) {
                nv.setValue(value);
            }
        }
    }

    public static void rtVarAddReg(VariableReference ref, IValue<?> val) {
        IValue<?> cur = ref.getValue();
        if (cur instanceof NumberValue nv && val instanceof NumberValue rv) {
            ref.setValue(new NumberValue(nv.value + rv.value));
        } else if (cur instanceof MutableStringValue ms) {
            ms.append(val.stringValue());
        } else if (cur instanceof RopeString rs) {
            // Rope 转为 MutableString（累加模式 StringBuilder 更优）
            MutableStringValue ms2 =
                    new MutableStringValue(rs.stringValue());
            ms2.append(val.stringValue());
            ref.setValue(ms2);
        } else if (cur instanceof StringValue cs && !cs.canBeNumber()) {
            MutableStringValue ms3 =
                    new MutableStringValue(cs.stringValue());
            ms3.append(val.stringValue());
            ref.setValue(ms3);
        } else {
            ref.setValue(cur.add(val));
        }
    }


    private Class<?> loadClass(String name, byte[] bytecode) {
        return new ClassLoader(JITCompiler.class.getClassLoader()) {
            public Class<?> define() {
                return defineClass(name, bytecode, 0, bytecode.length);
            }
        }.define();
    }

    private void emitInvokeMethod(ClassWriter cw, String className, String callDesc, int argCount) {
        String INVDATA = "priv/seventeen/artist/aria/callable/InvocationData";
        String INVDATA_DESC = "Lpriv/seventeen/artist/aria/callable/InvocationData;";

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "invoke",
                "(" + INVDATA_DESC + ")" + IVALUE_DESC, null,
                new String[]{"priv/seventeen/artist/aria/exception/AriaException"});
        mv.visitCode();

        // 加载参数: data.get(0), data.get(1), ...
        for (int i = 0; i < argCount; i++) {
            mv.visitVarInsn(ALOAD, 1); // data
            emitIntConst(mv, i);
            mv.visitMethodInsn(INVOKEVIRTUAL, INVDATA, "get", "(I)" + IVALUE_DESC, false);
        }
        // 加载 Context: data.getContext()
        mv.visitVarInsn(ALOAD, 1); // data
        mv.visitMethodInsn(INVOKEVIRTUAL, INVDATA, "getContext", "()" + CONTEXT_DESC, false);

        // invokestatic call(arg0, arg1, ..., ctx)
        mv.visitMethodInsn(INVOKESTATIC, className, "call", callDesc, false);
        mv.visitInsn(ARETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
