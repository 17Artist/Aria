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
package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.SandboxConfig;
import priv.seventeen.artist.aria.value.IValue;

import static org.junit.jupiter.api.Assertions.*;

/** 真异步：async{} 提交线程池产出真 Promise；await 阻塞取值；闭包捕获、沙箱传播、异常、只执行一次。 */
public class AsyncTest {
    @BeforeAll static void s() { Aria.getEngine().initialize(); }
    @BeforeEach void r() { Interpreter.resetCallDepth(); Interpreter.clearSandbox(); }
    private IValue<?> eval(String c) throws AriaException { return Aria.eval(c, Aria.createContext()); }
    private double num(String c) throws AriaException { return eval(c).numberValue(); }
    private String str(String c) throws AriaException { return eval(c).stringValue(); }

    @Test void asyncAwaitValue() throws Exception {
        assertEquals(42.0, num("var.p = async { return 42 }\nreturn await p"), 1e-9);
    }

    @Test void asyncClosureCapture() throws Exception {
        // async body 应能读到定义处外层变量（闭包捕获）
        assertEquals(6.0, num("var.x = 5\nvar.p = async { return x + 1 }\nreturn await p"), 1e-9);
    }

    @Test void asyncStringResult() throws Exception {
        assertEquals("hi-bob", str("var.name = 'bob'\nvar.p = async { return 'hi-' + name }\nreturn await p"));
    }

    @Test void asyncRunsExactlyOnce() throws Exception {
        // 关键回归：旧实现会在主线程 + 线程池重复执行 body。真异步应只执行一次。
        double once = num("global.asyncOnce = 0\n"
                + "var.p = async { global.asyncOnce = global.asyncOnce + 1\n return 1 }\n"
                + "var.r = await p\n"
                + "return global.asyncOnce\n");
        assertEquals(1.0, once, 1e-9, "async body 应恰好执行一次（不重复）");
    }

    @Test void asyncExceptionPropagatesToAwait() {
        // async body 抛异常 → Promise reject → await 抛出
        assertThrows(Exception.class, () -> eval("var.p = async { throw 'boom' }\nreturn await p"));
    }

    @Test void asyncMapSpreadInBody() throws Exception {
        assertEquals(2.0, num("var.p = async { return {...{'a':1}, 'b':2} }\nvar.m = await p\nreturn m.size()"), 1e-9);
    }

    @Test void asyncTryCatchInBody() throws Exception {
        assertEquals("z", str("var.p = async { try { throw 'z' } catch (e) { return e } }\nreturn await p"));
    }

    @Test void asyncSandboxPropagation() {
        // worker 线程必须继承沙箱：受限沙箱下 async body 用 fs 应被阻止（await 抛出）
        SandboxConfig sb = SandboxConfig.builder().allowedNamespaces("math").maxInstructions(100000).build();
        assertThrows(Exception.class, () ->
            Aria.eval("var.p = async { return fs.exists('x') }\nreturn await p", Aria.createContext(), sb));
    }

    @Test void asyncSandboxAllowsWhitelisted() throws Exception {
        // 受限沙箱下 async body 用白名单内的 math 应正常
        SandboxConfig sb = SandboxConfig.builder().allowedNamespaces("math").maxInstructions(100000).build();
        IValue<?> r = Aria.eval("var.p = async { return math.abs(-9) }\nreturn await p", Aria.createContext(), sb);
        assertEquals(9.0, r.numberValue(), 1e-9);
    }

    @Test void asyncMultipleConcurrent() throws Exception {
        // 同时发起多个 async，分别 await，结果各自正确（线程池并发）
        double sum = num(
            "var.a = async { return 1 }\n" +
            "var.b = async { return 2 }\n" +
            "var.c = async { return 3 }\n" +
            "return (await a) + (await b) + (await c)\n");
        assertEquals(6.0, sum, 1e-9);
    }
}
