# Aria — 咏叹调

> 以简驭繁，一行代码，一段咏叹。

Aria 是运行在 JVM 之上的轻量脚本语言。如同歌剧中的咏叹调，她追求用最纯粹的表达，传递最丰富的意图。

没有分号的束缚，没有冗余的修饰。换行即是句读，箭头即是函数，点号即是声明。

Aria 是基于作者早年作品 Shimmer 的续作，语法理念和运行时结构设计几乎一致，新增运行时JIT优化、整数特化、JS 兼容模式、更多语法等特性。

## 特点

- 自有 KISS 语法与 JS 兼容模式共享同一套 IR/VM/JIT 管线
- ASM JIT 运行时热点优化
- 标准 Java 17 运行，无额外依赖，毫秒级启动
- JS 兼容模式支持大部分 ES5.1 语法及 ES6 常用特性
- 五种命名空间变量系统（var/val/global/server/client）
- 适用于嵌入式脚本、游戏逻辑、配置热更新

## 自有语法

没有分号，没有 `new`，没有冗余修饰。换行即语句，箭头即函数，点号即声明：

```
var.name = 'World'
var.greet = -> { return 'Hello, ' + args[0] + '!' }
print(greet(name))
```

### 点号前缀变量

五种命名空间，一个点号分发语义：

```
var.x = 10          // 局部可变
val.PI = 3.14       // 局部不可变
global.score = 0    // 全局共享，线程安全
server.config        // 读取触发监听
client.name = 'A'   // 写入触发监听
```

### 箭头函数

所有函数只有一种形式：

```
var.fibonacci = -> {
    if (args[0] <= 1) return args[0]
    return fibonacci(args[0] - 1) + fibonacci(args[0] - 2)
}
```

### 类系统

单继承，无修饰符，够用就好：

```
class Animal {
    var.name = 'unknown'
    new = -> { self.name = args[0] }
    speak = -> { return self.name + ' says hello' }
}

class Dog extends Animal {
    speak = -> { return self.name + ' barks!' }
}

val.dog = Dog('Rex')
print(dog.speak())  // Rex barks!
```

### Java 互操作

```
val.HashMap = use('java.util.HashMap')
val.map = HashMap()
map.put('key', 'value')
```

## JavaScript 兼容模式

内置兼容 JS 解析器，也可通过 `Aria.Mode.JAVASCRIPT` 显式指定。JS 代码被转换为 Aria AST 后共享同一套 IR/VM/JIT 管线。

```javascript
class Animal {
    constructor(name) {
        this.name = name;
    }
    speak() {
        return this.name + ' speaks';
    }
}

const dog = new Animal('Rex');
console.log(dog.speak());
```

## 性能

IsolatedBenchmark 独立测试，预热 15 轮取 5 轮均值（单位 ms，越小越好）：

| 基准测试                  | Aria    | Rhino   | Nashorn | Groovy   | GraalJS  | Java 原生 |
|-----------------------|---------|---------|---------|----------|----------|---------|
| Fibonacci(25)         | 9.7 ms  | 22.0 ms | 2.1 ms  | 196.9 ms | 39.0 ms  | 0.26 ms |
| Loop Arithmetic 1M    | 0.32 ms | 30.7 ms | 10.5 ms | 7.4 ms   | 369.7 ms | 0.23 ms |
| String Concat 100K    | 2.1 ms  | 3.1 ms  | 1.6 ms  | 0.31 ms  | 33.5 ms  | 0.68 ms |
| Array/List Ops 10K    | 0.47 ms | 0.73 ms | 1.1 ms  | 3.0 ms   | 4.7 ms   | 0.46 ms |
| Float Arithmetic 1M   | 3.3 ms  | 32.2 ms | 15.9 ms | 14500 ms | 376.6 ms | 2.5 ms  |
| Object/Map Ops 10K    | 2.5 ms  | 1.8 ms  | 5.1 ms  | 3.2 ms   | 11.4 ms  | 2.5 ms  |
| Function Call 100K    | 0.17 ms | 3.5 ms  | 1.1 ms  | 106.7 ms | 48.0 ms  | ~0 ms   |
| Branch Intensive 100K | 0.31 ms | 12.7 ms | 7.7 ms  | 1.6 ms   | 52.6 ms  | 0.16 ms |

- 注：以上数据中GraalJS为JVM中运行（解释模式）


## 架构

```
源代码 → Lexer → Parser → Compiler → IR → Optimizer → VM + JIT
```


## 快速开始


```java
Context context = Aria.createContext();
IValue<?> result = Aria.eval("1 + 2 * 3", context);
System.out.println(result.numberValue()); // 7.0
```

```java
// .js 后缀自动识别为 JavaScript 模式
var unit = Aria.compile("script.js", ctx, jsCode);
unit.execute();
```


## 技术栈

- Java 17
- ASM 9.6（JIT 字节码生成）
- JLine3（REPL）
- LSP4J（语言服务器）
- Gradle + JMH + JaCoCo

## 许可证

[Apache License 2.0](LICENSE)

Copyright 2026 17Artist
