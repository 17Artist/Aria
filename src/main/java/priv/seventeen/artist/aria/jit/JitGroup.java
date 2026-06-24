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

import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.LocalStorage;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.value.FunctionValue;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.reference.VariableReference;

/**
 * 互递归整组 JIT 编译（CompiledGroup_N）的运行期支撑：入口守卫 + 去优化回落。
 */
public final class JitGroup {

    /** 组成员子程序，index 与生成类的 callFast_i / idx 一一对应（entry 在 0）。 */
    final IRProgram[] members;
    /** 各成员的变量名（var 命名空间 key 名）。 */
    final String[] names;
    /** 编译时上下文，仅在调用方未携带 Context 时作回落基底（正常路径用调用方 Context）。 */
    private final Context defCtx;

    public JitGroup(IRProgram[] members, String[] names, Context defCtx) {
        this.members = members;
        this.names = names;
        this.defCtx = defCtx;
    }

    /**
     * 编译代码入口守卫。返回 {@code null} 表示组仍有效，可继续走编译的 callFast；
     * 返回非 null 表示已检测到重绑并完成整组去优化，返回值即本次调用的解释器结果。
     */
    public static IValue<?> enter(JitGroup g, InvocationData data, int idx) throws AriaException {
        Context ctx = data.getContext();
        if (ctx != null && g.stillBound(ctx)) {
            return null; // 仍有效，调用方继续走编译代码
        }
        // 整组去优化：清空所有成员 compiledCode，后续调用回落解释器/重新编译。
        for (IRProgram m : g.members) {
            m.setCompiledCode(null);
        }
        // 用解释器重新执行本次调用，保证拿到当前 var 绑定下的正确结果。
        Context base = (ctx != null) ? ctx : g.defCtx;
        if (base == null) return NoneValue.NONE;
        IValue<?> selfV = (data.getTarget() instanceof IValue<?> t) ? t : NoneValue.NONE;
        Context callCtx = base.createCallContext(selfV, data.getArgs());
        IValue<?> result = new Interpreter().execute(g.members[idx], callCtx).getValue();
        return result != null ? result : NoneValue.NONE;
    }

    /**
     * 组内每个成员当前是否仍绑定到本组对应的子程序。
     */
    private boolean stillBound(Context ctx) {
        LocalStorage ls = ctx.getLocalStorage();
        if (ls == null) return false;
        for (int i = 0; i < names.length; i++) {
            VariableReference ref = ls.getVarVariableExisting(VariableKey.of(names[i]));
            IValue<?> v = (ref != null) ? ref.getValue() : null;
            if (!(v instanceof FunctionValue fv) || fv.getSourceProgram() != members[i]) {
                return false;
            }
        }
        return true;
    }
}
