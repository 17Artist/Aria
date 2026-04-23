---
title: "数据类型"
description: "Aria 的 7 种基本数据类型详解"
order: 2
---

# 数据类型

Aria 有 7 种基本类型，每种都很直观。你可以用 `type.typeof()` 随时查看一个值是什么类型。

| 类型       | 示例                   | 说明            |
|----------|----------------------|---------------|
| number   | `42`, `3.14`         | 数字，整数和小数都是它   |
| string   | `'hello'`, `"world"` | 文本            |
| boolean  | `true`, `false`      | 对或错，只有两个值     |
| none     | `none`               | "没有值"，表示空     |
| list     | `[1, 2, 3]`          | 有序的值的集合，像购物清单 |
| map      | `{'name': 'Alice'}`  | 键值对，像一本字典     |
| function | `-> { return 1 }`    | 一段可以反复执行的代码   |

---

## number — 数字

数字就是数字，不区分整数和小数，写法和数学里一样：

```aria
var.age = 25
var.pi = 3.14
var.score = age + 10       // 35
```

也支持一些特殊写法（不常用，了解即可）：

```aria
0xFF                // 十六进制，等于 255
0b1010              // 二进制，等于 10
0o77                // 八进制，等于 63
1e10                // 科学计数法，等于 10000000000
```

### 数字的方法

```aria
var.n = 3.14159
n.toInt()               // 3（去掉小数部分）
n.toFixed(2)            // '3.14'（保留两位小数，返回文本）
n.round(3)              // 3.142（四舍五入到指定小数位）
n.abs()                 // 3.14159（绝对值）
n.ceil()                // 4（向上取整）
n.floor()               // 3（向下取整）
n.isNaN()               // false（是否为非数字）
n.isInfinite()          // false（是否为无穷大）
```

---

## string — 字符串

字符串就是一段文本。Aria 有三种写法：

```aria
var.a = 'hello'         // 单引号：纯文本，写什么就是什么
var.b = "hello"         // 双引号：支持插值（见下方）
var.c = """             
这是第一行
这是第二行
"""                     // 三引号：多行文本
```

### 字符串插值

双引号字符串里可以用 `{表达式}` 嵌入变量或计算结果，单引号不行：

```aria
var.name = 'World'
var.msg = "Hello, {name}!"     // Hello, World!
var.calc = "1 + 2 = {1 + 2}"  // 1 + 2 = 3
var.raw = 'Hello, {name}!'    // Hello, {name}!（单引号原样输出）
```

### 常用方法

```aria
var.s = 'Hello World'
s.length()                  // 11
s.substring(0, 5)           // 'Hello'
s.replace('World', 'Aria')  // 'Hello Aria'（字面量替换）
s.replaceAll('\\w+', '*')   // '* *'（正则替换所有匹配）
s.replaceFirst('\\w+', '*') // '* World'（正则替换第一个匹配）
s.split(' ')                // ['Hello', 'World']
s.contains('lo')            // true
s.toUpperCase()             // 'HELLO WORLD'
s.toLowerCase()             // 'hello world'
s.trim()                    // 去掉首尾空格
s.startsWith('Hello')       // true
s.endsWith('World')         // true
s.indexOf('World')          // 6
s.lastIndexOf('l')          // 9
s.charAt(0)                 // 'H'
s.repeat(2)                 // 'Hello WorldHello World'
s.isEmpty()                 // false
s.equals('Hello World')     // true（严格相等）
s.equalsIgnoreCase('hello world')  // true（忽略大小写）
```

### 转义字符

在字符串里表示特殊字符：

| 写法   | 含义  | 写法   | 含义  |
|------|-----|------|-----|
| `\n` | 换行  | `\t` | 制表符 |
| `\\` | 反斜杠 | `\'` | 单引号 |
| `\"` | 双引号 | `\r` | 回车  |

---

## boolean — 布尔值

布尔值只有两个：`true`（真）和 `false`（假），用来做判断：

```aria
var.isReady = true
var.isEmpty = false

if (isReady) {
    print('准备好了！')
}
```

布尔值参与数学运算时，`true` 当作 `1`，`false` 当作 `0`。

---

## none — 空值

