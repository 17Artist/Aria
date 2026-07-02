package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.CallableWithInvoker;
import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.ObjectValue;
import priv.seventeen.artist.aria.value.StoreOnlyValue;
import priv.seventeen.artist.aria.value.Variable;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户真实失败脚本的最小复现(验收用例)：
 *   self.parent['纵向滑块'].setDragYRatio((self.moveY - self.wheelValue * 0.1).round(1))
 * 涉及：属性(moveY/wheelValue 惰性)、算术/比较、对象索引 getElement、链式方法调用、number.round()。
 */
public class ScrollScriptReproTest {

    static Slider lastSlider;

    @BeforeAll
    static void setup() {
        CallableManager.INSTANCE.registerObject(Scroll.class);
        CallableManager.INSTANCE.registerObject(Slider.class);
    }

    /** 子控件"纵向滑块" */
    public static class Slider implements IAriaObject {
        double dragY = -999;
        @Override public String getTypeName() { return "slider"; }
        @AriaInvokeHandler("setDragYRatio")
        public void setDragYRatio(InvocationData data) {
            this.dragY = data.get(0).numberValue();
            lastSlider = this;
        }
    }

    /** self 控件：有惰性属性 moveY/wheelValue，parent 对象函数，getElement 取子控件 */
    public static class Scroll implements IAriaObject {
        final double moveY, wheelValue;
        final IAriaObject parent;
        final Slider slider = new Slider();
        Scroll(double moveY, double wheelValue, IAriaObject parent) {
            this.moveY = moveY; this.wheelValue = wheelValue; this.parent = parent;
        }
        @Override public String getTypeName() { return "scroll"; }
        private Variable lazy(double v) {
            ICallable inner = data -> new NumberValue(v);
            return new Variable.Normal(new VariableReference(
                    new StoreOnlyValue<>(new CallableWithInvoker(inner, this))));
        }
        @Override public Variable getVariable(String name) {
            if (name.equals("moveY")) return lazy(moveY);
            if (name.equals("wheelValue")) return lazy(wheelValue);
            return Variable.Normal.NONE;
        }
        @Override public Variable getElement(String name) {
            if (name.equals("纵向滑块")) {
                return new Variable.Normal(new VariableReference(new ObjectValue<>(slider)));
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

    // self = child；child.parent = holder(持有 slider)
    private Scroll scene(double moveY, double wheel) {
        Scroll holder = new Scroll(0, 0, null);
        return new Scroll(moveY, wheel, holder);
    }

    @Test
    void numberRound() throws AriaException {
        assertEquals(5.5, Aria.eval("return (5.46).round(1)", Aria.createContext()).numberValue(),
                "number.round(1) 应四舍五入到 1 位");
    }

    @Test
    void lazyPropsArith() throws AriaException {
        // self.moveY - self.wheelValue * 0.1  →  0.8 - 2*0.1 = 0.6
        assertEquals(0.6, Aria.eval("return self.moveY - self.wheelValue * 0.1", selfCtx(scene(0.8, 2))).numberValue(), 1e-9);
    }

    @Test
    void parentIndexElement() throws AriaException {
        // self.parent['纵向滑块'] 应取到 slider 对象
        IValue<?> el = Aria.eval("return self.parent['纵向滑块']", selfCtx(scene(0.8, 2)));
        assertInstanceOf(ObjectValue.class, el, "self.parent['纵向滑块'] 应为 slider 对象");
        assertInstanceOf(Slider.class, ((ObjectValue<?>) el).jvmValue());
    }

    @Test
    void fullChain() throws AriaException {
        lastSlider = null;
        Scroll s = scene(0.8, 2);
        Aria.eval("self.parent['纵向滑块'].setDragYRatio((self.moveY - self.wheelValue * 0.1).round(1))\n", selfCtx(s));
        // 0.8 - 2*0.1 = 0.6 → round(1)=0.6
        assertNotNull(lastSlider, "setDragYRatio 应被调用(整条链跑通)");
        assertEquals(0.6, lastSlider.dragY, 1e-9, "整条链应把 0.6 传给 setDragYRatio");
    }

    // JIT 阈值=1，实战几乎全走 JIT。以下循环执行同一编译产物越过阈值，复现 JIT 路径 bug。

    @Test
    void parentIndexElement_JIT() throws AriaException {
        var r = Aria.compile("jitIdx", "return self.parent['纵向滑块']\n");
        IValue<?> el = null;
        for (int i = 0; i < 200; i++) el = r.execute(selfCtx(scene(0.8, 2)));
        assertInstanceOf(ObjectValue.class, el, "JIT 路径下 self.parent['纵向滑块'] 应取到对象(而非 NONE)");
    }

    @Test
    void fullChain_JIT() throws AriaException {
        var r = Aria.compile("jitChain",
                "self.parent['纵向滑块'].setDragYRatio((self.moveY - self.wheelValue * 0.1).round(1))\n");
        Scroll s = null;
        for (int i = 0; i < 200; i++) { lastSlider = null; s = scene(0.8, 2); r.execute(selfCtx(s)); }
        assertNotNull(lastSlider, "JIT 路径下整条链应跑通");
        assertEquals(0.6, lastSlider.dragY, 1e-9, "JIT 路径下应把 0.6 传给 setDragYRatio");
    }
}
