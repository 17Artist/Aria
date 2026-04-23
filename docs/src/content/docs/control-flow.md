---
title: "控制流"
description: "if/while/for/switch/match 控制流语句"
order: 5
---

# 控制流

Aria 提供了 `if`、`while`、`for`、`switch`、`match` 等控制流语句，以及 `break`、`next`、`return` 跳转语句。

## if / elif / else

条件分支语句，条件需要用括号包裹：

```aria
if (score >= 90) {
    print('A')
} elif (score >= 60) {
    print('B')
} else {
    print('C')
}
```

`elif` 可以有多个，`else` 可选但只存在一个且位于末尾：

```aria
if (x > 0) {
    print('positive')
} elif (x == 0) {
    print('zero')
} elif (x > -10) {
    print('small negative')
} else {
    print('large negative')
}
```

## while 循环

当条件为真时重复执行循环体：

```aria
var.i = 0
while (i < 10) {
    print(i)
    i++
}
```

配合 `break` 提前退出：

```aria
var.i = 0
while (true) {
    if (i >= 5) {
        break
    }
    print(i)
    i++
}
```

## for-in 循环

遍历列表：

```aria
for (item in [1, 2, 3]) {
    print(item)
}
```

遍历 Range：

```aria
for (i in Range(0, 10)) {
    print(i)
}
```

解构遍历 Map 的键值对：

```aria
val.map = {'name': 'Alice', 'age': 30}
for (k, v in map) {
    print(k + ' = ' + v)
}
```

## for（C 风格循环）

传统的三段式循环，需要括号包裹：

```aria
for (var.i = 0; i < 10; i++) {
    print(i)
}
```

三个部分分别是初始化、条件、更新：

```aria
for (var.sum = 0, var.i = 1; i <= 100; i++) {
    sum += i
}
print(sum)    // 5050
```

## switch（穿透语义）

`switch` 匹配成功后会穿透到下一个 `case`，需要用 `break` 显式终止：

```aria
switch (value) {
    case 1 {
        print('one')
        break
    }
    case 2 {
        print('two')
        break
    }
    else {
        print('other')
    }
}
```

利用穿透特性处理多个值：

```aria
switch (day) {
    case 'Saturday' {
    }
    case 'Sunday' {
        print('weekend')
        break
    }
    else {
        print('weekday')
    }
}
```

上面的例子中，`'Saturday'` 匹配后穿透到 `'Sunday'` 的代码块，打印 `'weekend'`。

## match（不穿透）

`match` 与 `switch` 类似，但匹配成功后自动跳出，不会穿透：

```aria
match (value) {
    case 1 {
        print('one')
    }
    case 2 {
        print('two')
    }
    else {
        print('other')
    }
}
```

不需要写 `break`，每个 `case` 执行完毕后自动结束。

## break / next / return

三种跳转语句：

`break` — 跳出当前循环或 `switch`：

```aria
for (i in Range(0, 100)) {
    if (i > 10) {
        break
    }
    print(i)
}
```

`next` — 跳过本次迭代，进入下一次循环（等价于其他语言的 `continue`）：

```aria
for (i in Range(0, 10)) {
    if (i % 2 == 0) {
        next
    }
    print(i)    // 只打印奇数：1, 3, 5, 7, 9
}
```

`return` — 从函数中返回值：

```aria
var.max = -> {
    if (args[0] > args[1]) {
        return args[0]
    }
    return args[1]
}
print(max(3, 7))    // 7
```

不带表达式的 `return` 返回 `none`：

```aria
var.check = -> {
    if (args[0] < 0) {
        return          // 返回 none
    }
    print('valid: ' + args[0])
}
```
