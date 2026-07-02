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

package priv.seventeen.artist.aria.runtime;

import priv.seventeen.artist.aria.Aria;
import priv.seventeen.artist.aria.annotation.AriaAnnotation;
import priv.seventeen.artist.aria.annotation.AnnotationRegistry;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.CallableWithInvoker;
import priv.seventeen.artist.aria.callable.FastBinaryLambda;
import priv.seventeen.artist.aria.callable.FastUnaryLambda;
import priv.seventeen.artist.aria.callable.FunctionCallable;
import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.compiler.ir.IRInstruction;
import priv.seventeen.artist.aria.compiler.ir.IROpCode;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.interop.JavaClassMirror;
import priv.seventeen.artist.aria.jit.JITCompiler;
import priv.seventeen.artist.aria.object.ClassDefinition;
import priv.seventeen.artist.aria.object.ClassInstance;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.object.RangeObject;
import priv.seventeen.artist.aria.parser.SourceLocation;
import priv.seventeen.artist.aria.value.*;
import priv.seventeen.artist.aria.value.reference.IReference;
import priv.seventeen.artist.aria.value.reference.ValueReference;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Interpreter {

    private static final JITCompiler jitCompiler = new JITCompiler();

    /** 参数数组缓存 — 避免高频小数组分配 */
    private static final IValue<?>[] EMPTY_ARGS = new IValue<?>[0];

    private static final Map<String, ICallable> STATIC_CALLABLES = new ConcurrentHashMap<>();

    private static final Map<String, ICallable> CONSTRUCTORS = new ConcurrentHashMap<>();

    /** CALL_METHOD 单态内联缓存：按 receiver 类型解析对象函数，命中时必须校验
     *  obj.getClass()，否则多态调用点（同一 PC 先后传入不同类）会调错方法。 */
    private record MethodCache(Class<?> recvClass, ICallable fn) {}

    public static void registerStatic(String name, ICallable callable) {
        STATIC_CALLABLES.put(name, callable);
    }

    public static void registerConstructor(String name, ICallable callable) {
        CONSTRUCTORS.put(name, callable);
    }

    public static ICallable getStatic(String name) {
        return STATIC_CALLABLES.get(name);
    }

    private static final int MAX_CALL_DEPTH = 512;
    private static final ThreadLocal<Integer> callDepth = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<SandboxConfig> sandboxConfig = new ThreadLocal<>();
    private static final ThreadLocal<long[]> instructionCounter = ThreadLocal.withInitial(() -> new long[]{0});
    private static final ThreadLocal<long[]> executionStartTime = ThreadLocal.withInitial(() -> new long[]{0});

    // 非沙箱模式 callDepth：必须 per-thread（解释器实例可能被模块单例/共享闭包跨线程共享，
    // 用实例字段会在并发下竞态、把计数冲到上限触发假"Stack overflow"）。用 ThreadLocal<int[]> 持有
    // 可变计数，避免 Integer 装箱；static 使同一线程跨多个 Interpreter 实例共享同一真实栈深度。
    private static final ThreadLocal<int[]> callDepthLocalTL = ThreadLocal.withInitial(() -> new int[1]);

    public static void setSandbox(SandboxConfig config) {
        sandboxConfig.set(config);
        if (config != null && config.getMaxExecutionTimeMs() > 0) {
            executionStartTime.get()[0] = System.currentTimeMillis();
        }
        instructionCounter.get()[0] = 0;
    }

    /** 当前线程的沙箱配置（无沙箱时为 null）。供 Java 互操作等入口做沙箱判定。 */
    public static SandboxConfig currentSandbox() { return sandboxConfig.get(); }

    public static void clearSandbox() {
        sandboxConfig.remove();
        instructionCounter.remove();
    }

    public static void resetCallDepth() {
        callDepth.set(0);
        callDepthLocalTL.get()[0] = 0;
    }

    /** 重置当前线程的 callDepth（非沙箱模式） */
    public void resetLocalCallDepth() {
        callDepthLocalTL.get()[0] = 0;
    }

    public Result execute(IRProgram program, Context context) throws AriaException {
        SandboxConfig config = sandboxConfig.get();
        if (config != null) {
            // 沙箱模式：用 ThreadLocal 保证多线程安全
            int maxDepth = config.getMaxCallDepth();
            int depth = callDepth.get();
            if (depth >= maxDepth) {
                throw new AriaRuntimeException("Stack overflow: call depth exceeded " + maxDepth);
            }
            callDepth.set(depth + 1);
            try {
                return executeInternal(program, context);
            } finally {
                callDepth.set(depth);
            }
        } else {
            // 非沙箱：per-thread 计数（解释器实例可能跨线程共享）
            int[] cd = callDepthLocalTL.get();
            int depth = cd[0];
            if (depth >= MAX_CALL_DEPTH) {
                throw new AriaRuntimeException("Stack overflow: call depth exceeded " + MAX_CALL_DEPTH);
            }
            cd[0] = depth + 1;
            try {
                return executeInternal(program, context);
            } finally {
                cd[0] = depth;
            }
        }
    }

    private Result executeInternal(IRProgram program, Context context) throws AriaException {
        boolean sandboxActive = sandboxConfig.get() != null;
        if (!sandboxActive && program.isCompiled()) {
            try {
                IValue<?> result = program.getCompiledCode().invoke(
                        new InvocationData(context, context.getSelf(), context.getArgsRef()));
                return Result.returnResult(result != null ? result : NoneValue.NONE);
            } catch (AriaException e) {
                throw e;
            } catch (Exception e) {
                program.setCompiledCode(null); // JIT 出错，回退
            }
        }
        int execCount = program.incrementExecCount();
        if (!sandboxActive && execCount == JITCompiler.getThreshold()) {
            tryJITCompile(program, context);
        }

        final IRInstruction[] code = program.getInstructions();
        final IValue<?>[] constants = program.getConstants();
        final VariableKey[] keys = program.getVariableKeys();
        final SourceLocation[] sourceMap = program.getSourceMap();
        final IRProgram[] subPrograms = program.getSubPrograms();
        final IValue<?>[] registers = new IValue<?>[Math.max(program.getRegisterCount(), 1)];

        // 避免运行时每次 HashMap 查找
        final VariableReference[] varRefs;
        final ValueReference[] valRefs;
        final VariableReference[] globalRefs;
        if (keys != null && keys.length > 0) {
            varRefs = new VariableReference[keys.length];
            valRefs = new ValueReference[keys.length];
            globalRefs = new VariableReference[keys.length];
            // 扫描指令，预解析需要的变量引用
            for (IRInstruction inst : code) {
                int ki = inst.a;
                if (ki >= 0 && ki < keys.length) {
                    switch (inst.opcode) {
                        case LOAD_VAR, STORE_VAR -> {
                            if (varRefs[ki] == null) varRefs[ki] = context.getLocalStorage().getVarVariable(keys[ki]);
                        }
                        case LOAD_VAL -> {
                            if (valRefs[ki] == null) valRefs[ki] = context.getLocalStorage().getValVariable(keys[ki]);
                        }
                        case LOAD_GLOBAL, STORE_GLOBAL -> {
                            if (globalRefs[ki] == null) globalRefs[ki] = context.getGlobalStorage().getGlobalVariable(keys[ki]);
                        }
                        default -> {}
                    }
                }
            }
        } else {
            varRefs = null; valRefs = null; globalRefs = null;
        }

        // 初始化寄存器为 NONE
        Arrays.fill(registers, NoneValue.NONE);
        final VariableReference[] scopeRefs = (keys != null && keys.length > 0) ? new VariableReference[keys.length] : null;

        int pc = 0;
        final int len = code.length;

        // try-catch 栈：存储 (catchPC, scopeDepth)
        final Deque<int[]> tryStack = new ArrayDeque<>();
        // async 指令收集
        int asyncStartPC = -1;

        IValue<?> lastValue = NoneValue.NONE;

        while (pc < len) {
            final IRInstruction inst = code[pc];
            try {
                switch (inst.opcode) {
                    case LOAD_CONST:
                        registers[inst.dst] = constants[inst.a];
                        break;
                    case LOAD_NONE:
                        registers[inst.dst] = NoneValue.NONE;
                        break;
                    case LOAD_TRUE:
                        registers[inst.dst] = BooleanValue.TRUE;
                        break;
                    case LOAD_FALSE:
                        registers[inst.dst] = BooleanValue.FALSE;
                        break;

                    case LOAD_VAR: {
                        registers[inst.dst] = varRefs[inst.a].getValue();
                        break;
                    }
                    case STORE_VAR: {
                        IValue<?> val = registers[inst.dst];
                        // 数字赋值时创建新实例避免引用共享
                        if (val instanceof NumberValue nv) {
                            varRefs[inst.a].setValue(new NumberValue(nv.value));
                        } else {
                            varRefs[inst.a].setValue(val);
                        }
                        break;
                    }
                    case LOAD_VAL: {
                        // val 存储优先(宿主 forceSetLocalValue 注入的不可变值);为空则回退 scope→var——
                        // 因为脚本侧 `val.x = ...` 实际编译成 STORE_SCOPE(见 Compiler.emitStore),
                        // 否则 `val.x` 点读取不到脚本声明的 val(裸读经 scope 回退可读,点读此前恒 none)。
                        IValue<?> lv = valRefs[inst.a].getValue();
                        registers[inst.dst] = (lv == null || lv instanceof NoneValue)
                                ? resolveValFallback(context, keys[inst.a], lv) : lv;
                        break;
                    }
                    case LOAD_GLOBAL: {
                        registers[inst.dst] = globalRefs[inst.a].getValue();
                        break;
                    }
                    case STORE_GLOBAL: {
                        globalRefs[inst.a].setValue(registers[inst.dst]);
                        break;
                    }
                    case STORE_VAL: {
                        // val 不可变:初始化一次,重赋(脚本端)报错;宿主 forceSetLocalValue 不走此路,可覆盖。
                        ValueReference vref = context.getLocalStorage().getValVariable(keys[inst.a]);
                        if (vref.isAssigned()) {
                            throw new AriaRuntimeException("Cannot reassign immutable val '"
                                    + keys[inst.a].getName() + "' (use var for mutable variables)");
                        }
                        vref.setValue(registers[inst.dst]);
                        break;
                    }
                    case LOAD_CLIENT: {
                        VariableKey key = keys[inst.a];
                        Variable.Normal v = context.getClientVariable(key);
                        registers[inst.dst] = v.ariaValue();
                        break;
                    }
                    case STORE_CLIENT: {
                        VariableKey key = keys[inst.a];
                        Variable.Normal v = context.getClientVariable(key);
                        v.setValue(registers[inst.dst]);
                        break;
                    }
                    case LOAD_SERVER: {
                        VariableKey key = keys[inst.a];
                        Variable.Normal v = context.getServerVariable(key);
                        registers[inst.dst] = v.ariaValue();
                        break;
                    }
                    case STORE_SERVER: {
                        VariableKey key = keys[inst.a];
                        Variable.Normal v = context.getServerVariable(key);
                        v.setValue(registers[inst.dst]);
                        break;
                    }
                    case LOAD_SCOPE: {
                        VariableReference ref = scopeRefs != null ? scopeRefs[inst.a] : null;
                        if (ref == null) {
                            VariableReference scopeRef = context.getScopeStack().getExisting(keys[inst.a]);
                            if (scopeRef != null) {
                                ref = scopeRef;
                            } else {
                                // Fallback: 查 var 存储
                                VariableReference varRef = context.getLocalStorage().getVarVariableExisting(keys[inst.a]);
                                if (varRef != null) {
                                    ref = varRef;
                                } else {
                                    // 再回退 val 存储:脚本 `val.x = v` 写入 val(ValueReference),裸读 `x` 也应取到。
                                    // ValueReference 与 scopeRefs(VariableReference[])类型不同,不缓存,直接读后退出。
                                    ValueReference valRef = context.getLocalStorage().getValVariableExisting(keys[inst.a]);
                                    if (valRef != null && valRef.isAssigned()) {
                                        registers[inst.dst] = valRef.getValue();
                                        break;
                                    }
                                    // 最终 fallback: 在 scope 中创建
                                    ref = context.getScopeStack().get(keys[inst.a]);
                                }
                            }
                            if (scopeRefs != null) scopeRefs[inst.a] = ref;
                        }
                        registers[inst.dst] = ref.getValue();
                        break;
                    }
                    case MAP_MERGE: {
                        // 字典展开 {...x}：x 必须是 map，否则报错（不静默吞噬）。
                        // base 始终是 NEW_MAP 产出的 MapValue；spread 可能是 MapValue 或 SmallMapValue。
                        IValue<?> base = registers[inst.dst];
                        IValue<?> spread = registers[inst.a];
                        Map<IValue<?>, IValue<?>> spreadMap;
                        if (spread instanceof MapValue smv) spreadMap = smv.jvmValue();
                        else if (spread instanceof SmallMapValue ssmv) spreadMap = ssmv.jvmValue();
                        else throw new AriaRuntimeException(
                                    "Cannot spread non-map value in map literal: " + spread.getClass().getSimpleName());
                        Map<IValue<?>, IValue<?>> merged = base instanceof MapValue bmv
                                ? new LinkedHashMap<>(bmv.jvmValue()) : new LinkedHashMap<>();
                        merged.putAll(spreadMap); // 右覆盖左
                        registers[inst.dst] = new MapValue(merged);
                        break;
                    }
                    case DECLARE_SCOPE: {
                        // 声明性绑定：在当前作用域层级新建/绑定，强制 shadow 外层同名变量（catch 变量等）。
                        // 不走 scopeRefs 缓存，因每次进入声明点应绑定到当前层级。
                        VariableReference ref = context.getScopeStack().getInCurrentLevel(keys[inst.a]);
                        ref.setValue(registers[inst.dst]);
                        break;
                    }
                    case STORE_SCOPE: {
                        VariableReference ref = scopeRefs != null ? scopeRefs[inst.a] : null;
                        if (ref == null) {
                            // 裸名赋值应更新已存在的绑定，而非在当前块作用域新建影子（否则块内 `x=v`
                            // 对外层 var/scope 变量的写入会随 POP_SCOPE 丢失）。解析顺序与 LOAD_SCOPE 对称：
                            // 作用域链已存在 → var 存储已存在 → 都没有才在当前作用域新建。
                            ref = context.getScopeStack().getExisting(keys[inst.a]);
                            if (ref == null) {
                                ref = context.getLocalStorage().getVarVariableExisting(keys[inst.a]);
                            }
                            if (ref == null) {
                                ref = context.getScopeStack().get(keys[inst.a]);
                            }
                            if (scopeRefs != null) scopeRefs[inst.a] = ref;
                        }
                        ref.setValue(registers[inst.dst]);
                        break;
                    }
                    case LOAD_SELF:
                        registers[inst.dst] = context.getSelf();
                        break;
                    case LOAD_ARG:
                        registers[inst.dst] = context.getArg(inst.a);
                        break;
                    case LOAD_ARGS: {
                        IValue<?>[] args = context.getArgs();
                        List<IValue<?>> list = new ArrayList<>(args.length);
                        Collections.addAll(list, args);
                        registers[inst.dst] = new ListValue(list);
                        break;
                    }
                    case ADD: {
                        IValue<?> la = registers[inst.a], ra = registers[inst.b];
                        if (la instanceof NumberValue ln && ra instanceof NumberValue rn) {
                            double r = ln.value + rn.value;
                            IValue<?> dst = registers[inst.dst];
                            if (dst instanceof NumberValue nv && dst != la && dst != ra) { nv.value = r; }
                            else { registers[inst.dst] = new NumberValue(r); }
                        } else if (la instanceof MutableStringValue msv) {
                            // 累加器模式：已是 MutableStringValue，原地追加，零分配
                            registers[inst.dst] = msv.append(ra.stringValue());
                        } else if (la instanceof StringValue ls && ra instanceof StringValue rs
                                && !ls.canBeNumber() && !rs.canBeNumber()) {
                            // 第一次字符串拼接：直接升级为 MutableStringValue，后续在同一 builder 上追加
                            registers[inst.dst] = new MutableStringValue(ls.stringValue()).append(rs.stringValue());
                        } else if (la instanceof RopeString lrs) {
                            // Rope 累加：flatten 一次转成 MutableStringValue，后续走上面的 msv 分支
                            registers[inst.dst] = new MutableStringValue(lrs.stringValue()).append(ra.stringValue());
                        } else { registers[inst.dst] = la.add(ra); }
                        break;
                    }
                    case SUB: {
                        IValue<?> la = registers[inst.a], ra = registers[inst.b];
                        if (la instanceof NumberValue ln && ra instanceof NumberValue rn) {
                            double r = ln.value - rn.value;
                            IValue<?> dst = registers[inst.dst];
                            if (dst instanceof NumberValue nv && dst != la && dst != ra) { nv.value = r; }
                            else { registers[inst.dst] = new NumberValue(r); }
                        } else { registers[inst.dst] = la.sub(ra); }
                        break;
                    }
                    case MUL: {
                        IValue<?> la = registers[inst.a], ra = registers[inst.b];
                        if (la instanceof NumberValue ln && ra instanceof NumberValue rn) {
                            double r = ln.value * rn.value;
                            IValue<?> dst = registers[inst.dst];
                            if (dst instanceof NumberValue nv && dst != la && dst != ra) { nv.value = r; }
                            else { registers[inst.dst] = new NumberValue(r); }
                        } else { registers[inst.dst] = la.mul(ra); }
                        break;
                    }
                    case DIV: {
                        IValue<?> la = registers[inst.a], ra = registers[inst.b];
                        if (la instanceof NumberValue ln && ra instanceof NumberValue rn) {
                            double r = rn.value == 0 ? 0 : ln.value / rn.value; // Shimmer 对齐：除零->0
                            IValue<?> dst = registers[inst.dst];
                            if (dst instanceof NumberValue nv && dst != la && dst != ra) { nv.value = r; }
                            else { registers[inst.dst] = new NumberValue(r); }
                        } else { registers[inst.dst] = la.div(ra); }
                        break;
                    }
                    case MOD: {
                        IValue<?> la = registers[inst.a], ra = registers[inst.b];
                        if (la instanceof NumberValue ln && ra instanceof NumberValue rn) {
                            double r = rn.value == 0 ? 0 : ln.value % rn.value; // Shimmer 对齐：取余零->0
                            IValue<?> dst = registers[inst.dst];
                            if (dst instanceof NumberValue nv && dst != la && dst != ra) { nv.value = r; }
                            else { registers[inst.dst] = new NumberValue(r); }
                        } else { registers[inst.dst] = la.mod(ra); }
                        break;
                    }
                    case NEG: {
                        double r = -registers[inst.a].numberValue();
                        if (registers[inst.dst] instanceof NumberValue nv) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }
                    case INC: {
                        double r = registers[inst.a].numberValue() + 1;
                        registers[inst.dst] = new NumberValue(r);
                        break;
                    }
                    case DEC: {
                        double r = registers[inst.a].numberValue() - 1;
                        registers[inst.dst] = new NumberValue(r);
                        break;
                    }
                    case ADD_NUM: {
                        double r = registers[inst.a].numberValue() + registers[inst.b].numberValue();
                        IValue<?> dst = registers[inst.dst];
                        if (dst instanceof NumberValue nv && dst != registers[inst.a] && dst != registers[inst.b]) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }
                    case SUB_NUM: {
                        double r = registers[inst.a].numberValue() - registers[inst.b].numberValue();
                        IValue<?> dst = registers[inst.dst];
                        if (dst instanceof NumberValue nv && dst != registers[inst.a] && dst != registers[inst.b]) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }
                    case MUL_NUM: {
                        double r = registers[inst.a].numberValue() * registers[inst.b].numberValue();
                        IValue<?> dst = registers[inst.dst];
                        if (dst instanceof NumberValue nv && dst != registers[inst.a] && dst != registers[inst.b]) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }
                    case DIV_NUM: {
                        double divisor = registers[inst.b].numberValue();
                        double r = divisor == 0 ? 0 : registers[inst.a].numberValue() / divisor; // Shimmer 对齐：除零->0
                        IValue<?> dst = registers[inst.dst];
                        if (dst instanceof NumberValue nv && dst != registers[inst.a] && dst != registers[inst.b]) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }
                    case MOD_NUM: {
                        double divisor = registers[inst.b].numberValue();
                        double r = divisor == 0 ? 0 : registers[inst.a].numberValue() % divisor;
                        IValue<?> dst = registers[inst.dst];
                        if (dst instanceof NumberValue nv && dst != registers[inst.a] && dst != registers[inst.b]) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }

                    case BIT_AND: {
                        double r = (int) registers[inst.a].numberValue() & (int) registers[inst.b].numberValue();
                        if (registers[inst.dst] instanceof NumberValue nv) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }
                    case BIT_OR: {
                        double r = (int) registers[inst.a].numberValue() | (int) registers[inst.b].numberValue();
                        if (registers[inst.dst] instanceof NumberValue nv) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }
                    case BIT_XOR: {
                        double r = (int) registers[inst.a].numberValue() ^ (int) registers[inst.b].numberValue();
                        if (registers[inst.dst] instanceof NumberValue nv) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }
                    case BIT_NOT: {
                        double r = ~(int) registers[inst.a].numberValue();
                        if (registers[inst.dst] instanceof NumberValue nv) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }
                    case SHL: {
                        double r = (int) registers[inst.a].numberValue() << (int) registers[inst.b].numberValue();
                        if (registers[inst.dst] instanceof NumberValue nv) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }
                    case SHR: {
                        double r = (int) registers[inst.a].numberValue() >> (int) registers[inst.b].numberValue();
                        if (registers[inst.dst] instanceof NumberValue nv) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }
                    case USHR: {
                        double r = (int) registers[inst.a].numberValue() >>> (int) registers[inst.b].numberValue();
                        if (registers[inst.dst] instanceof NumberValue nv) { nv.value = r; }
                        else { registers[inst.dst] = new NumberValue(r); }
                        break;
                    }

                    case EQ:
                        registers[inst.dst] = registers[inst.a].eq(registers[inst.b]);
                        break;
                    case NE:
                        registers[inst.dst] = registers[inst.a].ne(registers[inst.b]);
                        break;
                    case GT:
                        registers[inst.dst] = registers[inst.a].gt(registers[inst.b]);
                        break;
                    case LT:
                        registers[inst.dst] = registers[inst.a].lt(registers[inst.b]);
                        break;
                    case GE:
                        registers[inst.dst] = registers[inst.a].ge(registers[inst.b]);
                        break;
                    case LE:
                        registers[inst.dst] = registers[inst.a].le(registers[inst.b]);
                        break;
                    case IN_RANGE: {
                        registers[inst.dst] = evalInRange(registers[inst.a], registers[inst.b]);
                        break;
                    }

                    case IN_CHECK: {
                        registers[inst.dst] = evalInCheck(registers[inst.a], registers[inst.b]);
                        break;
                    }
                    case INSTANCEOF_CHECK: {
                        registers[inst.dst] = evalInstanceof(registers[inst.a], registers[inst.b]);
                        break;
                    }

                    case NOT:
                        registers[inst.dst] = BooleanValue.of(!registers[inst.a].booleanValue());
                        break;
                    case AND:
                        // 短路求值：左操作数为 false 则结果为左操作数，否则为右操作数
                        registers[inst.dst] = !registers[inst.a].booleanValue()
                                ? registers[inst.a] : registers[inst.b];
                        break;
                    case OR:
                        // 短路求值：左操作数为 true 则结果为左操作数，否则为右操作数
                        registers[inst.dst] = registers[inst.a].booleanValue()
                                ? registers[inst.a] : registers[inst.b];
                        break;
                    case GET_PROP: {
                        IValue<?> obj = registers[inst.a];
                        String propName = inst.name;
                        if (obj instanceof ObjectValue<?> ov && ov.jvmValue() instanceof ClassDefinition cd) {
                            // 静态字段
                            if (cd.hasStaticField(propName)) {
                                registers[inst.dst] = cd.getStaticField(propName);
                                break;
                            }
                            // 静态方法 → FunctionValue
                            IRProgram sm = cd.findStaticMethod(propName);
                            if (sm != null) {
                                final IRProgram smRef = sm;
                                ICallable callable = data -> {
                                    Context ctx = data.getContext() != null ? data.getContext() : context;
                                    Context callCtx = ctx.createCallContext(null, data.getArgs());
                                    return execute(smRef, callCtx).getValue();
                                };
                                registers[inst.dst] = new FunctionValue(callable);
                                break;
                            }
                            registers[inst.dst] = NoneValue.NONE;
                            break;
                        }
                        if (obj instanceof ObjectValue<?> ov) {
                            // Shimmer 对齐：先查对象函数(self.parent 等)、再 getVariable + 惰性属性 auto-invoke
                            registers[inst.dst] = resolveObjectProperty(ov, propName, context);
                        } else if (obj instanceof AriaClassValue cv) {
                            ClassInstance ci = cv.jvmValue();
                            if (ci != null) {
                                ClassDefinition classDef = ci.getClassDefinition();
                                // 先检查 getter 方法: __get_propName（仅在类有任意访问器时查）
                                if (classDef != null && classDef.hasAnyAccessor()) {
                                    IRProgram getterProg = classDef.findMethod("__get_" + propName);
                                    if (getterProg != null) {
                                        Context callCtx = context.createCallContext(obj, EMPTY_ARGS);
                                        Result getterResult = execute(getterProg, callCtx);
                                        registers[inst.dst] = getterResult.getValue();
                                        break;
                                    }
                                }
                                // 普通字段访问 — 单次 get 然后判 null
                                IReference fieldRef = ci.getFields().get(propName);
                                if (fieldRef != null) {
                                    registers[inst.dst] = fieldRef.getValue();
                                } else if (classDef != null) {
                                    // 查找类定义中的方法，包装为 FunctionValue
                                    IRProgram methodProg = classDef.findMethod(propName);
                                    if (methodProg != null) {
                                        final IValue<?> capturedObj = obj;
                                        ICallable methodCallable = data -> {
                                            Context callCtx = data.getContext().createCallContext(capturedObj, data.getArgs());
                                            return execute(methodProg, callCtx).getValue();
                                        };
                                        registers[inst.dst] = new FunctionValue(methodCallable);
                                    } else {
                                        registers[inst.dst] = NoneValue.NONE;
                                    }
                                } else {
                                    registers[inst.dst] = NoneValue.NONE;
                                }
                            } else {
                                registers[inst.dst] = NoneValue.NONE;
                            }
                        } else if (obj instanceof MapValue mv) {
                            IValue<?> val = mv.jvmValue().get(new StringValue(propName));
                            registers[inst.dst] = val != null ? val : NoneValue.NONE;
                        } else if (obj instanceof SmallMapValue sm) {
                            registers[inst.dst] = sm.get(propName);
                        } else if (obj instanceof ListValue lv && "length".equals(propName)) {
                            registers[inst.dst] = new NumberValue(lv.jvmValue().size());
                        } else if (obj instanceof StringValue sv && "length".equals(propName)) {
                            registers[inst.dst] = new NumberValue(sv.stringValue().length());
                        } else {
                            // 尝试静态方法查找（作为方法引用）
                            ICallable staticCallable = STATIC_CALLABLES.get(propName);
                            if (staticCallable != null) {
                                registers[inst.dst] = new FunctionValue(staticCallable);
                            } else {
                                registers[inst.dst] = NoneValue.NONE;
                            }
                        }
                        break;
                    }
                    case SET_PROP: {
                        // SET_PROP: dst=objReg, a=valueReg, name=propName
                        IValue<?> obj = registers[inst.dst];
                        IValue<?> val = registers[inst.a];
                        String propName = inst.name;
                        if (obj instanceof ObjectValue<?> ov && ov.jvmValue() instanceof ClassDefinition cd) {
                            // 静态字段写入
                            cd.setStaticField(propName, val);
                            break;
                        }
                        if (obj instanceof ObjectValue<?> ov) {
                            IAriaObject so = ov.jvmValue();
                            Variable v = so.getVariable(propName);
                            v.setValue(val);
                        } else if (obj instanceof AriaClassValue cv) {
                            ClassInstance ci = cv.jvmValue();
                            if (ci != null) {
                                // 先检查 setter 方法: __set_propName（仅当类有任意访问器时查）
                                ClassDefinition classDef = ci.getClassDefinition();
                                if (classDef != null && classDef.hasAnyAccessor()) {
                                    IRProgram setterProg = classDef.findMethod("__set_" + propName);
                                    if (setterProg != null) {
                                        Context callCtx = context.createCallContext(obj, new IValue<?>[]{ val });
                                        execute(setterProg, callCtx);
                                        break;
                                    }
                                }
                                // 已有 reference 则原地写，避免分配新 VariableReference
                                IReference existing = ci.getFields().get(propName);
                                if (existing != null) {
                                    existing.setValue(val);
                                } else {
                                    ci.getFields().put(propName, new VariableReference(val));
                                }
                            }
                        } else if (obj instanceof MapValue mv) {
                            mv.jvmValue().put(new StringValue(propName), val);
                        } else if (obj instanceof SmallMapValue sm) {
                            IValue<?> after = sm.put(propName, val);
                            if (after != sm) registers[inst.dst] = after;
                        }
                        break;
                    }

                    case GET_INDEX: {
                        IValue<?> obj = registers[inst.a];
                        if (inst.b == -1) {
                            // 空索引 — 返回对象本身
                            registers[inst.dst] = obj;
                        } else {
                            IValue<?> idx = registers[inst.b];
                            if (obj instanceof ListValue lv) {
                                int index = (int) idx.numberValue();
                                List<IValue<?>> list = lv.jvmValue();
                                if (index >= 0 && index < list.size()) {
                                    registers[inst.dst] = list.get(index);
                                } else if (inst.c == 1) {
                                    // for-in 迭代哨兵：越界=终止，保持 none
                                    registers[inst.dst] = NoneValue.NONE;
                                } else {
                                    // Shimmer 对齐：显式索引越界抛异常（而非静默 none）
                                    throw new AriaRuntimeException("列表索引越界: " + index + " (size=" + list.size() + ")");
                                }
                            } else if (obj instanceof MapValue mv) {
                                if (inst.c == 1) {
                                    // for-in 迭代:第 idx 个 [key,value] 对
                                    registers[inst.dst] = mapEntryAt(mv.jvmValue(), (int) idx.numberValue());
                                } else {
                                    IValue<?> val = mv.jvmValue().get(idx);
                                    if (val == null) {
                                        // 尝试用字符串键
                                        val = mv.jvmValue().get(new StringValue(idx.stringValue()));
                                    }
                                    registers[inst.dst] = val != null ? val : NoneValue.NONE;
                                }
                            } else if (obj instanceof SmallMapValue sm) {
                                registers[inst.dst] = sm.get(idx);
                            } else if (obj instanceof StringValue sv) {
                                int index = (int) idx.numberValue();
                                String s = sv.stringValue();
                                if (index >= 0 && index < s.length()) {
                                    registers[inst.dst] = new StringValue(String.valueOf(s.charAt(index)));
                                } else {
                                    registers[inst.dst] = NoneValue.NONE;
                                }
                            } else if (obj instanceof ObjectValue<?> ov && ov.jvmValue() instanceof RangeObject range) {
                                // Range 快速路径：复用 NumberValue 对象
                                double val = range.getStart() + idx.numberValue() * range.getStep();
                                if (range.getStep() > 0 ? val < range.getEnd() : val > range.getEnd()) {
                                    if (registers[inst.dst] instanceof NumberValue nv) {
                                        nv.value = val; // 直接修改，避免 new
                                    } else {
                                        registers[inst.dst] = new NumberValue(val);
                                    }
                                } else {
                                    registers[inst.dst] = NoneValue.NONE;
                                }
                            } else if (obj instanceof ObjectValue<?> ov) {
                                // IAriaObject 元素访问: obj['key'] — 惰性属性 auto-invoke(对齐 GET_PROP)
                                Variable elem = ov.jvmValue().getElement(idx.stringValue());
                                registers[inst.dst] = resolveLazyProperty(elem != null ? elem.ariaValue() : null, context);
                            } else {
                                registers[inst.dst] = NoneValue.NONE;
                            }
                        }
                        break;
                    }
                    case SET_INDEX: {
                        // SET_INDEX: dst=objReg, a=idxReg, b=valueReg
                        IValue<?> obj = registers[inst.dst];
                        IValue<?> val = registers[inst.b];
                        if (inst.a == -1) {
                            // 空索引 — list.add
                            if (obj instanceof ListValue lv) {
                                lv.jvmValue().add(val);
                            }
                        } else {
                            IValue<?> idx = registers[inst.a];
                            if (obj instanceof ListValue lv) {
                                int index = (int) idx.numberValue();
                                List<IValue<?>> list = lv.jvmValue();
                                while (list.size() <= index) list.add(NoneValue.NONE);
                                list.set(index, val);
                            } else if (obj instanceof MapValue mv) {
                                mv.jvmValue().put(idx, val);
                            } else if (obj instanceof SmallMapValue sm) {
                                IValue<?> after = sm.put(idx.stringValue(), val);
                                if (after != sm) registers[inst.dst] = after;
                            } else if (obj instanceof ObjectValue<?> ov) {
                                // IAriaObject 元素设置: obj['key'] = value
                                // 用抽象 Variable.setValue —— ShimmerControl.getElement 返回的是 ObjectVar(非 Normal)，
                                // 原先只对 Variable.Normal 写入会静默丢弃对控件元素的赋值。
                                Variable elem = ov.jvmValue().getElement(idx.stringValue());
                                if (elem != null) {
                                    elem.setValue(val);
                                }
                            }
                        }
                        break;
                    }
                    case CALL: {
                        // CALL: dst=结果, a=calleeReg, b=argCount, c=argBase
                        // 特殊处理 __import__
                        if ("__import__".equals(inst.name)) {
                            // import 机制 — 从当前引擎的 GlobalStorage 获取
                            ICallable importFn = context.getGlobalStorage().getMeta("__import__");
                            if (importFn == null) {
                                importFn = STATIC_CALLABLES.get("__import__");
                            }
                            if (importFn != null) {
                                IValue<?>[] callArgs = new IValue<?>[]{ registers[inst.a] };
                                registers[inst.dst] = importFn.invoke(new InvocationData(context, null, callArgs));
                            } else {
                                registers[inst.dst] = NoneValue.NONE;
                            }
                            break;
                        }
                        IValue<?> callee = registers[inst.a];
                        int argCount = inst.b;
                        int argBase = inst.c;
                        // 零分配：直接引用寄存器数组
                        if (callee instanceof FunctionValue fv) {
                            ICallable callable = fv.getCallable();
                            // 快速 lambda 内联：直接用 double 参数，避免 InvocationData
                            if (callable instanceof FastBinaryLambda fbl
                                    && argCount == 2
                                    && registers[argBase] instanceof NumberValue na
                                    && registers[argBase + 1] instanceof NumberValue nb) {
                                double callResult = fbl.invokeFastDouble(na.value, nb.value);
                                // 如果下一条是 VAR_ADD_REG 且消费本 CALL 的结果，直接加到 var 上
                                if (pc + 1 < len) {
                                    IRInstruction next = code[pc + 1];
                                    if (next.opcode == IROpCode.VAR_ADD_REG && next.b == inst.dst) {
                                        IValue<?> cur = varRefs[next.a].getValue();
                                        if (cur instanceof NumberValue nv) {
                                            varRefs[next.a].setValue(new NumberValue(nv.value + callResult));
                                            pc += 2; // 跳过 CALL 和 VAR_ADD_REG
                                            continue;
                                        }
                                    }
                                }
                                registers[inst.dst] = new NumberValue(callResult);
                            } else if (callable instanceof FastUnaryLambda ful
                                    && argCount == 1
                                    && registers[argBase] instanceof NumberValue na2) {
                                // 单参数快速路径：args[0] op const
                                registers[inst.dst] = new NumberValue(ful.invokeFastDouble(na2.value));
                            } else {
                                registers[inst.dst] = callable.invoke(
                                        new InvocationData(context, null, registers, argBase, argCount));
                            }
                        } else if (callee instanceof ObjectValue<?> ov) {
                            // 类构造：ClassName(args) 直接调用
                            Object inner = ov.jvmValue();
                            if (inner instanceof ClassDefinition cd) {
                                IValue<?>[] callArgs = new IValue<?>[argCount];
                                for (int i = 0; i < argCount; i++) callArgs[i] = registers[argBase + i];
                                registers[inst.dst] = constructScriptClass(cd, callArgs, context);
                            } else if (inner instanceof JavaClassMirror jcm) {
                                IValue<?>[] callArgs = new IValue<?>[argCount];
                                for (int i = 0; i < argCount; i++) callArgs[i] = registers[argBase + i];
                                registers[inst.dst] = jcm.newInstance(callArgs);
                            } else {
                                // Shimmer 对齐(用户要求)：非函数值加括号 → 吞掉括号，返回原值
                                registers[inst.dst] = callee;
                            }
                        } else {
                            // 尝试作为 ICallable 调用
                            if (callee instanceof StoreOnlyValue<?> sov && sov.jvmValue() instanceof ICallable ic) {
                                registers[inst.dst] = ic.invoke(
                                        new InvocationData(context, null, registers, argBase, argCount));
                            } else {
                                // Shimmer 对齐：非函数值加括号 → 吞掉括号，返回原值（5() -> 5，"x"() -> "x"）
                                registers[inst.dst] = callee;
                            }
                        }
                        break;
                    }
                    case CALL_METHOD: {
                        // CALL_METHOD: dst=结果, a=objReg, b=argCount, name=方法名, c=argBase
                        IValue<?> obj = registers[inst.a];
                        String methodName = inst.name;
                        int argCount = inst.b;
                        int argBase = inst.c;

                        // 快速路径：cache 命中且 receiver 类型一致时跳过所有 instanceof 检查。
                        // 必须校验 obj.getClass()——cache 按 receiver 类型解析的对象函数，
                        // 否则多态调用点会复用上一个类的方法导致调错。
                        if (inst.cache instanceof MethodCache mc && mc.recvClass() == receiverClass(obj)) {
                            ICallable cachedFunc = mc.fn();
                            IValue<?>[] fastArgs = new IValue<?>[argCount];
                            for (int i = 0; i < argCount; i++) fastArgs[i] = registers[argBase + i];
                            registers[inst.dst] = callObjectFunction(cachedFunc, context, obj, fastArgs);
                            break;
                        }

                        IValue<?>[] callArgs = new IValue<?>[argCount];
                        for (int i = 0; i < argCount; i++) {
                            callArgs[i] = registers[argBase + i];
                        }

                        // 先尝试对象自身的方法
                        if (obj instanceof AriaClassValue cv && cv.jvmValue() != null) {
                            ClassInstance ci = cv.jvmValue();
                            // 先查实例字段中的函数值
                            if (ci.getFields().containsKey(methodName)) {
                                IValue<?> method = ci.getFields().get(methodName).getValue();
                                if (method instanceof FunctionValue fv) {
                                    Context callCtx = context.createCallContext(obj, callArgs);
                                    registers[inst.dst] = fv.getCallable().invoke(new InvocationData(callCtx, obj, callArgs));
                                    break;
                                }
                            }
                            // 再查类定义中的方法
                            ClassDefinition classDef = ci.getClassDefinition();
                            if (classDef != null) {
                                IRProgram methodProg = classDef.findMethod(methodName);
                                if (methodProg != null) {
                                    Context callCtx = context.createCallContext(obj, callArgs);
                                    Result methodResult = execute(methodProg, callCtx);
                                    registers[inst.dst] = methodResult.getValue();
                                    break;
                                }
                            }
                        }
                        if (obj instanceof ObjectValue<?> ov) {
                            IAriaObject so = ov.jvmValue();
                            Variable v = so.getVariable(methodName);
                            IValue<?> method = v.ariaValue();
                            if (method instanceof FunctionValue fv) {
                                Context callCtx = context.createCallContext(obj, callArgs);
                                registers[inst.dst] = fv.getCallable().invoke(new InvocationData(callCtx, obj, callArgs));
                                break;
                            }
                        }

                        // 尝试静态注册表：typeName.method
                        String typeName = getTypeName(obj);
                        ICallable staticMethod = STATIC_CALLABLES.get(typeName + "." + methodName);
                        if (staticMethod != null) {
                            registers[inst.dst] = staticMethod.invoke(new InvocationData(context, obj, callArgs));
                            break;
                        }

                        // 尝试 CallableManager 对象函数注册表（带缓存）
                        ICallable objFunc = CallableManager.INSTANCE
                                .getObjectFunction(receiverClass(obj), methodName);
                        if (objFunc != null) {
                            inst.cache = new MethodCache(receiverClass(obj), objFunc);
                            registers[inst.dst] = callObjectFunction(objFunc, context, obj, callArgs);
                            break;
                        }

                        // 通用方法查找
                        ICallable genericMethod = STATIC_CALLABLES.get(methodName);
                        if (genericMethod != null) {
                            registers[inst.dst] = genericMethod.invoke(new InvocationData(context, obj, callArgs));
                        } else {
                            // Shimmer 对齐(用户要求)：obj.field() 中 field 非函数 → 吞掉括号，返回 obj.field 的成员值
                            registers[inst.dst] = memberOf(obj, methodName);
                        }
                        break;
                    }
                    case CALL_STATIC: {
                        int argCount = inst.b;
                        int argBase = inst.a;

                        ICallable cached = (ICallable) inst.cache;
                        if (cached != null) {
                            // 沙箱激活时，cache 命中也必须过命名空间检查（防止"先无沙箱预热 cache、
                            // 后加沙箱复用同一已编译 routine"绕过检查）。仅在沙箱激活时有开销。
                            SandboxConfig sbHit = sandboxConfig.get();
                            if (sbHit != null && inst.name != null) {
                                int di = inst.name.indexOf('.');
                                String hns = di >= 0 ? inst.name.substring(0, di) : inst.name;
                                if (!sbHit.isNamespaceAllowed(hns)) {
                                    throw new AriaRuntimeException("Sandbox: namespace '" + hns + "' is not allowed");
                                }
                            }
                            // FastUnaryLambda: 零分配，纯 double
                            if (cached instanceof FastUnaryLambda ful
                                    && argCount == 1 && registers[argBase] instanceof NumberValue na) {
                                double r = ful.invokeFastDouble(na.value);
                                if (registers[inst.dst] instanceof NumberValue nv) { nv.value = r; }
                                else { registers[inst.dst] = new NumberValue(r); }
                                break;
                            }
                            // FastBinaryLambda: 零分配，纯 double
                            if (cached instanceof FastBinaryLambda fbl
                                    && argCount == 2 && registers[argBase] instanceof NumberValue na2
                                    && registers[argBase + 1] instanceof NumberValue nb) {
                                double r = fbl.invokeFastDouble(na2.value, nb.value);
                                if (registers[inst.dst] instanceof NumberValue nv2) { nv2.value = r; }
                                else { registers[inst.dst] = new NumberValue(r); }
                                break;
                            }
                            // 通用缓存路径：分配参数数组，直接 invoke
                            IValue<?>[] cArgs = argCount == 1
                                    ? new IValue<?>[]{ registers[argBase] }
                                    : argCount == 2
                                    ? new IValue<?>[]{ registers[argBase], registers[argBase + 1] }
                                    : copyArgs(registers, argBase, argCount);
                            registers[inst.dst] = cached.invoke(new InvocationData(context, null, cArgs));
                            break;
                        }

                        String fn = inst.name;
                        // 沙箱命名空间过滤
                        SandboxConfig sbCfg = sandboxConfig.get();
                        if (sbCfg != null && fn != null) {
                            int dotIdx = fn.indexOf('.');
                            String ns = dotIdx >= 0 ? fn.substring(0, dotIdx) : fn;
                            if (!sbCfg.isNamespaceAllowed(ns)) {
                                throw new AriaRuntimeException("Sandbox: namespace '" + ns + "' is not allowed");
                            }
                        }
                        // 内联数学函数
                        if (argCount == 1 && fn != null) {
                            double arg = registers[argBase].numberValue();
                            double result = switch (fn) {
                                case "math.sin" -> Math.sin(arg);
                                case "math.cos" -> Math.cos(arg);
                                case "math.tan" -> Math.tan(arg);
                                case "math.abs" -> Math.abs(arg);
                                case "math.floor" -> Math.floor(arg);
                                case "math.ceil" -> Math.ceil(arg);
                                case "math.sqrt" -> Math.sqrt(arg);
                                case "math.log" -> Math.log(arg);
                                default -> Double.NaN;
                            };
                            if (!Double.isNaN(result)) {
                                if (registers[inst.dst] instanceof NumberValue d) { d.value = result; }
                                else { registers[inst.dst] = new NumberValue(result); }
                                break;
                            }
                        }
                        // 参数数组
                        IValue<?>[] callArgs = argCount == 1
                                ? new IValue<?>[]{ registers[argBase] }
                                : argCount == 2
                                ? new IValue<?>[]{ registers[argBase], registers[argBase + 1] }
                                : copyArgs(registers, argBase, argCount);
                        // CallableManager 查找
                        ICallable callable = null;
                        if (fn != null) {
                            int dot = fn.indexOf('.');
                            if (dot >= 0) {
                                callable = CallableManager.INSTANCE.getStaticFunction(fn.substring(0, dot), fn.substring(dot + 1));
                            }
                            if (callable == null) {
                                callable = CallableManager.INSTANCE.getStaticFunction("", fn);
                            }
                        }
                        if (callable != null) {
                            inst.cache = callable;
                            registers[inst.dst] = callable.invoke(new InvocationData(context, null, callArgs));
                        } else if ("super".equals(fn)) {
                            // super(args) — 调用父类构造器
                            IValue<?> selfVal = context.getSelf();
                            if (selfVal instanceof AriaClassValue cv && cv.jvmValue() != null) {
                                ClassDefinition classDef = cv.jvmValue().getClassDefinition();
                                if (classDef != null && classDef.getParent() != null) {
                                    ClassDefinition parentDef = classDef.getParent();
                                    if (parentDef.getFieldInitProgram() != null) {
                                        Context initCtx = context.createCallContext(selfVal, EMPTY_ARGS);
                                        execute(parentDef.getFieldInitProgram(), initCtx);
                                    }
                                    if (parentDef.getConstructorProgram() != null) {
                                        Context ctorCtx = context.createCallContext(selfVal, callArgs);
                                        execute(parentDef.getConstructorProgram(), ctorCtx);
                                    }
                                }
                            }
                            registers[inst.dst] = NoneValue.NONE;
                        } else if (fn != null && fn.indexOf('.') >= 0) {
                            // ns.method(args) 形式 — 对象方法分派
                            int dot = fn.indexOf('.');
                            IValue<?> obj = resolveVariable(context, fn.substring(0, dot));
                            String methodName = fn.substring(dot + 1);
                            registers[inst.dst] = dispatchMethodCall(context, obj, methodName, callArgs, argCount);
                        } else {
                            // scope/var 中的函数值
                            IValue<?> fnVal = resolveVariable(context, fn);
                            if (fnVal instanceof FunctionValue fvCall) {
                                ICallable fnCallable = fvCall.getCallable();
                                if (fnCallable instanceof FunctionCallable fc) {
                                    ICallable fp = fc.getFastPath();
                                    inst.cache = fp != null ? fp : fnCallable;
                                } else {
                                    inst.cache = fnCallable;
                                }
                                registers[inst.dst] = ((ICallable) inst.cache).invoke(new InvocationData(context, null, callArgs));
                            } else if (fnVal instanceof StoreOnlyValue<?> sovCall && sovCall.jvmValue() instanceof CallableWithInvoker cwiCall) {
                                inst.cache = cwiCall.getCallable();
                                registers[inst.dst] = cwiCall.getCallable().invoke(new InvocationData(context, null, callArgs));
                            } else {
                                registers[inst.dst] = NoneValue.NONE;
                            }
                        }
                        break;
                    }
                    case CALL_CONSTRUCTOR: {
                        // CALL_CONSTRUCTOR: dst=结果, a=argBase, b=argCount, name=className
                        int argCount = inst.b;
                        int argBase = inst.a;
                        IValue<?>[] callArgs = new IValue<?>[argCount];
                        for (int i = 0; i < argCount; i++) {
                            callArgs[i] = registers[argBase + i];
                        }
                        registers[inst.dst] = constructByName(inst.name, callArgs, context);
                        break;
                    }
                    case NEW_LIST: {
                        // NEW_LIST: dst=结果, a=baseReg, b=count
                        int baseReg = inst.a;
                        int count = inst.b;
                        List<IValue<?>> list = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            list.add(registers[baseReg + i]);
                        }
                        registers[inst.dst] = new ListValue(list);
                        break;
                    }
                    case NEW_MAP: {
                        // NEW_MAP: dst=结果, a=baseReg, b=entryCount (key-value pairs)
                        int baseReg = inst.a;
                        int entryCount = inst.b;
                        Map<IValue<?>, IValue<?>> map = new LinkedHashMap<>(entryCount * 2);
                        for (int i = 0; i < entryCount; i++) {
                            IValue<?> key = registers[baseReg + i * 2];
                            IValue<?> val = registers[baseReg + i * 2 + 1];
                            map.put(key, val);
                        }
                        registers[inst.dst] = new MapValue(map);
                        break;
                    }
                    case NEW_ASYNC: {
                        // async { body }：把 body 子程序提交线程池执行，产出真 Promise。
                        final IRProgram asyncBody = subPrograms[inst.a];
                        final Context capturedCtx = context.snapshotForClosure(); // 闭包捕获外层 scope/var/self/args
                        final SandboxConfig capturedSb = sandboxConfig.get();      // 沙箱随任务传播到 worker 线程
                        final java.util.concurrent.CompletableFuture<IValue<?>> future =
                                new java.util.concurrent.CompletableFuture<>();
                        ThreadPoolManager.INSTANCE.executor().submit(() -> {
                            try {
                                if (capturedSb != null) Interpreter.setSandbox(capturedSb);
                                Context asyncCtx = capturedCtx.createCallContext(capturedCtx.getSelf(), EMPTY_ARGS);
                                IValue<?> r = new Interpreter().execute(asyncBody, asyncCtx).getValue();
                                future.complete(r != null ? r : NoneValue.NONE);
                            } catch (Throwable t) {
                                future.completeExceptionally(t);
                            } finally {
                                if (capturedSb != null) Interpreter.clearSandbox();
                            }
                        });
                        registers[inst.dst] = new PromiseValue(future);
                        break;
                    }
                    case NEW_FUNCTION: {
                        final IRProgram subProg = subPrograms[inst.a];
                        // 捕获定义时的 context，并快照当前 scope（闭包捕获）
                        final Context capturedCtx = context.snapshotForClosure();
                        if (subProg.isCompiled()) {
                            final ICallable compiled = subProg.getCompiledCode();
                            if (subProg.isJitContextFree()) {
                                // 纯数值 JIT 路径：直接复用 compiled，省一层 lambda + InvocationData
                                registers[inst.dst] = new FunctionValue(compiled);
                            } else {
                                // 非纯数值 JIT 路径：必须 createCallContext 给一个干净的 ScopeStack，
                                // 否则 PUSH_SCOPE / STORE_SCOPE 会污染调用方的 scope（递归时 LOAD_SCOPE n
                                // 会读到被覆盖的值）
                                registers[inst.dst] = new FunctionValue(data -> {
                                    Context callCtx = capturedCtx.createCallContext(
                                            data.getTarget() instanceof IValue<?> t ? t : NoneValue.NONE,
                                            data.getArgs());
                                    return compiled.invoke(new InvocationData(callCtx, null, data));
                                });
                            }
                        } else {
                            // 尝试快速 lambda（纳秒级，无需 JIT）
                            ICallable fastLambda = tryCreateFastLambda(subProg);
                            if (fastLambda != null) {
                                registers[inst.dst] = new FunctionValue(fastLambda);
                            } else {
                                final IRInstruction[] subCode = subProg.getInstructions();
                                final IValue<?>[] subConstants = subProg.getConstants();
                                final VariableKey[] subKeys = subProg.getVariableKeys();
                                final IRProgram[] subSubs = subProg.getSubPrograms();
                                final int subRegCount = Math.max(subProg.getRegisterCount(), 1);
                                final Interpreter self = this;

                                // 在 lambda 创建时确定上下文模式，避免每次调用都检查
                                final boolean lightContext = capturedCtx.getScopeStack().depth() <= 0;
                                final boolean noSandbox = sandboxConfig.get() == null;

                                ICallable callable = (InvocationData data) -> {
                                    // 首次调用时尝试 JIT 编译
                                    if (!subProg.isCompiled() && !subProg.isJitScheduled()) {
                                        int cnt = subProg.incrementExecCount();
                                        if (cnt >= JITCompiler.getThreshold()) {
                                            subProg.setJitScheduled(true);
                                            try {
                                                if (jitCompiler.canCompile(subProg)) {
                                                    ICallable c = jitCompiler.compile(subProg, capturedCtx);
                                                    if (c != null) subProg.setCompiledCode(c);
                                                }
                                            } catch (Throwable ignored) {}
                                        }
                                    }
                                    // JIT 编译完成后切换到编译代码（沙箱模式下跳过 JIT）
                                    if (subProg.isCompiled() && noSandbox) {
                                        if (subProg.isJitContextFree()) {
                                            return subProg.getCompiledCode().invoke(data);
                                        }
                                        // 非纯数值路径：必须 createCallContext 给干净 ScopeStack，
                                        // 否则递归时 PUSH_SCOPE / STORE_SCOPE 污染调用方 scope
                                        Context jitCallCtx = lightContext
                                                ? capturedCtx.createLightCallContext(
                                                data.getTarget() instanceof IValue<?> t1 ? t1 : NoneValue.NONE,
                                                data.getArgs())
                                                : capturedCtx.createCallContext(
                                                data.getTarget() instanceof IValue<?> t2 ? t2 : NoneValue.NONE,
                                                data.getArgs());
                                        return subProg.getCompiledCode().invoke(
                                                new InvocationData(jitCallCtx, null, data));
                                    }
                                    Context callCtx = lightContext
                                            ? capturedCtx.createLightCallContext(
                                            data.getTarget() instanceof IValue<?> t ? t : NoneValue.NONE,
                                            data.getArgs())
                                            : capturedCtx.createCallContext(
                                            data.getTarget() instanceof IValue<?> t ? t : NoneValue.NONE,
                                            data.getArgs());
                                    return self.executeInline(subCode, subConstants, subKeys, subSubs, subRegCount, callCtx, subProg);
                                };
                                registers[inst.dst] = new FunctionValue(callable);
                            } // else (not fast lambda)
                        } // else (not compiled)
                        // 互递归组编译用：让函数值记住它的子程序，编译时可经 var 名取到兄弟函数 IR
                        if (registers[inst.dst] instanceof FunctionValue fvSrc) {
                            fvSrc.setSourceProgram(subProg);
                        }
                        break;
                    }
                    case NEW_INSTANCE: {
                        // NEW_INSTANCE: dst=结果, a=nameConstIndex, b=argCount, c=argBase, name=className
                        String className = constants[inst.a].stringValue();
                        int argCount = inst.b;
                        int argBase = inst.c;
                        IValue<?>[] callArgs = new IValue<?>[argCount];
                        for (int i = 0; i < argCount; i++) {
                            callArgs[i] = registers[argBase + i];
                        }
                        registers[inst.dst] = constructByName(className, callArgs, context);
                        break;
                    }
                    case JUMP:
                        pc = inst.a;
                        continue; // 跳过 pc++
                    case JUMP_IF_TRUE:
                        if (registers[inst.dst].booleanValue()) {
                            pc = inst.a;
                            continue;
                        }
                        break;
                    case JUMP_IF_FALSE:
                        if (!registers[inst.dst].booleanValue()) {
                            pc = inst.a;
                            continue;
                        }
                        break;
                    case JUMP_IF_NONE:
                        if (registers[inst.dst] instanceof NoneValue) {
                            pc = inst.a;
                            continue;
                        }
                        break;

                    case PUSH_SCOPE:
                        context.pushScope();
                        if (scopeRefs != null) Arrays.fill(scopeRefs, null);
                        break;
                    case POP_SCOPE:
                        context.popScope();
                        if (scopeRefs != null) Arrays.fill(scopeRefs, null);
                        break;

                    case RETURN: {
                        IValue<?> retVal = inst.dst >= 0 ? registers[inst.dst] : NoneValue.NONE;
                        return Result.returnResult(retVal);
                    }
                    case BREAK:
                        return Result.breakResult();
                    case NEXT:
                        return Result.nextResult();

                    case TRY_BEGIN:
                        // TRY_BEGIN: a=catchPC (已被编译器修补)
                        tryStack.push(new int[]{ inst.a, context.getScopeStack().depth() });
                        break;
                    case TRY_END:
                        if (!tryStack.isEmpty()) {
                            tryStack.pop();
                        }
                        break;
                    case THROW: {
                        IValue<?> errVal = inst.dst >= 0 ? registers[inst.dst] : NoneValue.NONE;
                        throw new AriaRuntimeException(errVal.stringValue());
                    }

                    case ASYNC_BEGIN:
                        asyncStartPC = pc + 1;
                        break;
                    case AWAIT: {
                        // AWAIT: dst=结果, a=operandReg。若 operand 是 PromiseValue 则阻塞等待；
                        // 否则直接透传（与 JS 语义一致，await 非 Promise 直接返回原值）
                        IValue<?> op = registers[inst.a];
                        if (op instanceof PromiseValue pv) {
                            registers[inst.dst] = pv.awaitValue();
                        } else {
                            registers[inst.dst] = op != null ? op : NoneValue.NONE;
                        }
                        break;
                    }
                    case ASYNC_END: {
                        if (asyncStartPC >= 0) {
                            final int startPC = asyncStartPC;
                            final int endPC = pc;
                            final Context asyncCtx = context.createAsyncContext();
                            final IRProgram prog = program;
                            ThreadPoolManager.INSTANCE.submitTask(() -> {
                                try {
                                    Interpreter asyncInterpreter = new Interpreter();
                                    asyncInterpreter.executeRange(prog, asyncCtx, startPC, endPC, registers);
                                } catch (Exception e) {
                                    System.err.println("[Aria] Async error: " + e.getMessage());
                                }
                            });
                            asyncStartPC = -1;
                        }
                        break;
                    }

                    case CONCAT: {
                        // CONCAT: dst=结果, a=baseReg, b=count
                        int baseReg = inst.a;
                        int count = inst.b;
                        // 预算总长度，一次性分配 StringBuilder 容量
                        int totalLen = 0;
                        for (int i = 0; i < count; i++) totalLen += registers[baseReg + i].stringValue().length();
                        StringBuilder sb = new StringBuilder(totalLen);
                        for (int i = 0; i < count; i++) {
                            sb.append(registers[baseReg + i].stringValue());
                        }
                        // concat 路径：跳过 Double.parseDouble 探测
                        registers[inst.dst] = new StringValue(sb.toString(), true);
                        break;
                    }

                    case INIT_OR_GET: {
                        // INIT_OR_GET: dst=结果, a=keyIndex, b=valueReg, name=namespace(可选)
                        VariableKey key = keys[inst.a];
                        IValue<?> initVal = registers[inst.b];
                        Variable.Normal v;
                        if (inst.name != null) {
                            v = switch (inst.name) {
                                case "var" -> context.getLocalVariable(key);
                                case "global" -> context.getGlobalVariable(key);
                                case "server" -> context.getServerVariable(key);
                                case "client" -> context.getClientVariable(key);
                                default -> context.getScopeVariable(key);
                            };
                        } else {
                            v = context.getScopeVariable(key);
                        }
                        IValue<?> current = v.ariaValue();
                        if (current instanceof NoneValue) {
                            v.setValue(initVal);
                            registers[inst.dst] = initVal;
                        } else {
                            registers[inst.dst] = current;
                        }
                        break;
                    }

                    case GET_FIELD: {
                        IValue<?> obj = registers[inst.a];
                        String fieldName = inst.name;
                        if (obj instanceof AriaClassValue cv && cv.jvmValue() != null) {
                            ClassInstance ci = cv.jvmValue();
                            if (ci.getFields().containsKey(fieldName)) {
                                registers[inst.dst] = ci.getFields().get(fieldName).getValue();
                            } else {
                                registers[inst.dst] = NoneValue.NONE;
                            }
                        } else {
                            registers[inst.dst] = NoneValue.NONE;
                        }
                        break;
                    }
                    case SET_FIELD: {
                        // SET_FIELD: dst=objReg, a=valueReg, name=fieldName
                        IValue<?> obj = registers[inst.dst];
                        IValue<?> val = registers[inst.a];
                        String fieldName = inst.name;
                        if (obj instanceof AriaClassValue cv && cv.jvmValue() != null) {
                            cv.jvmValue().getFields().put(fieldName, new VariableReference(val));
                        }
                        break;
                    }
                    case INVOKE_SUPER: {
                        // INVOKE_SUPER: dst=结果, a=selfReg, b=argCount, name=方法名, c=argBase
                        // 从当前 self 的类定义的父类中查找方法并执行
                        IValue<?> selfVal = context.getSelf();
                        if (selfVal instanceof AriaClassValue cv && cv.jvmValue() != null) {
                            ClassDefinition classDef = cv.jvmValue().getClassDefinition();
                            if (classDef != null && classDef.getParent() != null) {
                                ClassDefinition parentDef = classDef.getParent();
                                String methodName = inst.name;
                                int argCount = inst.b;
                                int argBase = inst.c;
                                IRProgram methodProg = parentDef.findMethod(methodName);
                                if (methodProg != null) {
                                    IValue<?>[] callArgs = new IValue<?>[argCount];
                                    for (int i = 0; i < argCount; i++) callArgs[i] = registers[argBase + i];
                                    Context callCtx = context.createCallContext(selfVal, callArgs);
                                    Result methodResult = execute(methodProg, callCtx);
                                    registers[inst.dst] = methodResult.getValue();
                                } else {
                                    registers[inst.dst] = NoneValue.NONE;
                                }
                            } else {
                                registers[inst.dst] = NoneValue.NONE;
                            }
                        } else {
                            registers[inst.dst] = NoneValue.NONE;
                        }
                        break;
                    }
                    case DEFINE_CLASS: {
                        // DEFINE_CLASS: dst=classReg, a=fieldInitSubIdx, b=ctorSubIdx, c=staticInitSubIdx
                        // name = "className|parentName|fieldMeta|methodMeta|staticFieldMeta|staticMethodMeta"
                        String[] parts = inst.name.split("\\|", -1);
                        String className = parts[0];
                        String parentName = parts.length > 1 ? parts[1] : "";
                        String fieldMetaStr = parts.length > 2 ? parts[2] : "";
                        String methodMetaStr = parts.length > 3 ? parts[3] : "";
                        String staticFieldMetaStr = parts.length > 4 ? parts[4] : "";
                        String staticMethodMetaStr = parts.length > 5 ? parts[5] : "";

                        ClassDefinition classDef = new ClassDefinition(className);

                        // 设置父类
                        if (!parentName.isEmpty()) {
                            VariableKey parentKey = VariableKey.of(parentName);
                            Variable.Normal parentVar = context.getScopeVariable(parentKey);
                            IValue<?> parentVal = parentVar.ariaValue();
                            if (parentVal instanceof ObjectValue<?> pov && pov.jvmValue() instanceof ClassDefinition pd) {
                                classDef.setParent(pd);
                            }
                        }

                        // 解析字段元数据
                        if (!fieldMetaStr.isEmpty()) {
                            for (String fm : fieldMetaStr.split(",")) {
                                if (fm.startsWith("var.")) {
                                    classDef.addField(fm.substring(4), true);
                                } else if (fm.startsWith("val.")) {
                                    classDef.addField(fm.substring(4), false);
                                }
                            }
                        }

                        // 静态字段元数据
                        if (!staticFieldMetaStr.isEmpty()) {
                            for (String fm : staticFieldMetaStr.split(",")) {
                                if (fm.startsWith("var.")) {
                                    classDef.addStaticField(fm.substring(4), true);
                                } else if (fm.startsWith("val.")) {
                                    classDef.addStaticField(fm.substring(4), false);
                                }
                            }
                        }

                        // 设置字段初始化程序
                        if (inst.a >= 0 && inst.a < subPrograms.length) {
                            classDef.setFieldInitProgram(subPrograms[inst.a]);
                        }

                        // 静态初始化子程序
                        if (inst.c >= 0 && inst.c < subPrograms.length) {
                            classDef.setStaticInitProgram(subPrograms[inst.c]);
                        }

                        // 解析方法元数据并设置方法程序
                        if (!methodMetaStr.isEmpty()) {
                            for (String mm : methodMetaStr.split(",")) {
                                String[] mp = mm.split(":", 2);
                                if (mp.length == 2) {
                                    int subIdx = Integer.parseInt(mp[1]);
                                    if (subIdx >= 0 && subIdx < subPrograms.length) {
                                        classDef.addMethod(mp[0], subPrograms[subIdx]);
                                    }
                                }
                            }
                        }

                        // 静态方法元数据
                        if (!staticMethodMetaStr.isEmpty()) {
                            for (String mm : staticMethodMetaStr.split(",")) {
                                String[] mp = mm.split(":", 2);
                                if (mp.length == 2) {
                                    int subIdx = Integer.parseInt(mp[1]);
                                    if (subIdx >= 0 && subIdx < subPrograms.length) {
                                        classDef.addStaticMethod(mp[0], subPrograms[subIdx]);
                                    }
                                }
                            }
                        }

                        // 设置构造器程序
                        if (inst.b >= 0 && inst.b < subPrograms.length) {
                            classDef.setConstructorProgram(subPrograms[inst.b]);
                        }

                        if (inst.metadata instanceof Object[] annMeta && annMeta.length == 3) {
                            @SuppressWarnings("unchecked")
                            var classAnns = (List<AriaAnnotation>) annMeta[0];
                            @SuppressWarnings("unchecked")
                            var fieldAnns = (Map<String, List<AriaAnnotation>>) annMeta[1];
                            @SuppressWarnings("unchecked")
                            var methodAnns = (Map<String, List<AriaAnnotation>>) annMeta[2];
                            classDef.setClassAnnotations(classAnns);
                            fieldAnns.forEach(classDef::setFieldAnnotations);
                            methodAnns.forEach(classDef::setMethodAnnotations);

                            // 注册到全局 AnnotationRegistry
                            var registry = Aria.getEngine().getAnnotationRegistry();
                            for (var ann : classAnns) {
                                registry.register(new AnnotationRegistry.AnnotatedTarget(
                                        ann, AnnotationRegistry.AnnotatedTarget.TargetKind.CLASS,
                                        className, null, null));
                            }
                            for (var entry : methodAnns.entrySet()) {
                                for (var ann : entry.getValue()) {
                                    registry.register(new AnnotationRegistry.AnnotatedTarget(
                                            ann, AnnotationRegistry.AnnotatedTarget.TargetKind.METHOD,
                                            entry.getKey(), className, null));
                                }
                            }
                            Map<String, IValue<?>> fieldDefaults = extractFieldDefaults(
                                    classDef.getFieldInitProgram(), context, fieldAnns.keySet());
                            for (var entry : fieldAnns.entrySet()) {
                                IValue<?> fieldValue = fieldDefaults.get(entry.getKey());
                                for (var ann : entry.getValue()) {
                                    registry.register(new AnnotationRegistry.AnnotatedTarget(
                                            ann, AnnotationRegistry.AnnotatedTarget.TargetKind.FIELD,
                                            entry.getKey(), className, fieldValue));
                                }
                            }
                        }

                        registers[inst.dst] = new ObjectValue<>(classDef);

                        // 运行静态字段初始化：self = ObjectValue<ClassDefinition>，
                        // SET_PROP 走 ClassDefinition 分支写入 staticFields
                        if (classDef.getStaticInitProgram() != null) {
                            Context staticCtx = context.createCallContext(
                                    (IValue<?>) registers[inst.dst], EMPTY_ARGS);
                            execute(classDef.getStaticInitProgram(), staticCtx);
                        }
                        break;
                    }

                    case MOVE:
                        registers[inst.dst] = registers[inst.a];
                        break;
                    case NOP:
                        break;

                    case VAR_INC: {
                        // var[a] += 1
                        IValue<?> cur = varRefs[inst.a].getValue();
                        if (cur instanceof NumberValue nv) {
                            varRefs[inst.a].setValue(new NumberValue(nv.value + 1));
                        }
                        break;
                    }
                    case VAR_ADD_CONST: {
                        // var[a] += const[b]
                        IValue<?> cur = varRefs[inst.a].getValue();
                        IValue<?> c = constants[inst.b];
                        if (cur instanceof NumberValue nv && c instanceof NumberValue cv) {
                            varRefs[inst.a].setValue(new NumberValue(nv.value + cv.value));
                        } else {
                            varRefs[inst.a].setValue(cur.add(c));
                        }
                        break;
                    }
                    case VAR_ADD_REG: {
                        // var[a] += r[b]
                        IValue<?> cur = varRefs[inst.a].getValue();
                        IValue<?> val = registers[inst.b];
                        if (cur instanceof NumberValue nv && val instanceof NumberValue rv) {
                            varRefs[inst.a].setValue(new NumberValue(nv.value + rv.value));
                        } else if (cur instanceof MutableStringValue ms) {
                            ms.append(val.stringValue());
                        } else if (cur instanceof RopeString rs) {
                            // Rope 转为 MutableString（累加模式 StringBuilder 更优）
                            MutableStringValue ms = new MutableStringValue(rs.stringValue());
                            ms.append(val.stringValue());
                            varRefs[inst.a].setValue(ms);
                        } else if (cur instanceof StringValue cs && !cs.canBeNumber()) {
                            MutableStringValue ms = new MutableStringValue(cs.stringValue());
                            ms.append(val.stringValue());
                            varRefs[inst.a].setValue(ms);
                        } else {
                            varRefs[inst.a].setValue(cur.add(val));
                        }
                        break;
                    }
                    case SCOPE_INC: {
                        // scope[a] += 1
                        VariableKey key = keys[inst.a];
                        Variable.Normal v = context.getScopeVariable(key);
                        IValue<?> cur = v.ariaValue();
                        if (cur instanceof NumberValue nv) {
                            v.setValue(new NumberValue(nv.value + 1));
                        }
                        break;
                    }
                    case SCOPE_ADD_REG: {
                        // scope[a] += r[b]
                        VariableKey key = keys[inst.a];
                        Variable.Normal v = context.getScopeVariable(key);
                        IValue<?> cur = v.ariaValue();
                        IValue<?> val = registers[inst.b];
                        if (cur instanceof NumberValue nv && val instanceof NumberValue rv) {
                            v.setValue(new NumberValue(nv.value + rv.value));
                        } else {
                            v.setValue(cur.add(val));
                        }
                        break;
                    }
                }
                pc++;
                SandboxConfig sbConfig = sandboxConfig.get();
                if (sbConfig != null) {
                    long[] counter = instructionCounter.get();
                    counter[0]++;
                    if ((counter[0] & 0x3FF) == 0) {
                        if (sbConfig.getMaxInstructions() > 0 && counter[0] > sbConfig.getMaxInstructions()) {
                            throw new AriaRuntimeException("Sandbox: instruction limit exceeded (" + sbConfig.getMaxInstructions() + ")");
                        }
                        if (sbConfig.getMaxExecutionTimeMs() > 0) {
                            long elapsed = System.currentTimeMillis() - executionStartTime.get()[0];
                            if (elapsed > sbConfig.getMaxExecutionTimeMs()) {
                                throw new AriaRuntimeException("Sandbox: execution time exceeded (" + sbConfig.getMaxExecutionTimeMs() + "ms)");
                            }
                        }
                    }
                }
            } catch (AriaException e) {
                if (!tryStack.isEmpty()) {
                    int[] catchInfo = tryStack.pop();
                    registers[0] = new StringValue(e.getMessage());
                    pc = catchInfo[0];
                    continue;
                }
                // 附加源码位置信息后重新抛出
                if (pc < sourceMap.length) {
                    SourceLocation loc = sourceMap[pc];
                    if (loc != null && loc.startLine() >= 0) {
                        throw new AriaRuntimeException(e.getMessage(), loc.startLine(), loc.startColumn(), loc.endLine());
                    }
                }
                throw e;
            } catch (Exception e) {
                if (!tryStack.isEmpty()) {
                    int[] catchInfo = tryStack.pop();
                    registers[0] = new StringValue(e.getMessage() != null ? e.getMessage() : "error");
                    pc = catchInfo[0];
                    continue;
                }
                SourceLocation loc = pc < sourceMap.length ? sourceMap[pc] : null;
                if (loc != null && loc.startLine() >= 0) {
                    throw new AriaRuntimeException(e.getMessage(), loc.startLine(), loc.startColumn(), loc.endLine());
                }
                throw new AriaRuntimeException(e.getMessage() != null ? e.getMessage() : "Internal error", e);
            }
        }

        return Result.noneResult(lastValue);
    }

    void executeRange(IRProgram program, Context context, int startPC, int endPC, IValue<?>[] parentRegisters)
            throws AriaException {
        final IRInstruction[] code = program.getInstructions();
        final IValue<?>[] constants = program.getConstants();
        final VariableKey[] keys = program.getVariableKeys();
        final IRProgram[] subPrograms = program.getSubPrograms();
        final IValue<?>[] registers = new IValue<?>[program.getRegisterCount()];

        // 复制父寄存器状态
        System.arraycopy(parentRegisters, 0, registers, 0,
                Math.min(parentRegisters.length, registers.length));

        // 创建一个只包含 [startPC, endPC) 范围指令的子程序来执行
        int rangeLen = endPC - startPC;
        IRInstruction[] rangeCode = new IRInstruction[rangeLen];
        System.arraycopy(code, startPC, rangeCode, 0, rangeLen);

        SourceLocation[] rangeSrcMap = new SourceLocation[rangeLen];
        SourceLocation[] fullSrcMap = program.getSourceMap();
        if (fullSrcMap.length >= endPC) {
            System.arraycopy(fullSrcMap, startPC, rangeSrcMap, 0, rangeLen);
        }

        // 调整跳转目标偏移
        for (IRInstruction inst : rangeCode) {
            switch (inst.opcode) {
                case JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE, JUMP_IF_NONE, TRY_BEGIN:
                    inst.a -= startPC;
                    break;
                default:
                    break;
            }
        }

        IRProgram subProg = new IRProgram(program.getName() + "<async>");
        subProg.setInstructions(rangeCode);
        subProg.setConstants(constants);
        subProg.setVariableKeys(keys);
        subProg.setRegisterCount(program.getRegisterCount());
        subProg.setSourceMap(rangeSrcMap);
        subProg.setSubPrograms(subPrograms);

        execute(subProg, context);
    }

    private static String getTypeName(IValue<?> value) {
        if (value instanceof NumberValue) return "number";
        if (value instanceof StringValue) return "string";
        if (value instanceof BooleanValue) return "boolean";
        if (value instanceof ListValue) return "list";
        if (value instanceof MapValue) return "map";
        if (value instanceof NoneValue) return "none";
        if (value instanceof AriaClassValue cv && cv.jvmValue() != null) return cv.jvmValue().getTypeName();
        if (value instanceof ObjectValue<?> ov) return ov.jvmValue().getTypeName();
        if (value instanceof FunctionValue) return "function";
        return "unknown";
    }

    IValue<?> executeInline(IRInstruction[] code, IValue<?>[] constants, VariableKey[] keys,
                            IRProgram[] subPrograms, int regCount, Context context) throws AriaException {
        return executeInline(code, constants, keys, subPrograms, regCount, context, null);
    }

    IValue<?> executeInline(IRInstruction[] code, IValue<?>[] constants, VariableKey[] keys,
                            IRProgram[] subPrograms, int regCount, Context context, IRProgram program) throws AriaException {
        int[] cd = callDepthLocalTL.get();
        int depth = cd[0];
        if (depth >= MAX_CALL_DEPTH) {
            throw new AriaRuntimeException("Stack overflow: call depth exceeded " + MAX_CALL_DEPTH);
        }
        cd[0] = depth + 1;
        try {
            // 自递归检测：带 IRProgram 缓存
            // flag: 0=未检测, 1=CALL_STATIC自递归, 3=CALL自递归, -1=确定不是, 2=暂不确定
            // selfName 用于校验“递归调用目标确实是本函数自己”，否则互递归（如 fa 内调 fb）
            // 会被这两个数值快路径误当成自递归 → 把对 fb 的调用算成调回 fa（深度≥3 重入时显式出错）。
            // 这是与 JIT detectSelfRecursion(F1, commit acaaed6) 同源的缺陷，存在于解释器并行快路径里。
            final String selfName = (program != null) ? program.getName() : null;
            if (program != null) {
                byte flag = program.getSelfRecursiveFlag();
                if (flag == 1) {
                    return executeSelfRecursiveNumeric(code, constants, regCount, context);
                }
                if (flag == 3) {
                    return executeCallRecursiveNumeric(code, constants, regCount, context);
                }
                if (flag == 0 || flag == 2) {
                    byte result = checkNumericSelfRecursive(code, constants, selfName);
                    if (result > 0) {
                        program.setSelfRecursiveFlag((byte) 1);
                        return executeSelfRecursiveNumeric(code, constants, regCount, context);
                    } else if (result == 0) {
                        program.setSelfRecursiveFlag((byte) 2); // 暂不确定，下次重试
                    } else {
                        // CALL_STATIC 不匹配，尝试 CALL 自递归（同样校验调用目标名 == 本函数名）
                        if (checkCallSelfRecursive(code, constants, keys, selfName) >= 0) {
                            program.setSelfRecursiveFlag((byte) 3);
                            return executeCallRecursiveNumeric(code, constants, regCount, context);
                        }
                        program.setSelfRecursiveFlag((byte) -1);
                    }
                }
            } else if (checkNumericSelfRecursive(code, constants, selfName) > 0) {
                return executeSelfRecursiveNumeric(code, constants, regCount, context);
            } else if (checkCallSelfRecursive(code, constants, keys, selfName) >= 0) {
                return executeCallRecursiveNumeric(code, constants, regCount, context);
            }
            boolean needVarRefs = true, needScopeRefs = true;
            if (program != null) {
                byte vf = program.getHasVarOpsFlag();
                if (vf < 0) { vf = hasVarOps(code) ? (byte) 1 : (byte) 0; program.setHasVarOpsFlag(vf); }
                byte sf = program.getHasScopeOpsFlag();
                if (sf < 0) { sf = hasScopeOps(code) ? (byte) 1 : (byte) 0; program.setHasScopeOpsFlag(sf); }
                needVarRefs = vf != 0;
                needScopeRefs = sf != 0;
            }
            return executeInlineInternal(code, constants, keys, subPrograms, regCount, context,
                    needVarRefs, needScopeRefs);
        } finally {
            cd[0] = depth;
        }
    }

    private IValue<?> executeInlineInternal(IRInstruction[] code, IValue<?>[] constants, VariableKey[] keys,
                                            IRProgram[] subPrograms, int regCount, Context context,
                                            boolean needVarRefs, boolean needScopeRefs) throws AriaException {

        final IValue<?>[] registers = new IValue<?>[regCount];
        Arrays.fill(registers, NoneValue.NONE);
        final boolean hasKeys = keys != null && keys.length > 0;
        final VariableReference[] scopeRefs = (hasKeys && needScopeRefs) ? new VariableReference[keys.length] : null;
        // 预解析 var 引用到局部数组（不用 inst.cache，避免跨 Context 串）
        final VariableReference[] varRefs = (hasKeys && needVarRefs) ? new VariableReference[keys.length] : null;

        int pc = 0;
        final int len = code.length;

        while (pc < len) {
            final IRInstruction inst = code[pc];
            try {
                switch (inst.opcode) {
                    case LOAD_CONST -> registers[inst.dst] = constants[inst.a];
                    case LOAD_NONE -> registers[inst.dst] = NoneValue.NONE;
                    case LOAD_TRUE -> registers[inst.dst] = BooleanValue.TRUE;
                    case LOAD_FALSE -> registers[inst.dst] = BooleanValue.FALSE;
                    case LOAD_VAR -> {
                        VariableReference ref = varRefs[inst.a];
                        if (ref == null) { ref = context.getLocalStorage().getVarVariable(keys[inst.a]); varRefs[inst.a] = ref; }
                        registers[inst.dst] = ref.getValue();
                    }
                    case LOAD_VAL -> {
                        // 函数体内 `val.x` 点读:此前 executeInlineInternal 无此 case → 恒 none。
                        // val 存储优先,空则回退 scope→var(脚本 val.x= 实际入 scope)。
                        IValue<?> lv = context.getLocalStorage().getValVariable(keys[inst.a]).getValue();
                        registers[inst.dst] = (lv == null || lv instanceof NoneValue)
                                ? resolveValFallback(context, keys[inst.a], lv) : lv;
                    }
                    case STORE_VAR -> {
                        VariableReference ref = varRefs[inst.a];
                        if (ref == null) { ref = context.getLocalStorage().getVarVariable(keys[inst.a]); varRefs[inst.a] = ref; }
                        ref.setValue(registers[inst.dst]);
                    }
                    case LOAD_SELF -> registers[inst.dst] = context.getSelf();
                    case LOAD_ARG -> registers[inst.dst] = context.getArg(inst.a);
                    case LOAD_ARGS -> {
                        IValue<?>[] args = context.getArgs();
                        List<IValue<?>> list = new ArrayList<>(args.length);
                        Collections.addAll(list, args);
                        registers[inst.dst] = new ListValue(list);
                    }
                    case LOAD_SCOPE -> {
                        VariableReference ref = scopeRefs != null ? scopeRefs[inst.a] : null;
                        if (ref == null) {
                            VariableReference scopeRef = context.getScopeStack().getExisting(keys[inst.a]);
                            if (scopeRef != null) {
                                ref = scopeRef;
                            } else {
                                VariableReference varRef = context.getLocalStorage().getVarVariableExisting(keys[inst.a]);
                                if (varRef != null) {
                                    ref = varRef;
                                } else {
                                    // val 回退(同主循环):脚本 val.x= 写入 val 存储,裸读也应取到
                                    ValueReference valRef2 = context.getLocalStorage().getValVariableExisting(keys[inst.a]);
                                    if (valRef2 != null && valRef2.isAssigned()) {
                                        registers[inst.dst] = valRef2.getValue();
                                        pc++; continue;
                                    }
                                    ref = context.getScopeStack().get(keys[inst.a]);
                                }
                            }
                            if (scopeRefs != null) scopeRefs[inst.a] = ref;
                        }
                        registers[inst.dst] = ref.getValue();
                    }
                    case STORE_SCOPE -> {
                        VariableReference ref = scopeRefs != null ? scopeRefs[inst.a] : null;
                        if (ref == null) {
                            ref = context.getScopeStack().get(keys[inst.a]);
                            if (scopeRefs != null) scopeRefs[inst.a] = ref;
                        }
                        ref.setValue(registers[inst.dst]);
                    }
                    case STORE_VAL -> {
                        ValueReference vref = context.getLocalStorage().getValVariable(keys[inst.a]);
                        if (vref.isAssigned()) {
                            throw new AriaRuntimeException("Cannot reassign immutable val '"
                                    + keys[inst.a].getName() + "' (use var for mutable variables)");
                        }
                        vref.setValue(registers[inst.dst]);
                    }
                    case ADD -> {
                        IValue<?> la = registers[inst.a], ra = registers[inst.b];
                        if (la instanceof NumberValue ln && ra instanceof NumberValue rn) {
                            registers[inst.dst] = new NumberValue(ln.value + rn.value);
                        } else if (la instanceof StringValue ls && ra instanceof StringValue rs
                                && !ls.canBeNumber() && !rs.canBeNumber()) {
                            registers[inst.dst] = RopeString.concat(new RopeString(ls.stringValue()), rs.stringValue());
                        } else if (la instanceof RopeString lrs) {
                            registers[inst.dst] = RopeString.concat(lrs, ra.stringValue());
                        } else { registers[inst.dst] = la.add(ra); }
                    }
                    case SUB -> {
                        IValue<?> la = registers[inst.a], ra = registers[inst.b];
                        if (la instanceof NumberValue ln && ra instanceof NumberValue rn) {
                            registers[inst.dst] = new NumberValue(ln.value - rn.value);
                        } else { registers[inst.dst] = la.sub(ra); }
                    }
                    case MUL -> {
                        IValue<?> la = registers[inst.a], ra = registers[inst.b];
                        if (la instanceof NumberValue ln && ra instanceof NumberValue rn) {
                            registers[inst.dst] = new NumberValue(ln.value * rn.value);
                        } else { registers[inst.dst] = la.mul(ra); }
                    }
                    case DIV -> {
                        IValue<?> la = registers[inst.a], ra = registers[inst.b];
                        if (la instanceof NumberValue ln && ra instanceof NumberValue rn) {
                            registers[inst.dst] = new NumberValue(rn.value == 0 ? 0 : ln.value / rn.value); // Shimmer 对齐：除零->0
                        } else { registers[inst.dst] = la.div(ra); }
                    }
                    case LE -> registers[inst.dst] = registers[inst.a].le(registers[inst.b]);
                    case LT -> registers[inst.dst] = registers[inst.a].lt(registers[inst.b]);
                    case GT -> registers[inst.dst] = registers[inst.a].gt(registers[inst.b]);
                    case GE -> registers[inst.dst] = registers[inst.a].ge(registers[inst.b]);
                    case EQ -> registers[inst.dst] = registers[inst.a].eq(registers[inst.b]);
                    case NE -> registers[inst.dst] = registers[inst.a].ne(registers[inst.b]);
                    case IN_RANGE -> registers[inst.dst] = evalInRange(registers[inst.a], registers[inst.b]);
                    case IN_CHECK -> registers[inst.dst] = evalInCheck(registers[inst.a], registers[inst.b]);
                    case INSTANCEOF_CHECK -> registers[inst.dst] = evalInstanceof(registers[inst.a], registers[inst.b]);
                    case NOT -> registers[inst.dst] = BooleanValue.of(!registers[inst.a].booleanValue());
                    case JUMP -> { pc = inst.a; continue; }
                    case JUMP_IF_FALSE -> {
                        if (!registers[inst.dst].booleanValue()) { pc = inst.a; continue; }
                    }
                    case JUMP_IF_TRUE -> {
                        if (registers[inst.dst].booleanValue()) { pc = inst.a; continue; }
                    }
                    case JUMP_IF_NONE -> {
                        if (registers[inst.dst] instanceof NoneValue) { pc = inst.a; continue; }
                    }
                    case PUSH_SCOPE -> {
                        context.pushScope();
                        if (scopeRefs != null) Arrays.fill(scopeRefs, null);
                    }
                    case POP_SCOPE -> {
                        context.popScope();
                        if (scopeRefs != null) Arrays.fill(scopeRefs, null);
                    }
                    case RETURN -> {
                        return inst.dst >= 0 ? registers[inst.dst] : NoneValue.NONE;
                    }
                    case CALL -> {
                        IValue<?> callee = registers[inst.a];
                        int argCount = inst.b;
                        int argBase = inst.c;
                        if (callee instanceof FunctionValue fv) {
                            ICallable callable = fv.getCallable();
                            // 快速 lambda 内联
                            if (callable instanceof FastBinaryLambda fbl
                                    && argCount == 2
                                    && registers[argBase] instanceof NumberValue na
                                    && registers[argBase + 1] instanceof NumberValue nb) {
                                registers[inst.dst] = new NumberValue(fbl.invokeFastDouble(na.value, nb.value));
                            } else if (callable instanceof FastUnaryLambda ful
                                    && argCount == 1
                                    && registers[argBase] instanceof NumberValue na2) {
                                registers[inst.dst] = new NumberValue(ful.invokeFastDouble(na2.value));
                            } else {
                                registers[inst.dst] = callable.invoke(
                                        new InvocationData(context, null, registers, argBase, argCount));
                            }
                        } else if (callee instanceof StoreOnlyValue<?> sov && sov.jvmValue() instanceof ICallable ic) {
                            registers[inst.dst] = ic.invoke(
                                    new InvocationData(context, null, registers, argBase, argCount));
                        } else {
                            // Shimmer 对齐：非函数值加括号 → 吞掉括号，返回原值
                            registers[inst.dst] = callee;
                        }
                    }
                    case CALL_STATIC -> {
                        int argCount = inst.b;
                        int argBase = inst.a;

                        ICallable cached2 = (ICallable) inst.cache;
                        if (cached2 != null) {
                            // 沙箱激活时 cache 命中也要过命名空间检查（防止预热绕过，见主循环同处注释）
                            SandboxConfig sbHit2 = sandboxConfig.get();
                            if (sbHit2 != null && inst.name != null) {
                                int di = inst.name.indexOf('.');
                                String hns = di >= 0 ? inst.name.substring(0, di) : inst.name;
                                if (!sbHit2.isNamespaceAllowed(hns)) {
                                    throw new AriaRuntimeException("Sandbox: namespace '" + hns + "' is not allowed");
                                }
                            }
                            registers[inst.dst] = cached2.invoke(new InvocationData(context, null, registers, argBase, argCount));
                            break;
                        }

                        String fn = inst.name;
                        // 沙箱过滤
                        SandboxConfig sbCfg2 = sandboxConfig.get();
                        if (sbCfg2 != null && fn != null) {
                            int dotIdx2 = fn.indexOf('.');
                            String ns2 = dotIdx2 >= 0 ? fn.substring(0, dotIdx2) : fn;
                            if (!sbCfg2.isNamespaceAllowed(ns2)) {
                                throw new AriaRuntimeException("Sandbox: namespace '" + ns2 + "' is not allowed");
                            }
                        }
                        // 内联数学函数
                        if (argCount == 1 && fn != null) {
                            double arg = registers[argBase].numberValue();
                            double result = switch (fn) {
                                case "math.sin" -> Math.sin(arg);
                                case "math.cos" -> Math.cos(arg);
                                case "math.tan" -> Math.tan(arg);
                                case "math.abs" -> Math.abs(arg);
                                case "math.floor" -> Math.floor(arg);
                                case "math.ceil" -> Math.ceil(arg);
                                case "math.round" -> Math.round(arg);
                                case "math.sqrt" -> Math.sqrt(arg);
                                case "math.log" -> Math.log(arg);
                                default -> Double.NaN;
                            };
                            if (!Double.isNaN(result)) {
                                registers[inst.dst] = new NumberValue(result);
                                break;
                            }
                        }
                        // 参数数组
                        IValue<?>[] callArgs = argCount == 1
                                ? new IValue<?>[]{ registers[argBase] }
                                : argCount == 2
                                ? new IValue<?>[]{ registers[argBase], registers[argBase + 1] }
                                : copyArgs(registers, argBase, argCount);
                        // CallableManager 查找
                        ICallable callable = null;
                        if (fn != null) {
                            int dot = fn.indexOf('.');
                            if (dot >= 0) {
                                callable = CallableManager.INSTANCE.getStaticFunction(fn.substring(0, dot), fn.substring(dot + 1));
                            }
                            if (callable == null) {
                                callable = CallableManager.INSTANCE.getStaticFunction("", fn);
                            }
                        }
                        if (callable != null) {
                            inst.cache = callable;
                            registers[inst.dst] = callable.invoke(new InvocationData(context, null, callArgs));
                        } else if (fn != null && fn.indexOf('.') >= 0) {
                            int dot = fn.indexOf('.');
                            IValue<?> obj = resolveVariable(context, fn.substring(0, dot));
                            registers[inst.dst] = dispatchMethodCall(context, obj, fn.substring(dot + 1), callArgs, argCount);
                        } else if ("super".equals(fn)) {
                            IValue<?> selfVal = context.getSelf();
                            if (selfVal instanceof AriaClassValue cv && cv.jvmValue() != null) {
                                ClassDefinition classDef = cv.jvmValue().getClassDefinition();
                                if (classDef != null && classDef.getParent() != null) {
                                    ClassDefinition parentDef = classDef.getParent();
                                    if (parentDef.getFieldInitProgram() != null) {
                                        execute(parentDef.getFieldInitProgram(), context.createCallContext(selfVal, new IValue<?>[0]));
                                    }
                                    if (parentDef.getConstructorProgram() != null) {
                                        execute(parentDef.getConstructorProgram(), context.createCallContext(selfVal, callArgs));
                                    }
                                }
                            }
                            registers[inst.dst] = NoneValue.NONE;
                        } else {
                            // 裸函数名：查 scope/var
                            IValue<?> fnVal = resolveVariable(context, fn);
                            if (fnVal instanceof FunctionValue fvCall) {
                                inst.cache = fvCall.getCallable();
                                registers[inst.dst] = fvCall.getCallable().invoke(new InvocationData(context, null, callArgs));
                            } else {
                                registers[inst.dst] = NoneValue.NONE;
                            }
                        }
                    }
                    case CALL_CONSTRUCTOR -> {
                        int argCount = inst.b;
                        int argBase = inst.a;
                        IValue<?>[] callArgs = new IValue<?>[argCount];
                        for (int i = 0; i < argCount; i++) callArgs[i] = registers[argBase + i];
                        // 缓存构造器查找
                        ICallable ctor = (ICallable) inst.cache;
                        if (ctor == null) {
                            ctor = CallableManager.INSTANCE.getConstructor(inst.name);
                            inst.cache = ctor;
                        }
                        if (ctor != null) {
                            registers[inst.dst] = ctor.invoke(new InvocationData(context, null, callArgs));
                        } else {
                            registers[inst.dst] = NoneValue.NONE;
                        }
                    }
                    case GET_INDEX -> {
                        IValue<?> obj = registers[inst.a];
                        IValue<?> idx = registers[inst.b];
                        if (obj instanceof ListValue lv) {
                            int index = (int) idx.numberValue();
                            List<IValue<?>> list = lv.jvmValue();
                            if (index >= 0 && index < list.size()) {
                                registers[inst.dst] = list.get(index);
                            } else if (inst.c == 1) {
                                registers[inst.dst] = NoneValue.NONE; // for-in 迭代哨兵
                            } else {
                                throw new AriaRuntimeException("列表索引越界: " + index + " (size=" + list.size() + ")");
                            }
                        } else if (obj instanceof MapValue mv) {
                            if (inst.c == 1) {
                                registers[inst.dst] = mapEntryAt(mv.jvmValue(), (int) idx.numberValue());
                            } else {
                                IValue<?> val = mv.jvmValue().get(idx);
                                if (val == null) {
                                    val = mv.jvmValue().get(new StringValue(idx.stringValue()));
                                }
                                registers[inst.dst] = val != null ? val : NoneValue.NONE;
                            }
                        } else if (obj instanceof SmallMapValue sm) {
                            registers[inst.dst] = sm.get(idx);
                        } else if (obj instanceof StringValue sv) {
                            int index = (int) idx.numberValue();
                            String s = sv.stringValue();
                            registers[inst.dst] = (index >= 0 && index < s.length())
                                    ? new StringValue(String.valueOf(s.charAt(index)))
                                    : NoneValue.NONE;
                        } else if (obj instanceof ObjectValue<?> ov && ov.jvmValue() instanceof RangeObject range) {
                            double val = range.getStart() + idx.numberValue() * range.getStep();
                            if (range.getStep() > 0 ? val < range.getEnd() : val > range.getEnd()) {
                                if (registers[inst.dst] instanceof NumberValue nv) { nv.value = val; }
                                else { registers[inst.dst] = new NumberValue(val); }
                            } else {
                                registers[inst.dst] = NoneValue.NONE;
                            }
                        } else if (obj instanceof ObjectValue<?> ov) {
                            // IAriaObject 元素访问 obj['key'] — 惰性属性 auto-invoke(对齐 GET_PROP)
                            Variable elem = ov.jvmValue().getElement(idx.stringValue());
                            registers[inst.dst] = resolveLazyProperty(elem != null ? elem.ariaValue() : null, context);
                        } else {
                            registers[inst.dst] = NoneValue.NONE;
                        }
                    }
                    case INC -> registers[inst.dst] = new NumberValue(registers[inst.a].numberValue() + 1);
                    case MOVE -> registers[inst.dst] = registers[inst.a];
                    case NEW_FUNCTION -> {
                        final IRProgram subProg = subPrograms[inst.a];
                        final Context capturedCtx = context.snapshotForClosure();
                        if (subProg.isCompiled()) {
                            final ICallable compiled = subProg.getCompiledCode();
                            if (subProg.isJitContextFree()) {
                                registers[inst.dst] = new FunctionValue(compiled);
                            } else {
                                registers[inst.dst] = new FunctionValue(data -> {
                                    Context cc = capturedCtx.createCallContext(
                                            data.getTarget() instanceof IValue<?> t ? t : NoneValue.NONE, data.getArgs());
                                    return compiled.invoke(new InvocationData(cc, null, data));
                                });
                            }
                        } else {
                            ICallable fastLambda = tryCreateFastLambda(subProg);
                            if (fastLambda != null) {
                                registers[inst.dst] = new FunctionValue(fastLambda);
                            } else {
                                final IRInstruction[] sc = subProg.getInstructions();
                                final IValue<?>[] sconst = subProg.getConstants();
                                final VariableKey[] skeys = subProg.getVariableKeys();
                                final IRProgram[] ssubs = subProg.getSubPrograms();
                                final int sreg = Math.max(subProg.getRegisterCount(), 1);
                                final Interpreter self = this;
                                final boolean lightCtx = capturedCtx.getScopeStack().depth() <= 0;
                                registers[inst.dst] = new FunctionValue(data -> {
                                    int cnt = subProg.incrementExecCount();
                                    if (cnt == JITCompiler.getThreshold()) {
                                        try {
                                            Context jitCtx = data.getContext() != null ? data.getContext() : capturedCtx;
                                            if (jitCompiler.canCompile(subProg)) {
                                                ICallable c = jitCompiler.compile(subProg, jitCtx);
                                                if (c != null) subProg.setCompiledCode(c);
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                    if (subProg.isCompiled()) {
                                        return subProg.getCompiledCode().invoke(data);
                                    }
                                    Context cc = lightCtx
                                            ? capturedCtx.createLightCallContext(
                                            data.getTarget() instanceof IValue<?> t ? t : NoneValue.NONE, data.getArgs())
                                            : capturedCtx.createCallContext(
                                            data.getTarget() instanceof IValue<?> t ? t : NoneValue.NONE, data.getArgs());
                                    return self.executeInline(sc, sconst, skeys, ssubs, sreg, cc, subProg);
                                });
                            } // else (not fast lambda)
                        } // else (not compiled)
                        // 互递归组编译用：让函数值记住其子程序，编译时可经 var 名取到兄弟函数 IR（嵌套组）
                        if (registers[inst.dst] instanceof FunctionValue fvSrcInline) {
                            fvSrcInline.setSourceProgram(subProg);
                        }
                    }
                    case VAR_INC -> {
                        VariableReference ref = varRefs[inst.a];
                        if (ref == null) { ref = context.getLocalStorage().getVarVariable(keys[inst.a]); varRefs[inst.a] = ref; }
                        IValue<?> cur = ref.getValue();
                        if (cur instanceof NumberValue nv) ref.setValue(new NumberValue(nv.value + 1));
                    }
                    case VAR_ADD_CONST -> {
                        VariableReference ref = varRefs[inst.a];
                        if (ref == null) { ref = context.getLocalStorage().getVarVariable(keys[inst.a]); varRefs[inst.a] = ref; }
                        IValue<?> cur = ref.getValue();
                        IValue<?> c = constants[inst.b];
                        if (cur instanceof NumberValue nv && c instanceof NumberValue cv) {
                            ref.setValue(new NumberValue(nv.value + cv.value));
                        } else {
                            ref.setValue(cur.add(c));
                        }
                    }
                    case VAR_ADD_REG -> {
                        VariableReference ref = varRefs[inst.a];
                        if (ref == null) { ref = context.getLocalStorage().getVarVariable(keys[inst.a]); varRefs[inst.a] = ref; }
                        IValue<?> cur = ref.getValue();
                        IValue<?> val = registers[inst.b];
                        if (cur instanceof NumberValue nv && val instanceof NumberValue rv) {
                            ref.setValue(new NumberValue(nv.value + rv.value));
                        } else if (cur instanceof MutableStringValue ms) {
                            ms.append(val.stringValue());
                        } else if (cur instanceof RopeString rs) {
                            MutableStringValue ms = new MutableStringValue(rs.stringValue());
                            ms.append(val.stringValue());
                            ref.setValue(ms);
                        } else if (cur instanceof StringValue cs && !cs.canBeNumber()) {
                            MutableStringValue ms = new MutableStringValue(cs.stringValue());
                            ms.append(val.stringValue());
                            ref.setValue(ms);
                        } else {
                            ref.setValue(cur.add(val));
                        }
                    }
                    case NOP -> {}
                    default -> {
                        // 回退到完整 execute
                        IRProgram prog = new IRProgram("inline");
                        prog.setInstructions(code);
                        prog.setConstants(constants);
                        prog.setVariableKeys(keys);
                        prog.setSubPrograms(subPrograms);
                        prog.setRegisterCount(regCount);
                        return execute(prog, context).getValue();
                    }
                }
                pc++;
            } catch (AriaException e) {
                throw e;
            } catch (Exception e) {
                throw new AriaRuntimeException("Inline execution error at PC=" + pc + ": " + e.getMessage(), e);
            }
        }
        return NoneValue.NONE;
    }            private static ICallable tryCreateFastLambda(IRProgram sub) {
        if (sub == null) return null;
        IRInstruction[] c = sub.getInstructions();
        // 分析 lambda 的 IR 模式
        // 寻找：LOAD_ARG(0) + LOAD_ARG(1) + ADD/SUB/MUL/DIV + RETURN
        int loadArgCount = 0;
        int maxArgIndex = -1;
        IROpCode arithOp = null;
        for (IRInstruction inst : c) {
            switch (inst.opcode) {
                case PUSH_SCOPE, POP_SCOPE, NOP, MOVE -> {}
                case LOAD_ARG -> { loadArgCount++; maxArgIndex = Math.max(maxArgIndex, inst.a); }
                case LOAD_CONST -> {}
                case ADD, SUB, MUL, DIV -> { if (arithOp == null) arithOp = inst.opcode; else return null; }
                case RETURN -> {}
                default -> { return null; } // 不支持的指令
            }
        }
        if (arithOp == null) return null;
        // 只有 args[0] op args[1] 模式才用 FastBinaryLambda（两个不同参数）
        if (loadArgCount == 2 && maxArgIndex == 1) {
            return new FastBinaryLambda(arithOp);
        }
        return null; // 其他模式不优化，走 executeInline
    }

    /**
     * `~~` 模糊匹配:右值是 Range 则做区间包含(RangeObject.contains,半开 [start,end));
     * 否则退化为相等性检查(等价 ==),与文档一致。此前实现只认 ListValue 且无 == 回退,故恒 false。
     */
    private static IValue<?> evalInRange(IValue<?> target, IValue<?> range) {
        if (range instanceof ObjectValue<?> ov && ov.jvmValue() instanceof RangeObject ro) {
            return BooleanValue.of(ro.contains(target.numberValue()));
        }
        if (range instanceof ListValue lv && lv.jvmValue().size() >= 2) {
            double val = target.numberValue();
            return BooleanValue.of(val >= lv.jvmValue().get(0).numberValue()
                    && val <= lv.jvmValue().get(1).numberValue());
        }
        return BooleanValue.of(target.eq(range).booleanValue());
    }

    /** `key in obj` 成员检查:Map 含键 / List 索引有效 / 类实例含字段 / 对象含成员。 */
    private static IValue<?> evalInCheck(IValue<?> key, IValue<?> obj) {
        boolean found = false;
        if (obj instanceof MapValue mv) {
            found = mv.jvmValue().containsKey(new StringValue(key.stringValue()));
        } else if (obj instanceof ListValue lv) {
            int idx = (int) key.numberValue();
            found = idx >= 0 && idx < lv.jvmValue().size();
        } else if (obj instanceof AriaClassValue cv && cv.jvmValue() != null) {
            found = cv.jvmValue().getFields().containsKey(key.stringValue());
        } else if (obj instanceof ObjectValue<?> ov) {
            Variable v = ov.jvmValue().getVariable(key.stringValue());
            found = v.ariaValue() != null && !(v.ariaValue() instanceof NoneValue);
        }
        return BooleanValue.of(found);
    }

    /**
     * 惰性属性求值(对齐 Shimmer 的 auto-invoke 语义)。
     * ArcartX 把控件属性(如 {@code self.y})存成 {@code StoreOnlyValue<CallableWithInvoker>}(AttributeCallable)——
     * 属性被读取用作值时,Shimmer 会以当前上下文自动调用求值(Additive/Multiplicative/Relational/... 每个操作数消费点)。
     * Aria 在属性读取(GET_PROP)处统一做等价求值:仅当解析出的值包着 {@link CallableWithInvoker} 时自动调用,
     * 不影响 Aria class 的方法引用({@link FunctionValue},jvmValue 为 ICallable,故 {@code self.method} 仍可作引用/被 {@code ()} 调用)。
     */
    public static IValue<?> resolveLazyProperty(IValue<?> v, Context ctx) throws AriaException {
        if (v == null) return NoneValue.NONE;
        if (v.jvmValue() instanceof CallableWithInvoker cwi) {
            return cwi.invoke(ctx, EMPTY_ARGS);
        }
        return v;
    }

    /**
     * 对象属性读取(GET_PROP for ObjectValue)的完整解析,对齐 Shimmer 的 {@code Dot.getNode} 顺序：
     * <b>先查注册对象函数</b>(如 {@code @AriaInvokeHandler("parent")}),命中则以零用户参数调用并返回其(已包装)结果
     * —— 覆盖"{@code self.parent} 不带括号也能取父对象"的 Shimmer 语义(Aria 的 GET_PROP 原本只查 getVariable,
     * 从不查对象函数,故 self.parent 恒 NONE);否则走 {@code getVariable} + 惰性属性 auto-invoke({@link #resolveLazyProperty})。
     */
    public static IValue<?> resolveObjectProperty(ObjectValue<?> ov, String propName, Context ctx) throws AriaException {
        IAriaObject so = ov.jvmValue();
        if (so == null) return NoneValue.NONE;
        ICallable objFunc = CallableManager.INSTANCE.getObjectFunction(so.getClass(), propName);
        if (objFunc != null) {
            return callObjectFunction(objFunc, ctx, ov, EMPTY_ARGS);
        }
        Variable v = so.getVariable(propName);
        return resolveLazyProperty(v != null ? v.ariaValue() : null, ctx);
    }

    /** obj.name 的成员取值：用于"非函数括号后缀吞掉括号后返回原成员值"(obj.field() == obj.field)。 */
    private static IValue<?> memberOf(IValue<?> obj, String name) {
        if (obj instanceof AriaClassValue cv && cv.jvmValue() != null) {
            var f = cv.jvmValue().getFields().get(name);
            if (f != null) return f.getValue();
        } else if (obj instanceof ObjectValue<?> ov) {
            Variable v = ov.jvmValue().getVariable(name);
            if (v != null && v.ariaValue() != null) return v.ariaValue();
        }
        return NoneValue.NONE;
    }

    /** `a instanceof B` 类型判断:脚本类沿继承链匹配;其它按类型名比较。 */
    private static IValue<?> evalInstanceof(IValue<?> obj, IValue<?> type) {
        boolean match = false;
        if (type instanceof ObjectValue<?> ov && ov.jvmValue() instanceof ClassDefinition cd) {
            if (obj instanceof AriaClassValue cv && cv.jvmValue() != null) {
                ClassDefinition objDef = cv.jvmValue().getClassDefinition();
                while (objDef != null) {
                    if (objDef.getName().equals(cd.getName())) { match = true; break; }
                    objDef = objDef.getParent();
                }
            }
        } else {
            String typeName = type.stringValue();
            String objType = getTypeName(obj);
            match = objType.equals(typeName) || objType.startsWith("ClassDef:" + typeName);
        }
        return BooleanValue.of(match);
    }

    /**
     * for-in 迭代模式下取 Map 的第 i 个 [key, value] 对(插入序),越界返回 none。
     * 用于 `for (k, v in map)` / `for (e in map)`——GET_INDEX 普通模式按键查找无法遍历 Map。
     */
    public static IValue<?> mapEntryAt(java.util.Map<IValue<?>, IValue<?>> m, int i) {
        if (i < 0 || i >= m.size()) return NoneValue.NONE;
        int j = 0;
        for (java.util.Map.Entry<IValue<?>, IValue<?>> e : m.entrySet()) {
            if (j++ == i) {
                List<IValue<?>> pair = new ArrayList<>(2);
                pair.add(e.getKey());
                pair.add(e.getValue());
                return new ListValue(pair);
            }
        }
        return NoneValue.NONE;
    }

    /** LOAD_VAL 回退解析:val 存储为空时按 scope→var 查找(脚本 `val.x=` 实际入 scope)。 */
    private static IValue<?> resolveValFallback(Context context, VariableKey key, IValue<?> current) {
        VariableReference sr = context.getScopeStack().getExisting(key);
        if (sr != null) {
            IValue<?> sv = sr.getValue();
            if (sv != null && !(sv instanceof NoneValue)) return sv;
        }
        VariableReference vr = context.getLocalStorage().getVarVariableExisting(key);
        if (vr != null) {
            IValue<?> vv = vr.getValue();
            if (vv != null && !(vv instanceof NoneValue)) return vv;
        }
        return current == null ? NoneValue.NONE : current;
    }

    private void tryJITCompile(IRProgram program, Context context) {
        try {
            if (jitCompiler.canCompile(program)) {
                ICallable compiled = jitCompiler.compile(program, context);
                if (compiled != null) program.setCompiledCode(compiled);
            }
        } catch (Throwable ignored) {}
    }

    private static IValue<?> resolveVariable(Context context, String name) {
        VariableKey key = VariableKey.of(name);
        // scope
        VariableReference scopeRef = context.getScopeStack().getExisting(key);
        if (scopeRef != null) {
            IValue<?> val = scopeRef.getValue();
            if (val != null && !(val instanceof NoneValue)) return val;
        }
        // var
        VariableReference varRef = context.getLocalStorage().getVarVariableExisting(key);
        if (varRef != null) {
            IValue<?> val = varRef.getValue();
            if (val != null && !(val instanceof NoneValue)) return val;
        }
        // val(脚本 val.x= 现写入 val 存储;裸名 x / x.method() 的接收者解析也须查 val)
        ValueReference valRef = context.getLocalStorage().getValVariableExisting(key);
        if (valRef != null && valRef.isAssigned()) {
            IValue<?> val = valRef.getValue();
            if (val != null && !(val instanceof NoneValue)) return val;
        }
        // global
        Variable.Normal gv = context.getGlobalVariable(key);
        if (gv.ariaValue() != null && !(gv.ariaValue() instanceof NoneValue)) {
            return gv.ariaValue();
        }
        return NoneValue.NONE;
    }

    private IValue<?> dispatchMethodCall(Context context, IValue<?> obj, String methodName,
                                         IValue<?>[] callArgs, int argCount) throws AriaException {
        if (obj == null || obj instanceof NoneValue) return NoneValue.NONE;

        // ClassDefinition — 静态方法调用 ClassName.staticMethod(args)
        if (obj instanceof ObjectValue<?> ov && ov.jvmValue() instanceof ClassDefinition cd) {
            IRProgram sm = cd.findStaticMethod(methodName);
            if (sm != null) {
                Context callCtx = context.createCallContext(null, callArgs);
                return execute(sm, callCtx).getValue();
            }
            // 静态字段恰好是 FunctionValue 的也支持
            if (cd.hasStaticField(methodName)) {
                IValue<?> v = cd.getStaticField(methodName);
                if (v instanceof FunctionValue fv) {
                    Context callCtx = context.createCallContext(null, callArgs);
                    return fv.getCallable().invoke(new InvocationData(callCtx, null, callArgs));
                }
            }
            return NoneValue.NONE;
        }

        // AriaClassValue — 脚本类实例
        if (obj instanceof AriaClassValue cv && cv.jvmValue() != null) {
            ClassInstance ci = cv.jvmValue();
            if (ci.getFields().containsKey(methodName)) {
                IValue<?> method = ci.getFields().get(methodName).getValue();
                if (method instanceof FunctionValue fv) {
                    Context callCtx = context.createCallContext(obj, callArgs);
                    return fv.getCallable().invoke(new InvocationData(callCtx, obj, callArgs));
                }
            }
            ClassDefinition classDef = ci.getClassDefinition();
            if (classDef != null) {
                IRProgram methodProg = classDef.findMethod(methodName);
                if (methodProg != null) {
                    Context callCtx = context.createCallContext(obj, callArgs);
                    return execute(methodProg, callCtx).getValue();
                }
            }
            // Shimmer 对齐(用户要求)：obj.field() 中 field 非函数 → 吞掉括号，返回 obj.field 成员值
            return memberOf(obj, methodName);
        }

        // ObjectValue — Java 对象或 IAriaObject
        if (obj instanceof ObjectValue<?> ov) {
            IAriaObject so = ov.jvmValue();
            Variable mv = so.getVariable(methodName);
            IValue<?> method = mv.ariaValue();
            if (method instanceof FunctionValue fv) {
                Context callCtx = context.createCallContext(obj, callArgs);
                return fv.getCallable().invoke(new InvocationData(callCtx, obj, callArgs));
            }
            if (method instanceof StoreOnlyValue<?> sov && sov.jvmValue() instanceof CallableWithInvoker cwi) {
                return cwi.getCallable().invoke(new InvocationData(context, obj, callArgs));
            }
        }

        // 其他类型 — CallableManager 对象函数
        ICallable objFunc = CallableManager.INSTANCE
                .getObjectFunction(receiverClass(obj), methodName);
        if (objFunc != null) {
            return callObjectFunction(objFunc, context, obj, callArgs);
        }

        // Shimmer 对齐：非函数成员加括号 → 吞掉括号，返回成员值（无则 none）
        return memberOf(obj, methodName);
    }

    /**
     * 方法分派用的接收者类：若接收者是 {@link ObjectValue} 包装(IAriaObject 等)，用其【被包装对象的真实类】查找
     * CallableManager 里注册的对象函数（否则 obj.getClass() 恒为 ObjectValue，永远查不到注册在具体类下的方法）。
     * 非包装值(StringValue/ListValue 等 stdlib 值本身即注册类)直接用其类，行为不变。
     */
    private static Class<?> receiverClass(IValue<?> obj) {
        if (obj instanceof ObjectValue<?> ov && ov.jvmValue() != null) {
            return ov.jvmValue().getClass();
        }
        return obj.getClass();
    }

    /**
     * 调用对象函数并构造 InvocationData。两种约定：
     * <ul>
     *   <li><b>{@link ObjectValue} 包装对象</b>(IAriaObject 等)：self 经 <b>target</b> 传入(解包后的对象)，
     *       {@code args} 只含【用户参数】——与宿主注册的实例方法/target 函数约定一致(它们用 getTarget()/this 取 self、
     *       从 args[0] 读第一个用户参数)。</li>
     *   <li><b>非包装值</b>(String/List/Map 等 stdlib 值)：保持既有约定，self 前插为 {@code args[0]}、target=obj。</li>
     * </ul>
     */
    /**
     * JIT/解释器共用的注册对象函数分派。用 {@link #receiverClass}【解包 ObjectValue 取真实类】查找 CallableManager 里
     * 注册在具体类下的对象函数(否则 ObjectValue 的 getClass() 恒为 ObjectValue.class，永远查不到 → 控件/自定义对象的
     * 方法在 JIT 下全部失效),命中则按正确 self 约定调用({@link #callObjectFunction}：ObjectValue 接收者不前插 self、
     * target 为解包对象;stdlib 值前插 self)。返回 {@code null} 表示未注册该方法,调用方继续走后续兜底。
     */
    public static IValue<?> invokeRegisteredObjectMethod(IValue<?> obj, String methodName,
                                                         IValue<?>[] userArgs, Context ctx) throws AriaException {
        if (obj == null) return null;
        ICallable objFunc = CallableManager.INSTANCE.getObjectFunction(receiverClass(obj), methodName);
        if (objFunc == null) return null;
        return callObjectFunction(objFunc, ctx, obj, userArgs);
    }

    private static IValue<?> callObjectFunction(ICallable objFunc, Context ctx, IValue<?> obj, IValue<?>[] callArgs)
            throws AriaException {
        if (obj instanceof ObjectValue<?> ov) {
            return objFunc.invoke(new InvocationData(ctx, ov.jvmValue(), callArgs));
        }
        IValue<?>[] objCallArgs = new IValue<?>[callArgs.length + 1];
        objCallArgs[0] = obj;
        System.arraycopy(callArgs, 0, objCallArgs, 1, callArgs.length);
        return objFunc.invoke(new InvocationData(ctx, obj, objCallArgs));
    }

    private IValue<?> constructScriptClass(ClassDefinition cd, IValue<?>[] callArgs,
                                           Context context) throws AriaException {
        ClassInstance instance = new ClassInstance(cd);

        for (Map.Entry<String, Boolean> entry : cd.collectAllFieldMeta().entrySet()) {
            instance.getFields().put(entry.getKey(), new VariableReference(NoneValue.NONE));
        }

        AriaClassValue instanceVal = new AriaClassValue(instance);

        ClassDefinition parentDef = cd.getParent();
        while (parentDef != null) {
            if (parentDef.getFieldInitProgram() != null) {
                Context initCtx = context.createCallContext(instanceVal, EMPTY_ARGS);
                execute(parentDef.getFieldInitProgram(), initCtx);
            }
            parentDef = parentDef.getParent();
        }

        if (cd.getFieldInitProgram() != null) {
            Context initCtx = context.createCallContext(instanceVal, EMPTY_ARGS);
            execute(cd.getFieldInitProgram(), initCtx);
        }

        if (cd.getConstructorProgram() != null) {
            Context ctorCtx = context.createCallContext(instanceVal, callArgs);
            execute(cd.getConstructorProgram(), ctorCtx);
        }

        return instanceVal;
    }

    private IValue<?> constructByName(String className, IValue<?>[] callArgs, Context context) throws AriaException {
        // 类名按完整链解析(scope→var→val→global),不能只查 scope:
        // val.X = use(..) 现写入 val 存储(以往写 scope),只查 scope 会找不到 → 构造失败。
        IValue<?> classDef = resolveVariable(context, className);

        if (classDef instanceof ObjectValue<?> ov && ov.jvmValue() instanceof ClassDefinition cd) {
            return constructScriptClass(cd, callArgs, context);
        } else if (classDef instanceof ObjectValue<?> ov2 && ov2.jvmValue() instanceof JavaClassMirror jcm) {
            return jcm.newInstance(callArgs);
        } else {
            ICallable ctor = CONSTRUCTORS.get(className);
            if (ctor == null) {
                ctor = CallableManager.INSTANCE.getConstructor(className);
            }
            if (ctor != null) {
                return ctor.invoke(new InvocationData(context, null, callArgs));
            }
            return NoneValue.NONE;
        }
    }

    private static boolean hasScopeOps(IRInstruction[] code) {
        for (IRInstruction inst : code) {
            if (inst.opcode == IROpCode.LOAD_SCOPE || inst.opcode == IROpCode.STORE_SCOPE) return true;
        }
        return false;
    }

    private static boolean hasVarOps(IRInstruction[] code) {
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case LOAD_VAR, STORE_VAR, VAR_INC, VAR_ADD_CONST, VAR_ADD_REG -> { return true; }
                default -> {}
            }
        }
        return false;
    }

    private static boolean hasArgsOps(IRInstruction[] code) {
        for (IRInstruction inst : code) {
            if (inst.opcode == IROpCode.LOAD_ARG || inst.opcode == IROpCode.LOAD_ARGS) return true;
        }
        return false;
    }


    private static IValue<?>[] copyArgs(IValue<?>[] registers, int argBase, int argCount) {
        IValue<?>[] args = new IValue<?>[argCount];
        System.arraycopy(registers, argBase, args, 0, argCount);
        return args;
    }


    private static int checkCallSelfRecursive(IRInstruction[] code, IValue<?>[] constants,
                                              VariableKey[] keys, String selfName) {
        if (selfName == null || keys == null) return -1; // 无法校验调用目标名 → 保守走通用路径
        // 一遍扫描：记录每个 LOAD_VAR 的目标寄存器→key；对每条 CALL 校验其 callee 名 == 本函数名。
        // 任一 CALL 调的不是自己（互递归 var.fb 在 fa 内）→ 返回 -1，避免 executeCallRecursiveNumeric
        // 把跨函数调用当成自递归（同 JIT F1 修复）。
        java.util.Map<Integer, Integer> loadVarRegs = new java.util.HashMap<>();
        int selfKeyIdx = -1;
        boolean hasCall = false;
        for (IRInstruction inst : code) {
            switch (inst.opcode) {
                case LOAD_VAR -> loadVarRegs.put(inst.dst, inst.a);
                case CALL -> {
                    hasCall = true;
                    Integer ki = loadVarRegs.get(inst.a);
                    if (ki == null || ki < 0 || ki >= keys.length || keys[ki] == null
                            || !selfName.equals(keys[ki].getName())) {
                        return -1;
                    }
                    selfKeyIdx = ki;
                }
                case CONCAT, LOAD_SCOPE, STORE_SCOPE, GET_INDEX, SET_INDEX,
                     NEW_LIST, NEW_MAP, CALL_METHOD, CALL_CONSTRUCTOR,
                     LOAD_SELF, LOAD_ARGS, NEW_FUNCTION, CALL_STATIC,
                     STORE_VAR -> { return -1; }
                default -> {}
            }
        }
        if (!hasCall || selfKeyIdx < 0) return -1;
        for (IValue<?> c : constants) {
            if (!(c instanceof NumberValue) && !(c instanceof BooleanValue) && !(c instanceof NoneValue)) {
                return -1;
            }
        }
        return selfKeyIdx;
    }


    private IValue<?> executeCallRecursiveNumeric(IRInstruction[] code, IValue<?>[] constants,
                                                  int regCount, Context context) throws AriaException {
        int frameSize = regCount + 4;
        double[] stack = new double[frameSize * 256];
        int stackTop = 0;
        double[] regs = new double[regCount];

        IValue<?>[] args = context.getArgs();
        double[] argValues = null;
        double[] argRestore = new double[2];

        int pc = 0;
        final int len = code.length;

        while (true) {
            if (pc >= len) {
                if (stackTop == 0) return NumberValue.of(0);
                stackTop--;
                int base = stackTop * frameSize;
                pc = (int) stack[base];
                int retDst = (int) stack[base + 1];
                for (int i = 0; i < regCount; i++) regs[i] = stack[base + 2 + i];
                regs[retDst] = 0;
                argRestore[0] = stack[base + 2 + regCount];
                argRestore[1] = stack[base + 3 + regCount];
                argValues = argRestore;
                pc++;
                continue;
            }

            IRInstruction inst = code[pc];

            switch (inst.opcode) {
                case LOAD_CONST -> regs[inst.dst] = constants[inst.a].numberValue();
                case LOAD_ARG -> {
                    if (argValues != null && inst.a < argValues.length) {
                        regs[inst.dst] = argValues[inst.a];
                    } else if (args != null && inst.a < args.length) {
                        regs[inst.dst] = args[inst.a].numberValue();
                    } else {
                        regs[inst.dst] = 0;
                    }
                }
                case LOAD_NONE, LOAD_FALSE -> regs[inst.dst] = 0;
                case LOAD_TRUE -> regs[inst.dst] = 1;
                case LOAD_VAR -> {} // 跳过：CALL 时直接当自递归处理

                case ADD, ADD_NUM -> regs[inst.dst] = regs[inst.a] + regs[inst.b];
                case SUB, SUB_NUM -> regs[inst.dst] = regs[inst.a] - regs[inst.b];
                case MUL, MUL_NUM -> regs[inst.dst] = regs[inst.a] * regs[inst.b];
                case DIV, DIV_NUM -> {
                    double r = regs[inst.b];
                    regs[inst.dst] = r == 0 ? 0 : regs[inst.a] / r; // Shimmer 对齐：除零->0
                }
                case MOD, MOD_NUM -> {
                    double r = regs[inst.b];
                    regs[inst.dst] = r == 0 ? 0 : regs[inst.a] % r; // Shimmer 对齐：取余零->0
                }
                case NEG -> regs[inst.dst] = -regs[inst.a];
                case INC -> regs[inst.dst] = regs[inst.a] + 1;
                case DEC -> regs[inst.dst] = regs[inst.a] - 1;

                case EQ -> regs[inst.dst] = regs[inst.a] == regs[inst.b] ? 1 : 0;
                case NE -> regs[inst.dst] = regs[inst.a] != regs[inst.b] ? 1 : 0;
                case LT -> regs[inst.dst] = regs[inst.a] < regs[inst.b] ? 1 : 0;
                case GT -> regs[inst.dst] = regs[inst.a] > regs[inst.b] ? 1 : 0;
                case LE -> regs[inst.dst] = regs[inst.a] <= regs[inst.b] ? 1 : 0;
                case GE -> regs[inst.dst] = regs[inst.a] >= regs[inst.b] ? 1 : 0;
                // Shimmer 对齐：数值真值 = value > 0（负数/0/NaN 均假），修复热路径把负数当 true 的 bug。
                case NOT -> regs[inst.dst] = regs[inst.a] <= 0 ? 1 : 0;
                case AND -> regs[inst.dst] = (regs[inst.a] > 0 && regs[inst.b] > 0) ? 1 : 0;
                case OR -> regs[inst.dst] = (regs[inst.a] > 0 || regs[inst.b] > 0) ? 1 : 0;

                case JUMP -> { pc = inst.a; continue; }
                case JUMP_IF_TRUE -> { if (regs[inst.dst] > 0) { pc = inst.a; continue; } }
                case JUMP_IF_FALSE -> { if (regs[inst.dst] <= 0) { pc = inst.a; continue; } }

                case MOVE -> regs[inst.dst] = regs[inst.a];

                case RETURN -> {
                    double retVal = inst.dst >= 0 ? regs[inst.dst] : 0;
                    if (stackTop == 0) {
                        return NumberValue.of(retVal);
                    }
                    stackTop--;
                    int base = stackTop * frameSize;
                    pc = (int) stack[base];
                    int retDst = (int) stack[base + 1];
                    for (int i = 0; i < regCount; i++) regs[i] = stack[base + 2 + i];
                    regs[retDst] = retVal;
                    argRestore[0] = stack[base + 2 + regCount];
                    argRestore[1] = stack[base + 3 + regCount];
                    argValues = argRestore;
                    pc++;
                    continue;
                }

                case CALL -> {
                    // 自递归：当作 CALL_STATIC 处理
                    int argCount = inst.b;
                    int argBase = inst.c;

                    if (stackTop >= MAX_CALL_DEPTH) {
                        throw new AriaRuntimeException("Stack overflow: recursive depth exceeded " + MAX_CALL_DEPTH);
                    }
                    if ((stackTop + 1) * frameSize > stack.length) {
                        double[] newStack = new double[stack.length * 2];
                        System.arraycopy(stack, 0, newStack, 0, stack.length);
                        stack = newStack;
                    }

                    int base = stackTop * frameSize;
                    stack[base] = pc;
                    stack[base + 1] = inst.dst;
                    for (int i = 0; i < regCount; i++) stack[base + 2 + i] = regs[i];
                    stack[base + 2 + regCount] = argValues != null && argValues.length > 0 ? argValues[0] :
                            (args != null && args.length > 0 ? args[0].numberValue() : 0);
                    stack[base + 3 + regCount] = argValues != null && argValues.length > 1 ? argValues[1] :
                            (args != null && args.length > 1 ? args[1].numberValue() : 0);
                    stackTop++;

                    double a0 = argCount > 0 ? regs[argBase] : 0;
                    double a1 = argCount > 1 ? regs[argBase + 1] : 0;

                    args = null;
                    argRestore[0] = a0;
                    argRestore[1] = a1;
                    argValues = argRestore;
                    pc = 0;
                    continue;
                }

                case NOP, PUSH_SCOPE, POP_SCOPE -> {}

                default -> {
                    return fallbackToGenericExecution(code, constants, null, null, regCount, context);
                }
            }
            pc++;
        }
    }
    private static byte checkNumericSelfRecursive(IRInstruction[] code, IValue<?>[] constants, String selfName) {
        if (selfName == null) return -1; // 无法校验调用目标名 → 保守走通用路径
        boolean hasSelfCallStatic = false;
        boolean hasPendingCache = false;
        for (IRInstruction inst : code) {
            if (inst.opcode == IROpCode.CALL_STATIC) {
                // executeSelfRecursiveNumeric 把每条 CALL_STATIC 都当成对自身的递归调用。
                // 因此只要存在一条非自身的 CALL_STATIC（互递归裸名 fb / math.* 等），就不能用该快路径。
                if (!selfName.equals(inst.name)) return -1;
                if (inst.cache != null) {
                    hasSelfCallStatic = true;
                } else {
                    hasPendingCache = true;
                }
            }
            switch (inst.opcode) {
                case CONCAT, LOAD_SCOPE, STORE_SCOPE, GET_INDEX, SET_INDEX,
                     NEW_LIST, NEW_MAP, CALL_METHOD, CALL_CONSTRUCTOR,
                     LOAD_SELF, LOAD_ARGS, NEW_FUNCTION:
                    return -1; // 确定不是
                default: break;
            }
        }
        if (!hasSelfCallStatic && !hasPendingCache) return -1; // 没有任何自身 CALL_STATIC
        if (!hasSelfCallStatic) return 0;
        for (IValue<?> c : constants) {
            if (!(c instanceof NumberValue) && !(c instanceof BooleanValue) && !(c instanceof NoneValue)) {
                return -1;
            }
        }
        return 1; // 确认是自递归
    }


    private IValue<?> executeSelfRecursiveNumeric(IRInstruction[] code, IValue<?>[] constants,
                                                  int regCount, Context context) throws AriaException {
        // 虚拟调用栈：每帧保存 [pc, dst, reg0, reg1, ..., regN]
        int frameSize = regCount + 4; // pc + dst + registers + arg0 + arg1
        double[] stack = new double[frameSize * 256]; // 初始 256 层深度
        int stackTop = 0; // 栈顶指针（帧数）

        // 当前帧的 double 寄存器
        double[] regs = new double[regCount];

        // 参数传递：首次从 context.getArgs()，自递归时从 argValues
        IValue<?>[] args = context.getArgs();
        double[] argValues = null;
        double[] argRestore = new double[2]; // 复用的参数恢复缓冲

        int pc = 0;
        final int len = code.length;

        while (true) {
            if (pc >= len) {
                // 隐式返回 0
                if (stackTop == 0) return NumberValue.of(0);
                stackTop--;
                int base = stackTop * frameSize;
                pc = (int) stack[base];
                int retDst = (int) stack[base + 1];
                for (int i = 0; i < regCount; i++) regs[i] = stack[base + 2 + i];
                regs[retDst] = 0;
                pc++;
                continue;
            }

            IRInstruction inst = code[pc];

            switch (inst.opcode) {
                case LOAD_CONST -> {
                    regs[inst.dst] = constants[inst.a].numberValue();
                }
                case LOAD_ARG -> {
                    if (argValues != null && inst.a < argValues.length) {
                        regs[inst.dst] = argValues[inst.a];
                    } else if (args != null && inst.a < args.length) {
                        regs[inst.dst] = args[inst.a].numberValue();
                    } else {
                        regs[inst.dst] = 0;
                    }
                }
                case LOAD_NONE, LOAD_FALSE -> regs[inst.dst] = 0;
                case LOAD_TRUE -> regs[inst.dst] = 1;

                case ADD, ADD_NUM -> regs[inst.dst] = regs[inst.a] + regs[inst.b];
                case SUB, SUB_NUM -> regs[inst.dst] = regs[inst.a] - regs[inst.b];
                case MUL, MUL_NUM -> regs[inst.dst] = regs[inst.a] * regs[inst.b];
                case DIV, DIV_NUM -> {
                    double r = regs[inst.b];
                    regs[inst.dst] = r == 0 ? 0 : regs[inst.a] / r; // Shimmer 对齐：除零->0
                }
                case MOD, MOD_NUM -> {
                    double r = regs[inst.b];
                    regs[inst.dst] = r == 0 ? 0 : regs[inst.a] % r; // Shimmer 对齐：取余零->0
                }
                case NEG -> regs[inst.dst] = -regs[inst.a];
                case INC -> regs[inst.dst] = regs[inst.a] + 1;
                case DEC -> regs[inst.dst] = regs[inst.a] - 1;

                case EQ -> regs[inst.dst] = regs[inst.a] == regs[inst.b] ? 1 : 0;
                case NE -> regs[inst.dst] = regs[inst.a] != regs[inst.b] ? 1 : 0;
                case LT -> regs[inst.dst] = regs[inst.a] < regs[inst.b] ? 1 : 0;
                case GT -> regs[inst.dst] = regs[inst.a] > regs[inst.b] ? 1 : 0;
                case LE -> regs[inst.dst] = regs[inst.a] <= regs[inst.b] ? 1 : 0;
                case GE -> regs[inst.dst] = regs[inst.a] >= regs[inst.b] ? 1 : 0;
                // Shimmer 对齐：数值真值 = value > 0（负数/0/NaN 均假），修复热路径把负数当 true 的 bug。
                case NOT -> regs[inst.dst] = regs[inst.a] <= 0 ? 1 : 0;
                case AND -> regs[inst.dst] = (regs[inst.a] > 0 && regs[inst.b] > 0) ? 1 : 0;
                case OR -> regs[inst.dst] = (regs[inst.a] > 0 || regs[inst.b] > 0) ? 1 : 0;

                case JUMP -> { pc = inst.a; continue; }
                case JUMP_IF_TRUE -> { if (regs[inst.dst] > 0) { pc = inst.a; continue; } }
                case JUMP_IF_FALSE -> { if (regs[inst.dst] <= 0) { pc = inst.a; continue; } }

                case MOVE -> regs[inst.dst] = regs[inst.a];

                case RETURN -> {
                    double retVal = inst.dst >= 0 ? regs[inst.dst] : 0;
                    if (stackTop == 0) {
                        return NumberValue.of(retVal);
                    }
                    stackTop--;
                    int base = stackTop * frameSize;
                    pc = (int) stack[base];
                    int retDst = (int) stack[base + 1];
                    for (int i = 0; i < regCount; i++) regs[i] = stack[base + 2 + i];
                    regs[retDst] = retVal;
                    argRestore[0] = stack[base + 2 + regCount];
                    argRestore[1] = stack[base + 3 + regCount];
                    argValues = argRestore;
                    pc++;
                    continue;
                }

                case CALL_STATIC -> {
                    int argCount = inst.b;
                    int argBase = inst.a;

                    if (stackTop >= MAX_CALL_DEPTH) {
                        throw new AriaRuntimeException("Stack overflow: recursive depth exceeded " + MAX_CALL_DEPTH);
                    }

                    if ((stackTop + 1) * frameSize > stack.length) {
                        double[] newStack = new double[stack.length * 2];
                        System.arraycopy(stack, 0, newStack, 0, stack.length);
                        stack = newStack;
                    }

                    int base = stackTop * frameSize;
                    stack[base] = pc;
                    stack[base + 1] = inst.dst;
                    for (int i = 0; i < regCount; i++) stack[base + 2 + i] = regs[i];
                    // 保存当前帧的参数值（用于弹栈后 LOAD_ARG 恢复）
                    stack[base + 2 + regCount] = argValues != null && argValues.length > 0 ? argValues[0] :
                            (args != null && args.length > 0 ? args[0].numberValue() : 0);
                    stack[base + 3 + regCount] = argValues != null && argValues.length > 1 ? argValues[1] :
                            (args != null && args.length > 1 ? args[1].numberValue() : 0);
                    stackTop++;

                    // 保存参数到栈帧末尾（frameSize 已包含空间）
                    // 但 frameSize = regCount + 2，没有额外空间存参数
                    // 简化：参数值直接存到新帧的 regs 对应位置
                    // fib 的 LOAD_ARG 0 会把 args[0] 存到 regs[dst]
                    // 所以只需要把参数值暂存，让 LOAD_ARG 能读到

                    double a0 = argCount > 0 ? regs[argBase] : 0;
                    double a1 = argCount > 1 ? regs[argBase + 1] : 0;

                    args = null;
                    argRestore[0] = a0;
                    argRestore[1] = a1;
                    argValues = argRestore;
                    pc = 0;
                    continue;
                }

                case NOP, PUSH_SCOPE, POP_SCOPE -> {}

                default -> {
                    // 不支持的指令，回退到通用路径
                    return fallbackToGenericExecution(code, constants, null, null, regCount, context);
                }
            }
            pc++;
        }
    }

    private IValue<?> fallbackToGenericExecution(IRInstruction[] code, IValue<?>[] constants,
                                                 VariableKey[] keys, IRProgram[] subPrograms,
                                                 int regCount, Context context) throws AriaException {
        final IValue<?>[] registers = new IValue<?>[regCount];
        Arrays.fill(registers, NoneValue.NONE);
        // 简化回退：直接用通用解释器
        IRProgram prog = new IRProgram("fallback");
        prog.setInstructions(code);
        prog.setConstants(constants);
        prog.setVariableKeys(keys != null ? keys : new VariableKey[0]);
        prog.setSubPrograms(subPrograms != null ? subPrograms : new IRProgram[0]);
        prog.setRegisterCount(regCount);
        return execute(prog, context).getValue();
    }

    private Map<String, IValue<?>> extractFieldDefaults(
            IRProgram fieldInit, Context context, Set<String> wantedFields) {
        if (fieldInit == null || wantedFields == null || wantedFields.isEmpty()) {
            return Collections.emptyMap();
        }
        IRInstruction[] code = fieldInit.getInstructions();
        if (code == null || code.length == 0) return Collections.emptyMap();
        IValue<?>[] consts = fieldInit.getConstants();
        IRProgram[] subs = fieldInit.getSubPrograms();

        Map<String, IValue<?>> result = new HashMap<>();
        for (int i = 0; i < code.length; i++) {
            IRInstruction inst = code[i];
            if (inst.opcode != IROpCode.SET_PROP) continue;
            String fieldName = inst.name;
            if (fieldName == null || !wantedFields.contains(fieldName)) continue;
            // SET_PROP 操作数：dst=objReg, a=valueReg
            int valueReg = inst.a;

            IRInstruction writer = null;
            for (int j = i - 1; j >= 0; j--) {
                if (code[j].dst == valueReg) {
                    writer = code[j];
                    break;
                }
            }
            if (writer == null) continue;

            IValue<?> v = null;
            switch (writer.opcode) {
                case NEW_FUNCTION -> {
                    if (subs != null && writer.a >= 0 && writer.a < subs.length) {
                        v = wrapSubProgramAsFunctionValue(subs[writer.a], context);
                    }
                }
                case LOAD_CONST -> {
                    if (consts != null && writer.a >= 0 && writer.a < consts.length) {
                        v = consts[writer.a];
                    }
                }
                case LOAD_TRUE -> v = BooleanValue.TRUE;
                case LOAD_FALSE -> v = BooleanValue.FALSE;
                case LOAD_NONE -> v = NoneValue.NONE;
                default -> { /* 复杂表达式：跳过 */ }
            }
            if (v != null) result.put(fieldName, v);
        }
        return result;
    }


    private IValue<?> wrapSubProgramAsFunctionValue(IRProgram subProg, Context capturedCtx) {
        Context snap = capturedCtx.snapshotForClosure();
        Interpreter self = this;
        return new FunctionValue((InvocationData data) -> {
            IValue<?> target = data.getTarget() instanceof IValue<?> t ? t : NoneValue.NONE;
            Context callCtx = snap.createCallContext(target, data.getArgs());
            return self.execute(subProg, callCtx).getValue();
        });
    }
}
