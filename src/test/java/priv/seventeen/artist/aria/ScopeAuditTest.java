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
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

import static org.junit.jupiter.api.Assertions.*;

/** 作用域/闭包对抗审查：重点验证 STORE_SCOPE 改动后，块内赋值更新外层 vs 声明性绑定(catch/for-in)的局部性。 */
public class ScopeAuditTest {
    @BeforeAll static void s() { Aria.getEngine().initialize(); }
    @BeforeEach void r() { Interpreter.resetCallDepth(); Interpreter.clearSandbox(); }
    private IValue<?> eval(String c) throws AriaException { return Aria.eval(c, Aria.createContext()); }
    private double num(String c) throws AriaException { return eval(c).numberValue(); }
    private String str(String c) throws AriaException { return eval(c).stringValue(); }
    private boolean none(String c) throws AriaException { return eval(c) instanceof NoneValue; }

    // 块内裸名赋值应更新外层（我的 STORE_SCOPE 修复目标）
    @Test void ifBlockAssignUpdatesOuter() throws Exception { assertEquals(5.0, num("r=0\nif (true) { r=5 }\nreturn r"), 1e-9); }
    @Test void whileBlockAssignUpdatesOuter() throws Exception { assertEquals(10.0, num("s=0\ni=0\nwhile (i<5) { s=s+i\ni=i+1 }\nreturn s"), 1e-9); }
    @Test void nestedBlockAssignUpdatesOuter() throws Exception { assertEquals(9.0, num("x=1\nif (true) { if (true) { x=9 } }\nreturn x"), 1e-9); }
    @Test void forBodyAssignUpdatesOuter() throws Exception { assertEquals(11.0, num("r=0\nfor (i in Range(0,100)) { if (i>10) { break }\n r=r+1 }\nreturn r"), 1e-9); }

    // 块内首次裸名新变量：块外不可见（局部）
    @Test void blockNewBareVarLocal() throws Exception { assertTrue(none("if (true) { y=5 }\nreturn y")); }

    // 声明性绑定的局部性（关键回归点）
    @Test void catchVarShadowsOuter() throws Exception {
        // catch(e) 应 shadow 外层 var.e，不污染它
        assertEquals("outer", str("var.e='outer'\ntry { throw 'boom' } catch (e) { }\nreturn var.e"));
    }
    @Test void catchVarUsableInBlock() throws Exception {
        assertEquals("msg", str("try { throw 'msg' } catch (e) { return e }"));
    }
    @Test void catchVarNotLeaked() throws Exception {
        assertTrue(none("try { throw 'x' } catch (e) { }\nreturn e"));
    }
    @Test void forInVarShadowsOuter() throws Exception {
        // Shimmer 对齐: controlflow-11 —— 循环变量存真实作用域：循环后可见=末次迭代值(Range 双端闭 0..3 → 3)；
        // var.i 是独立命名空间，不受影响(仍 99)。
        assertEquals(3.0, num("var.i=99\nfor (i in Range(0,3)) { }\nreturn i"), 1e-9);
        assertEquals(99.0, num("var.i=99\nfor (i in Range(0,3)) { }\nreturn var.i"), 1e-9);
    }

    // 闭包语义（Shimmer 对齐 R2：lambda 体全新 ScopeStack，不捕获外层 scope）
    @Test void closureMutableCounter() throws Exception {
        // count 每次调用从 none 起步：n() 恒 1(none++ → 1)，a+b = 2。
        assertEquals(2.0, num("var.counter=-> { count=0\n return -> { count++\n return count } }\nn=counter()\na=n()\nb=n()\nreturn a+b"), 1e-9);
    }
    @Test void twoCountersIndependent() throws Exception {
        // fa()/fb() 恒 1 → 1 + 0*1 = 1。
        assertEquals(1.0, num("var.mk=-> { c=0\n return -> { c++\n return c } }\nfa=mk()\nfb=mk()\nfa()\nfa()\nreturn fa() + 0*fb()"), 1e-9);
    }
    @Test void closureSeesOuterMutation() throws Exception {
        // 体内 x 不可见 → none+1 = 1.0。
        assertEquals(1.0, num("x=10\nvar.f=-> { return x+1 }\nf()\nx=20\nreturn f()"), 1e-9);
    }
}
