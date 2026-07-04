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

package priv.seventeen.artist.aria.compiler.ir;

import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.parser.SourceLocation;
import priv.seventeen.artist.aria.value.IValue;

public class IRProgram {
    private String name;
    private IValue<?>[] constants;
    private VariableKey[] variableKeys;
    private IRInstruction[] instructions;
    private int registerCount;
    private SourceLocation[] sourceMap;
    private IRProgram[] subPrograms;

    private volatile int executionCount;
    private volatile ICallable compiledCode;  // JIT 编译后的原生代码
    private volatile boolean jitScheduled;    // 是否已提交异步 JIT 编译
    // JIT 编译产物是否完全不依赖 Context（如 fastDoubleRecursion / fastDoubleVars 数值路径）
    // 为 true 时调用方可直接以 compiled 作为 FunctionValue 的 callable，省一层 lambda + InvocationData 包装
    private volatile boolean jitContextFree;

    // 自递归检测缓存：避免每次 executeInlineInternal 入口都遍历 code[]
    private volatile byte selfRecursiveFlag; // 0=未检测, 1=是, -1=否

    // 调用快路径标志缓存：避免每次调用都扫描 code[] 判断是否需要分配 varRefs / scopeRefs 缓存数组。
    // -1=未计算, 0=无该类操作（可省数组分配）, 1=有
    private volatile byte hasVarOpsFlag = -1;
    private volatile byte hasScopeOpsFlag = -1;
    // inline 解释器整体可执行性缓存(variables-3/async-2)：-1=未计算, 0=含不支持指令(整体走完整解释器), 1=可 inline
    private volatile byte inlineSupportFlag = -1;

    public IRProgram(String name) {
        this.name = name;
        this.constants = new IValue<?>[0];
        this.variableKeys = new VariableKey[0];
        this.instructions = new IRInstruction[0];
        this.registerCount = 0;
        this.sourceMap = new SourceLocation[0];
        this.subPrograms = new IRProgram[0];
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public IValue<?>[] getConstants() { return constants; }
    public void setConstants(IValue<?>[] constants) { this.constants = constants; }
    public VariableKey[] getVariableKeys() { return variableKeys; }
    public void setVariableKeys(VariableKey[] variableKeys) { this.variableKeys = variableKeys; }
    public IRInstruction[] getInstructions() { return instructions; }
    public void setInstructions(IRInstruction[] instructions) { this.instructions = instructions; }
    public int getRegisterCount() { return registerCount; }
    public void setRegisterCount(int registerCount) { this.registerCount = registerCount; }
    public SourceLocation[] getSourceMap() { return sourceMap; }
    public void setSourceMap(SourceLocation[] sourceMap) { this.sourceMap = sourceMap; }
    public IRProgram[] getSubPrograms() { return subPrograms; }
    public void setSubPrograms(IRProgram[] subPrograms) { this.subPrograms = subPrograms; }

    public int incrementExecCount() { return ++executionCount; }
    public boolean isCompiled() { return compiledCode != null; }
    public ICallable getCompiledCode() { return compiledCode; }
    public void setCompiledCode(ICallable code) { this.compiledCode = code; }
    public boolean isJitScheduled() { return jitScheduled; }
    public void setJitScheduled(boolean scheduled) { this.jitScheduled = scheduled; }
    public boolean isJitContextFree() { return jitContextFree; }
    public void setJitContextFree(boolean v) { this.jitContextFree = v; }

    public byte getSelfRecursiveFlag() { return selfRecursiveFlag; }
    public void setSelfRecursiveFlag(byte flag) { this.selfRecursiveFlag = flag; }

    public byte getHasVarOpsFlag() { return hasVarOpsFlag; }
    public void setHasVarOpsFlag(byte flag) { this.hasVarOpsFlag = flag; }
    public byte getHasScopeOpsFlag() { return hasScopeOpsFlag; }
    public void setHasScopeOpsFlag(byte flag) { this.hasScopeOpsFlag = flag; }
    public byte getInlineSupportFlag() { return inlineSupportFlag; }
    public void setInlineSupportFlag(byte flag) { this.inlineSupportFlag = flag; }
}
