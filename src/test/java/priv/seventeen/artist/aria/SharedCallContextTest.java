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
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.StringValue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Context.createSharedCallContext（Shimmer 对齐: variables-6，对照 Shimmer {@code Context(context, self, args)}）：
 * 复用调用方同一 ScopeStack（裸名临时变量跨调用互通），仅替换 self/args。
 * 供 i-Common AttributeCallable（A8）恢复"跨 action 裸名变量共享"语义。
 */
public class SharedCallContextTest {
    @BeforeAll static void s() { Aria.getEngine().initialize(); }
    @BeforeEach void r() { Interpreter.resetCallDepth(); Interpreter.clearSandbox(); }

    // Shimmer 对齐(R8)：执行边界作用域配平后，脚本自身 push 的顶层作用域在每次 eval 结束时弹回
    // (Shimmer BlockStatement 语义,裸名不再跨 eval 残留)。宿主要维持跨调用共享层,须自行 pushScope
    // 持有一层常驻作用域(与 i-Common AttributeCallable 在同一执行链内共享调用方"活"作用域一致)。
    @Test void sharedContextSeesCallerScopeVariables() throws Exception {
        Context caller = Aria.createContext();
        caller.pushScope(); // 宿主常驻层(执行边界只弹回到该基准深度,不弹宿主层)
        caller.getScopeStack().get(priv.seventeen.artist.aria.context.VariableKey.of("x"))
                .setValue(new NumberValue(5));
        Context shared = caller.createSharedCallContext(null, new IValue<?>[0]);
        assertEquals(5.0, Aria.eval("return x", shared).numberValue(), 1e-9,
                "共享上下文应读到调用方的裸名变量（同一 ScopeStack）");
    }

    @Test void sharedContextWritesBackToCallerScope() throws Exception {
        Context caller = Aria.createContext();
        caller.pushScope(); // 宿主常驻层
        caller.getScopeStack().get(priv.seventeen.artist.aria.context.VariableKey.of("x"))
                .setValue(new NumberValue(1));
        Context shared = caller.createSharedCallContext(null, new IValue<?>[0]);
        Aria.eval("x = 9", shared);
        assertEquals(9.0, Aria.eval("return x", caller).numberValue(), 1e-9,
                "共享上下文中的裸名写入应对调用方可见（写同一绑定）");
    }

    @Test void sharedContextReplacesArgsAndSelf() throws Exception {
        Context caller = Aria.createContext();
        caller.setArgs(new IValue<?>[]{ new StringValue("caller-arg") });
        Context shared = caller.createSharedCallContext(null, new IValue<?>[]{ new NumberValue(42) });
        assertEquals(42.0, Aria.eval("return args[0]", shared).numberValue(), 1e-9,
                "共享上下文应使用替换后的 args");
        assertEquals("caller-arg", caller.getArg(0).stringValue(), "调用方 args 不受影响");
    }

    @Test void sharedContextSharesVarStorage() throws Exception {
        Context caller = Aria.createContext();
        Aria.eval("var.v = 7", caller);
        Context shared = caller.createSharedCallContext(null, new IValue<?>[0]);
        assertEquals(7.0, Aria.eval("return var.v", shared).numberValue(), 1e-9,
                "共享上下文应共享同一 LocalStorage（var 存储）");
    }
}
