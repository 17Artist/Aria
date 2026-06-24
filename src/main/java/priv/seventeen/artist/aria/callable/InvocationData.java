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

package priv.seventeen.artist.aria.callable;

import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

public class InvocationData {

    private final Context context;
    private final Object target;
    private final IValue<?>[] args;

    private final IValue<?>[] regs;
    private final int regBase;
    private final int regCount;

    public InvocationData(Context context, Object target, IValue<?>[] args) {
        this.context = context;
        this.target = target;
        this.args = args != null ? args : new IValue<?>[0];
        this.regs = null;
        this.regBase = 0;
        this.regCount = 0;
    }

    public InvocationData(Context context, Object target, IValue<?>[] registers, int base, int count) {
        this.context = context;
        this.target = target;
        this.args = null;
        this.regs = registers;
        this.regBase = base;
        this.regCount = count;
    }

    /** 透传构造：复用 source 的参数存储（避免 args 数组拷贝），仅替换 context/target。 */
    public InvocationData(Context context, Object target, InvocationData source) {
        this.context = context;
        this.target = target;
        if (source.args != null) {
            this.args = source.args;
            this.regs = null;
            this.regBase = 0;
            this.regCount = 0;
        } else {
            this.args = null;
            this.regs = source.regs;
            this.regBase = source.regBase;
            this.regCount = source.regCount;
        }
    }

    public Context getContext() { return context; }
    public Object getTarget() { return target; }
    public IValue<?>[] getArgs() {
        if (args != null) return args;
        IValue<?>[] result = new IValue<?>[regCount];
        System.arraycopy(regs, regBase, result, 0, regCount);
        return result;
    }

    public IValue<?> get(int index) {
        if (args != null) {
            return (index >= 0 && index < args.length) ? args[index] : NoneValue.NONE;
        }
        return (index >= 0 && index < regCount) ? regs[regBase + index] : NoneValue.NONE;
    }

    public IValue<?> getOrDefault(int index, IValue<?> defaultValue) {
        if (args != null) {
            return (index >= 0 && index < args.length) ? args[index] : defaultValue;
        }
        return (index >= 0 && index < regCount) ? regs[regBase + index] : defaultValue;
    }

    public int argCount() { return args != null ? args.length : regCount; }


    public int size() { return argCount(); }
}
