---
title: "JavaScript 兼容模式"
description: "ES6+ 语法支持与 JS→Aria 语义映射"
order: 13
---

# JavaScript 兼容模式

Aria 内置了 JavaScript 兼容解析器，覆盖 ES5.1 大部分语法和 ES6 常用特性（class、箭头函数、模板字符串、`let`/`const`、解构、`?.`、`??`）。JS 代码在解析阶段被转换为等价的 Aria AST，共享同一套 IR/VM/JIT 管线——不是另一个引擎，而是同一个引擎的另一种语法前端。

适用于从 Nashorn 迁移的项目：`Java.type()`、`new ClassName()`、`.static` 访问等 Nashorn 惯用写法均已兼容。

## 启用方式

### 自动检测

当文件名以 `.js` 结尾时，Aria 自动使用 JavaScript 模式：

```java
// 自动检测为 JavaScript 模式
var unit = Aria.compile("script.js", ctx, code);
```

### 显式指定

通过 `Mode.JAVASCRIPT` 参数显式启用：

```java
var unit = Aria.compile("test", ctx, code, Aria.Mode.JAVASCRIPT);
```

或对单次 `eval` 调用使用显式模式重载（当 code 字符串没有对应 `.js` 文件名时特别有用——例如想在 `Aria.eval` 里用模板字符串、箭头函数等 ES6 语法）：

```java
IValue<?> result = Aria.eval("let n = 'World'; return `Hello, ${n}!`;",
        ctx, Aria.Mode.JAVASCRIPT);
```

无参 `Aria.eval(code, ctx)` 固定走 Aria 原生模式，反引号/`let` 等 JS 专属语法会报错。

内部实现中，`detectMode()` 方法根据文件名后缀判断模式：

```java
public static Mode detectMode(String filename) {
    if (filename != null && filename.endsWith(".js")) return Mode.JAVASCRIPT;
    return Mode.ARIA;
}
```

JavaScript 模式下，词法分析器使用扩展的关键字表（`JS_KEYWORDS`），在 Aria 原生关键字基础上增加了 `let`、`const`、`function`、`var`、`typeof`、`instanceof`、`of`、`do`、`delete`、`void`、`default`、`this`、`static`、`null`、`undefined` 等关键字。其中 `continue` 被直接映射为 `NEXT` token。

## 支持的 JS 语法

| 语法类别   | 支持的特性                                                                                                 |
|--------|-------------------------------------------------------------------------------------------------------|
| 变量声明   | `var`、`let`、`const`，数组解构 `[a, b]`，对象解构 `{a, b}`，剩余参数 `...rest`                                        |
| 函数     | `function` 声明、函数表达式、箭头函数、参数默认值、**参数解构** `({a,b}) => ...` / `function f([x,y])`、`arguments` 对象、嵌套函数、递归、高阶函数 |
| 控制流    | `if`/`else if`/`else`、`while`、`for`（经典三段式）、`for...of`、`for...in`、`do...while`、`break`、`continue`、标签语句 |
| switch | `switch`/`case`/`default`/`break`                                                                     |
| 异常处理   | `try`/`catch`/`finally`、`throw`                                                                       |
| 类      | `class`、`extends`、`constructor`、方法、字段、**`static` 字段与方法**（支持继承）、`super.method()` 与 `super(args)` 构造调用、getter/setter |
| 运算符    | `===`/`!==`、`&&`/`\|\|`/`!`、三元 `? :`、`typeof`、`instanceof`、`in`、`?.`、`??`、`++`/`--`、逗号运算符、算术/比较/位运算   |
| 对象/数组  | 对象 spread `{...a, b}`、数组 spread `[...a, b]`、短属性 `{x, y}`、方法简写 `{ m() { ... } }`、计算属性 `[key]: val`  |
| 字面量    | 数字、字符串（含模板字符串 `` `${expr}` ``）、布尔值、`null`、`undefined`、数组 `[]`、对象 `{}`                                 |
| 模块     | `import { a, b } from 'module'`、`import * as name from 'module'`、`export`                             |
| 异步     | **`await expr`**（阻塞等待 Promise）、`Promise.resolve` / `Promise.reject` / `Promise.all`、`.then` / `.catchErr`；`async function` 作为 no-op 标记 |
| ASI 续行 | 行首 `+` `-` `*` `/` `.` `?.` `&&` `\|\|` `??` 等二元运算符/成员访问会自动接续上一行（跨多个空行也支持） |

