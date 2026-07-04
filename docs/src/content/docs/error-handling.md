---
title: "异常处理"
description: "try/catch/finally 与 throw 语句"
order: 8
---

# 异常处理

Aria 提供 `try` / `catch` / `finally` 结构来处理运行时异常，以及 `throw` 语句来主动抛出异常。

## 运行时错误消息格式

未被脚本捕获的运行时错误抛给宿主时，消息带有**编译单元名、行号与出错源码行**：

```
单元: [脚本名] 运行时错误, 位于第 X 行, 到第 X 行
请检查: <出错的源码行>
错误信息: <具体原因> (line X:Y)
```

例如执行 `return [1,2][5]`：

```
单元: [eval] 运行时错误, 位于第 1 行, 到第 1 行
请检查: return [1,2][5]
错误信息: 列表索引越界: 5 (size=2) (line 1:8)
```

常见运行时错误：

| 场景                             | 错误信息                          |
|--------------------------------|-------------------------------|
| 下标读取列表越界（`l[5]`）               | `列表索引越界: 5 (size=2)`          |
| 调用命名空间中不存在的函数（`math.nope(1)`）  | `点运算解析工具集函数不存在: math.nope`    |
| 对不可调用的值带参调用（`x = 5` 后 `x(1)`）  | `不支持的后缀运算`                    |
| 非法操作数组合（`1 + [1]` 等）           | `number 类型不支持与 list 类型进行加法运算` |
| 对非数字字符串取负（`-x`，x 为 `'abc'`）    | `字符串内容非数字: ... 无法转换为数字进行运算`   |

> 注意几类**不报错**的情形：读取未定义裸名 → `none`；`args` 越界 → `none`；
> 除以 0 → `0.0`；对非函数值加**空**括号 → 返回值本身；`list.get()` 上越界 → `none`。

## try / catch / finally

基本语法：

```aria
try {
    var.result = riskyOperation()
} catch (e) {
    print('Error: ' + e)
} finally {
    cleanup()
}
```

三个块的组合规则：
- `try` 块是必需的
- `catch` 块是可选的
- `finally` 块是可选的
- `catch` 和 `finally` 至少需要一个

## catch 变量

`catch` 后的变量名用括号包裹，用于接收异常信息：

```aria
try {
    throw 'something went wrong'
} catch (e) {
    print(e)    // something went wrong
}
```

也可以省略括号和变量名，不捕获异常信息：

```aria
try {
    throw 'error'
} catch {
    print('An error occurred')
}
```

## throw 语句

使用 `throw` 抛出异常。可以抛出任意值：

```aria
throw 'something went wrong'
```

```aria
throw 'Invalid argument: ' + arg
```

抛出的值会被 `catch` 块中的变量接收。

## 仅 try-finally

当不需要捕获异常，但需要确保清理逻辑执行时，可以省略 `catch`：

```aria
try {
    file = openFile('data.txt')
    processFile(file)
} finally {
    closeFile(file)
}
```

## 嵌套异常处理

`try` / `catch` / `finally` 可以嵌套使用：

```aria
try {
    try {
        throw 'inner error'
    } catch (e) {
        print('Inner catch: ' + e)
        throw 'rethrown: ' + e
    }
} catch (e) {
    print('Outer catch: ' + e)
}
// Inner catch: inner error
// Outer catch: rethrown: inner error
```


## 最佳实践

将可能出错的代码放在 `try` 块中，保持 `try` 块尽量小：

```aria
data = none
try {
    data = parseInput(rawInput)
} catch (e) {
    print('Parse failed: ' + e)
    data = defaultValue
}
```

使用 `finally` 确保资源清理：

```aria
connection = none
try {
    connection = connect(host, port)
    connection.send(data)
} catch (e) {
    print('Connection error: ' + e)
} finally {
    if (connection != none) {
        connection.close()
    }
}
```

在需要时重新抛出异常，添加上下文信息：

```aria
try {
    processData(input)
} catch (e) {
    throw 'Failed to process data: ' + e
}
```

## 编译错误与 lenient 模式

默认（严格）模式下任何**解析错误**都会在编译时立即抛出 `CompileException`（fail-fast），
坏语句不会被静默丢弃。

宿主可以用 **lenient 编译模式**（`Aria.compile(name, ctx, code, true)`）恢复
Shimmer 式的宽容行为：解析出错的语句**及其后所有内容**被丢弃，保留前缀程序继续执行，
错误记录为警告（`getWarnings()`）。详见 [JVM 嵌入指南](embedding)。
