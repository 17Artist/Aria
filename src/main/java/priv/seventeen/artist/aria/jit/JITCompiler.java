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
import priv.seventeen.artist.aria.callable.CallableWithInvoker;
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
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;
import priv.seventeen.artist.aria.value.reference.IReference;
import priv.seventeen.artist.aria.value.reference.VariableReference;
import priv.seventeen.artist.aria.value.reference.ValueReference;
import priv.seventeen.artist.aria.value.ListValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.objectweb.asm.Opcodes.*;

public class JITCompiler {

    // 运行一次即触发 JIT:首次执行(解释)后即调度编译,后续执行/调用走编译代码。
    // 编译为 AriaCompiledRoutine 的脚本及其函数热路径因此尽快进入 JIT 流程。
    private static final int JIT_THRESHOLD = 1;
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
                case SET_PROP:   // self.x = v(类方法字段写)→ rtSetProp,使含字段赋值的方法可 JIT
                    // 复合优化指令
                case VAR_INC, VAR_ADD_CONST, VAR_ADD_REG:
                    // Shimmer 对齐(R2 系)：赋值 RHS 自动调用
                case AUTO_INVOKE:
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
                    break;
                case LOAD_VAR:
                    // LOAD_VAR 用于加载递归函数引用 — 允许
                    break;
                case CALL:
                    // CALL __import__ 是解释器特例（模块加载），JIT 未实现该分支，
                    // 编译后会返回 none。import 非热点代码，含它的程序一律不 JIT，走解释器（正确）。
                    if ("__import__".equals(inst.name)) return false;
                    // 其余 CALL 只允许自递归调用（callee 来自 LOAD_VAR）
                    break;
                case CALL_STATIC:
                    // math.* 内联、ns.method（rtCallByNameCached）、裸名（自递归 / 兄弟函数 /
                    // 任意 var 函数）都允许：doCompile 对纯数值组走整组编译，否则通用路径以 rtCallByName
                    // 动态分派（正确）。name 为 null 无法解析时拒绝；
                    // A4："super" 由解释器特判(调父类构造器)，JIT 无对应实现 → 整程序走解释器。
                    if (inst.name == null || "super".equals(inst.name)) return false;
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    /**
     * A4(jit-3/17)：fast 数值路径可安全内联的静态调用终检——
     * 元数必须精确匹配；解释器走注册表分派的函数(round/pow/min/max/random/PI/E)还要求
     * 注册仍为内建默认实现(宿主覆盖后不内联,程序落回通用路径经注册表分派,与解释器一致)。
     * sin..log 这 8 个解释器两个执行循环同样硬编码 Math.*(无视注册表)，无条件可内联。
     */
    private boolean isInlineableFastStatic(IRInstruction inst) {
        String fn = inst.name;
        if (fn == null) return false;
        int argc = inst.b;
        return switch (fn) {
            case "math.sin", "math.cos", "math.tan", "math.abs",
                 "math.floor", "math.ceil", "math.sqrt", "math.log" -> argc == 1;
            case "math.round" -> argc == 1 && CallableManager.INSTANCE.isDefaultStatic("math", "round");
            case "math.pow" -> argc == 2 && CallableManager.INSTANCE.isDefaultStatic("math", "pow");
            case "math.min" -> argc == 2 && CallableManager.INSTANCE.isDefaultStatic("math", "min");
            case "math.max" -> argc == 2 && CallableManager.INSTANCE.isDefaultStatic("math", "max");
            // random/PI/E：默认实现忽略入参，任意元数结果一致，只校验默认性
            case "math.random" -> CallableManager.INSTANCE.isDefaultStatic("math", "random");
            case "math.PI" -> CallableManager.INSTANCE.isDefaultStatic("math", "PI");
            case "math.E" -> CallableManager.INSTANCE.isDefaultStatic("math", "E");
            default -> false;
        };
    }

    public ICallable compile(IRProgram subProgram, Context context) {
        try {
            return doCompile(subProgram, context);
        } catch (Exception e) {
            return null;
        }
    }