`JSON.parse` / `JSON.stringify` 标准库支持（同时保留 Aria 原生小写 `json.parse` / `json.stringify`）。

## JS → Aria 语义映射

### 变量声明

`var` 和 `let` 映射为可变变量（`var.xxx`），`const` 映射为不可变变量（`val.xxx`）：

| JS                | Aria            |
|-------------------|-----------------|
| `var x = 42`      | `var.x = 42`    |
| `let x = 10`      | `var.x = 10`    |
| `const PI = 3.14` | `val.PI = 3.14` |

未初始化的变量自动赋值为 `none`。

### 函数声明

`function` 声明被转换为 lambda 赋值，参数通过 `args[N]` 绑定：

JS 代码：
```javascript
function add(a, b) {
    return a + b;
}
```

等价 Aria 代码：
```aria
var.add = -> {
    a = args[0]
    b = args[1]
    return a + b
}
```

### 箭头函数

表达式体箭头函数自动添加 `return`：

JS 代码：
```javascript
const double = (x) => x * 2;
```

等价 Aria 代码：
```aria
val.double = -> {
    x = args[0]
    return x * 2
}
```

块体箭头函数保持原有 `return` 语句：

JS 代码：
```javascript
const add = (a, b) => {
    return a + b;
};
```

等价 Aria 代码：
```aria
val.add = -> {
    a = args[0]
    b = args[1]
    return a + b
}
```

单参数箭头函数可省略括号：

```javascript
const square = x => x * x;
```

### this → self

JS 中的 `this` 关键字被词法分析器识别为 `THIS` token，在运行时映射为 Aria 的 `self` 语义。

### null / undefined → none

`null` 和 `undefined` 都映射为 Aria 的 `none` 值：

| JS | Aria |
|----|-----------|
| `null` | `none` |
| `undefined` | `none` |

### 严格等于 === / !== → == / !=

JS 的严格等于运算符映射为 Aria 的普通等于运算符：

| JS | Aria |
|----|-----------|
| `a === b` | `a == b` |
| `a !== b` | `a != b` |

### typeof → type.typeof()

JS 的 `typeof` 运算符映射为 Aria 的 `type.typeof()` 调用：

JS 代码：
```javascript
typeof 42    // "number"
```

等价 Aria 代码：
```aria
type.typeof(42)
```

### continue → next

JS 的 `continue` 在词法分析阶段直接映射为 `NEXT` token，对应 Aria 的 `next` 语句：

| JS         | Aria   |
|------------|--------|
| `continue` | `next` |

### do...while → while(true) + 条件 break

`do...while` 循环被转换为 `while(true)` 加条件退出：

JS 代码：
```javascript
do {
    count = count + 1;
} while (count < 5);
```

等价 Aria 代码：
```aria
while (true) {
    count = count + 1
    if (!(count < 5)) {
        break
    }
}
```

解析器在循环体末尾插入 `if(!condition) { break }` 来模拟 `do...while` 语义。

### for...of → for-in

JS 的 `for...of` 映射为 Aria 的 `for-in` 循环：

JS 代码：
```javascript
for (let x of [1, 2, 3]) {
    sum = sum + x;
}
```

等价 Aria 代码：
```aria
for x in [1, 2, 3] {
    sum = sum + x
}
```

`for...in` 同样映射为 `ForInStmt`。

### switch / case / default

JS 的 `switch` 语句映射为 Aria 的 `SwitchStmt`，`default` 分支映射为 else 块：

JS 代码：
```javascript
switch (x) {
    case 1:
        result = 'one';
        break;
    case 2:
        result = 'two';
        break;
    default:
        result = 'other';
}
```

等价 Aria 代码：
```aria
switch (x) {
    case 1 {
        result = 'one'
        break
    }
    case 2 {
        result = 'two'
        break
    }
    else {
        result = 'other'
    }
}
```

### class 声明

JS 的 `class` 声明映射为 Aria 的 `ClassDeclStmt`：

JS 代码：
```javascript
class Animal {
    constructor(name) {
        this.name = name;
    }
    speak() {
        return this.name + ' makes a sound';
    }
}

class Dog extends Animal {
    speak() {
        return this.name + ' barks';
    }
}
```

