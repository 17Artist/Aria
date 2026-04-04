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

public enum IROpCode {
    // 常量
    LOAD_CONST, LOAD_NONE, LOAD_TRUE, LOAD_FALSE,

    // 变量（对应Aria的Dot系统）
    LOAD_VAR, STORE_VAR,           // var.xxx
    LOAD_VAL,                       // val.xxx（只读）
    LOAD_GLOBAL, STORE_GLOBAL,     // global.xxx
    LOAD_CLIENT, STORE_CLIENT,     // client.xxx
    LOAD_SERVER, STORE_SERVER,     // server.xxx
    LOAD_SCOPE, STORE_SCOPE,       // 裸标识符（ScopeStack）
    LOAD_SELF,                      // self
    LOAD_ARG, LOAD_ARGS,           // args[n] / args

    // 算术
    ADD, SUB, MUL, DIV, MOD, NEG, INC, DEC,
    ADD_NUM, SUB_NUM, MUL_NUM, DIV_NUM, MOD_NUM,

    // 位运算
    BIT_AND, BIT_OR, BIT_XOR, BIT_NOT, SHL, SHR, USHR,

    // 比较
    EQ, NE, GT, LT, GE, LE, IN_RANGE,

    // 逻辑
    NOT, AND, OR,

    // 属性与调用
    GET_PROP, SET_PROP,
    GET_INDEX, SET_INDEX,
    CALL, CALL_METHOD, CALL_STATIC, CALL_CONSTRUCTOR,

    // 对象创建
    NEW_LIST, NEW_MAP, NEW_FUNCTION, NEW_INSTANCE,

    // 控制流
    JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE, JUMP_IF_NONE,

    // 作用域
    PUSH_SCOPE, POP_SCOPE,

    // 控制信号
    RETURN, BREAK, NEXT,

    // 异常
    TRY_BEGIN, TRY_END, THROW,

    // 异步
    ASYNC_BEGIN, ASYNC_END,

    // 字符串
    CONCAT,

    // ~= 运算符
    INIT_OR_GET,

    // 类
    GET_FIELD, SET_FIELD, INVOKE_SUPER,
    DEFINE_CLASS,  // 定义类：将类元数据（字段默认值+方法+构造器）注册到 scope

    // 移动
    MOVE,  // r[dst] = r[src]

    // 空操作
    NOP,

    // 将常见的 LOAD+OP+STORE 模式合并为单条指令
    VAR_INC,         // var[key] += 1（原地修改）
    VAR_ADD_CONST,   // var[key] += const[idx]
    VAR_ADD_REG,     // var[key] += r[src]
    SCOPE_INC,       // scope[key] += 1
    SCOPE_ADD_REG,   // scope[key] += r[src]

    FOR_RANGE_INIT,  // 初始化 Range 循环：dst=iterVar, a=rangeReg, b=bodyStart
    FOR_RANGE_NEXT,  // Range 循环下一步：dst=iterVar, a=rangeReg, b=counterReg, c=bodyEnd → 跳回bodyStart或退出

    COMMA,              // 逗号运算符：丢弃左值，保留右值
    IN_CHECK,           // 'key' in obj：检查键是否存在
    INSTANCEOF_CHECK,   // a instanceof B：类型检查
}