`none` 表示"没有值"。当一个变量还没有被赋值，或者函数没有返回任何东西时，它的值就是 `none`：

```aria
var.x = none
print(x)                 // none

var.result = none
if (result == none) {
    print('还没有结果')
}
```

### 安全访问

当你不确定一个值是不是 `none` 时，可以用 `?.` 和 `??` 来安全地访问：

```aria
// 如果 user 是 none，不会报错，直接返回 none
var.name = user?.name

// 如果左边是 none，就用右边的默认值
var.name = user?.name ?? '匿名用户'
```

---

## list — 列表

列表是一组有序的值，用方括号 `[]` 创建。可以放任何类型的值：

```aria
var.fruits = ['苹果', '香蕉', '橘子']
var.numbers = [1, 2, 3, 4, 5]
var.mixed = [1, 'hello', true, none]    // 可以混合类型
var.empty = []                           // 空列表
```

### 基本操作

```aria
var.list = [1, 2, 3]
list.add(4)                 // 添加元素 → [1, 2, 3, 4]
list.get(0)                 // 获取第一个元素 → 1
list.set(0, 10)             // 修改第一个元素 → [10, 2, 3, 4]
list.remove(0)              // 移除第一个元素
list.size()                 // 列表长度
list.contains('x')          // 是否包含某个值
list.indexOf(2)             // 元素首次出现的位置 → 1
list.lastIndexOf(2)         // 元素最后出现的位置
list.isEmpty()              // 是否为空
list.sort()                 // 按数值排序（原地）
list.reverse()              // 反转（原地）
list.clear()                // 清空
list.subList(1, 3)          // 截取子列表 [from, to)
list.addAll([4, 5])         // 追加另一个列表的所有元素
list.removeAll([1, 3])      // 移除与另一个列表重叠的元素
```

### 用 + 和 - 操作列表

```aria
var.a = [1, 2] + [3, 4]    // 合并 → [1, 2, 3, 4]
var.b = [1, 2, 3] + 4      // 追加 → [1, 2, 3, 4]
var.c = [1, 2, 3] - 0      // 移除索引 0 → [2, 3]
```

### 下标访问 `[]`

列表支持用 `[]` 按索引读写元素：

```aria
var.list = [1, 2, 3]

// 读取 — 按索引获取元素，越界返回 none
list[0]                     // 1
list[5]                     // none（越界安全，不会报错）

// 写入 — 设置指定索引的值
list[0] = 10                // [10, 2, 3]

// 写入越界索引时，自动用 none 填充中间空位
list[5] = 99                // [10, 2, 3, none, none, 99]

// 空索引写入 — 追加元素（等同于 list.add）
list[] = 'new'              // [10, 2, 3, none, none, 99, 'new']
```

### 高阶函数

这些方法接受一个函数作为参数，对列表中的每个元素执行操作：

```aria
val.list = [1, 2, 3, 4, 5]

// 映射：对每个元素执行操作，返回新列表
val.doubled = list.map(-> { return args[0] * 2 })
// → [2, 4, 6, 8, 10]

// 过滤：只保留满足条件的元素
val.evens = list.filter(-> { return args[0] % 2 == 0 })
// → [2, 4]

// 累积：把所有元素合并成一个值
val.sum = list.reduce(-> { return args[0] + args[1] }, 0)
// → 15

// 遍历：对每个元素执行操作（不返回新列表）
list.forEach(-> { println(args[0]) })

// 查找：返回第一个满足条件的元素
val.found = list.find(-> { return args[0] > 3 })
// → 4

// 查找索引：返回第一个满足条件的索引
val.idx = list.findIndex(-> { return args[0] > 3 })
// → 3

// 判断：是否全部/存在满足条件
val.allPositive = list.every(-> { return args[0] > 0 })  // true
val.hasEven = list.some(-> { return args[0] % 2 == 0 })  // true

// 映射并展平：如果函数返回列表则展开
val.flat = list.flatMap(-> { return [args[0], args[0] * 10] })
// → [1, 10, 2, 20, 3, 30, 4, 40, 5, 50]

// 按自定义规则排序（原地）
val.words = ['banana', 'fig', 'apple']
words.sortBy(-> { return args[0].length() })
// → ['fig', 'apple', 'banana']

// 拼接为字符串
val.str = list.join(', ')  // '1, 2, 3, 4, 5'
```

