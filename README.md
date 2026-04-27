# Aria — 咏叹调

> 以简驭繁，一行代码，一段咏叹。

Aria 是运行在 JVM 之上的轻量脚本语言。如同歌剧中的咏叹调，她追求用最纯粹的表达，传递最丰富的意图。

没有分号的束缚，没有冗余的修饰。换行即是句读，箭头即是函数，点号即是声明。

Aria 是基于作者早年作品 Shimmer 的续作，语法理念和运行时结构设计几乎一致，新增运行时JIT优化、整数特化、JS 兼容模式、更多语法等特性。

## 特点

- 自有 KISS 语法与 JS 兼容模式共享同一套 IR/VM/JIT 管线
- ASM JIT 运行时热点优化（数值函数特化、寄存器分配、自递归 callFast 直跳）
- 标准 Java 17 运行，无额外依赖，毫秒级启动
- JS 兼容模式覆盖 ES5.1 + ES6 常用特性：模板字符串、箭头函数、class（含 `static` 与 `super`）、解构（含函数参数）、spread、`for-of`、`?.` `??`、ASI 续行、await/Promise
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

JMH 基准测试（OpenJDK 17，3 轮预热 + 5 轮测量 × 1s，单 fork，AverageTime 模式）。每个引擎单独运行两轮取均值。单位 ms/op，数字越小越好。

| 工作负载                  | Aria    | Rhino | Nashorn | Groovy | GraalJS | Java 原生 |
|-----------------------|---------|-------|---------|--------|---------|---------|
| Loop Arithmetic 1M    | 0.232   | 25.47 | 14.46   | 5.42   | 79.78   | 0.232   |
| Float Arithmetic 1M   | 1.85    | 25.32 | 14.04   | 5.10   | 84.73   | 0.923   |
| String Concat 100K    | 0.793   | 2.68  | 1.97    | 0.945  | 8.43    | 0.098   |
| Array/List Ops 10K    | 1.75    | 0.363 | 0.146   | 0.056  | 1.20    | 0.041   |
| Object/Map Ops 10K    | 0.311   | 1.44  | 0.264   | 0.678  | 1.67    | 0.243   |
| Branch Intensive 100K | 0.081   | 8.76  | 6.19    | 3.68   | 9.97    | 0.061   |
| Fibonacci(25)         | 0.183   | 2.01  | 0.633   | 3.78   | 13.10   | 0.182   |
| Function Call 100K    | 1.61    | 2.97  | 1.45    | 1.60   | 11.37   | ~0      |

说明：

- 数值循环（Loop / Fibonacci）与 Java 原生持平；Branch / Object 接近；Float / String 慢于 Java 原生 2-8×（IValue 包装开销），但仍快于其他脚本引擎
- **Array/List Ops 10K：Aria 1.75 ms**。CALL_STATIC 在 JIT 路径下通过 `inst.cache` 缓存 `(objClass, ICallable)`，命中后跳过 CallableManager 的 HashMap 查找，相较缓存前的 2.48 ms 提速约 30%。仍是当前最明显的优化空间
- Fibonacci(25) 0.184 ms 与 Java 0.182 ms 持平——ASM JIT 把数值递归编译为 `static double callFast(double)` 后 JVM C2 继续内联
- Function Call 100K 中 Aria 与 Nashorn / Groovy 处在同一量级；Java 原生的 `x = x+1` 100k 次被 C2 作为常量消除，数据接近零
- GraalJS 在 OpenJDK 17 上以解释模式运行（缺 GraalVM Compiler runtime），用 GraalVM JDK 跑能显著提速
- Float Arithmetic 的 Java 数据采用 `double` 循环变量（与 Aria 全 double 语义对等）；用 `int` 循环变量测得 2.80 ms

跑法：
```bash
# 单个引擎
./gradlew jmh -PjmhInclude=AriaBenchmark

# 全部
./gradlew jmh
```


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
