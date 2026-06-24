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

package priv.seventeen.artist.aria.value;

import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;

public final class FunctionValue extends IValue<ICallable> {

    private final ICallable callable;
    // JIT 互递归组编译用：该函数值对应的子程序，便于编译一个函数时取到兄弟函数的 IR。
    // 仅运行时填充（NEW_FUNCTION 创建处），不参与序列化/相等性。
    private IRProgram sourceProgram;

    public FunctionValue(ICallable callable) {
        this.callable = callable;
    }

    public ICallable getCallable() { return callable; }
    public IRProgram getSourceProgram() { return sourceProgram; }
    public void setSourceProgram(IRProgram sourceProgram) { this.sourceProgram = sourceProgram; }

    @Override public ICallable jvmValue() { return callable; }
    @Override public double numberValue() { return 0; }
    @Override public String stringValue() { return "function"; }
    @Override public boolean booleanValue() { return true; }
    @Override public int typeID() { return 7; }
    @Override public boolean canMath() { return false; }
    @Override public boolean isBaseType() { return false; }

    @Override
    public String toString() { return "function"; }
}