---

## map — 字典

字典用来存储"键-值"对，就像一本真正的字典：用一个词（键）查找它的解释（值）。用花括号 `{}` 创建：

```aria
var.person = {'name': 'Alice', 'age': 30}
var.empty = {}
```

### 基本操作

```aria
var.map = {'a': 1, 'b': 2}
map.put('c', 3)             // 添加键值对
map.get('a')                // 获取值 → 1
map.remove('b')             // 移除键值对
map.size()                  // 键值对数量
map.keys()                  // 所有键 → ['a', 'c']
map.values()                // 所有值 → [1, 3]
map.entries()               // 键值对列表 → [['a', 1], ['c', 3]]
map.containsKey('a')        // 是否包含某个键 → true
map.containsValue(1)        // 是否包含某个值 → true
map.isEmpty()               // 是否为空
map.clear()                 // 清空
map.putAll({'d': 4})        // 合并另一个字典
map.putIfAbsent('a', 99)   // 键不存在时才设置（'a' 已存在，不覆盖）
map.getOrDefault('z', 0)   // 获取值，不存在返回默认值 → 0
```

### 用 + 和 - 操作字典

```aria
var.a = {'x': 1} + {'y': 2}    // 合并 → {'x': 1, 'y': 2}
var.b = {'x': 1, 'y': 2} - 'y' // 移除键 → {'x': 1}
```

### 下标访问 `[]`

字典支持用 `[]` 按键读写值：

```aria
var.map = {'name': 'Alice', 'age': 30}

// 读取 — 按键获取值，键不存在返回 none
map['name']                 // 'Alice'
map['missing']              // none

// 写入 — 设置键值对（键存在则覆盖，不存在则新增）
map['name'] = 'Bob'         // {'name': 'Bob', 'age': 30}
map['email'] = 'bob@x.com'  // 新增键
```

### 高阶函数

```aria
val.scores = {'math': 90, 'english': 55, 'science': 80}

// 遍历每个键值对
scores.forEach(-> { println(args[0] + ' = ' + args[1]) })

// 过滤：只保留及格的科目
val.passed = scores.filter(-> { return args[1] >= 60 })
// → {'math': 90, 'science': 80}

// 值映射：把每个分数翻倍
val.doubled = scores.mapValues(-> { return args[0] * 2 })
// → {'math': 180, 'english': 110, 'science': 160}

// 获取键值对列表
val.pairs = scores.entries()
// → [['math', 90], ['english', 55], ['science', 80]]
```

---

## function — 函数

函数是一段可以反复执行的代码。在 Aria 中，所有函数都用 `-> {}` 来定义，参数通过 `args` 获取：

```aria
var.greet = -> {
    return 'Hello, ' + args[0] + '!'
}
print(greet('World'))       // Hello, World!
```

函数可以像普通值一样传递（这叫"一等公民"）：

```aria
var.double = -> { return args[0] * 2 }

// 把函数作为参数传给另一个函数
var.apply = -> {
    val.fn = args[0]
    val.value = args[1]
    return fn(value)
}
print(apply(double, 5))     // 10
```

> 函数的更多用法（闭包、递归、高阶函数等）在 [函数](functions) 章节详细介绍。

---

## 类型检查与转换

### 查看类型

```aria
type.typeof(42)             // 'number'
type.typeof('hello')        // 'string'
type.typeof(true)           // 'boolean'
type.typeof(none)           // 'none'
type.typeof([1, 2])         // 'list'
type.typeof({'a': 1})       // 'map'
type.typeof(-> { })         // 'function'
```

### 判断类型

```aria
type.isNumber(42)           // true
type.isString('hello')      // true
type.isNone(none)           // true
type.isList([1, 2])         // true
type.isMap({'a': 1})        // true
type.isFunction(-> { })     // true
```

### 转换类型

```aria
type.toNumber('42')         // 42
type.toString(42)           // '42'
type.toBoolean(0)           // false
type.toBoolean(1)           // true
```