    private ICallable doCompile(IRProgram program, Context jitContext) throws Exception {
        IRInstruction[] code = program.getInstructions();
        IValue<?>[] constants = program.getConstants();
        VariableKey[] keys = program.getVariableKeys();
        IRProgram[] subPrograms = program.getSubPrograms();

        int maxArgIndex = detectMaxArgIndex(code);
        int argCount = maxArgIndex + 1;

        Set<Integer> selfRecursiveCallPCs = detectSelfRecursion(code, keys, program);

        boolean numericOnly = isNumericOnly(code) && constantsAreNumeric(constants);

        // A4(jit-1..6)：fast 路径准入终检——调用覆盖/flag 寄存器/LOAD_VAR 用途逐项校验，任一不满足
        // 走通用(值语义正确)路径。
        boolean fastDoubleRecursion = numericOnly && !selfRecursiveCallPCs.isEmpty()
                && isFastPlanSafe(code, selfRecursiveCallPCs, false, false);

        if (numericOnly && jitContext != null) {
            List<IRProgram> numericGroup = detectNumericGroup(program, jitContext);
            if (numericGroup.size() > 1) {
                try {
                    // compileGroup 负责给组内每个成员 setCompiledCode + setJitContextFree，并返回 entry 产物。
                    ICallable group = compileGroup(numericGroup, jitContext);
                    if (group != null) return group;
                } catch (Throwable ignored) {
                    // 回落单函数路径
                }
            }
        }

        boolean hasVarOps = hasVarOperations(code);
        // fastVars 是根程序路径,基准=解释器主循环 → 还须显式数值 RETURN(见 isFastPlanSafe)
        boolean fastDoubleVars = numericOnly && selfRecursiveCallPCs.isEmpty() && hasVarOps && argCount == 0
                && isFastPlanSafe(code, Collections.emptySet(), true, true);

        boolean fastLongVars = fastDoubleVars && isIntegerSafe(code, constants);

        // A4(jit-1)：fastVars 入口守卫的 var 键集合(进入时存量值可能被读到的 var 必须是 NumberValue)
        int[] guardKeyIndices = (fastDoubleVars || fastLongVars) ? computeGuardKeyIndices(code) : null;

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
        // INSTS：每个 PC 对应的 IRInstruction，用于在 JIT 生成代码里通过 inst.cache 缓存运行时解析结果
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "INSTS",
                "[Lpriv/seventeen/artist/aria/compiler/ir/IRInstruction;", null, null).visitEnd();
        // A4(jit-1)：PROGRAM/GUARD_KEYS — fastVars 入口守卫用(守卫失败经 rtFastVarsGuard 回退解释器)
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "PROGRAM", IRPROGRAM_DESC, null, null).visitEnd();
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "GUARD_KEYS", "[I", null, null).visitEnd();

        if (fastDoubleRecursion) {
            // 生成 callFast(double, double, ...) → double
            String fastDesc = buildFastDescriptor(argCount);
            MethodVisitor fmv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "callFast", fastDesc, null, null);
            fmv.visitCode();
            // 单函数即“size==1 的组”：自递归 PC 全部指向组内唯一成员 index 0（方法名 callFast）。
            Map<Integer, Integer> selfCallTargets = new HashMap<>();
            for (int spc : selfRecursiveCallPCs) selfCallTargets.put(spc, 0);
            emitFastDoubleBytecode(fmv, code, constants, argCount, regCount, usedRegs,
                    selfCallTargets, new String[]{"callFast"}, new int[]{argCount}, className);
            fmv.visitMaxs(0, 0);
            fmv.visitEnd();

            // 生成 call(IValue, ..., Context) → IValue 作为入口包装
            String callDesc = buildCallDescriptor(argCount);
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "call", callDesc, null, null);
            mv.visitCode();
            // 提取 double 参数——A4(jit-9)：numberValue() 多态强转(none→0、数字串→值)，
            // 与解释器数值递归快路径 LOAD_ARG 的 args[i].numberValue() 一致；原 CHECKCAST 对非
            // NumberValue 抛 CCE。
            for (int i = 0; i < argCount; i++) {
                mv.visitVarInsn(ALOAD, i);
                mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "numberValue", "()D", false);
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
                // 从 InvocationData 提取参数——A4(jit-9)：同上 numberValue() 多态强转
                for (int i = 0; i < argCount; i++) {
                    imv.visitVarInsn(ALOAD, 1); // data
                    emitIntConst(imv, i);
                    imv.visitMethodInsn(INVOKEVIRTUAL, INVDATA, "get", "(I)" + IVALUE_DESC, false);
                    imv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "numberValue", "()D", false);
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
                    usedRegs, className, fastVarDesc);
            fmv.visitMaxs(0, 0);
            fmv.visitEnd();

            // 生成 call(IValue, ..., Context) → IValue 作为入口包装
            String callDesc = buildCallDescriptor(argCount);
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "call", callDesc, null, null);
            mv.visitCode();
            emitFastVarsGuard(mv, className, argCount, true); // A4(jit-1)：入口守卫(long 版还查整数性)
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
                    usedRegs, className, fastVarDesc);
            fmv.visitMaxs(0, 0);
            fmv.visitEnd();

            // 生成 call(IValue, ..., Context) → IValue 作为入口包装
            String callDesc = buildCallDescriptor(argCount);
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "call", callDesc, null, null);
            mv.visitCode();
            emitFastVarsGuard(mv, className, argCount, false); // A4(jit-1)：入口守卫
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
                    usedRegs, selfRecursiveCallPCs, className, callDesc, program);
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
                File dir = new File(dumpDir);
                dir.mkdirs();
                Files.write(new File(dir, className.replace('/', '_') + ".class").toPath(), bytecode);
            } catch (IOException ignored) {}
        }
        Class<?> clazz = loadClass(className.replace('/', '.'), bytecode);

        // 7. 设置静态字段
        clazz.getField("CONSTANTS").set(null, constants);
        clazz.getField("KEYS").set(null, keys);
        clazz.getField("SUB_PROGRAMS").set(null, subPrograms);
        clazz.getField("INSTS").set(null, code);
        clazz.getField("PROGRAM").set(null, program);
        if (guardKeyIndices != null) {
            clazz.getField("GUARD_KEYS").set(null, guardKeyIndices);
        }
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
                // 仅当被调用变量名 == 本函数名才算自递归。否则（如互递归 dot 形式
                // var.fb(n)，callee 来自 LOAD_VAR fb 但本函数是 fa）绝不能误判为自递归，
                // 否则 fast 路径会 INVOKESTATIC 直跳回自身(fa)，算出错误结果。
                if (keyIdx != null && selfName != null
                        && keyIdx >= 0 && keyIdx < keys.length
                        && keys[keyIdx] != null
                        && selfName.equals(keys[keyIdx].getName())) {
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

    /**
     * 检测以 entry 为起点、经 jitContext 可达的“纯数值函数组”（互递归 / 单向数值调用闭包）。
     * 返回组内函数有序列表（entry 在 index 0）；size==1 表示仅 entry（等价现有单函数路径）。
     * 取兄弟函数 IR 的途径：CALL_STATIC.name 或 CALL(callee 经 LOAD_VAR 回溯的变量名)
     *   → jitContext.getLocalValue(name).ariaValue() → FunctionValue.getSourceProgram()。
     * 准入：组内每个函数 isNumericOnly + constantsAreNumeric；任一不满足或取不到兄弟 IR
     *   → 该 callee 视为“边界”，不并入组（边界调用未来回落装箱路径）。
     */
    private List<IRProgram> detectNumericGroup(IRProgram entry, Context jitContext) {
        List<IRProgram> group = new ArrayList<>();
        group.add(entry);
        if (jitContext == null) return group;
        Set<IRProgram> visited = new HashSet<>();
        visited.add(entry);
        Deque<IRProgram> work = new ArrayDeque<>();
        work.add(entry);
        while (!work.isEmpty() && group.size() < 16) {
            IRProgram f = work.poll();
            IRInstruction[] code = f.getInstructions();
            VariableKey[] keys = f.getVariableKeys();
            Map<Integer, Integer> loadVarRegs = new HashMap<>();
            for (IRInstruction inst : code) {
                String calleeName = null;
                if (inst.opcode == IROpCode.LOAD_VAR) {
                    loadVarRegs.put(inst.dst, inst.a);
                } else if (inst.opcode == IROpCode.CALL_STATIC) {
                    calleeName = inst.name;
                } else if (inst.opcode == IROpCode.CALL) {
                    Integer keyIdx = loadVarRegs.get(inst.a);
                    if (keyIdx != null && keyIdx >= 0 && keyIdx < keys.length && keys[keyIdx] != null) {
                        calleeName = keys[keyIdx].getName();
                    }
                }
                if (calleeName == null) continue;
                if (calleeName.indexOf('.') >= 0) continue;          // 命名空间调用(math.* 等)，非 var 函数
                if (calleeName.equals(f.getName())) continue;        // 自递归，已是组成员
                IRProgram callee = resolveSiblingFunction(calleeName, jitContext);
                if (callee == null || visited.contains(callee)) continue;
                if (!isNumericOnly(callee.getInstructions()) || !constantsAreNumeric(callee.getConstants())) {
                    continue;                                        // 非纯数值 → 边界，不并入
                }
                visited.add(callee);
                group.add(callee);
                work.add(callee);
            }
        }
        return group;
    }

    /**
     * 经 JIT 编译时上下文按变量名取到兄弟函数的 FunctionValue。
     */
    private FunctionValue resolveSiblingFunctionValue(String name, Context jitContext) {
        try {
            VariableReference ref = jitContext.getLocalStorage().getVarVariableExisting(VariableKey.of(name));
            if (ref != null && ref.getValue() instanceof FunctionValue fv) {
                return fv;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 经 JIT 编译时上下文按变量名取到兄弟函数的子程序 IR（依赖 FunctionValue.sourceProgram 地基）。 */
    private IRProgram resolveSiblingFunction(String name, Context jitContext) {
        FunctionValue fv = resolveSiblingFunctionValue(name, jitContext);
        return fv != null ? fv.getSourceProgram() : null;
    }

    private static final String JITGROUP = "priv/seventeen/artist/aria/jit/JitGroup";
    private static final String JITGROUP_DESC = "L" + JITGROUP + ";";

    /**
     * 把一组纯数值互递归函数整组编译进一个类。
     */
    private ICallable compileGroup(List<IRProgram> group, Context jitContext) throws Exception {
        int n = group.size();
        String[] names = new String[n];
        Map<String, Integer> nameToIndex = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String nm = group.get(i).getName();
            if (nm == null || nm.isEmpty() || nm.indexOf('.') >= 0) return null;
            names[i] = nm;
            nameToIndex.put(nm, i);
        }
        if (nameToIndex.size() != n) return null; // 名字冲突，无法可靠区分成员

        // 校验每个成员当前确实绑定到本组对应子程序（守卫按 sourceProgram 身份比较，见 JitGroup.stillBound）。
        for (int i = 0; i < n; i++) {
            FunctionValue fv = resolveSiblingFunctionValue(names[i], jitContext);
            if (fv == null || fv.getSourceProgram() != group.get(i)) return null;
        }

        // 逐成员校验 + 计算布局 + 构建 callTargets。
        int[] argCounts = new int[n];
        int[] regCounts = new int[n];
        boolean[][] usedRegsArr = new boolean[n][];
        String[] methodNames = new String[n];
        @SuppressWarnings("unchecked")
        Map<Integer, Integer>[] callTargetsArr = new Map[n];
        for (int i = 0; i < n; i++) {
            IRProgram m = group.get(i);
            IRInstruction[] mc = m.getInstructions();
            if (mc == null || mc.length == 0) return null;
            if (!isNumericOnly(mc) || !constantsAreNumeric(m.getConstants())) return null;
            if (!isFastDoubleEmittable(mc)) return null;
            Map<Integer, Integer> ct = buildGroupCallTargets(m, nameToIndex);
            if (ct == null) return null; // 含无法解析为组成员/math.* 的调用
            // A4(jit-1..6)：组成员同样过 fast 计划终检(调用覆盖/flag 寄存器/LOAD_VAR 用途)
            if (!isFastPlanSafe(mc, ct.keySet(), false, false)) return null;
            callTargetsArr[i] = ct;
            argCounts[i] = detectMaxArgIndex(mc) + 1;
            regCounts[i] = m.getRegisterCount();
            usedRegsArr[i] = analyzeUsedRegisters(mc, regCounts[i]);
            methodNames[i] = "callFast_" + i;
        }

        int classId = CLASS_COUNTER.incrementAndGet();
        String className = "priv/seventeen/artist/aria/jit/CompiledGroup_" + classId;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V17, ACC_PUBLIC | ACC_FINAL, className, null, "java/lang/Object",
                new String[]{ICALLABLE});
        cw.visitField(ACC_PUBLIC, "idx", "I", null, null).visitEnd();
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "GROUP", JITGROUP_DESC, null, null).visitEnd();

        // 每个成员一个 callFast_i
        for (int i = 0; i < n; i++) {
            IRProgram m = group.get(i);
            MethodVisitor fmv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, methodNames[i],
                    buildFastDescriptor(argCounts[i]), null, null);
            fmv.visitCode();
            emitFastDoubleBytecode(fmv, m.getInstructions(), m.getConstants(), argCounts[i],
                    regCounts[i], usedRegsArr[i], callTargetsArr[i], methodNames, argCounts, className);
            fmv.visitMaxs(0, 0);
            fmv.visitEnd();
        }

        // 构造器 <init>(I)V：设置 idx
        MethodVisitor ctor = cw.visitMethod(ACC_PUBLIC, "<init>", "(I)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitVarInsn(ILOAD, 1);
        ctor.visitFieldInsn(PUTFIELD, className, "idx", "I");
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        emitGroupInvoke(cw, className, argCounts, methodNames);

        cw.visitEnd();
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

        IRProgram[] members = group.toArray(new IRProgram[0]);
        JitGroup info = new JitGroup(members, names, jitContext);
        clazz.getField("GROUP").set(null, info);

        // 每个成员实例化一份（带各自 idx），整组一起挂上 compiledCode。
        ICallable entry = null;
        for (int i = 0; i < n; i++) {
            ICallable instance = (ICallable) clazz.getDeclaredConstructor(int.class).newInstance(i);
            IRProgram m = group.get(i);
            m.setJitScheduled(true);     // 组级去重：兄弟成员自身阈值触发时不再重复编译
            m.setJitContextFree(true);   // 入口 invoke 自带 Context 守卫，无需外层 createCallContext
            m.setCompiledCode(instance);
            if (i == 0) entry = instance;
        }
        return entry;
    }

    /** invoke 入口：JitGroup.enter 守卫（失败则返回其去优化结果）→ switch(idx) 拆箱调 callFast_i 装箱。 */
    private void emitGroupInvoke(ClassWriter cw, String className, int[] argCounts, String[] methodNames) {
        String invDataDesc = "L" + INVOC_DATA + ";";
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "invoke",
                "(" + invDataDesc + ")" + IVALUE_DESC, null,
                new String[]{"priv/seventeen/artist/aria/exception/AriaException"});
        mv.visitCode();
        // IValue r = JitGroup.enter(GROUP, data, idx);  if (r != null) return r;
        mv.visitFieldInsn(GETSTATIC, className, "GROUP", JITGROUP_DESC);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "idx", "I");
        mv.visitMethodInsn(INVOKESTATIC, JITGROUP, "enter",
                "(" + JITGROUP_DESC + invDataDesc + "I)" + IVALUE_DESC, false);
        mv.visitVarInsn(ASTORE, 2);
        Label proceed = new Label();
        mv.visitVarInsn(ALOAD, 2);
        mv.visitJumpInsn(IFNULL, proceed);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ARETURN);
        mv.visitLabel(proceed);

        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "idx", "I");
        int n = argCounts.length;
        Label[] cases = new Label[n];
        for (int i = 0; i < n; i++) cases[i] = new Label();
        Label dflt = new Label();
        mv.visitTableSwitchInsn(0, n - 1, dflt, cases);
        for (int i = 0; i < n; i++) {
            mv.visitLabel(cases[i]);
            for (int j = 0; j < argCounts[i]; j++) {
                mv.visitVarInsn(ALOAD, 1);
                emitIntConst(mv, j);
                mv.visitMethodInsn(INVOKEVIRTUAL, INVOC_DATA, "get", "(I)" + IVALUE_DESC, false);
                // A4(jit-9)：numberValue() 多态强转(与解释器数值递归快路径一致)，原 CHECKCAST 抛 CCE
                mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "numberValue", "()D", false);
            }
            mv.visitMethodInsn(INVOKESTATIC, className, methodNames[i],
                    buildFastDescriptor(argCounts[i]), false);
            emitNewNumberValue(mv);
            mv.visitInsn(ARETURN);
        }
        mv.visitLabel(dflt);
        mv.visitFieldInsn(GETSTATIC, NONEVAL, "NONE", "L" + NONEVAL + ";");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /** 组成员的指令是否全部为 emitFastDoubleBytecode 能正确发射的纯数值/控制流指令。
     *  A4：LOAD_NONE/TRUE/FALSE 与 JUMP_IF_NONE 已从纯数值模型剔除(与 isNumericOnly 白名单一致)。 */
    private boolean isFastDoubleEmittable(IRInstruction[] code) {
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case LOAD_CONST, LOAD_VAR, LOAD_ARG:
                case ADD, SUB, MUL, DIV, MOD, NEG, INC, DEC:
                case ADD_NUM, SUB_NUM, MUL_NUM, DIV_NUM, MOD_NUM:
                case EQ, NE, GT, LT, GE, LE, NOT, AND, OR:
                case JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE:
                case RETURN, CALL, CALL_STATIC, MOVE, PUSH_SCOPE, POP_SCOPE, NOP:
                // AUTO_INVOKE：纯数值模型下值恒为 double(不可调用)→ no-op；
                // var 持函数值时入口守卫(非 NumberValue)已回落解释器。
                case AUTO_INVOKE:
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    /**
     * 为组成员构建 callTargets：pc → 被调成员在组内的 index。
     * CALL_STATIC(name) / CALL(callee 经 LOAD_VAR 回溯的 key 名) 解析 calleeName：
     *  - math.* 且 emitFastStaticCall 支持 → 不入 callTargets（由 emitFastStaticCall 内联），跳过；
     *  - 命中组成员名 → 记 pc→index；
     *  - 其它（无法解析 / 非组的 var 函数 / 不支持的命名空间调用）→ 返回 null 表示该组不可整编译。
     */
    private Map<Integer, Integer> buildGroupCallTargets(IRProgram m, Map<String, Integer> nameToIndex) {
        Map<Integer, Integer> ct = new HashMap<>();
        IRInstruction[] code = m.getInstructions();
        VariableKey[] keys = m.getVariableKeys();
        Map<Integer, Integer> loadVarRegs = new HashMap<>();
        for (int pc = 0; pc < code.length; pc++) {
            IRInstruction inst = code[pc];
            if (inst.opcode == IROpCode.LOAD_VAR) {
                loadVarRegs.put(inst.dst, inst.a);
                continue;
            }
            String calleeName;
            if (inst.opcode == IROpCode.CALL_STATIC) {
                calleeName = inst.name;
            } else if (inst.opcode == IROpCode.CALL) {
                Integer keyIdx = loadVarRegs.get(inst.a);
                if (keyIdx == null || keyIdx < 0 || keyIdx >= keys.length || keys[keyIdx] == null) {
                    return null; // CALL 但 callee 来源不明，无法编译
                }
                calleeName = keys[keyIdx].getName();
            } else {
                continue;
            }
            if (calleeName == null) return null;
            if (calleeName.indexOf('.') >= 0) {
                // 命名空间调用：仅允许可安全内联的 math.*(元数精确 + 注册仍为内建默认,A4 jit-17)。
                if (isInlineableFastStatic(inst)) continue;
                return null;
            }
            Integer idx = nameToIndex.get(calleeName);
            if (idx == null) return null; // 调用了组外的 var 函数 → 整组不可编译
            ct.put(pc, idx);
        }
        return ct;
    }

    /** 纯数值 fast 路径可直接内联的静态函数名(结构白名单；元数/默认实现由 isInlineableFastStatic 终检)。 */
    private static final java.util.Set<String> FAST_INLINE_STATICS = java.util.Set.of(
            "math.sin", "math.cos", "math.tan", "math.abs", "math.floor", "math.ceil",
            "math.sqrt", "math.log", "math.round", "math.pow", "math.min", "math.max",
            "math.random", "math.PI", "math.E");

    /**
     * A4(jit-1/2/3/4)：fast 数值路径准入改为白名单——只许「数字常量、VAR 读写、算术比较、
     * 数值跳转、RETURN、可内联/组内调用」。LOAD_TRUE/FALSE/NONE、JUMP_IF_NONE(??)、
     * LOAD_SERVER/CLIENT/GLOBAL、GET_PROP/SET_PROP 等一律非纯数值(否则布尔/none var 被
     * 强转 0.0 销毁、server 读被 no-op 冻结、?? 恒取左值)。
     * 结构判定；调用覆盖/flag 寄存器/返回形状由 {@link #isFastPlanSafe} 终检。
     */
    private boolean isNumericOnly(IRInstruction[] code) {
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case LOAD_CONST, LOAD_ARG, LOAD_VAR, STORE_VAR:
                case VAR_INC, VAR_ADD_CONST, VAR_ADD_REG:
                case ADD, SUB, MUL, DIV, MOD, NEG, INC, DEC:
                case ADD_NUM, SUB_NUM, MUL_NUM, DIV_NUM, MOD_NUM:
                case EQ, NE, LT, GT, LE, GE, NOT, AND, OR:
                case JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE:
                case RETURN, MOVE, PUSH_SCOPE, POP_SCOPE, NOP:
                // AUTO_INVOKE：纯数值模型下值恒为 double(不可调用)→ no-op(守卫兜底)
                case AUTO_INVOKE:
                    break;
                case CALL:
                    // 自递归/组内直跳由 isFastPlanSafe 校验覆盖；未覆盖的 CALL 会被其拒绝
                    break;
                case CALL_STATIC:
                    if (inst.name == null) return false;
                    // 裸名(自递归/组成员)结构上放行；命名空间调用只许 FAST_INLINE_STATICS
                    if (inst.name.indexOf('.') >= 0 && !FAST_INLINE_STATICS.contains(inst.name)) {
                        return false;
                    }
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    /**
     * A4(jit-1..6)：fast 数值路径(fastDoubleRecursion / fastDoubleVars / fastLongVars / 组编译)准入终检。
     * fast 路径把一切建模为 double/long——只有当每条指令在该模型下与解释器逐位一致时才放行：
     * <ul>
     *   <li>flag 寄存器分析：比较/NOT 在解释器产 BooleanValue，fast 路径产 0/1——flag 只许被
     *       NOT/AND/OR(传播)/MOVE(复制)/JUMP_IF_TRUE|FALSE(真值消费)使用，逃逸到 STORE_VAR/RETURN/
     *       算术/调用参数则拒绝(否则 var 存进 NumberValue(1.0) 而解释器是 TRUE)。</li>
     *   <li>调用覆盖：CALL 必须在编译计划内(自递归/组直跳)；CALL_STATIC 必须在计划内或可内联
     *       (isInlineableFastStatic)——杜绝"兜底 0 吞调用"(jit-3)。</li>
     *   <li>LOAD_VAR 在无 var 槽路径(递归/组)只许作已覆盖 CALL 的 callee 引用(否则读成 0)。</li>
     *   <li>fastVars(根程序,基准=解释器主循环)：RETURN 必须带数值寄存器且不可能落到隐式尾部
     *       (主循环隐式返回 NONE,fast 只能回 0.0)。</li>
     * </ul>
     */
    private boolean isFastPlanSafe(IRInstruction[] code, Set<Integer> coveredCallPCs,
                                   boolean allowVarSlots, boolean requireExplicitNumericReturn) {
        int regCount = 1;
        for (IRInstruction inst : code) {
            regCount = Math.max(regCount, Math.max(inst.dst, Math.max(inst.a, inst.b)) + 1);
        }
        // ---- flag 寄存器传播 ----
        // 比较/NOT/AND/OR 的结果一律记 flag：解释器主循环产 BooleanValue/操作数值、数值快路径产 0/1、
        // fast 生成码产 0/1 或选值——三者只有「>0 真值」这一观测面一致，故 flag 只许经真值消费。
        boolean[] flag = new boolean[regCount];
        boolean changed = true;
        while (changed) {
            changed = false;
            for (IRInstruction inst : code) {
                switch (inst.opcode) {
                    case EQ, NE, LT, GT, LE, GE, NOT, AND, OR -> {
                        if (inst.dst >= 0 && !flag[inst.dst]) { flag[inst.dst] = true; changed = true; }
                    }
                    case MOVE -> {
                        if (inst.a >= 0 && inst.a < regCount && flag[inst.a]
                                && inst.dst >= 0 && !flag[inst.dst]) { flag[inst.dst] = true; changed = true; }
                    }
                    default -> {}
                }
            }
        }
        // ---- flag 消费检查：只许 NOT/AND/OR/MOVE/JUMP_IF_TRUE|FALSE ----
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case ADD, SUB, MUL, DIV, MOD, ADD_NUM, SUB_NUM, MUL_NUM, DIV_NUM, MOD_NUM,
                     EQ, NE, LT, GT, LE, GE -> {
                    if (isFlagReg(flag, inst.a) || isFlagReg(flag, inst.b)) return false;
                }
                case NEG, INC, DEC -> { if (isFlagReg(flag, inst.a)) return false; }
                case STORE_VAR -> { if (isFlagReg(flag, inst.dst)) return false; }
                case VAR_ADD_REG -> { if (isFlagReg(flag, inst.b)) return false; }
                case RETURN -> { if (inst.dst >= 0 && isFlagReg(flag, inst.dst)) return false; }
                case CALL -> {
                    for (int i = 0; i < inst.b; i++) if (isFlagReg(flag, inst.c + i)) return false;
                }
                case CALL_STATIC -> {
                    for (int i = 0; i < inst.b; i++) if (isFlagReg(flag, inst.a + i)) return false;
                }
                default -> {}
            }
        }
        // ---- 调用覆盖 & var 指令 ----
        for (int pc = 0; pc < code.length; pc++) {
            IRInstruction inst = code[pc];
            switch (inst.opcode) {
                case CALL -> { if (!coveredCallPCs.contains(pc)) return false; }
                case CALL_STATIC -> {
                    if (!coveredCallPCs.contains(pc) && !isInlineableFastStatic(inst)) return false;
                }
                case STORE_VAR, VAR_INC, VAR_ADD_CONST, VAR_ADD_REG -> {
                    if (!allowVarSlots) return false; // 递归/组发射器无 var 槽,写会被静默丢弃
                }
                default -> {}
            }
        }
        // ---- 无 var 槽路径：LOAD_VAR 只许作已覆盖 CALL 的 callee 引用 ----
        if (!allowVarSlots) {
            Set<Integer> calleeRegs = new HashSet<>();
            for (int pc : coveredCallPCs) {
                if (code[pc].opcode == IROpCode.CALL) calleeRegs.add(code[pc].a);
            }
            for (IRInstruction inst : code) {
                if (inst.opcode == IROpCode.LOAD_VAR && !calleeRegs.contains(inst.dst)) return false;
            }
            // callee 寄存器不得被其它指令当数据消费(会读成 0.0)
            for (int pc = 0; pc < code.length; pc++) {
                IRInstruction inst = code[pc];
                if (inst.opcode == IROpCode.CALL && coveredCallPCs.contains(pc)) continue; // 自身消费 callee 合法
                switch (inst.opcode) {
                    case ADD, SUB, MUL, DIV, MOD, ADD_NUM, SUB_NUM, MUL_NUM, DIV_NUM, MOD_NUM,
                         EQ, NE, LT, GT, LE, GE, AND, OR -> {
                        if (calleeRegs.contains(inst.a) || calleeRegs.contains(inst.b)) return false;
                    }
                    case NEG, NOT, INC, DEC, MOVE -> { if (calleeRegs.contains(inst.a)) return false; }
                    case RETURN, JUMP_IF_TRUE, JUMP_IF_FALSE -> {
                        if (inst.dst >= 0 && calleeRegs.contains(inst.dst)) return false;
                    }
                    case CALL_STATIC -> {
                        for (int i = 0; i < inst.b; i++) if (calleeRegs.contains(inst.a + i)) return false;
                    }
                    default -> {}
                }
            }
        }
        // ---- fastVars 返回形状(基准=主循环：RETURN dst<0 / 隐式尾部 → NONE，fast 只能回 0.0) ----
        if (requireExplicitNumericReturn) {
            for (IRInstruction inst : code) {
                if (inst.opcode == IROpCode.RETURN && inst.dst < 0) return false;
            }
            if (code.length == 0 || code[code.length - 1].opcode != IROpCode.RETURN) return false;
            for (IRInstruction inst : code) {
                switch (inst.opcode) {
                    case JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE -> {
                        if (inst.a >= code.length) return false; // 跳到程序末尾 = 隐式 none 返回
                    }
                    default -> {}
                }
            }
        }
        return true;
    }

    private static boolean isFlagReg(boolean[] flag, int reg) {
        return reg >= 0 && reg < flag.length && flag[reg];
    }

    /**
     * A4(jit-1)：fastVars 入口守卫需检查的 var 键——「进入时的存量值可能被读到」的全部 var：
     * 只读、线性序上读先于写、或首个 STORE_VAR 可被前向跳越过(条件写)的键。
     * 首写必达且先于一切读的键无需守卫(其存量值不可能被消费)。
     */
    private int[] computeGuardKeyIndices(IRInstruction[] code) {
        Map<Integer, Integer> firstRead = new LinkedHashMap<>();
        Map<Integer, Integer> firstStore = new LinkedHashMap<>();
        List<int[]> jumps = new ArrayList<>();
        for (int pc = 0; pc < code.length; pc++) {
            IRInstruction inst = code[pc];
            switch (inst.opcode) {
                // VAR_INC/VAR_ADD_* 读改写——按读处理(存量值参与运算)
                case LOAD_VAR, VAR_INC, VAR_ADD_CONST, VAR_ADD_REG -> firstRead.putIfAbsent(inst.a, pc);
                case STORE_VAR -> firstStore.putIfAbsent(inst.a, pc);
                case JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE -> jumps.add(new int[]{pc, inst.a});
                default -> {}
            }
        }
        Set<Integer> all = new LinkedHashSet<>();
        all.addAll(firstRead.keySet());
        all.addAll(firstStore.keySet());
        List<Integer> guarded = new ArrayList<>();
        for (int key : all) {
            Integer r = firstRead.get(key), s = firstStore.get(key);
            boolean needGuard;
            if (s == null) {
                needGuard = true;                       // 只读
            } else if (r != null && r < s) {
                needGuard = true;                       // 读在写前
            } else {
                needGuard = false;                      // 写前置——但若首写可被前向跳越过则仍需守卫
                for (int[] j : jumps) {
                    if (j[0] < s && j[1] > s) { needGuard = true; break; }
                }
            }
            if (needGuard) guarded.add(key);
        }
        int[] out = new int[guarded.size()];
        for (int i = 0; i < out.length; i++) out[i] = guarded.get(i);
        return out;
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
            // A4(jit-1)：布尔/none/字符串常量一律非纯数值(LOAD_CONST 在 fast 路径会被强转 double)
            if (!(c instanceof NumberValue)) return false;
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

    /**
     * A4(jit-1)：fastVars 入口守卫发射——调用 rtFastVarsGuard 检查全部「存量值可能被读到」的 var
     * 当前是否为 NumberValue(long 版还要求有限整数)。任一不满足则 rtFastVarsGuard 已用完整解释器
     * 执行本次并返回结果(非 null)，此处直接 ARETURN；全部满足返回 null，落入 fast 路径。
     */
    private void emitFastVarsGuard(MethodVisitor mv, String className, int argCount, boolean longMode) {
        mv.visitFieldInsn(GETSTATIC, className, "PROGRAM", IRPROGRAM_DESC);
        mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
        mv.visitFieldInsn(GETSTATIC, className, "GUARD_KEYS", "[I");
        mv.visitVarInsn(ALOAD, argCount); // ctx 在参数列表最后(fastVars argCount==0 → slot 0)
        mv.visitInsn(longMode ? ICONST_1 : ICONST_0);
        mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                "rtFastVarsGuard",
                "(" + IRPROGRAM_DESC + "[" + VKEY_DESC + "[I" + CONTEXT_DESC + "Z)" + IVALUE_DESC, false);
        mv.visitInsn(DUP);
        Label proceed = new Label();
        mv.visitJumpInsn(IFNULL, proceed);
        mv.visitInsn(ARETURN);
        mv.visitLabel(proceed);
        mv.visitInsn(POP);
    }

    /**
     * A4(controlflow-15)：回边中断检查发射——计数器每 1024 次调 rtPollInterrupt(与解释器
     * 回跳检查节奏一致)，被中断抛「脚本被中断」。counterSlot 为 int 局部变量槽。
     */
    private void emitInterruptCheck(MethodVisitor mv, int counterSlot) {
        mv.visitIincInsn(counterSlot, 1);
        mv.visitVarInsn(ILOAD, counterSlot);
        mv.visitIntInsn(SIPUSH, 1023);
        mv.visitInsn(IAND);
        Label skip = new Label();
        mv.visitJumpInsn(IFNE, skip);
        mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                "rtPollInterrupt", "()V", false);
        mv.visitLabel(skip);
    }


    /**
     * 生成纯数值 callFast 体（自递归与互递归统一）。
     * callTargets：pc → 被调成员在组内的 index；单函数路径即 {selfPC → 0}。
     * calleeMethodNames[i] / calleeArgCounts[i]：组内成员 i 的方法名（"callFast" 或 "callFast_i"）与参数个数。
     * 组内 CALL / CALL_STATIC 发射 INVOKESTATIC className.<callFast_i>，描述符严格按被调成员自己的 argCount。
     */
    private void emitFastDoubleBytecode(MethodVisitor mv, IRInstruction[] code, IValue<?>[] constants,
                                        int argCount, int regCount, boolean[] usedRegs,
                                        Map<Integer, Integer> callTargets, String[] calleeMethodNames,
                                        int[] calleeArgCounts, String className) {
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
        // A4(controlflow-15)：回边中断检查计数器
        int interruptSlot = nextLocal;

        // 标签
        Label[] labels = new Label[code.length + 1];
        for (int i = 0; i <= code.length; i++) labels[i] = new Label();

        for (int i = 0; i < regCount; i++) {
            if (usedRegs[i] && fastRegToLocal[i] >= 0) {
                mv.visitInsn(DCONST_0);
                mv.visitVarInsn(DSTORE, fastRegToLocal[i]);
            }
        }
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ISTORE, interruptSlot);

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
                case LOAD_VAR -> {
                    // 仅作为组内直跳 CALL 的 callee 引用(isFastPlanSafe 已校验)，数值上无意义 → 0
                    mv.visitInsn(DCONST_0);
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
                    // 窥孔：SUB→CALL 融合（仅 CALL 形式；CALL_STATIC 形式由非融合路径处理以确保正确性）。
                    // SUB 结果即被调的第 0 个参数，已在栈上；其余补 0 至被调成员自己的 argCount。
                    boolean fusedSub = false;
                    if (pc + 1 < code.length) {
                        IRInstruction nx = code[pc + 1];
                        Integer tgt = callTargets.get(pc + 1);
                        if (nx.opcode == IROpCode.CALL && tgt != null
                                && nx.b == 1 && nx.c == inst.dst && calleeArgCounts[tgt] >= 1) {
                            int calleeArgC = calleeArgCounts[tgt];
                            for (int i = 1; i < calleeArgC; i++) mv.visitInsn(DCONST_0);
                            mv.visitMethodInsn(INVOKESTATIC, className, calleeMethodNames[tgt],
                                    buildFastDescriptor(calleeArgC), false);
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
                    // A4(jit-6/operators-12)：除零守护 → 0(与解释器/IData.div 一致)
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    emitFastDoubleDivMod(mv, DDIV);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case MOD, MOD_NUM -> {
                    // A4(jit-6/operators-12)：模零守护 → 0
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    emitFastDoubleDivMod(mv, DREM);
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
                     JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE -> {
                    pc = emitDoubleCmpAndControl(mv, code, pc, inst, fastRegToLocal, labels, interruptSlot);
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
                    Integer tgt = callTargets.get(pc);
                    if (tgt == null) {
                        // isFastPlanSafe 已保证全部 CALL 被覆盖——到达即选路 bug，编译失败回落解释器
                        throw new IllegalStateException("fastDouble: uncovered CALL at pc=" + pc);
                    }
                    // CALL（dot 形式 var.fX(..)）：a=calleeReg, b=argCount, c=argBase
                    emitFastGroupCall(mv, fastRegToLocal, inst.c, inst.b, tgt,
                            calleeMethodNames, calleeArgCounts, className);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case CALL_STATIC -> {
                    Integer tgt = callTargets.get(pc);
                    if (tgt != null) {
                        // CALL_STATIC（裸名 fX(..) 或自递归）：a=argBase, b=argCount
                        emitFastGroupCall(mv, fastRegToLocal, inst.a, inst.b, tgt,
                                calleeMethodNames, calleeArgCounts, className);
                        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                    } else {
                        emitFastStaticCall(mv, fastRegToLocal, inst);
                    }
                }

                case MOVE -> {
                    if (fastRegToLocal[inst.a] != fastRegToLocal[inst.dst]) {
                        mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                    }
                }
                // PUSH/POP_SCOPE：fast 数值路径不含任何 scope 变量操作(LOAD/STORE_SCOPE 不在准入集)，
                // 配平交由执行边界兜底(Interpreter.executeGuarded, R8)；AUTO_INVOKE：double 恒不可调用。
                case PUSH_SCOPE, POP_SCOPE, AUTO_INVOKE, NOP -> {}
                // A4(jit-2)：未覆盖 opcode 一律编译失败(回落解释器)，绝不静默 no-op
                default -> throw new IllegalStateException(
                        "fastDouble: unsupported opcode " + inst.opcode + " at pc=" + pc);
            }
        }

        mv.visitLabel(labels[code.length]);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DRETURN);
    }

    /** 栈上 [.., a, b] → [.., a op b]，b==0 时结果 0(除零/模零守护,对齐 IData.div/mod)。 */
    private void emitFastDoubleDivMod(MethodVisitor mv, int op) {
        Label nonZero = new Label();
        Label end = new Label();
        mv.visitInsn(DUP2);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DCMPL);
        mv.visitJumpInsn(IFNE, nonZero);
        mv.visitInsn(POP2);
        mv.visitInsn(POP2);
        mv.visitInsn(DCONST_0);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(nonZero);
        mv.visitInsn(op);
        mv.visitLabel(end);
    }

    /**
     * 发射组内（含自递归）直跳调用：从 argBase 起按被调成员自己的 argCount 压入 double 参数（不足补 0、
     * 多余截断），再 INVOKESTATIC className.<callFast_calleeIdx>。结果 double 留在栈顶由调用方 DSTORE。
     * 描述符严格按被调成员 argCount，否则 VerifyError。
     */
    private void emitFastGroupCall(MethodVisitor mv, int[] fastRegToLocal, int argBase, int callArgCount,
                                   int calleeIdx, String[] calleeMethodNames, int[] calleeArgCounts,
                                   String className) {
        int calleeArgC = calleeArgCounts[calleeIdx];
        for (int i = 0; i < calleeArgC; i++) {
            if (i < callArgCount) {
                int r = argBase + i;
                if (r >= 0 && r < fastRegToLocal.length && fastRegToLocal[r] >= 0) {
                    mv.visitVarInsn(DLOAD, fastRegToLocal[r]);
                } else {
                    mv.visitInsn(DCONST_0);
                }
            } else {
                mv.visitInsn(DCONST_0);
            }
        }
        mv.visitMethodInsn(INVOKESTATIC, className, calleeMethodNames[calleeIdx],
                buildFastDescriptor(calleeArgC), false);
    }


    private int emitDoubleCmpAndControl(MethodVisitor mv, IRInstruction[] code, int pc,
                                        IRInstruction inst, int[] fastRegToLocal, Label[] labels,
                                        int interruptSlot) {
        switch (inst.opcode) {
            case LE, LT, GT, GE -> {
                IRInstruction next = (pc + 1 < code.length) ? code[pc + 1] : null;
                if (next != null && (next.opcode == IROpCode.JUMP_IF_TRUE || next.opcode == IROpCode.JUMP_IF_FALSE) && next.dst == inst.dst) {
                    if (next.a <= pc) emitInterruptCheck(mv, interruptSlot); // A4：回边中断检查
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
                    if (next.a <= pc) emitInterruptCheck(mv, interruptSlot);
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
                    if (next.a <= pc) emitInterruptCheck(mv, interruptSlot);
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
                // A4(jit-5)：真值 = value > 0(Shimmer)——!v 为 1 当且仅当 v<=0(含 NaN)
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                Label trueL = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFLE, trueL);
                mv.visitInsn(DCONST_0);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(trueL);
                mv.visitInsn(DCONST_1);
                mv.visitLabel(endL);
                mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
            }
            case AND -> {
                // A4(jit-5)：左真值(>0)选右，否则选左(值语义与解释器 AND 一致，真值 >0)
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                Label useRight = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFGT, useRight);
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(useRight);
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                mv.visitLabel(endL);
                mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
            }
            case OR -> {
                // A4(jit-5)：左真值(>0)选左，否则选右
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                Label useRight = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFLE, useRight);
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(useRight);
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                mv.visitLabel(endL);
                mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
            }
            case JUMP -> {
                if (inst.a <= pc) emitInterruptCheck(mv, interruptSlot); // A4：回边中断检查
                mv.visitJumpInsn(GOTO, labels[inst.a]);
            }
            case JUMP_IF_TRUE -> {
                // Shimmer 对齐：真值 = value > 0（DCMPL 结果 -1/0/1，IFGT 仅在 value>0 跳转；NaN->-1 视为假）
                if (inst.a <= pc) emitInterruptCheck(mv, interruptSlot);
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.dst]);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                mv.visitJumpInsn(IFGT, labels[inst.a]);
            }
            case JUMP_IF_FALSE -> {
                // Shimmer 对齐：假值 = value <= 0（含 NaN，DCMPL->-1，IFLE 跳转）
                if (inst.a <= pc) emitInterruptCheck(mv, interruptSlot);
                mv.visitVarInsn(DLOAD, fastRegToLocal[inst.dst]);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                mv.visitJumpInsn(IFLE, labels[inst.a]);
            }
            // A4(jit-4)：JUMP_IF_NONE 已被 isNumericOnly 排除(?? 在 double 模型不可表示)——到达即 bug
            default -> throw new IllegalStateException(
                    "fastDouble control: unsupported opcode " + inst.opcode);
        }
        return pc;
    }



    private void emitFastDoubleVarBytecode(MethodVisitor mv, IRInstruction[] code, IValue<?>[] constants,
                                           VariableKey[] keys, int argCount, int regCount,
                                           boolean[] usedRegs, String className, String fastVarDesc) {
        // 局部变量布局: [ctx(1 slot), reg0(2), reg1(2), ..., var0(2), var1(2), ..., interrupt(1)]
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

        // 收集所有 var 变量的 key 索引；A4(jit-1)：回写只覆盖被写过(STORE_VAR/VAR_*)的 var
        Set<Integer> varKeyIndices = new LinkedHashSet<>();
        Set<Integer> writtenKeyIndices = new LinkedHashSet<>();
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case LOAD_VAR -> varKeyIndices.add(inst.a);
                case STORE_VAR, VAR_INC, VAR_ADD_CONST, VAR_ADD_REG -> {
                    varKeyIndices.add(inst.a);
                    writtenKeyIndices.add(inst.a);
                }
                default -> {}
            }
        }

        // 为每个 var 变量分配 double 局部变量 slot
        Map<Integer, Integer> varDoubleSlots = new LinkedHashMap<>();
        for (int keyIdx : varKeyIndices) {
            varDoubleSlots.put(keyIdx, nextLocal);
            nextLocal += 2; // double 占 2 slots
        }
        // A4(controlflow-15)：回边中断检查计数器
        int interruptSlot = nextLocal;

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
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ISTORE, interruptSlot);

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
                    // var[a] += 1（Shimmer 对齐 R1：新值同时写 dst 寄存器——赋值语句值=新值）
                    Integer varSlot = varDoubleSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(DLOAD, varSlot);
                        mv.visitInsn(DCONST_1);
                        mv.visitInsn(DADD);
                        emitFastVarStoreWithDst(mv, varSlot, fastRegToLocal, inst.dst, false);
                    }
                }
                case VAR_ADD_CONST -> {
                    // var[a] += constants[b]（R1：新值同时写 dst）
                    Integer varSlot = varDoubleSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(DLOAD, varSlot);
                        IValue<?> c = constants[inst.b];
                        double d = (c instanceof NumberValue nv) ? nv.value : c.numberValue();
                        emitDoubleConst(mv, d);
                        mv.visitInsn(DADD);
                        emitFastVarStoreWithDst(mv, varSlot, fastRegToLocal, inst.dst, false);
                    }
                }
                case VAR_ADD_REG -> {
                    // var[a] += reg[b]（R1：新值同时写 dst）
                    Integer varSlot = varDoubleSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(DLOAD, varSlot);
                        mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                        mv.visitInsn(DADD);
                        emitFastVarStoreWithDst(mv, varSlot, fastRegToLocal, inst.dst, false);
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
                    // A4(jit-6/operators-12)：除零守护 → 0
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    emitFastDoubleDivMod(mv, DDIV);
                    mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                }
                case MOD, MOD_NUM -> {
                    // A4(jit-6/operators-12)：模零守护 → 0
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(DLOAD, fastRegToLocal[inst.b]);
                    emitFastDoubleDivMod(mv, DREM);
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
                     JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE -> {
                    pc = emitDoubleCmpAndControl(mv, code, pc, inst, fastRegToLocal, labels, interruptSlot);
                }

                case RETURN -> {
                    // 写回 var 变量到 Context(只写被 STORE 过的,jit-1)
                    emitVarWriteBack(mv, varDoubleSlots, ctxSlot, className, writtenKeyIndices);
                    if (inst.dst >= 0 && fastRegToLocal[inst.dst] >= 0) {
                        mv.visitVarInsn(DLOAD, fastRegToLocal[inst.dst]);
                    } else {
                        mv.visitInsn(DCONST_0);
                    }
                    mv.visitInsn(DRETURN);
                }

                case CALL_STATIC -> emitFastStaticCall(mv, fastRegToLocal, inst);

                case MOVE -> {
                    if (fastRegToLocal[inst.a] != fastRegToLocal[inst.dst]) {
                        mv.visitVarInsn(DLOAD, fastRegToLocal[inst.a]);
                        mv.visitVarInsn(DSTORE, fastRegToLocal[inst.dst]);
                    }
                }
                // PUSH/POP_SCOPE：fast 数值路径不含任何 scope 变量操作(LOAD/STORE_SCOPE 不在准入集)，
                // 配平交由执行边界兜底(Interpreter.executeGuarded, R8)；AUTO_INVOKE：double 恒不可调用。
                case PUSH_SCOPE, POP_SCOPE, AUTO_INVOKE, NOP -> {}
                // A4(jit-2)：未覆盖 opcode 一律编译失败(回落解释器)，绝不静默 no-op
                default -> throw new IllegalStateException(
                        "fastDoubleVars: unsupported opcode " + inst.opcode + " at pc=" + pc);
            }
        }

        // 尾部安全返回（写回 var 变量）——isFastPlanSafe 已保证尾部不可达(末指令必为 RETURN 且无跳转到末尾)
        mv.visitLabel(labels[code.length]);
        emitVarWriteBack(mv, varDoubleSlots, ctxSlot, className, writtenKeyIndices);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DRETURN);
    }

    /**
     * Shimmer 对齐(R1)：fast 数值路径 VAR_INC/VAR_ADD_CONST/VAR_ADD_REG 的存储收尾——
     * 栈顶新值先(必要时 DUP2)写回 var 槽，再写 dst 寄存器槽(赋值语句值=新值，尾语句隐式返回可见)。
     * long 模式传 {@code longMode=true}(LSTORE)；dst 未映射时仅写 var 槽。
     */
    private void emitFastVarStoreWithDst(MethodVisitor mv, int varSlot, int[] fastRegToLocal,
                                         int dst, boolean longMode) {
        int store = longMode ? LSTORE : DSTORE;
        int dstSlot = (dst >= 0 && dst < fastRegToLocal.length) ? fastRegToLocal[dst] : -1;
        if (dstSlot >= 0 && dstSlot != varSlot) {
            mv.visitInsn(DUP2); // double/long 均为 category-2
            mv.visitVarInsn(store, varSlot);
            mv.visitVarInsn(store, dstSlot);
        } else {
            mv.visitVarInsn(store, varSlot);
        }
    }

    private void emitVarWriteBack(MethodVisitor mv, Map<Integer, Integer> varDoubleSlots,
                                  int ctxSlot, String className, Set<Integer> writtenKeyIndices) {
        for (var entry : varDoubleSlots.entrySet()) {
            int keyIdx = entry.getKey();
            int varSlot = entry.getValue();
            if (!writtenKeyIndices.contains(keyIdx)) continue; // A4(jit-1)：只读 var 不回写
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
                                      int ctxSlot, String className, Set<Integer> writtenKeyIndices) {
        for (var entry : varLongSlots.entrySet()) {
            int keyIdx = entry.getKey();
            int varSlot = entry.getValue();
            if (!writtenKeyIndices.contains(keyIdx)) continue; // A4(jit-1)：只读 var 不回写
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
                                      IRInstruction inst, int[] fastRegToLocal, Label[] labels,
                                      int interruptSlot) {
        switch (inst.opcode) {
            case LE, LT, GT, GE -> {
                IRInstruction next = (pc + 1 < code.length) ? code[pc + 1] : null;
                if (next != null && (next.opcode == IROpCode.JUMP_IF_TRUE || next.opcode == IROpCode.JUMP_IF_FALSE) && next.dst == inst.dst) {
                    if (next.a <= pc) emitInterruptCheck(mv, interruptSlot); // A4：回边中断检查
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
                    if (next.a <= pc) emitInterruptCheck(mv, interruptSlot);
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
                    if (next.a <= pc) emitInterruptCheck(mv, interruptSlot);
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
                // A4(jit-5)：真值 = value > 0——!v 为 1 当且仅当 v<=0
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LCMP);
                Label trueL = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFLE, trueL);
                mv.visitInsn(LCONST_0);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(trueL);
                mv.visitInsn(LCONST_1);
                mv.visitLabel(endL);
                mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
            }
            case AND -> {
                // A4(jit-5)：左真值(>0)选右，否则选左
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LCMP);
                Label useRight = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFGT, useRight);
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(useRight);
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                mv.visitLabel(endL);
                mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
            }
            case OR -> {
                // A4(jit-5)：左真值(>0)选左，否则选右
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LCMP);
                Label useRight = new Label();
                Label endL = new Label();
                mv.visitJumpInsn(IFLE, useRight);
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                mv.visitJumpInsn(GOTO, endL);
                mv.visitLabel(useRight);
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                mv.visitLabel(endL);
                mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
            }
            case JUMP -> {
                if (inst.a <= pc) emitInterruptCheck(mv, interruptSlot); // A4：回边中断检查
                mv.visitJumpInsn(GOTO, labels[inst.a]);
            }
            case JUMP_IF_TRUE -> {
                // Shimmer 对齐：真值 = value > 0（LCMP 结果 -1/0/1，IFGT 仅在 value>0 跳转）
                if (inst.a <= pc) emitInterruptCheck(mv, interruptSlot);
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.dst]);
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LCMP);
                mv.visitJumpInsn(IFGT, labels[inst.a]);
            }
            case JUMP_IF_FALSE -> {
                // Shimmer 对齐：假值 = value <= 0
                if (inst.a <= pc) emitInterruptCheck(mv, interruptSlot);
                mv.visitVarInsn(LLOAD, fastRegToLocal[inst.dst]);
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LCMP);
                mv.visitJumpInsn(IFLE, labels[inst.a]);
            }
            // A4(jit-4)：JUMP_IF_NONE 已被 isNumericOnly 排除——到达即 bug
            default -> throw new IllegalStateException(
                    "fastLong control: unsupported opcode " + inst.opcode);
        }
        return pc;
    }



    private void emitFastLongVarBytecode(MethodVisitor mv, IRInstruction[] code, IValue<?>[] constants,
                                         VariableKey[] keys, int argCount, int regCount,
                                         boolean[] usedRegs, String className, String fastVarDesc) {
        // 局部变量布局: [ctx(1 slot), reg0(2), reg1(2), ..., var0(2), var1(2), ..., interrupt(1)]
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

        // 收集所有 var 变量的 key 索引；A4(jit-1)：回写只覆盖被写过的 var
        Set<Integer> varKeyIndices = new LinkedHashSet<>();
        Set<Integer> writtenKeyIndices = new LinkedHashSet<>();
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case LOAD_VAR -> varKeyIndices.add(inst.a);
                case STORE_VAR, VAR_INC, VAR_ADD_CONST, VAR_ADD_REG -> {
                    varKeyIndices.add(inst.a);
                    writtenKeyIndices.add(inst.a);
                }
                default -> {}
            }
        }

        // 为每个 var 变量分配 long 局部变量 slot
        Map<Integer, Integer> varLongSlots = new LinkedHashMap<>();
        for (int keyIdx : varKeyIndices) {
            varLongSlots.put(keyIdx, nextLocal);
            nextLocal += 2; // long 占 2 slots
        }
        // A4(controlflow-15)：回边中断检查计数器
        int interruptSlot = nextLocal;

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
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ISTORE, interruptSlot);

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
                    // var[a] += 1L（Shimmer 对齐 R1：新值同时写 dst 寄存器）
                    Integer varSlot = varLongSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(LLOAD, varSlot);
                        mv.visitInsn(LCONST_1);
                        mv.visitInsn(LADD);
                        emitFastVarStoreWithDst(mv, varSlot, fastRegToLocal, inst.dst, true);
                    }
                }
                case VAR_ADD_CONST -> {
                    // var[a] += (long) constants[b]（R1：新值同时写 dst）
                    Integer varSlot = varLongSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(LLOAD, varSlot);
                        IValue<?> c = constants[inst.b];
                        double d = (c instanceof NumberValue nv) ? nv.value : c.numberValue();
                        emitLongConst(mv, (long) d);
                        mv.visitInsn(LADD);
                        emitFastVarStoreWithDst(mv, varSlot, fastRegToLocal, inst.dst, true);
                    }
                }
                case VAR_ADD_REG -> {
                    // var[a] += reg[b]（R1：新值同时写 dst）
                    Integer varSlot = varLongSlots.get(inst.a);
                    if (varSlot != null) {
                        mv.visitVarInsn(LLOAD, varSlot);
                        mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                        mv.visitInsn(LADD);
                        emitFastVarStoreWithDst(mv, varSlot, fastRegToLocal, inst.dst, true);
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
                    // A4(jit-6)：LREM 模零守护 → 0(原直接 LREM 抛 ArithmeticException 触发隐藏 deopt)
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                    mv.visitVarInsn(LLOAD, fastRegToLocal[inst.b]);
                    Label nonZero = new Label();
                    Label endMod = new Label();
                    mv.visitInsn(DUP2);
                    mv.visitInsn(LCONST_0);
                    mv.visitInsn(LCMP);
                    mv.visitJumpInsn(IFNE, nonZero);
                    mv.visitInsn(POP2);
                    mv.visitInsn(POP2);
                    mv.visitInsn(LCONST_0);
                    mv.visitJumpInsn(GOTO, endMod);
                    mv.visitLabel(nonZero);
                    mv.visitInsn(LREM);
                    mv.visitLabel(endMod);
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
                     JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE -> {
                    pc = emitLongCmpAndControl(mv, code, pc, inst, fastRegToLocal, labels, interruptSlot);
                }

                case RETURN -> {
                    // 写回 var 变量到 Context(只写被 STORE 过的,jit-1)
                    emitLongVarWriteBack(mv, varLongSlots, ctxSlot, className, writtenKeyIndices);
                    if (inst.dst >= 0 && fastRegToLocal[inst.dst] >= 0) {
                        mv.visitVarInsn(LLOAD, fastRegToLocal[inst.dst]);
                    } else {
                        mv.visitInsn(LCONST_0);
                    }
                    mv.visitInsn(L2D);
                    mv.visitInsn(DRETURN);
                }

                case CALL_STATIC -> emitFastLongStaticCall(mv, fastRegToLocal, inst);

                case MOVE -> {
                    if (fastRegToLocal[inst.a] != fastRegToLocal[inst.dst]) {
                        mv.visitVarInsn(LLOAD, fastRegToLocal[inst.a]);
                        mv.visitVarInsn(LSTORE, fastRegToLocal[inst.dst]);
                    }
                }
                // PUSH/POP_SCOPE：fast 数值路径不含任何 scope 变量操作(LOAD/STORE_SCOPE 不在准入集)，
                // 配平交由执行边界兜底(Interpreter.executeGuarded, R8)；AUTO_INVOKE：double 恒不可调用。
                case PUSH_SCOPE, POP_SCOPE, AUTO_INVOKE, NOP -> {}
                // A4(jit-2)：未覆盖 opcode 一律编译失败(回落解释器)，绝不静默 no-op
                default -> throw new IllegalStateException(
                        "fastLongVars: unsupported opcode " + inst.opcode + " at pc=" + pc);
            }
        }

        // 尾部安全返回（写回 var 变量）——isFastPlanSafe 已保证尾部不可达
        mv.visitLabel(labels[code.length]);
        emitLongVarWriteBack(mv, varLongSlots, ctxSlot, className, writtenKeyIndices);
        mv.visitInsn(LCONST_0);
        mv.visitInsn(L2D);
        mv.visitInsn(DRETURN);
    }

    private void emitFastLongStaticCall(MethodVisitor mv, int[] fastRegToLocal, IRInstruction inst) {
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
        // A4(jit-3)：isFastPlanSafe 已保证到达此处的 CALL_STATIC 均可内联——兜底 0 已删除，
        // 到达即选路 bug，编译失败回落解释器。
        throw new IllegalStateException("fastLong: uninlineable CALL_STATIC " + fn);
    }

    private void emitFastStaticCall(MethodVisitor mv, int[] fastRegToLocal, IRInstruction inst) {
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
        // A4(jit-3)：isFastPlanSafe 已保证到达此处的 CALL_STATIC 均可内联——兜底 0 已删除，
        // 到达即选路 bug，编译失败回落解释器。
        throw new IllegalStateException("fastDouble: uninlineable CALL_STATIC " + fn);
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

    private void emitBytecode(MethodVisitor mv, IRInstruction[] code, IValue<?>[] constants,
                              int argCount, int ctxLocal, int[] regToLocal, int regCount,
                              boolean[] usedRegs, Set<Integer> selfRecursivePCs,
                              String className, String callDesc,
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

        // A4：自递归 INVOKESTATIC 直跳只在函数体不含 scope 操作时安全——INVOKESTATIC 复用调用方
        // Context/ScopeStack，而解释器 FunctionCallable 每次 createCallContext(干净 ScopeStack)。
        // 含 LOAD_SCOPE/STORE_SCOPE/DECLARE_SCOPE 的递归函数改走 emitGenericCall(rtCall →
        // FunctionCallable 完整语义)。PUSH/POP_SCOPE 自身配平,不影响。
        boolean scopeFree = true;
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case LOAD_SCOPE, STORE_SCOPE, DECLARE_SCOPE -> scopeFree = false;
                default -> {}
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
        // A4(controlflow-15)：回边中断检查计数器
        int interruptSlot = nextVarRefSlot++;
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ISTORE, interruptSlot);

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
                    // A4(jit-20)：经 rtStoreVar——NumberValue 复制后存(与解释器主循环 STORE_VAR 一致)
                    Integer vrSlot = varRefSlots.get(inst.a);
                    if (vrSlot != null) {
                        mv.visitVarInsn(ALOAD, vrSlot);
                        mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                        mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                                "rtStoreVar", "(" + VREF_DESC + IVALUE_DESC + ")V", false);
                    }
                }
                case LOAD_SCOPE -> {
                    // rtLoadScope(ctx, KEYS[inst.a])：完整解析 scope→var→val→创建,与解释器 LOAD_SCOPE 一致。
                    // 此前只 getScopeStack().get() 仅查 scope,JIT 后的闭包读外层 var 取不到(返回 none)。
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
                    emitIntConst(mv, inst.a);
                    mv.visitInsn(AALOAD);
                    mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                            "rtLoadScope", "(" + CONTEXT_DESC + VKEY_DESC + ")" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case STORE_SCOPE -> {
                    // rtStoreScope(ctx, KEYS[inst.a], reg[inst.dst])：写已存在 scope/var 绑定或创建 scope,
                    // 与解释器(LOAD_SCOPE 缓存 var ref 后 STORE_SCOPE 复用)一致,使闭包对捕获 var 的复合赋值生效。
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitFieldInsn(GETSTATIC, className, "KEYS", "[" + VKEY_DESC);
                    emitIntConst(mv, inst.a);
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                    mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                            "rtStoreScope", "(" + CONTEXT_DESC + VKEY_DESC + IVALUE_DESC + ")V", false);
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
                    // A4(jit-18)：SmallMapValue 内联快路径删除(JIT 不再产 SmallMapValue)——
                    // 三态 c(0=普通/1=for-in/2=args) 一律走 rtGetIndex 完整语义(与解释器逐分支一致)。
                    if (inst.b == -1) {
                        // 空索引 — 返回对象本身(与解释器 GET_INDEX inst.b==-1 分支一致)
                        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    } else {
                        mv.visitVarInsn(ALOAD, regToLocal[inst.a]); // obj
                        mv.visitVarInsn(ALOAD, regToLocal[inst.b]); // idx
                        mv.visitVarInsn(ALOAD, ctxLocal);           // ctx
                        emitIntConst(mv, inst.c);                   // forIn 三态
                        mv.visitMethodInsn(INVOKESTATIC,
                                "priv/seventeen/artist/aria/jit/JITCompiler", "rtGetIndex",
                                "(" + IVALUE_DESC + IVALUE_DESC + CONTEXT_DESC + "I)" + IVALUE_DESC, false);
                        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                    }
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
                case SET_PROP -> {
                    // SET_PROP: dst=objReg, a=valueReg, name=propName
                    // objReg = rtSetProp(obj, propName, val, ctx)（写回以兼容 SmallMapValue 扩容）
                    mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                    mv.visitLdcInsn(inst.name == null ? "" : inst.name);
                    mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKESTATIC,
                            "priv/seventeen/artist/aria/jit/JITCompiler", "rtSetProp",
                            "(" + IVALUE_DESC + "Ljava/lang/String;" + IVALUE_DESC + CONTEXT_DESC + ")" + IVALUE_DESC, false);
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
                // FOR_RANGE_INIT/NEXT/BREAK：Compiler 当前不 emit；canCompile 也已不放行。
                // 若被 emit 触发说明 Compiler 新增了 emit 但未实现 JIT 路径——失败比静默生成
                // 空循环体安全（早期版本曾留 -> {} 空 case，会导致循环被 JIT 跳过）。
                case FOR_RANGE_INIT, FOR_RANGE_NEXT, BREAK ->
                        throw new IllegalStateException(
                                "JIT emit not implemented for opcode " + inst.opcode
                                        + " — canCompile() should have rejected this program");
                case VAR_INC -> {
                    // A4(jit-9)：经 rtVarInc——非数字走通用加法值模型(none+1=1、"a"+1="a1.0")，
                    // 与解释器 VAR_INC 一致；原 CHECKCAST NumberValue 抛 CCE。
                    // Shimmer 对齐(R1)：rt 助手返回新值 → 写 dst 寄存器(赋值语句值=新值)。
                    Integer vrSlot = varRefSlots.get(inst.a);
                    if (vrSlot != null) {
                        mv.visitVarInsn(ALOAD, vrSlot);
                        mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                                "rtVarInc", "(" + VREF_DESC + ")" + IVALUE_DESC, false);
                        emitStoreOrPop(mv, regToLocal, inst.dst);
                    }
                }
                case VAR_ADD_CONST -> {
                    // A4(jit-9)：经 rtVarAddConst(与解释器 VAR_ADD_CONST 一致，CCE 消灭)；R1：结果写 dst。
                    Integer vrSlot = varRefSlots.get(inst.a);
                    if (vrSlot != null) {
                        mv.visitVarInsn(ALOAD, vrSlot);
                        mv.visitFieldInsn(GETSTATIC, className, "CONSTANTS", "[" + IVALUE_DESC);
                        emitIntConst(mv, inst.b);
                        mv.visitInsn(AALOAD);
                        mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                                "rtVarAddConst", "(" + VREF_DESC + IVALUE_DESC + ")" + IVALUE_DESC, false);
                        emitStoreOrPop(mv, regToLocal, inst.dst);
                    }
                }
                case VAR_ADD_REG -> {
                    Integer vrSlot = varRefSlots.get(inst.a);
                    if (vrSlot != null) {
                        // 通用路径：支持 NumberValue 和字符串；R1：结果写 dst。
                        mv.visitVarInsn(ALOAD, vrSlot); // VariableReference
                        mv.visitVarInsn(ALOAD, regToLocal[inst.b]); // val
                        mv.visitMethodInsn(INVOKESTATIC,
                                "priv/seventeen/artist/aria/jit/JITCompiler",
                                "rtVarAddReg", "(" + VREF_DESC + IVALUE_DESC + ")" + IVALUE_DESC, false);
                        emitStoreOrPop(mv, regToLocal, inst.dst);
                    }
                }
                case AUTO_INVOKE -> {
                    // Shimmer 对齐(R2 系)：r[dst] 可调用 → 零参调用取结果(rtAutoInvoke)
                    mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                            "rtAutoInvoke", "(" + IVALUE_DESC + CONTEXT_DESC + ")" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
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
                    // Shimmer 对齐(gui-chain-10):普通构造器 new StringValue(str),canBeNumber 重算
                    // (插值 "42" 参与 +/== 走数值,与解释器 CONCAT 一致;concat 专用双参 ctor 已删除)。
                    mv.visitTypeInsn(NEW, STRVAL);
                    mv.visitInsn(DUP_X1);
                    mv.visitInsn(SWAP);
                    mv.visitMethodInsn(INVOKESPECIAL, STRVAL, "<init>", "(Ljava/lang/String;)V", false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case CALL_CONSTRUCTOR -> {
                    // A4(第13项复核)：经 rtCallConstructor 复用解释器 constructByName 完整链
                    // (脚本类→JavaClassMirror→Interpreter.CONSTRUCTORS→CallableManager,查不到→NONE)。
                    // 原直查 CallableManager.getConstructor：漏脚本类/Interpreter 构造器表，且 null 时 NPE。
                    mv.visitLdcInsn(inst.name == null ? "" : inst.name);
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
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/jit/JITCompiler",
                            "rtCallConstructor",
                            "(Ljava/lang/String;[" + IVALUE_DESC + CONTEXT_DESC + ")" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case CALL_METHOD -> {
                    // A4(jit-14/17)：list.add/list.size 内联快路径删除——内联会绕过注册表
                    // (宿主可重注册 add/size)且返回值形状由内联硬编码；一律 rtCallMethod
                    // (后备链与解释器 CALL_METHOD 完全一致)。
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
                    // A4(jit-18/operators-13/builtins-object-6)：SmallMapValue 内联构造删除——
                    // 一律 rtNewMap 产 MapValue(LinkedHashMap)，与解释器 NEW_MAP 完全一致
                    // (方法注册/for-in/MapBridge instanceof/typeID 全部对齐)。
                    int nmCount = inst.b;
                    int nmBase = inst.a;
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
                            "rtSetIndex", "(" + IVALUE_DESC + IVALUE_DESC + IVALUE_DESC + ")" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]); // 写回(SmallMapValue 扩容会换实例)
                }
                case NEW_FUNCTION -> {
                    // new FunctionValue(new FunctionCallable(SUB_PROGRAMS[inst.a], ctx.snapshotForClosure()))
                    // A4(jit-19)：闭包捕获定义时上下文快照(与解释器 NEW_FUNCTION 的 snapshotForClosure 一致)，
                    // 原直传运行时 ctx——闭包逃逸后捕获变量解析漂移。
                    mv.visitTypeInsn(NEW, FUNCTION_VALUE);
                    mv.visitInsn(DUP);
                    mv.visitTypeInsn(NEW, FUNCTION_CALLABLE);
                    mv.visitInsn(DUP);
                    mv.visitFieldInsn(GETSTATIC, className, "SUB_PROGRAMS", "[" + IRPROGRAM_DESC);
                    emitIntConst(mv, inst.a);
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, ctxLocal);
                    mv.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "snapshotForClosure",
                            "()" + CONTEXT_DESC, false);
                    mv.visitMethodInsn(INVOKESPECIAL, FUNCTION_CALLABLE, "<init>",
                            "(" + IRPROGRAM_DESC + CONTEXT_DESC + ")V", false);
                    mv.visitMethodInsn(INVOKESPECIAL, FUNCTION_VALUE, "<init>",
                            "(" + ICALLABLE_DESC + ")V", false);
                    // 互递归组编译用：设置 sourceProgram，否则 JIT 编译后的主程序创建的 fa/fb 没有 sourceProgram，
                    // 组入口守卫 JitGroup.stillBound 会误判重绑 → 整组永久去优化（与解释器 NEW_FUNCTION 行为对齐）。
                    mv.visitInsn(DUP);
                    mv.visitFieldInsn(GETSTATIC, className, "SUB_PROGRAMS", "[" + IRPROGRAM_DESC);
                    emitIntConst(mv, inst.a);
                    mv.visitInsn(AALOAD);
                    mv.visitMethodInsn(INVOKEVIRTUAL, FUNCTION_VALUE, "setSourceProgram",
                            "(" + IRPROGRAM_DESC + ")V", false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }

                // A4(jit-9)：numericOnly 的 CHECKCAST 特化删除——ADD/SUB/MUL 一律 emitBinaryArith
                // (instanceof 双守卫,失败走 IData 值模型)，DIV/MOD 一律 emitBinaryDiv/Mod(含除零守护)。
                case ADD -> emitBinaryArith(mv, regToLocal, inst, DADD);
                case SUB -> emitBinaryArith(mv, regToLocal, inst, DSUB);
                case MUL -> emitBinaryArith(mv, regToLocal, inst, DMUL);
                case DIV -> emitBinaryDiv(mv, regToLocal, inst);
                case MOD -> emitBinaryMod(mv, regToLocal, inst);
                case ADD_NUM, SUB_NUM, MUL_NUM, DIV_NUM, MOD_NUM ->
                        emitNumericBinary(mv, regToLocal, inst);
                case NEG -> {
                    // A4：与解释器同一 negate() helper(数字取负/字符串 nc()/list·map 抛"不支持的反转操作")
                    mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                    mv.visitMethodInsn(INVOKESTATIC, "priv/seventeen/artist/aria/runtime/Interpreter",
                            "negate", "(" + IVALUE_DESC + ")" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case INC -> {
                    // A4(jit-9)：走加法值模型 v.add(NumberValue.of(1))("a"++ → "a1.0"，与解释器 INC 一致)
                    mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                    mv.visitInsn(DCONST_1);
                    mv.visitMethodInsn(INVOKESTATIC, NUMVAL, "of", "(D)" + NUMVAL_DESC, false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "add",
                            "(Lpriv/seventeen/artist/aria/value/IData;)" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }
                case DEC -> {
                    // A4(jit-9)：走减法值模型 v.sub(NumberValue.of(1))(与解释器 DEC 一致)
                    mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
                    mv.visitInsn(DCONST_1);
                    mv.visitMethodInsn(INVOKESTATIC, NUMVAL, "of", "(D)" + NUMVAL_DESC, false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "sub",
                            "(Lpriv/seventeen/artist/aria/value/IData;)" + IVALUE_DESC, false);
                    mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
                }

                // A4(第13项复核)：比较直接调 IValue.le/lt/gt/ge 虚方法(与解释器同一实现——
                // ge/le 对非基础类型抛"类型不支持比较运算"；原 numberValue 比较吞掉该语义)。
                case LE -> emitComparison(mv, regToLocal, inst, "le");
                case LT -> emitComparison(mv, regToLocal, inst, "lt");
                case GT -> emitComparison(mv, regToLocal, inst, "gt");
                case GE -> emitComparison(mv, regToLocal, inst, "ge");
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

                case JUMP -> {
                    if (inst.a <= pc) emitInterruptCheck(mv, interruptSlot); // A4(controlflow-15)
                    mv.visitJumpInsn(GOTO, labels[inst.a]);
                }
                case JUMP_IF_TRUE -> {
                    if (inst.a <= pc) emitInterruptCheck(mv, interruptSlot);
                    mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                    mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "booleanValue", "()Z", false);
                    mv.visitJumpInsn(IFNE, labels[inst.a]);
                }
                case JUMP_IF_FALSE -> {
                    if (inst.a <= pc) emitInterruptCheck(mv, interruptSlot);
                    mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                    mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "booleanValue", "()Z", false);
                    mv.visitJumpInsn(IFEQ, labels[inst.a]);
                }
                case JUMP_IF_NONE -> {
                    // A4(A2 TODO)：c==1(for-in 判终)只认 ITER_END 哨兵(身份比较)——含 none 元素的
                    // 列表/map 不再提前终止；c==0(??/?.) 仍按 instanceof NoneValue(与解释器一致)。
                    if (inst.c == 1) {
                        mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                        mv.visitFieldInsn(GETSTATIC, NONEVAL, "ITER_END", "L" + NONEVAL + ";");
                        mv.visitJumpInsn(IF_ACMPEQ, labels[inst.a]);
                    } else {
                        mv.visitVarInsn(ALOAD, regToLocal[inst.dst]);
                        mv.visitTypeInsn(INSTANCEOF, NONEVAL);
                        mv.visitJumpInsn(IFNE, labels[inst.a]);
                    }
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
                    // A4(jit-12)：isSimpleBinaryLambda/emitInlinedLambda 模式匹配内联已删除——
                    // 臆断操作数为 args[0] op args[1]，对多运算/常量/顺序颠倒的 lambda 静默错算。
                    // 一律 emitGenericCall → rtCall → FunctionValue.getCallable().invoke(完整语义；
                    // 简单二元 lambda 由 FunctionCallable 的 FastBinaryLambda 探测天然加速)。
                    if (selfRecursivePCs.contains(pc) && scopeFree) {
                        // 自递归直跳(仅限无 scope 操作的函数体，见 scopeFree 说明)
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
                        emitGenericCall(mv, regToLocal, inst, ctxLocal);
                    }
                }
                case CALL_STATIC -> emitStaticCall(mv, regToLocal, inst, ctxLocal, className, pc);

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
        // A4(jit-9)：numberValue() 多态强转(与解释器 ADD_NUM 系的 registers[x].numberValue() 一致)，
        // 原 CHECKCAST NumberValue 对数字串/none 抛 CCE。
        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "numberValue", "()D", false);
        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, "numberValue", "()D", false);
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

    /**
     * A4(第13项复核)：比较发射改为直接调 IValue 上的 lt/le/gt/ge 虚方法(IData 里 final 实现)——
     * 与解释器逐位一致：数字/可数字符串数值比较、非基础类型 ge/le 抛"类型不支持比较运算"。
     * 原实现用 numberValue() 双转 + DCMP 内联，吞掉了 ge/le 的抛错语义。
     */
    private void emitComparison(MethodVisitor mv, int[] regToLocal, IRInstruction inst, String method) {
        mv.visitVarInsn(ALOAD, regToLocal[inst.a]);
        mv.visitVarInsn(ALOAD, regToLocal[inst.b]);
        mv.visitMethodInsn(INVOKEVIRTUAL, IVALUE, method,
                "(Lpriv/seventeen/artist/aria/value/IData;)" + BOOLVAL_DESC, false);
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
                                int ctxLocal, String className, int pc) {
        String fn = inst.name;
        int argBase = inst.a;
        int argCnt = inst.b;

        // A4(jit-17)：io.print/io.println 硬编码 System.out 内联删除——Aria 未注册 io 命名空间，
        // 解释器对 io.* 走 CALL_STATIC 兜底链得 NONE(不打印)，JIT 经 rtCallByNameCached 同链。
        // round/pow/min/max/random/PI/E 内联删除——解释器对它们走 CallableManager(宿主可覆盖)，
        // JIT 同走注册表分派(rtCallByNameCached → getStaticFunction)保持一致。
        // 仅保留 sin..log 8 个：解释器两个执行循环同样硬编码 Math.*(无视注册表)，内联即精确对齐。
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

        // 所有未知函数（含 ns.method 形式）通过 rtCallByNameCached 调用
        // 走 inst.cache 的快速路径：第一次解析 (objClass, methodName) → ICallable 后缓存，
        // 后续相同 objClass 的调用跳过 CallableManager.getObjectFunction 查找
        mv.visitFieldInsn(GETSTATIC, className, "INSTS",
                "[Lpriv/seventeen/artist/aria/compiler/ir/IRInstruction;");
        emitIntConst(mv, pc);
        mv.visitInsn(AALOAD);
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
                "rtCallByNameCached",
                "(Lpriv/seventeen/artist/aria/compiler/ir/IRInstruction;[" + IVALUE_DESC + CONTEXT_DESC + ")" + IVALUE_DESC, false);
        mv.visitVarInsn(ASTORE, regToLocal[inst.dst]);
    }

    /**
     * CALL 的运行时分派——与解释器 CALL 指令逐分支一致(A4 第13项复核补齐)：
     * FunctionValue → 脚本类构造(ClassDefinition) → JavaClassMirror → StoreOnly&lt;ICallable&gt; →
     * StoreOnly&lt;CWI&gt; → 吞括号返回原值。异常一律冒泡(解释器不吞异常,原 catch(Exception)
     * 会把调用内部错误吞成"返回 callee")。
     */
    public static IValue<?> rtCall(IValue<?> callee, IValue<?>[] args, Context ctx) throws AriaException {
        if (callee instanceof FunctionValue fv) {
            return fv.getCallable().invoke(new InvocationData(ctx, null, args));
        }
        if (callee instanceof ObjectValue<?> ov) {
            Object inner = ov.jvmValue();
            if (inner instanceof ClassDefinition cd) {
                return new Interpreter().constructScriptClass(cd, args, ctx);
            }
            if (inner instanceof priv.seventeen.artist.aria.interop.JavaClassMirror jcm) {
                return jcm.newInstance(args);
            }
            // Shimmer 对齐(R5)：带参 → 抛"不支持的后缀运算:"；空括号 → 吞(与解释器 CALL 一致)
            return Interpreter.callNonCallable(callee, args.length);
        }
        if (callee instanceof StoreOnlyValue<?> sov && sov.jvmValue() instanceof ICallable ic) {
            return ic.invoke(new InvocationData(ctx, null, args));
        }
        // Shimmer 对齐(interop-4)：走虚方法 invoke 保 AttributeCallable 覆写(self 上下文)。
        if (callee instanceof StoreOnlyValue<?> sov2 && sov2.jvmValue() instanceof CallableWithInvoker cwi) {
            return cwi.invoke(ctx, args);
        }
        // Shimmer 对齐(R5)：带参调用非可调用值 → 抛"不支持的后缀运算:"；空括号 → 吞括号返回原值
        return Interpreter.callNonCallable(callee, args.length);
    }


    /**
     * inst.cache 中存储的对象方法解析结果。A4(jit-15/17)：
     * <ul>
     *   <li>{@code objClass} 存 {@link Interpreter#receiverClass}(解包后的真实类)——ObjectValue 包装
     *       的不同宿主对象外层类相同，用外层类校验会调错方法。</li>
     *   <li>{@code generation} 存装配时 CallableManager 注册代数——宿主重注册后缓存自动失效。</li>
     *   <li>只在「接收者解析自 var 存储」时装配；热路径先验 scope 无同名遮蔽(解析优先级 scope→var)。</li>
     * </ul>
     */
    public static final class StaticCallCache {
        public final Class<?> objClass;
        public final ICallable callable;
        public final VariableKey baseKey;
        public final long generation;

        public StaticCallCache(Class<?> c, ICallable f, VariableKey k, long generation) {
            this.objClass = c;
            this.callable = f;
            this.baseKey = k;
            this.generation = generation;
        }
    }

    /**
     * 带 inst.cache 的 rtCallByName。仅用于含 '.' 的 fn name（obj.method 形式）。
     * Hot path：注册代数一致 + scope 无遮蔽 + var 值类型一致 → callObjectFunction(与解释器
     * MethodCache 命中路径同一调用约定)；任一不满足回落 slow path。
     */
    public static IValue<?> rtCallByNameCached(IRInstruction inst, IValue<?>[] args, Context ctx) throws AriaException {
        Object cached = inst.cache;
        if (cached instanceof StaticCallCache scc
                && scc.generation == CallableManager.INSTANCE.getGeneration()) {
            // A4(jit-15)：解析优先级 scope→var——scope 有同名非 none 绑定时不得用 var 缓存
            VariableReference scopeRef = ctx.getScopeStack().getExisting(scc.baseKey);
            IValue<?> scopeVal = scopeRef != null ? scopeRef.getValue() : null;
            if (scopeVal == null || scopeVal instanceof NoneValue) {
                VariableReference varRef = ctx.getLocalStorage().getVarVariableExisting(scc.baseKey);
                if (varRef != null) {
                    IValue<?> obj = varRef.getValue();
                    if (obj != null && Interpreter.receiverClass(obj) == scc.objClass) {
                        return Interpreter.callObjectFunction(scc.callable, ctx, obj, args);
                    }
                }
            }
        }
        return rtCallByNameCachedSlow(inst, args, ctx);
    }

    private static IValue<?> rtCallByNameCachedSlow(IRInstruction inst, IValue<?>[] args, Context ctx) throws AriaException {
        String fn = inst.name;
        int dot = fn == null ? -1 : fn.indexOf('.');
        if (dot <= 0) return rtCallByName(fn, args, ctx);

        String baseName = fn.substring(0, dot);
        String methodName = fn.substring(dot + 1);

        // 1. 命名空间静态函数(解释器先查 ns.method,再查全名注册在 "" 下的 "a.b")
        ICallable nsFn = CallableManager.INSTANCE.getStaticFunction(baseName, methodName);
        if (nsFn == null) nsFn = CallableManager.INSTANCE.getStaticFunction("", fn);
        if (nsFn != null) {
            return nsFn.invoke(new InvocationData(ctx, null, args));
        }

        // 2. 接收者解析：scope→var→val→global(与解释器 resolveVariable 完全同链)，记录来源
        VariableKey baseKey = VariableKey.of(baseName);
        IValue<?> obj = null;
        boolean fromVar = false;
        VariableReference scopeRef = ctx.getScopeStack().getExisting(baseKey);
        if (scopeRef != null) {
            IValue<?> v = scopeRef.getValue();
            if (v != null && !(v instanceof NoneValue)) obj = v;
        }
        if (obj == null) {
            VariableReference varRef = ctx.getLocalStorage().getVarVariableExisting(baseKey);
            if (varRef != null) {
                IValue<?> v = varRef.getValue();
                if (v != null && !(v instanceof NoneValue)) { obj = v; fromVar = true; }
            }
        }
        if (obj == null) {
            ValueReference valRef = ctx.getLocalStorage().getValVariableExisting(baseKey);
            if (valRef != null && valRef.isAssigned()) {
                IValue<?> v = valRef.getValue();
                if (v != null && !(v instanceof NoneValue)) obj = v;
            }
        }
        if (obj == null) {
            VariableReference globalRef = ctx.getGlobalStorage().getGlobalVariable(baseKey);
            if (globalRef != null) {
                IValue<?> v = globalRef.getValue();
                if (v != null && !(v instanceof NoneValue)) obj = v;
            }
        }
        if (obj == null) {
            // Shimmer 对齐(builtins-static-6, 与解释器同步)：命名空间存在但函数不存在 → 抛错。
            if (CallableManager.INSTANCE.hasStaticNamespace(baseName)) {
                throw new AriaRuntimeException("点运算解析工具集函数不存在: " + fn);
            }
            // Shimmer 对齐(R5, Z01/Z03)：none 接收者——带参调用抛"不支持的后缀运算:"，
            // 空括号 → none(与解释器 dispatchMethodCall 一致)。
            return Interpreter.callNonCallable(NoneValue.NONE, args.length);
        }

        // 3. 缓存装配(A4 jit-15)：仅当接收者解析自 var 存储、且分派首命中即注册对象函数
        //    (非脚本类/ClassDefinition 接收者——它们在 dispatchMethodCall 里优先于注册函数)。
        if (fromVar && !(obj instanceof AriaClassValue)
                && !(obj instanceof ObjectValue<?> ovv && ovv.jvmValue() instanceof ClassDefinition)) {
            Class<?> recvClass = Interpreter.receiverClass(obj);
            ICallable objFunc = CallableManager.INSTANCE.getObjectFunction(recvClass, methodName);
            if (objFunc != null) {
                inst.cache = new StaticCallCache(recvClass, objFunc, baseKey,
                        CallableManager.INSTANCE.getGeneration());
            }
        }

        // 4. 分派：与解释器 CALL_STATIC 的 dispatchMethodCall 同一实现(jit-14)
        return new Interpreter().dispatchMethodCall(ctx, obj, methodName, args, args.length);
    }

    /**
     * 裸标识符读取的运行期解析。Shimmer 对齐(variables-7/controlflow-13, 与解释器 LOAD_SCOPE 同步)：
     * 只查作用域栈(未命中即新建 none 引用)，不再回退 var/val——裸名与 var./val. 完全隔离。
     */
    public static IValue<?> rtLoadScope(Context ctx, VariableKey key) {
        return ctx.getScopeStack().get(key).getValue();
    }

    /**
     * 裸标识符写入的运行期解析。Shimmer 对齐(variables-7/controlflow-13, 与解释器 STORE_SCOPE 同步)：
     * 只写作用域栈(更新已存在绑定或在当前作用域新建)，不再写穿 var 存储。
     */
    public static void rtStoreScope(Context ctx, VariableKey key, IValue<?> value) {
        ctx.getScopeStack().get(key).setValue(value);
    }

    /**
     * 裸名 CALL_STATIC 的运行时分派——与解释器 CALL_STATIC 裸名链完全同序(A4 jit-3/14)：
     * 注册表("" 命名空间) → resolveVariable(scope→var→val→global) 的 FunctionValue/CWI →
     * 构造器表兜底 → NONE。含 '.' 的名字经 rtCallByNameCached 路由,不进此方法。
     */
    public static IValue<?> rtCallByName(String name, IValue<?>[] args, Context ctx) throws AriaException {
        if (name == null) return NoneValue.NONE;
        // 1. CallableManager 全局("")注册函数——解释器先查注册表再查变量
        ICallable globalFn = CallableManager.INSTANCE.getStaticFunction("", name);
        if (globalFn != null) {
            return globalFn.invoke(new InvocationData(ctx, null, args));
        }
        // 2. scope→var→val→global 完整解析(与解释器 resolveVariable 同一实现)
        IValue<?> fnVal = Interpreter.resolveVariable(ctx, name);
        if (fnVal instanceof FunctionValue fv) {
            return fv.getCallable().invoke(new InvocationData(ctx, null, args));
        }
        if (fnVal instanceof StoreOnlyValue<?> sov && sov.jvmValue() instanceof CallableWithInvoker cwi) {
            // Shimmer 对齐(interop-4)：虚方法 invoke 保 AttributeCallable 覆写。
            return cwi.invoke(ctx, args);
        }
        // 3. Shimmer 对齐(builtins-static-1)：裸名全 miss 回查构造器表。
        ICallable ctorFb = Interpreter.getRegisteredConstructor(name);
        if (ctorFb == null) ctorFb = CallableManager.INSTANCE.getConstructor(name);
        if (ctorFb != null) {
            return ctorFb.invoke(new InvocationData(ctx, null, args));
        }
        // Shimmer 对齐(R5)：带参调用不可调用值(含 none/未定义) → 抛"不支持的后缀运算:"；
        // 空括号 → 吞括号返回解析到的值(x()→x, undefined()→none)——与解释器 CALL_STATIC 一致。
        return Interpreter.callNonCallable(fnVal, args.length);
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

    /** 栈顶 IValue 收尾：dst 有映射则 ASTORE，否则 POP(防操作数栈失衡)。 */
    private void emitStoreOrPop(MethodVisitor mv, int[] regToLocal, int dst) {
        if (dst >= 0 && dst < regToLocal.length && regToLocal[dst] >= 0) {
            mv.visitVarInsn(ASTORE, regToLocal[dst]);
        } else {
            mv.visitInsn(POP);
        }
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
    public static IValue<?> rtGetProp(IValue<?> obj, String propName, Context ctx) throws AriaException {
        if (obj == null) return NoneValue.NONE;
        // ClassDefinition 静态字段/静态方法(对齐解释器 GET_PROP 首段；须先于下方通用 ObjectValue 分支)
        if (obj instanceof ObjectValue<?> cdov && cdov.jvmValue() instanceof ClassDefinition cd) {
            if (cd.hasStaticField(propName)) return cd.getStaticField(propName);
            IRProgram sm = cd.findStaticMethod(propName);
            if (sm != null) {
                ICallable callable = data -> {
                    Context c = data.getContext() != null ? data.getContext() : ctx;
                    return new Interpreter().execute(sm, c.createCallContext(null, data.getArgs())).getValue();
                };
                return new FunctionValue(callable);
            }
            return NoneValue.NONE;
        }
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
            ClassDefinition classDef = ci.getClassDefinition();
            // 属性 getter __get_xxx(对齐解释器；仅当类有任意访问器时查)——原 JIT 缺此，脚本类属性 getter 不触发
            if (classDef != null && classDef.hasAnyAccessor()) {
                IRProgram getterProg = classDef.findMethod("__get_" + propName);
                if (getterProg != null) {
                    return new Interpreter().execute(getterProg, ctx.createCallContext(obj, new IValue<?>[0])).getValue();
                }
            }
            IReference fieldRef = ci.getFields().get(propName);
            if (fieldRef != null) return fieldRef.getValue();
            // 类定义里的方法 → FunctionValue 包装
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
            // Shimmer 对齐：先查对象函数(self.parent 等)、再 getVariable + 惰性属性 auto-invoke
            return Interpreter.resolveObjectProperty(ov, propName, ctx);
        }
        if (obj instanceof ListValue lv && "length".equals(propName)) {
            return new NumberValue(lv.jvmValue().size());
        }
        if (obj instanceof StringValue sv && "length".equals(propName)) {
            return new NumberValue(sv.stringValue().length());
        }
        if (obj instanceof StoreOnlyValue<?> sov && sov.jvmValue() != null) {
            // Shimmer 对齐(interop-1, 与解释器 GET_PROP 同步)：StoreOnlyValue 宿主对象命中注册函数
            // (沿类层级/接口)→ 零参调用(target=原始对象)。
            ICallable objFunc = CallableManager.INSTANCE
                    .getObjectFunction(sov.jvmValue().getClass(), propName);
            if (objFunc != null) {
                return objFunc.invoke(new InvocationData(ctx, sov.jvmValue(), new IValue<?>[0]));
            }
        }
        // A4(第13项复核)：与解释器 GET_PROP 末段一致——静态注册函数按方法引用包装返回
        ICallable staticCallable = Interpreter.getStatic(propName);
        if (staticCallable != null) {
            return new FunctionValue(staticCallable);
        }
        return NoneValue.NONE;
    }

    /**
     * SET_PROP 运行时辅助:与 Interpreter.SET_PROP 一致。覆盖类实例字段(原地写或新建 ref)、setter 访问器、
     * Object 成员、静态字段、Map。返回(可能因 SmallMapValue 扩容而新建的)对象,调用方写回 obj 寄存器。
     * 使脚本类含 `self.x = ..` 的方法可走通用 JIT(此前 SET_PROP 不在 canCompile 白名单 → 方法不编译)。
     */
    public static IValue<?> rtSetProp(IValue<?> obj, String propName, IValue<?> val, Context ctx)
            throws AriaException {
        if (obj instanceof ObjectValue<?> ov && ov.jvmValue() instanceof ClassDefinition cd) {
            cd.setStaticField(propName, val);
            return obj;
        }
        if (obj instanceof ObjectValue<?> ov) {
            // Shimmer 对齐(interop-13, 与解释器 SET_PROP 同步)：对注册对象函数名赋值 → 抛错。
            if (CallableManager.INSTANCE.getObjectFunction(ov.jvmValue().getClass(), propName) != null) {
                throw new AriaRuntimeException("非变量类型无法进行赋值运算");
            }
            ov.jvmValue().getVariable(propName).setValue(val);
            return obj;
        }
        if (obj instanceof AriaClassValue cv) {
            ClassInstance ci = cv.jvmValue();
            if (ci != null) {
                ClassDefinition classDef = ci.getClassDefinition();
                if (classDef != null && classDef.hasAnyAccessor()) {
                    IRProgram setterProg = classDef.findMethod("__set_" + propName);
                    if (setterProg != null) {
                        try {
                            new Interpreter().execute(setterProg,
                                    ctx.createCallContext(obj, new IValue<?>[]{ val }));
                        } catch (Exception ignored) {}
                        return obj;
                    }
                }
                IReference existing = ci.getFields().get(propName);
                if (existing != null) existing.setValue(val);
                else ci.getFields().put(propName, new VariableReference(val));
            }
            return obj;
        }
        if (obj instanceof MapValue mv) {
            mv.jvmValue().put(new StringValue(propName), val);
            return obj;
        }
        if (obj instanceof SmallMapValue sm) {
            return sm.put(propName, val);
        }
        return obj;
    }

    /**
     * GET_INDEX 的运行时分派——与解释器 GET_INDEX 三态 c 逐分支一致(A4 jit-7/8/controlflow-14/interop-8)：
     * c==0 普通索引(list 越界抛"列表索引越界")、c==1 for-in(终结=ITER_END 哨兵)、c==2 args 索引(越界→NONE)。
     */
    public static IValue<?> rtGetIndex(IValue<?> obj, IValue<?> idx, Context ctx, int forIn) throws AriaException {
        // A8：forIn==3 为 callee-raw 模式——取出的值不 resolveLazyProperty(不无参 auto-invoke CWI),
        // 交给 CALL 带参调用(与解释器 GET_INDEX c==3 逐分支一致)。
        boolean rawCallee = forIn == 3;
        if (obj instanceof ListValue lv) {
            int index = (int) idx.numberValue();
            List<IValue<?>> list = lv.jvmValue();
            if (index >= 0 && index < list.size()) {
                // Shimmer 对齐(interop-3)：读出的惰性属性(CWI)在消费点自动求值(与解释器一致)
                IValue<?> el = list.get(index);
                return rawCallee ? el : Interpreter.resolveLazyProperty(el, ctx);
            }
            if (forIn == 1) return NoneValue.ITER_END;
            if (forIn == 2) return NoneValue.NONE;
            // A4(jit-7)：显式索引越界抛异常(与解释器同文本)，不再静默 none
            throw new AriaRuntimeException("列表索引越界: " + index + " (size=" + list.size() + ")");
        }
        if (obj instanceof SmallMapValue sm) {
            if (forIn == 1) {
                return Interpreter.mapEntryAt(sm.jvmValue(), (int) idx.numberValue());
            }
            IValue<?> val = sm.get(idx);
            return rawCallee ? val : Interpreter.resolveLazyProperty(val, ctx);
        }
        if (obj instanceof MapValue mv) {
            Map<IValue<?>, IValue<?>> map = mv.jvmValue();
            if (forIn == 1) {
                // for-in 迭代：第 idx 个 [key,value] 对(越界→ITER_END)
                return Interpreter.mapEntryAt(map, (int) idx.numberValue());
            }
            // 先用原始 IValue key 查
            IValue<?> val = map.get(idx);
            if (val == null && !(idx instanceof StringValue)) {
                // idx 不是 StringValue 时才需要包装重试
                val = map.get(new StringValue(idx.stringValue()));
            }
            // Shimmer 对齐(interop-3)：self.actions['x'] 读出的 CWI 自动求值(与解释器一致)
            return rawCallee ? val : Interpreter.resolveLazyProperty(val, ctx);
        }
        if (obj instanceof StringValue sv) {
            // A4(jit-8)：字符串按下标取单字符(与解释器 GET_INDEX StringValue 分支一致)
            int index = (int) idx.numberValue();
            String s = sv.stringValue();
            if (index >= 0 && index < s.length()) {
                return new StringValue(String.valueOf(s.charAt(index)));
            }
            return forIn == 1 ? NoneValue.ITER_END : NoneValue.NONE;
        }
        if (obj instanceof ObjectValue<?> ov
                && ov.jvmValue() instanceof RangeObject range) {
            // Shimmer 对齐(controlflow-01/02):for-in range 双端闭(i <= end),与解释器一致。
            double val = range.getStart() + idx.numberValue() * range.getStep();
            if (range.getStep() > 0 ? val <= range.getEnd() : val >= range.getEnd()) {
                return new NumberValue(val);
            }
            return forIn == 1 ? NoneValue.ITER_END : NoneValue.NONE;
        }
        if (forIn == 1) {
            // for-in 遇不可迭代对象：立即终结(与解释器一致)
            return NoneValue.ITER_END;
        }
        // 一般 IAriaObject 元素访问 obj['key'](对齐解释器 + Shimmer;此前 JIT 缺此分支 → self.parent['名'] 恒 NONE)。
        // Range 已在上面处理,此处是通用对象。
        if (obj instanceof ObjectValue<?> ov) {
            Variable elem = ov.jvmValue().getElement(idx.stringValue());
            IValue<?> ev = elem != null ? elem.ariaValue() : null;
            return rawCallee ? ev : Interpreter.resolveLazyProperty(ev, ctx);
        }
        return NoneValue.NONE;
    }

    /**
     * CALL_METHOD 的运行时分派——与解释器 CALL_METHOD 逐分支同序(A4 jit-14)：
     * AriaClassValue(实例字段函数→类方法) → 注册对象函数(receiverClass 解包) →
     * ObjectValue getVariable(FunctionValue/惰性 CWI) → STATIC_CALLABLES "typeName.method" →
     * STATIC_CALLABLES 裸方法名 → memberOf 吞括号。异常一律冒泡(解释器不吞)。
     */
    public static IValue<?> rtCallMethod(IValue<?> obj, String methodName, IValue<?>[] args, Context ctx) throws AriaException {
        // 1. AriaClassValue 实例方法(解释器先于注册函数检查)
        if (obj instanceof AriaClassValue cv && cv.jvmValue() != null) {
            ClassInstance ci = cv.jvmValue();
            // 先查实例字段中的函数值
            if (ci.getFields().containsKey(methodName)) {
                IValue<?> method = ci.getFields().get(methodName).getValue();
                if (method instanceof FunctionValue fv) {
                    Context callCtx = ctx.createCallContext(obj, args);
                    return fv.getCallable().invoke(new InvocationData(callCtx, obj, args));
                }
            }
            // 再查类定义中的方法
            ClassDefinition classDef = ci.getClassDefinition();
            if (classDef != null) {
                IRProgram methodProg = classDef.findMethod(methodName);
                if (methodProg != null) {
                    Context callCtx = ctx.createCallContext(obj, args);
                    Result result = new Interpreter().execute(methodProg, callCtx);
                    return result.getValue();
                }
            }
        }
        // 2. 注册对象函数(receiverClass 解包 ObjectValue/StoreOnly；注册方法压过同名宿主属性,interop-12)
        IValue<?> objMethodResult = Interpreter.invokeRegisteredObjectMethod(obj, methodName, args, ctx);
        if (objMethodResult != null) return objMethodResult;
        // 3. ObjectValue 宿主成员：FunctionValue / 惰性 CWI(interop-3/4)
        if (obj instanceof ObjectValue<?> ov && ov.jvmValue() != null) {
            IAriaObject so = ov.jvmValue();
            Variable v = so.getVariable(methodName);
            IValue<?> method = v != null ? v.ariaValue() : null;
            if (method instanceof FunctionValue fv) {
                Context callCtx = ctx.createCallContext(obj, args);
                return fv.getCallable().invoke(new InvocationData(callCtx, obj, args));
            }
            if (method instanceof StoreOnlyValue<?> sovM
                    && sovM.jvmValue() instanceof CallableWithInvoker cwiM) {
                return cwiM.invoke(ctx, args);
            }
        }
        // 4. Interpreter.registerStatic 注册表：typeName.method(A4 jit-14 补齐)
        String typeName = Interpreter.getTypeName(obj);
        ICallable staticMethod = Interpreter.getStatic(typeName + "." + methodName);
        if (staticMethod != null) {
            return staticMethod.invoke(new InvocationData(ctx, obj, args));
        }
        // 5. 裸方法名回退(A4 jit-14 补齐)
        ICallable genericMethod = Interpreter.getStatic(methodName);
        if (genericMethod != null) {
            return genericMethod.invoke(new InvocationData(ctx, obj, args));
        }
        // 6. Shimmer 对齐(R5 实测校正)：obj.field(带参) 中 field 非函数 → 抛"不支持的后缀运算:"；
        // 空括号(Shimmer 解析器丢弃) → 返回成员值(与解释器 CALL_METHOD/dispatchMethodCall 一致)。
        return Interpreter.callNonCallable(Interpreter.memberOf(obj, methodName), args.length);
    }

    public static IValue<?> rtNewList(IValue<?>[] elements) {
        List<IValue<?>> list = new ArrayList<>(elements.length);
        Collections.addAll(list, elements);
        return new ListValue(list);
    }

    /**
     * NEW_MAP 的运行时构造——A4(jit-18/operators-13/builtins-object-6/10/interop-9)：
     * SmallMapValue 生成点全面停用(类保留只读兼容)，一律 MapValue(LinkedHashMap,插入序)，
     * 与解释器 NEW_MAP 完全一致(方法注册/for-in 顺序/typeID/MapBridge instanceof)。
     */
    public static IValue<?> rtNewMap(IValue<?>[] kvPairs, int entryCount) {
        Map<IValue<?>, IValue<?>> map = new LinkedHashMap<>(Math.max(entryCount * 2, 4));
        for (int i = 0; i < entryCount; i++) {
            map.put(kvPairs[i * 2], kvPairs[i * 2 + 1]);
        }
        return new MapValue(map);
    }

    public static IValue<?> rtSetIndex(IValue<?> obj, IValue<?> index, IValue<?> value) {
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
        } else if (obj instanceof SmallMapValue sm) {
            // 对齐解释器 SET_INDEX：put 可能因扩容返回新实例(MapValue)，需写回 obj 寄存器(见 codegen ASTORE)
            return sm.put(index.stringValue(), value);
        } else if (obj instanceof ObjectValue<?> ov) {
            // 用抽象 Variable.setValue 兼容 ObjectVar(控件元素写入)——原先只对 Variable.Normal 写入，
            // ShimmerControl.getElement 返回的是 ObjectVar → JIT 下 self['child']=v / self['x']+=v / ++ 静默丢失。
            Variable elem = ov.jvmValue().getElement(index.stringValue());
            if (elem != null) elem.setValue(value);
        }
        return obj;
    }

    public static IValue<?> rtVarAddReg(VariableReference ref, IValue<?> val) throws AriaException {
        // Shimmer 对齐(operators-2/3):删字符串累加器分支,一律 cur.add(val) 产不可变值(与解释器一致)。
        // Shimmer 对齐(R1)：返回新值供生成码写 dst(赋值语句值=新值)。
        IValue<?> cur = ref.getValue();
        IValue<?> nv2 = (cur instanceof NumberValue nv && val instanceof NumberValue rv)
                ? new NumberValue(nv.value + rv.value)
                : cur.add(val);
        ref.setValue(nv2);
        return nv2;
    }

    /** A4(jit-20)：STORE_VAR——NumberValue 复制后存(与解释器主循环 STORE_VAR 完全一致)。 */
    public static void rtStoreVar(VariableReference ref, IValue<?> val) {
        if (val instanceof NumberValue nv) {
            ref.setValue(new NumberValue(nv.value));
        } else {
            ref.setValue(val);
        }
    }

    /** A4(jit-9)：VAR_INC——非数字走通用加法值模型(none+1=1、"a"+1="a1.0")，与解释器 VAR_INC 一致；R1：返回新值。 */
    public static IValue<?> rtVarInc(VariableReference ref) throws AriaException {
        IValue<?> cur = ref.getValue();
        IValue<?> nv2 = (cur instanceof NumberValue nv)
                ? new NumberValue(nv.value + 1)
                : cur.add(new NumberValue(1));
        ref.setValue(nv2);
        return nv2;
    }

    /** A4(jit-9)：VAR_ADD_CONST——与解释器 VAR_ADD_CONST 一致(数字快路径+通用值模型)；R1：返回新值。 */
    public static IValue<?> rtVarAddConst(VariableReference ref, IValue<?> c) throws AriaException {
        IValue<?> cur = ref.getValue();
        IValue<?> nv2 = (cur instanceof NumberValue nv && c instanceof NumberValue cv)
                ? new NumberValue(nv.value + cv.value)
                : cur.add(c);
        ref.setValue(nv2);
        return nv2;
    }

    /**
     * Shimmer 对齐(R2 系, Assignment.getResult)：AUTO_INVOKE 的运行时实现——
     * 可调用值(FunctionValue/StoreOnly&lt;CWI&gt;)零参调用取结果,否则原样返回(与解释器共用实现)。
     */
    public static IValue<?> rtAutoInvoke(IValue<?> value, Context ctx) throws AriaException {
        return Interpreter.autoInvokeIfCallable(value, ctx);
    }

    /** A4(第13项复核)：CALL_CONSTRUCTOR——复用解释器 constructByName 完整链(与解释器逐位一致)。 */
    public static IValue<?> rtCallConstructor(String name, IValue<?>[] args, Context ctx) throws AriaException {
        return new Interpreter().constructByName(name, args, ctx);
    }

    /**
     * A4(jit-1)：fastVars 运行时入口守卫。检查全部「存量值可能被读到」的 var 当前值：
     * 任一不是 NumberValue(long 模式还要求有限整数) → 本次调用改用完整解释器执行
     * (绕过已编译代码,杜绝布尔/none/字符串 var 被强转 0.0 回写销毁)，返回其结果；
     * 全部满足返回 null，调用方落入 fast 生成码。
     */
    public static IValue<?> rtFastVarsGuard(IRProgram program, VariableKey[] keys, int[] guardKeyIdxs,
                                            Context ctx, boolean longMode) throws AriaException {
        boolean ok = true;
        if (guardKeyIdxs != null) {
            var storage = ctx.getLocalStorage();
            for (int idx : guardKeyIdxs) {
                IValue<?> v = storage.getVarVariable(keys[idx]).getValue();
                if (!(v instanceof NumberValue nv)) { ok = false; break; }
                if (longMode) {
                    double d = nv.value;
                    // NaN/Infinity/非整数/超 long 范围——D2L 截断会改值,回退解释器
                    if (d != Math.floor(d) || Double.isInfinite(d)
                            || d > Long.MAX_VALUE || d < Long.MIN_VALUE) { ok = false; break; }
                }
            }
        }
        if (ok) return null;
        return new Interpreter().executeBypassCompiled(program, ctx).getValue();
    }

    /** A4(controlflow-15)：回边中断轮询——与解释器回跳检查同文本抛错，宿主可回收失控循环。 */
    public static void rtPollInterrupt() throws AriaRuntimeException {
        if (Thread.currentThread().isInterrupted()) {
            throw new AriaRuntimeException("脚本被中断");
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
