package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.CallableWithInvoker;
import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.ObjectValue;

import java.util.concurrent.atomic.AtomicInteger;

/** 复现 ArcartX 触发器(action)调用链：副作用原生函数是否执行 + 会话线程下是否可用。 */
public class TriggerInvokeReproTest {

    static final AtomicInteger COUNTER = new AtomicInteger(0);

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
        // 注册副作用原生函数 test.fire()
        CallableManager.INSTANCE.registerStaticFunction("test", "fire", data -> {
            COUNTER.incrementAndGet();
            return NoneValue.NONE;
        });
    }

    private static final IValue<?>[] EMPTY = new IValue<?>[0];

    // 复现 getResult -> AttributeCallable.invoke -> routine.execute
    private IValue<?> invokeLikeTrigger(AriaCompiledRoutine routine, Context ctx) throws AriaException {
        ICallable callable = inv -> {
            try { return routine.execute(inv.getContext()); }
            catch (AriaException e) { throw new RuntimeException(e); }
        };
        CallableWithInvoker cwi = new CallableWithInvoker(callable, NoneValue.NONE);
        // AttributeCallable.invoke: super.invoke(context.createCallContext(self, args), args)
        Context child = ctx.createCallContext(new ObjectValue<>(null), EMPTY);
        return cwi.invoke(child, EMPTY);
    }

    @Test
    void triggerSideEffectRunsOnMainThread() throws AriaException {
        COUNTER.set(0);
        Context ctx = new Context(new GlobalStorage());
        AriaCompiledRoutine routine = Aria.compile("action", "test.fire()\n");
        IValue<?> ret = invokeLikeTrigger(routine, ctx);
        System.out.println("[trigger] main-thread counter=" + COUNTER.get() + " ret=" + ret + " bool=" + ret.booleanValue());
    }

    @Test
    void triggerSideEffectRunsOnSessionThread() throws Exception {
        COUNTER.set(0);
        Context ctx = new Context(new GlobalStorage());
        AriaCompiledRoutine routine = Aria.compile("action", "test.fire()\n");
        // 模拟会话线程
        Thread t = new Thread(() -> {
            try { invokeLikeTrigger(routine, ctx); }
            catch (Throwable e) { System.out.println("[trigger] SESSION THREAD EX: " + e); e.printStackTrace(); }
        });
        t.start(); t.join();
        System.out.println("[trigger] session-thread counter=" + COUNTER.get());
    }

    @Test
    void multiStatementTrigger() throws AriaException {
        COUNTER.set(0);
        Context ctx = new Context(new GlobalStorage());
        // 多语句 + 条件 + 末尾布尔
        AriaCompiledRoutine routine = Aria.compile("action", "var.a = 1\ntest.fire()\nif (a > 0) { test.fire() }\nfalse\n");
        IValue<?> ret = invokeLikeTrigger(routine, ctx);
        System.out.println("[trigger] multi counter=" + COUNTER.get() + " ret=" + ret + " bool=" + ret.booleanValue());
    }
}