等价 Aria 代码：
```aria
class Animal {
    new = -> {
        self.name = args[0]
    }
    speak = -> {
        return self.name + ' makes a sound'
    }
}

class Dog extends Animal {
    speak = -> {
        return self.name + ' barks'
    }
}
```

## 参数脱糖机制

JavaScript 函数的命名参数在 Aria 中不存在直接对应物。JS 兼容解析器通过 `buildParamBindings` 方法将参数转换为 `args[N]` 索引绑定：

```aria
// 对于 function foo(a, b, c) { ... }
// 生成以下绑定语句插入到函数体开头：
a = args[0]
b = args[1]
c = args[2]
```

具体规则：
- 每个参数 `params[i]` 生成一条赋值语句：`参数名 = args[i]`
- 绑定使用裸标识符赋值（`STORE_SCOPE`），确保每次函数调用有独立的参数作用域
- 参数默认值在解析阶段被跳过（简化处理），不会生成默认值逻辑
- 剩余参数（`...rest`）在参数列表解析时被识别，但 spread 标记被消费后按普通参数处理

这意味着以下 JS 代码：

```javascript
const greet = (name, greeting) => {
    return greeting + ', ' + name + '!';
};
```

在内部被转换为：

```aria
val.greet = -> {
    name = args[0]
    greeting = args[1]
    return greeting + ', ' + name + '!'
}
```

## 注解支持

JS 兼容模式完整支持 Aria 的 `@annotation` 语法，可用于 class 声明和成员：

```javascript
@component('UserList')
class UserList {
    @observable
    data = []

    @action
    addItem(item) {
        this.data.push(item);
    }
}
```

注解在 JS 模式和 Aria 模式中行为完全一致，Java 端通过 `AnnotationRegistry` 读取。

## 限制和不支持的特性

| 不支持的特性                     | 说明                          | 替代方案                        |
|----------------------------|-----------------------------|-----------------------------|
| 正则表达式字面量                   | 不支持 `/pattern/flags`        | `regex.match(pattern, str)` |
| 变量提升（hoisting）             | 函数和变量不会提升                   | 先声明后使用                      |
| 原型链                        | 不支持 `prototype`/`__proto__` | 使用 `class`/`extends`        |
| 生成器函数                      | 不支持 `function*`/`yield`     | —                           |
| `Symbol`/`Proxy`/`Reflect` | 不支持                         | —                           |
| `with`/`debugger`          | 不支持                         | —                           |
| `async function` 自动包 Promise | 简化实现：`async` 只是 no-op 标记，body 同步执行；要让调用方得到 Promise，显式 `return Promise.resolve(x)` | — |

### Nashorn 迁移注意事项

| Nashorn 写法              | Aria 中的行为                         |
|-------------------------|-----------------------------------|
| `Java.type('xxx')`      | 完全兼容，也可用 `use('xxx')` 简写          |
| `new ClassName()`       | JS 模式完全兼容                         |
| `Class.static.method()` | 兼容，`.static` 被自动忽略                |
| `obj instanceof Type`   | 完全兼容                              |
| `/pattern/flags`        | 不支持，用 `regex.match(pattern, str)` |
| `arguments`             | 用 `args` 替代                       |
| `arguments.length`      | 用 `args.size()`                   |

## Promise 与 await

Aria 提供基于 `CompletableFuture` 的 Promise 实现，支持 `await` 关键字与常用 Promise 静态/实例方法。

### 基础用法

```javascript
// Promise.resolve / Promise.reject
let p = Promise.resolve(42);
let v = await p;              // v = 42

// 非 Promise 值 await 直接透传（JS 语义）
let x = await 7;              // x = 7

// Promise.all
let results = await Promise.all([Promise.resolve(1), Promise.resolve(2)]);
// results = [1, 2]

// then 链
let r = Promise.resolve(10)
    .then(x => x * 2)
    .then(x => x + 1);
let finalVal = await r;       // 21

// 异常处理：.catchErr（.catch 也注册了别名）
let safe = Promise.reject('bad').catchErr(err => 'caught: ' + err);
await safe;                   // 'caught: bad'
```

### 语义说明

