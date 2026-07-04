---
title: "函数"
description: "箭头函数、闭包、高阶函数与递归"
order: 6
---

# 函数

Aria 使用 `-> {}` 箭头语法定义函数。函数没有命名参数列表，所有参数通过内置的 `args` 数组访问。

## 定义与调用

```aria
var.greet = -> {
    return 'Hello, ' + args[0] + '!'
}
print(greet('World'))       // Hello, World!
```

> 函数调用位置的裸名（如上面的 `greet(...)`）会回退到 `var` 存储查找，
> 所以 `var.greet` 声明后可以直接 `greet(...)` 调用。

## 多参数

通过 `args[0]`、`args[1]`... 依次访问各个参数，`args.length` 是参数个数；
越界读取 `args[i]` 返回 `none`（不抛异常）：

```aria
var.add = -> {
    return args[0] + args[1]
}
print(add(3, 4))            // 7.0
```

## 函数作为一等公民

函数是值，可以作为参数传递、作为返回值返回：

```aria
var.apply = -> {
    fn = args[0]
    value = args[1]
    return fn(value)
}

var.double = -> { return args[0] * 2 }
print(apply(double, 5))     // 10.0
```

从函数中返回函数：

```aria
var.makeGreeter = -> {
    return -> {
        return 'Hello, ' + args[0] + '!'
    }
}

greet = makeGreeter()
print(greet('Aria'))        // Hello, Aria!
```

### ⚠️ 赋值右侧的自动调用

**赋值语句右侧**若是对裸名 / `var.` 名的**直接读取**，且读到的值可调用，
该值会被**零参自动调用**，变量得到的是调用结果而不是函数本身
（为兼容 Shimmer 历史行为）：

```aria
f = -> { return 9 }
g = f               // f 被自动调用！g = 9.0（不是函数）
```

传参位置（`apply(double, 5)`）与 `return f` **不受影响**，函数值原样传递。

### 空括号与非函数调用

- 对**非函数值**加空括号，括号被丢弃，返回值本身：`x = 5` 后 `x()` 得 `5.0`；
- 对非函数值**带参**调用则抛错（`不支持的后缀运算`）。

## 函数体作用域隔离

函数体使用**全新的作用域栈**，不捕获定义处的裸名临时变量——体内读外层裸名得 `none`、
写裸名也不影响外层。跨函数共享的状态请放进 `var.` 存储（函数体与外层共享
`var` / `val` / `global` / `server` / `client` 存储与 `self` / `args`）：

```aria
var.counter = -> {
    var.count = 0
    return -> {
        var.count++      // 经 var 存储共享（裸名 count 每次调用都是全新作用域）
        return var.count
    }
}

inc = counter()
print(inc())     // 1.0
print(inc())     // 2.0
print(inc())     // 3.0
```

隔离示例——裸名写不透外层：

```aria
s = ''
[1,2,3].forEach(-> { s += args[0] })
print(s)         // ''（体内 s 是函数自己的作用域，外层 s 不变）
```

同理，返回的函数也**不会捕获**外层函数体内的裸名局部变量——
"工厂函数携带配置"这类模式需要经 `var.` / `global.` 存储传递状态。

## 立即调用函数

定义后立即调用：

```aria
result = (-> {
    return 42
})()
print(result)    // 42.0
```

## 递归

`var.` 声明的函数可以在自身内部通过裸名调用自己（调用位置回退 `var` 存储）：

```aria
var.factorial = -> {
    if (args[0] <= 1) {
        return 1
    }
    return args[0] * factorial(args[0] - 1)
}
print(factorial(5))    // 120.0
```

```aria
var.fib = -> {
    if (args[0] <= 1) {
        return args[0]
    }
    return fib(args[0] - 1) + fib(args[0] - 2)
}
print(fib(10))    // 55.0
```

> ⚠️ **裸名函数不能自递归**：`f = -> { ... f(...) ... }` 中体内的 `f` 因作用域隔离
> 解析不到（新作用域里没有外层裸名），运行时抛错。递归函数请始终用 `var.f` 声明。

