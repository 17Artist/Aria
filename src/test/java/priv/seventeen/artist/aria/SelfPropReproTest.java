package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.CallableWithInvoker;
import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.value.FunctionValue;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.ObjectValue;
import priv.seventeen.artist.aria.value.StoreOnlyValue;
import priv.seventeen.artist.aria.value.Variable;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import static org.junit.jupiter.api.Assertions.*;

/** 复现并回归 ArcartX：self.y(惰性属性)、self.parent(对象函数返回 IAriaObject)、self.parent.y(链式) */
public class SelfPropReproTest {

    @BeforeAll
    static void setup() {
        // 注册对象函数(扫描 @AriaInvokeHandler("parent"))
        CallableManager.INSTANCE.registerObject(ControlLike.class);
    }

    /** 模拟 ArcartX ShimmerControl：属性=惰性 CallableWithInvoker；parent=对象函数返回 IAriaObject */
    public static class ControlLike implements IAriaObject {
        final IAriaObject parent;
        public ControlLike(IAriaObject parent) { this.parent = parent; }

        @Override public String getTypeName() { return "control"; }

        @Override public Variable getVariable(String name) {
            if (name.equals("y")) {
                ICallable inner = data -> new NumberValue(42);
                return new Variable.Normal(new VariableReference(
                        new StoreOnlyValue<>(new CallableWithInvoker(inner, this))));
            }
            return Variable.Normal.NONE;
        }

        @AriaInvokeHandler("parent")
        public IAriaObject getParent(InvocationData data) { return this.parent; }
    }

    private Context selfCtx(IAriaObject o) {
        Context ctx = Aria.createContext();
        ctx.setSelf(new ObjectValue<>(o));
        return ctx;
    }

    private IAriaObject child() {
        return new ControlLike(new ControlLike(null)); // child.parent 有惰性 y=42
    }

    @Test
    void selfLazyProperty() throws AriaException {
        assertEquals(42.0, Aria.eval("return self.y", selfCtx(child())).numberValue());
        assertEquals(43.0, Aria.eval("return self.y + 1", selfCtx(child())).numberValue());
    }

    @Test
    void selfParentBare_returnsObject() throws AriaException {
        // self.parent(不带括号)—— Shimmer 能取父对象；此前 Aria 恒 NONE
        IValue<?> p = Aria.eval("return self.parent", selfCtx(child()));
        assertInstanceOf(ObjectValue.class, p, "self.parent 应为父对象(ObjectValue),而非 NONE");
        assertInstanceOf(ControlLike.class, ((ObjectValue<?>) p).jvmValue());
    }

    @Test
    void selfParentCall_returnsObject() throws AriaException {
        // self.parent()(带括号)—— 对象函数返回 IAriaObject 必须包装成 ObjectValue，此前落 NONE
        IValue<?> p = Aria.eval("return self.parent()", selfCtx(child()));
        assertInstanceOf(ObjectValue.class, p, "self.parent() 应为父对象,而非 NONE(返回值包装缺 IAriaObject)");
    }

    @Test
    void selfParentDotY_chain() throws AriaException {
        // self.parent.y(裸链) 与 self.parent().y(方法调用链) 都应取到父的惰性 y=42
        assertEquals(42.0, Aria.eval("return self.parent.y", selfCtx(child())).numberValue());
        assertEquals(42.0, Aria.eval("return self.parent().y", selfCtx(child())).numberValue());
    }

    @Test
    void selfParentDotY_throughJIT() throws AriaException {
        AriaCompiledRoutine r = Aria.compile("jitChain", "return self.parent.y + 1\n");
        double last = 0;
        for (int i = 0; i < 200; i++) last = r.execute(selfCtx(child())).numberValue();
        assertEquals(43.0, last, "JIT 路径下 self.parent.y 链式仍正确");
    }

    @Test
    void rootParentIsNone() throws AriaException {
        // 根控件 parent=null → NONE(不崩)
        IValue<?> p = Aria.eval("return self.parent", selfCtx(new ControlLike(null)));
        assertFalse(p instanceof ObjectValue, "null 父对象应为 NONE");
    }

    @Test
    void notCallableLeak() throws AriaException {
        IValue<?> v = Aria.eval("return self.y", selfCtx(child()));
        assertFalse(v.jvmValue() instanceof CallableWithInvoker);
        assertFalse(v instanceof FunctionValue);
    }
}
