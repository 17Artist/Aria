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

package priv.seventeen.artist.aria.context;

import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.Variable;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.util.HashMap;

public class Context {

    private static final IValue<?>[] EMPTY_ARGS = new IValue<?>[0];

    private final GlobalStorage globalStorage;
    private final LocalStorage localStorage;
    private final ScopeStack scopeStack;
    private IValue<?> self;
    private IValue<?>[] args;

    public Context(GlobalStorage globalStorage) {
        this.globalStorage = globalStorage;
        this.localStorage = new LocalStorage();
        this.scopeStack = new ScopeStack();
        this.self = NoneValue.NONE;
        this.args = EMPTY_ARGS;
    }

    public Context(GlobalStorage globalStorage, LocalStorage localStorage, ScopeStack scopeStack) {
        this.globalStorage = globalStorage;
        this.localStorage = localStorage;
        this.scopeStack = scopeStack;
        this.self = NoneValue.NONE;
        this.args = EMPTY_ARGS;
    }


    public Variable.Normal getGlobalVariable(VariableKey key) {
        return new Variable.Normal(globalStorage.getGlobalVariable(key));
    }

    public Variable.Normal getClientVariable(VariableKey key) {
        return new Variable.Normal(globalStorage.getClientVariable(key));
    }

    public Variable.Normal getServerVariable(VariableKey key) {
        return new Variable.Normal(globalStorage.getServerVariable(key));
    }

    public Variable.Normal getLocalVariable(VariableKey key) {
        return new Variable.Normal(localStorage.getVarVariable(key));
    }

    public Variable.Normal getLocalValue(VariableKey key) {
        return new Variable.Normal(localStorage.getValVariable(key));
    }

    public void forceSetLocalValue(VariableKey key, IValue<?> value) {
        localStorage.getValVariable(key).forceSetValue(value);
    }

    public Variable.Normal getScopeVariable(VariableKey key) {
        return new Variable.Normal(scopeStack.get(key));
    }


    public void pushScope() { scopeStack.push(); }
    public void popScope() { scopeStack.pop(); }


    public IValue<?> getSelf() { return self; }
    public void setSelf(IValue<?> self) { this.self = self; }

    public IValue<?>[] getArgs() {
        IValue<?>[] copy = new IValue<?>[args.length];
        System.arraycopy(args, 0, copy, 0, args.length);
        return copy;
    }
    /** 直接获取内部 args 引用（不复制，用于性能关键路径） */
    public IValue<?>[] getArgsRef() { return args; }
    public void setArgs(IValue<?>[] args) { this.args = args != null ? args : EMPTY_ARGS; }

    public IValue<?> getArg(int index) {
        if (index >= 0 && index < args.length) return args[index];
        return NoneValue.NONE;
    }


    public GlobalStorage getGlobalStorage() { return globalStorage; }
    public LocalStorage getLocalStorage() { return localStorage; }
    public ScopeStack getScopeStack() { return scopeStack; }

    public Context createAsyncContext() {
        return new Context(globalStorage, localStorage, new ScopeStack());
    }

    public Context snapshotForClosure() {
        ScopeStack snapshotScope = new ScopeStack();
        HashMap<VariableKey, VariableReference> snap = this.scopeStack.snapshot();
        if (!snap.isEmpty()) {
            snapshotScope.push();
            snapshotScope.importAll(snap);
        }
        Context ctx = new Context(globalStorage, localStorage, snapshotScope);
        ctx.self = this.self;
        ctx.args = this.args;
        return ctx;
    }

    public Context createCallContext(IValue<?> self, IValue<?>[] args) {
        ScopeStack newScope;
        // 优化：如果父 scope 为空（非闭包），跳过 snapshot 避免 HashMap 复制
        if (this.scopeStack.depth() <= 0) {
            newScope = new ScopeStack();
        } else {
            newScope = new ScopeStack();
            HashMap<VariableKey, VariableReference> snap = this.scopeStack.snapshot();
            if (!snap.isEmpty()) {
                newScope.push();
                newScope.importAll(snap);
            }
        }
        Context ctx = new Context(globalStorage, localStorage, newScope);
        ctx.self = self;
        ctx.args = args != null ? args : EMPTY_ARGS;
        return ctx;
    }

    public Context createLightCallContext(IValue<?> self, IValue<?>[] args) {
        Context ctx = new Context(globalStorage, localStorage, new ScopeStack());
        ctx.self = self;
        ctx.args = args != null ? args : EMPTY_ARGS;
        return ctx;
    }
}
