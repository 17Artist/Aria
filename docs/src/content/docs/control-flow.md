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
i = 0
while (i < 10) {
    print(i)
    i++
}
```

> ⚠️ `while` 内的 `break` 不是"跳出这个 while"——它会**向外泄漏**：
> 有外层 `for` 时直接跳出那个 `for`；没有时**整个脚本立即终止**（返回 `none`）。
> 这是为兼容 Shimmer 历史行为保留的语义，详见下文 [break / next / return](#break--next--return)。
> 需要"提前退出 while"时请用循环条件表达：

```aria
i = 0
stop = false
while (!stop && i < 100) {
    if (i >= 5) {
        stop = true
    } else {
        print(i)
        i++
    }
}
```

## for-in 循环

遍历列表：

```aria
for (item in [1, 2, 3]) {
    print(item)
}
```

遍历 Range（`0..10` 是 `Range(0, 10)` 的字面量简写，**双端闭区间**）：

```aria
for (i in Range(0, 10)) {   // 遍历 0.0 ~ 10.0（含 10）
    print(i)
}

for (i in 0..10) {          // 等价写法
    print(i)
}
```

> `Range(a, b)` 在 `a > b` 且未显式给步长时是**空区间**（一次都不执行，不自动倒序）；
> 倒序需显式步长：`Range(10, 0, -1)`。

解构遍历 Map 的键值对：

```aria
m = {'name': 'Alice', 'age': 30}
for (k, v in m) {
    print(k + ' = ' + v)
}
```

几个值得注意的细节：

- 含 `none` 元素的列表可以**完整遍历**（不会在 `none` 处提前结束）；
- Map 解构时变量多于可取值时，多出的变量为 `none`（不报错）；
- **循环变量在循环结束后仍然可见**，保留最后一次迭代的值：

```aria
for (i in 1..3) { }
print(i)    // 3.0（循环后可见）
```

## for（C 风格循环）

传统的三段式循环，需要括号包裹：

```aria
for (i = 0; i < 10; i++) {
    print(i)
}
```

三个部分分别是初始化、条件、更新：

```aria
sum = 0
for (i = 1; i <= 100; i++) {
    sum += i
}
print(sum)    // 5050.0
```

初始化段只声明一个循环变量；需要额外变量时在 `for` 之前声明。
注意初始化段应使用**裸名**（`i = 0`）——若写 `var.i = 0`，条件里的裸名 `i`
读到的是另一个变量（命名空间隔离，见[变量系统](variables)）。

## switch

`switch`按顺序对每个 `case` 求条件并与"当前比对值"比较：

- 匹配的 `case` 执行其代码块，且**比对值被替换为该块的结果值**，随后继续比对后续 `case`；
- 所有 `case` 比对完后 `else` **总是执行**（若存在）；
- 已执行的块内出现 `break` 时，`switch` 立即结束并**跳过 `else`**；
- 不存在 C 式的无条件穿透。

```aria
switch (value) {
    case 1 {
        print('one')
        break    // 结束 switch,跳过 else
    }
    case 2 {
        print('two')
        break
    }
    else {
        print('other')    // 无 break 时总会执行
    }
}
```

注意：不写 `break` 时匹配块执行后 `else` 也会执行；且后续 `case` 的条件是与
匹配块的**结果值**比较（通常不再匹配）。

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

`break` — 跳出当前 `for` 循环或 `switch`：

```aria
for (i in Range(0, 100)) {
    if (i > 10) {
        break
    }
    print(i)
}
```

注意：`while` 内的 `break` 会**泄漏**——
跳出自身后继续向外传播，被最近的外层 `for`/`switch` 消耗；没有消耗者时整个脚本
终止（返回 `none`，`while` 之后的语句不再执行）。同理，`while` 末轮以 `next`
结束且随后条件为假退出时，残留的 `next` 也会向外泄漏。循环外的 `break`/`next`
同样使脚本终止。

`next` — 跳过本次迭代，进入下一次循环（等价于其他语言的 `continue`）：

```aria
for (i in Range(0, 10)) {
    if (i % 2 == 0) {
        next
    }
    print(i)    // 只打印奇数：1.0, 3.0, 5.0, 7.0, 9.0
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
print(max(3, 7))    // 7.0
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
