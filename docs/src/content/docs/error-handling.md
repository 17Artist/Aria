---
title: "异常处理"
description: "try/catch/finally 与 throw 语句"
order: 8
---

# 异常处理

Aria 提供 `try` / `catch` / `finally` 结构来处理运行时异常，以及 `throw` 语句来主动抛出异常。

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
    var.file = openFile('data.txt')
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
var.data = none
try {
    data = parseInput(rawInput)
} catch (e) {
    print('Parse failed: ' + e)
    data = defaultValue
}
```

使用 `finally` 确保资源清理：

```aria
var.connection = none
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