## 高阶函数

Aria 的列表和字典内置了丰富的高阶函数，接受函数作为参数。

### 列表高阶函数

`map(fn)` — 对每个元素调用 `fn(element, index)`，返回新列表：

```aria
list = [1, 2, 3, 4, 5]
doubled = list.map(-> { return args[0] * 2 })
// doubled = [2, 4, 6, 8, 10]
```

`filter(fn)` — 保留 `fn(element, index)` 返回 `true` 的元素：

```aria
list = [1, 2, 3, 4, 5, 6]
evens = list.filter(-> { return args[0] % 2 == 0 })
// evens = [2, 4, 6]
```

`reduce(fn, initial?)` — 通过 `fn(accumulator, element, index)` 累积计算：

```aria
list = [1, 2, 3, 4, 5]
sum = list.reduce(-> { return args[0] + args[1] }, 0)
// sum = 15.0
```

`forEach(fn)` — 对每个元素调用 `fn(element, index)`，无返回值：

```aria
list = ['a', 'b', 'c']
list.forEach(-> { println(args[1] + ': ' + args[0]) })
// 0.0: a
// 1.0: b
// 2.0: c
```

`find(fn)` — 返回第一个满足条件的元素，未找到返回 `none`：

```aria
list = [1, 2, 3, 4, 5]
found = list.find(-> { return args[0] > 3 })
// found = 4
```

`findIndex(fn)` — 返回第一个满足条件的索引，未找到返回 `-1`：

```aria
list = [10, 20, 30, 40]
idx = list.findIndex(-> { return args[0] > 25 })
// idx = 2
```

`every(fn)` — 所有元素都满足条件时返回 `true`：

```aria
list = [2, 4, 6, 8]
allEven = list.every(-> { return args[0] % 2 == 0 })
// allEven = true
```

`some(fn)` — 至少一个元素满足条件时返回 `true`：

```aria
list = [1, 3, 5, 6]
hasEven = list.some(-> { return args[0] % 2 == 0 })
// hasEven = true
```

`flatMap(fn)` — 映射后展平一层：

```aria
list = [1, 2, 3]
result = list.flatMap(-> { return [args[0], args[0] * 10] })
// result = [1, 10, 2, 20, 3, 30]
```

`sortBy(fn)` — 按 `fn(element)` 的返回值排序（原地修改）：

```aria
list = ['banana', 'apple', 'fig']
list.sortBy(-> { return args[0].length() })
// list = ['fig', 'apple', 'banana']
```

`join(separator?)` — 将列表元素拼接为字符串，默认分隔符为 `,`：

```aria
list = ['hello', 'world']
str = list.join(' ')
// str = 'hello world'

csv = [1, 2, 3].join(',')
// csv = '1.0,2.0,3.0'
```

### 字典高阶函数

`forEach(fn)` — 遍历每个键值对，调用 `fn(key, value)`：

```aria
m = {'name': 'Alice', 'age': 30}
m.forEach(-> { println(args[0] + ' = ' + args[1]) })
// name = Alice
// age = 30.0
```

`filter(fn)` — 保留 `fn(key, value)` 返回 `true` 的键值对，返回新字典：

```aria
scores = {'math': 90, 'english': 55, 'science': 80}
passed = scores.filter(-> { return args[1] >= 60 })
// passed = {'math': 90, 'science': 80}
```

`mapValues(fn)` — 对每个值调用 `fn(value, key)`，返回新字典：

```aria
prices = {'apple': 5, 'banana': 3}
doubled = prices.mapValues(-> { return args[0] * 2 })
// doubled = {'apple': 10, 'banana': 6}
```

`entries()` — 返回 `[[key, value], ...]` 形式的列表：

```aria
m = {'a': 1, 'b': 2}
pairs = m.entries()
// pairs = [['a', 1], ['b', 2]]
```
