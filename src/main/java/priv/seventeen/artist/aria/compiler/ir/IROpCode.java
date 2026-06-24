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
    ASYNC_BEGIN, ASYNC_END, AWAIT,

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

    COMMA,              // 保留槽位:逗号运算符已移除(仅作分隔符),此常量为 .aria 序列化 ordinal 稳定而留,从不发射
    IN_CHECK,           // 'key' in obj：检查键是否存在
    INSTANCEOF_CHECK,   // a instanceof B：类型检查

    // 注:新增 opcode 必须追加在末尾,保持既有 ordinal 不变(.aria 二进制按 ordinal 序列化)
    STORE_VAL,          // val.xxx = value：写入 val 不可变存储(脚本端只可初始化一次,重赋报错;Java 端 forceSet 可覆盖)
    DECLARE_SCOPE,      // 在当前作用域层级声明并绑定(catch 变量等声明性绑定,强制 shadow 外层同名变量,
                        // 区别于 STORE_SCOPE 的"裸名赋值更新已存在的外层/var 绑定")
    MAP_MERGE,          // 字典展开 {...x}：dst = dst 合并 r[a](右覆盖左)；r[a] 非 map 则报错
                        // (区别于通用 ADD 对 map+非map 的静默容错)
    NEW_ASYNC,          // async { body }：把 body(子程序 a)提交线程池执行，dst = 真 PromiseValue；
                        // 捕获闭包作用域 + 传播沙箱到 worker 线程；主线程不执行 body（await 阻塞取结果）
}
