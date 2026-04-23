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

## 多参数

通过 `args[0]`、`args[1]`... 依次访问各个参数：

```aria
var.add = -> {
    return args[0] + args[1]
}
print(add(3, 4))            // 7
```

## 函数作为一等公民

函数是值，可以赋值给变量、作为参数传递、作为返回值返回：

```aria
var.apply = -> {
    val.fn = args[0]
    val.value = args[1]
    return fn(value)
}

var.double = -> { return args[0] * 2 }
print(apply(double, 5))     // 10
```

从函数中返回函数：

```aria
var.multiplier = -> {
    val.factor = args[0]
    return -> {
        return args[0] * factor
    }
}

val.triple = multiplier(3)
print(triple(10))           // 30
```

## 闭包与作用域捕获

内部函数可以捕获外部作用域中的变量，形成闭包：

```aria
var.counter = -> {
    var.count = 0
    return -> {
        count++
        return count
    }
}

val.next = counter()
print(next())    // 1
print(next())    // 2
print(next())    // 3
```

## 立即调用函数

定义后立即调用：

```aria
val.result = (-> {
    return 42
})()
print(result)    // 42
```

## 递归

函数可以在自身内部调用自己：

```aria
var.factorial = -> {
    if (args[0] <= 1) {
        return 1
    }
    return args[0] * factorial(args[0] - 1)
}
print(factorial(5))    // 120
```

```aria
var.fib = -> {
    if (args[0] <= 1) {
        return args[0]
    }
    return fib(args[0] - 1) + fib(args[0] - 2)
}
print(fib(10))    // 55
```

## 高阶函数

Aria 的列表和字典内置了丰富的高阶函数，接受函数作为参数。

### 列表高阶函数

`map(fn)` — 对每个元素调用 `fn(element, index)`，返回新列表：

```aria
val.list = [1, 2, 3, 4, 5]
val.doubled = list.map(-> { return args[0] * 2 })
// doubled = [2, 4, 6, 8, 10]
```

`filter(fn)` — 保留 `fn(element, index)` 返回 `true` 的元素：

```aria
val.list = [1, 2, 3, 4, 5, 6]
val.evens = list.filter(-> { return args[0] % 2 == 0 })
// evens = [2, 4, 6]
```

`reduce(fn, initial?)` — 通过 `fn(accumulator, element, index)` 累积计算：

```aria
val.list = [1, 2, 3, 4, 5]
val.sum = list.reduce(-> { return args[0] + args[1] }, 0)
// sum = 15
```

`forEach(fn)` — 对每个元素调用 `fn(element, index)`，无返回值：

```aria
val.list = ['a', 'b', 'c']
list.forEach(-> { println(args[1] + ': ' + args[0]) })
// 0: a
// 1: b
// 2: c
```

`find(fn)` — 返回第一个满足条件的元素，未找到返回 `none`：

```aria
val.list = [1, 2, 3, 4, 5]
val.found = list.find(-> { return args[0] > 3 })
// found = 4
```

`findIndex(fn)` — 返回第一个满足条件的索引，未找到返回 `-1`：

```aria
val.list = [10, 20, 30, 40]
val.idx = list.findIndex(-> { return args[0] > 25 })
// idx = 2
```

`every(fn)` — 所有元素都满足条件时返回 `true`：

```aria
val.list = [2, 4, 6, 8]
val.allEven = list.every(-> { return args[0] % 2 == 0 })
// allEven = true
```

`some(fn)` — 至少一个元素满足条件时返回 `true`：

```aria
val.list = [1, 3, 5, 6]
val.hasEven = list.some(-> { return args[0] % 2 == 0 })
// hasEven = true
```

`flatMap(fn)` — 映射后展平一层：

```aria
val.list = [1, 2, 3]
val.result = list.flatMap(-> { return [args[0], args[0] * 10] })
// result = [1, 10, 2, 20, 3, 30]
```

`sortBy(fn)` — 按 `fn(element)` 的返回值排序（原地修改）：

```aria
val.list = ['banana', 'apple', 'fig']
list.sortBy(-> { return args[0].length() })
// list = ['fig', 'apple', 'banana']
```

`join(separator?)` — 将列表元素拼接为字符串，默认分隔符为 `,`：

```aria
val.list = ['hello', 'world']
val.str = list.join(' ')
// str = 'hello world'

val.csv = [1, 2, 3].join(',')
// csv = '1,2,3'
```

### 字典高阶函数

`forEach(fn)` — 遍历每个键值对，调用 `fn(key, value)`：

```aria
val.map = {'name': 'Alice', 'age': 30}
map.forEach(-> { println(args[0] + ' = ' + args[1]) })
// name = Alice
// age = 30
```

`filter(fn)` — 保留 `fn(key, value)` 返回 `true` 的键值对，返回新字典：

```aria
val.scores = {'math': 90, 'english': 55, 'science': 80}
val.passed = scores.filter(-> { return args[1] >= 60 })
// passed = {'math': 90, 'science': 80}
```

`mapValues(fn)` — 对每个值调用 `fn(value, key)`，返回新字典：

```aria
val.prices = {'apple': 5, 'banana': 3}
val.doubled = prices.mapValues(-> { return args[0] * 2 })
// doubled = {'apple': 10, 'banana': 6}
```

`entries()` — 返回 `[[key, value], ...]` 形式的列表：

```aria
val.map = {'a': 1, 'b': 2}
val.pairs = map.entries()
// pairs = [['a', 1], ['b', 2]]
```

## JS 模式函数参数增强

JavaScript 模式下函数/箭头函数的参数支持以下增强（Aria 原生语法通过 `args[N]` 访问参数，不受影响）：

### 默认参数

```javascript
function greet(name = 'World') {
    return 'Hello, ' + name + '!';
}

greet();        // 'Hello, World!'
greet('Aria');  // 'Hello, Aria!'
```

默认值用 nullish coalesce 语义：参数为 `null` / `undefined` 时才应用默认值。

### 参数解构

对象解构 `{a, b}` 和数组解构 `[a, b]` 都可用于函数参数：

```javascript
// 对象参数解构
function sumFields({ a, b }) {
    return a + b;
}
sumFields({ a: 7, b: 35 });  // 42

// 数组参数解构
function first2([a, b]) {
    return a + b;
}
first2([10, 20]);  // 30

// 箭头函数参数解构
let f = ({ x, y }) => x + y;
f({ x: 10, y: 32 });  // 42
```

### 剩余参数 `...rest`

```javascript
function join(sep, ...parts) {
    // parts 是剩余参数的列表
    return parts.join(sep);
}
```
