package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler;
import priv.seventeen.artist.aria.annotation.java.AriaNamespace;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.ObjectValue;
import priv.seventeen.artist.aria.value.Variable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JIT parity 第二批：覆盖 F/G 调查发现并修复的缺口。
 * 全部用「编译一次 + 循环 200 次越过 JIT 阈值(=1)」复现 JIT 路径，与解释器(单次 eval)对比。
 *  - Bug1 rtSetIndex：ObjectVar 元素写入(self['k']=v / += / ++)
 *  - Bug2 rtGetIndex for-in：Map 的 for(e in map) 迭代
 *  - Bug6 rtCallByName：val 存的 lambda 裸名调用
 *  - Bug7 NEG：对非数值(数字字符串)取负不 CCE
 *  - BugB CONCAT：插值字符串 canBeNumber=false → "{n}" + 1 拼接而非相加
 */
public class AriaJitParity2Test {

    @BeforeAll
    static void setup() {
        CallableManager.INSTANCE.registerObject(WriteWidget.class);
        CallableManager.INSTANCE.registerStaticFunction(ClockNs.class);
    }

    /** 命名空间数值静态函数——模拟 ArcartX 的 Time.currentTime() 等 */
    @AriaNamespace("Clock")
    public static class ClockNs {
        @AriaInvokeHandler("tick")
        public static double tick(InvocationData d) { return 250; }
    }

    /** getElement 返回 ObjectVar(setter/getter)——模拟 ShimmerControl 控件元素 */
    public static class WriteWidget implements IAriaObject {
        static double slotVal = 0;
        @Override public String getTypeName() { return "ww"; }
        @Override public Variable getElement(String name) {
            if (name.equals("slot")) {
                return new Variable.ObjectVar<>(
                        (v, k) -> { slotVal = v.numberValue(); return v; },
                        (k) -> new NumberValue(slotVal), name);
            }
            return Variable.Normal.NONE;
        }
    }

    private Context ctx() { return Aria.createContext(); }
    private Context selfCtx() { Context c = Aria.createContext(); c.setSelf(new ObjectValue<>(new WriteWidget())); return c; }

    private IValue<?> jit(String code, java.util.function.Supplier<Context> ctx) throws AriaException {
        AriaCompiledRoutine r = Aria.compile("jit", code.endsWith("\n") ? code : code + "\n");
        IValue<?> last = null;
        for (int i = 0; i < 200; i++) last = r.execute(ctx.get());
        return last;
    }

    // ---- Bug2: Map for-in ----
    @Test
    void mapForIn_interp_and_jit() throws AriaException {
        String code = "val.m = {'a': 10, 'b': 20, 'c': 30}\nvar.s = 0\nfor (e in m) { var.s = var.s + e[1] }\nreturn var.s\n";
        assertEquals(60.0, Aria.eval(code, ctx()).numberValue(), "解释器 Map for-in 求值和");
        assertEquals(60.0, jit(code, this::ctx).numberValue(), "JIT 下 Map for-in 应仍为 60(原忽略 for-in 哨兵 → 错乱)");
    }

    @Test
    void mapForInDestructure_jit() throws AriaException {
        String code = "val.m = {'a': 10, 'b': 20}\nvar.s = 0\nfor (k, v in m) { var.s = var.s + v }\nreturn var.s\n";
        assertEquals(30.0, jit(code, this::ctx).numberValue(), "JIT 下 for(k,v in map) 解构");
    }

    // ---- Bug1: ObjectVar 元素写入 ----
    @Test
    void elementAssign_objectVar_jit() throws AriaException {
        WriteWidget.slotVal = 0;
        jit("self['slot'] = 42", this::selfCtx);
        assertEquals(42.0, WriteWidget.slotVal, "JIT 下 self['slot']=42 应调 ObjectVar setter(原只写 Variable.Normal → 静默丢失)");
    }

    @Test
    void elementCompoundAssign_objectVar_jit() throws AriaException {
        // 每次迭代前重置(否则 static slotVal 会跨 200 次累加)——每次都是 10→15
        AriaCompiledRoutine r = Aria.compile("c1", "self['slot'] += 5\n");
        for (int i = 0; i < 200; i++) { WriteWidget.slotVal = 10; r.execute(selfCtx()); }
        assertEquals(15.0, WriteWidget.slotVal, "JIT 下 self['slot'] += 5 读-改-写(读走 getElement getter、写走 ObjectVar setter)");
    }

    @Test
    void elementIncrement_objectVar_jit() throws AriaException {
        AriaCompiledRoutine r = Aria.compile("c2", "self['slot']++\n");
        for (int i = 0; i < 200; i++) { WriteWidget.slotVal = 7; r.execute(selfCtx()); }
        assertEquals(8.0, WriteWidget.slotVal, "JIT 下 self['slot']++");
    }

    // ---- Bug6: val 存的 lambda 裸名调用 ----
    @Test
    void valLambdaCall_jit() throws AriaException {
        String code = "val.g = -> { return args[0] + 1 }\nreturn g(9)\n";
        assertEquals(10.0, Aria.eval(code, ctx()).numberValue(), "解释器 val lambda 裸名调用");
        assertEquals(10.0, jit(code, this::ctx).numberValue(), "JIT 下 val lambda 裸名调用(原 rtCallByName 漏查 val → NONE)");
    }

    // ---- Bug7: NEG 非数值不 CCE ----
    @Test
    void negOnNumericString_jit() throws AriaException {
        String code = "var.s = \"5\"\nreturn -var.s\n";
        assertEquals(-5.0, Aria.eval(code, ctx()).numberValue(), "解释器 -\"5\"");
        assertEquals(-5.0, jit(code, this::ctx).numberValue(), "JIT 下 -\"5\" 应 numberValue() 强转(原 CHECKCAST → CCE)");
    }

    // ---- Bug4: 纯数值路径调命名空间静态函数不吐 0 ----
    @Test
    void numericStaticCall_notZero_jit() throws AriaException {
        // Clock.tick()=250；250 % 100 = 50。原纯数值 fast 路径把非 math 静态调用兜底成 0 → 0
        String code = "var.x = Clock.tick() % 100\nreturn var.x\n";
        assertEquals(50.0, Aria.eval(code, ctx()).numberValue(), "解释器 Clock.tick()%100");
        assertEquals(50.0, jit(code, this::ctx).numberValue(), "JIT 下命名空间数值静态调用应真调用(原 fast 路径吐 0)");
    }

    // ---- BugB: CONCAT canBeNumber ----
    @Test
    void concatCanBeNumber_jit() throws AriaException {
        String code = "var.n = 1\nreturn \"{n}\" + 1\n";
        String interp = Aria.eval(code, ctx()).stringValue();
        String jit = jit(code, this::ctx).stringValue();
        assertEquals(interp, jit, "JIT 与解释器的插值+数字结果应一致(canBeNumber 对齐)");
    }
}
