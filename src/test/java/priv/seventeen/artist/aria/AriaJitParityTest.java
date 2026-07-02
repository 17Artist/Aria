package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.ListValue;
import priv.seventeen.artist.aria.value.ObjectValue;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JIT ↔ 解释器 对象分派 parity 回归。JIT 阈值=1，实战几乎全走 JIT——这些用例循环执行同一编译产物越过阈值，
 * 确保 JIT 路径与解释器（及 Shimmer 语义）一致。覆盖本轮修复：
 *  - 对象方法分派用 receiverClass 解包 ObjectValue（原 obj.getClass()=ObjectValue.class → JIT 下所有对象方法失效）
 *  - ObjectValue 接收者不前插 self（target=解包对象）
 *  - 方法返回 IAriaObject/List → wrapNativeResult 包装
 *  - 链式 a.b().c()、索引 a[k].m()
 *  - JIT helper 冒泡 AriaException（不再静默吞成 NONE）
 */
public class AriaJitParityTest {

    @BeforeAll
    static void setup() {
        CallableManager.INSTANCE.registerObject(Widget.class);
        CallableManager.INSTANCE.registerObject(Child.class);
    }

    public static class Child implements IAriaObject {
        @Override public String getTypeName() { return "child"; }
        @AriaInvokeHandler("val")
        public double val(InvocationData d) { return 7; }
    }

    public static class Widget implements IAriaObject {
        static double recorded = -1;
        final Child child = new Child();
        @Override public String getTypeName() { return "widget"; }
        @AriaInvokeHandler("echo")
        public double echo(InvocationData d) { return d.get(0).numberValue() * 2; }   // 带参、no-prepend
        @AriaInvokeHandler("setVal")
        public void setVal(InvocationData d) { recorded = d.get(0).numberValue(); }   // void
        @AriaInvokeHandler("child")
        public IAriaObject child(InvocationData d) { return child; }                  // 返回 IAriaObject
        @AriaInvokeHandler("names")
        public List<String> names(InvocationData d) { return Arrays.asList("a", "b", "c"); } // 返回 List
        @AriaInvokeHandler("boom")
        public double boom(InvocationData d) throws AriaRuntimeException { throw new AriaRuntimeException("boom!"); }
    }

    private Context ctx() {
        Context c = Aria.createContext();
        c.setSelf(new ObjectValue<>(new Widget()));
        return c;
    }

    /** 强制走 JIT：编译一次、执行 N 次越过阈值(=1)，返回最后一次结果。 */
    private IValue<?> jit(String code) throws AriaException {
        AriaCompiledRoutine r = Aria.compile("jit", code.endsWith("\n") ? code : code + "\n");
        IValue<?> last = null;
        for (int i = 0; i < 200; i++) last = r.execute(ctx());
        return last;
    }

    @Test
    void objectMethodWithArg_interp_and_jit() throws AriaException {
        assertEquals(10.0, Aria.eval("return self.echo(5)", ctx()).numberValue(), "解释器: self.echo(5)");
        assertEquals(10.0, jit("return self.echo(5)").numberValue(), "JIT: self.echo(5)（receiverClass 解包 + no-prepend）");
    }

    @Test
    void voidMethodRecords_jit() throws AriaException {
        Widget.recorded = -1;
        jit("self.setVal(42)");
        assertEquals(42.0, Widget.recorded, "JIT 下 self.setVal(42) 应写入");
    }

    @Test
    void methodReturnsObject_chain_jit() throws AriaException {
        // self.child() 返回 IAriaObject → 应 wrap 成 ObjectValue；再 .val() 链式
        assertInstanceOf(ObjectValue.class, jit("return self.child()"), "self.child() 应 wrap 成 ObjectValue");
        assertEquals(7.0, Aria.eval("return self.child().val()", ctx()).numberValue(), "解释器链式");
        assertEquals(7.0, jit("return self.child().val()").numberValue(), "JIT 链式 self.child().val()");
    }

    @Test
    void methodReturnsList_jit() throws AriaException {
        IValue<?> v = jit("return self.names()");
        assertInstanceOf(ListValue.class, v, "self.names() 应 wrap 成 ListValue（非 NONE）");
        assertEquals(3, ((ListValue) v).jvmValue().size());
        assertEquals(3.0, jit("return self.names().size()").numberValue(), "List.size() 链式");
    }

    @Test
    void throwingMethodPropagates_notSilentNone_jit() throws AriaException {
        // 关键：JIT 下方法抛异常应【冒泡】而非静默吞成 NONE（Agent D 修复）
        AriaCompiledRoutine r = Aria.compile("boom", "return self.boom()\n");
        assertThrows(AriaException.class, () -> {
            for (int i = 0; i < 200; i++) r.execute(ctx());
        }, "JIT 下抛异常的方法应冒泡 AriaException，不能静默返回 NONE");
    }

    @Test
    void throwingMethodPropagates_interpreter() {
        assertThrows(AriaException.class,
                () -> Aria.eval("return self.boom()", ctx()),
                "解释器下同样应冒泡");
    }
}