- **`await expr`**：若 `expr` 求值为 `PromiseValue`，阻塞当前线程直到 Future 完成并返回解析值；否则直接返回 `expr`。这不是事件循环而是阻塞等待，适合 mod 脚本、嵌入脚本等非单线程场景。
- **`async function f() { ... }`**：`async` 关键字作为 **no-op 标记**，`f()` 的 body 同步执行。若想让调用方拿到 Promise，显式 `return Promise.resolve(...)`。理由：真正的并发来源通常是 `fetch`/`http.get` 等生产者——它们把工作提交到 `ThreadPoolManager` 并返回 `PromiseValue`，消费者用 `await` 即可。
- **reject**：`Promise.reject(reason)` 创建已拒绝的 Promise；`await` 一个被拒绝的 Promise 会抛出 `RuntimeException`，可在 `try/catch` 中捕获，也可用 `.catchErr(fn)` 链式处理。

### Java 端互操作

用户可直接在 Java 端构造 `PromiseValue` 返回给脚本：

```java
import java.util.concurrent.CompletableFuture;
import priv.seventeen.artist.aria.runtime.ThreadPoolManager;
import priv.seventeen.artist.aria.value.*;

// 注册一个 fetch 函数：返回 Promise<StringValue>
manager.registerStaticFunction("", "fetch", data -> {
    String url = data.get(0).stringValue();
    CompletableFuture<IValue<?>> future = CompletableFuture.supplyAsync(() -> {
        // 在线程池上做 HTTP 请求
        String body = httpGet(url);
        return (IValue<?>) new StringValue(body);
    }, ThreadPoolManager.INSTANCE.executor());
    return new PromiseValue(future);
});
```

脚本端：

```javascript
let data = await fetch('http://example.com/api');
console.log(data);
```

## ASI 续行规则

Aria 的 JS 解析器支持标准的自动分号插入（ASI）续行：行首是二元运算符/成员访问符时，自动视为上一行的延续，**可跨多个空行**。

```javascript
// 合法：所有 + 在行首
let s = 'a'
    + 'b'
    + 'c';

// 合法：链式方法调用
let upper = 'hello'
    .toUpperCase()
    .trim();

// 合法：逻辑组合
let valid = isActive
    && hasPermission
    || isAdmin;
```

支持的续行起始符号：`+` `-` `*` `/` `%` `||` `&&` `??` `|` `^` `&` `==` `!=` `===` `!==` `<` `>` `<=` `>=` `<<` `>>` `>>>` `?`（三元）、`.`、`?.`。

不续行的情形（与标准 JS ASI 一致）：`[` 与 `(` 在行首**不**自动续行（避免与"新语句的数组字面量/分组"混淆），赋值 `=` 也不自动续行。

### JS 代码

```javascript
// 斐波那契数列
function fib(n) {
    if (n <= 0) { return 0; }
    if (n === 1) { return 1; }
    return fib(n - 1) + fib(n - 2);
}

// 高阶函数
function apply(fn, x) {
    return fn(x);
}

function double(n) {
    return n * 2;
}

// 数组遍历
var sum = 0;
for (let x of [1, 2, 3, 4, 5]) {
    sum = sum + x;
}

// 条件分支
var grade = 75;
if (grade >= 90) {
    result = 'A';
} else if (grade >= 60) {
    result = 'B';
} else {
    result = 'C';
}

// switch
var day = 2;
switch (day) {
    case 1:
        name = 'Monday';
        break;
    case 2:
        name = 'Tuesday';
        break;
    default:
        name = 'Other';
}

// try/catch
try {
    throw 'error';
} catch (e) {
    msg = 'caught: ' + e;
} finally {
    cleanup = true;
}
```

### 等价 Aria 代码

```aria
// 斐波那契数列
var.fib = -> {
    n = args[0]
    if (n <= 0) { return 0 }
    if (n == 1) { return 1 }
    return fib(n - 1) + fib(n - 2)
}

// 高阶函数
var.apply = -> {
    fn = args[0]
    x = args[1]
    return fn(x)
}

var.double = -> {
    n = args[0]
    return n * 2
}

// 数组遍历
var.sum = 0
for x in [1, 2, 3, 4, 5] {
    sum = sum + x
}

// 条件分支
var.grade = 75
if (grade >= 90) {
    result = 'A'
} elif (grade >= 60) {
    result = 'B'
} else {
    result = 'C'
}

// switch
var.day = 2
switch (day) {
    case 1 {
        name = 'Monday'
        break
    }
    case 2 {
        name = 'Tuesday'
        break
    }
    else {
        name = 'Other'
    }
}

// try/catch
try {
    throw 'error'
} catch (e) {
    msg = 'caught: ' + e
} finally {
    cleanup = true
}
```
