# Aria — 咏叹调

> 以简驭繁，一行代码，一段咏叹。

Aria 是运行在 JVM 之上的轻量脚本语言。如同歌剧中的咏叹调，她追求用最纯粹的表达，传递最丰富的意图。

没有分号的束缚，没有冗余的修饰。换行即是句读，箭头即是函数，点号即是声明。

Aria 是基于作者早年作品 Shimmer 的续作，语法理念和运行时结构设计几乎一致，新增运行时JIT优化、更多语法等特性。

## 特点

- 自有 KISS 语法，IR/VM/JIT 编译管线
- ASM JIT 运行时热点优化（数值函数特化、寄存器分配、自递归 callFast 直跳）
- 标准 Java 17 运行，无额外依赖，毫秒级启动
- 五种命名空间变量系统（var/val/global/server/client）
- 适用于嵌入式脚本、游戏逻辑、配置热更新

## 自有语法

没有分号，没有 `new`，没有冗余修饰。换行即语句，箭头即函数，点号即声明：

```
name = 'World'
var.greet = -> { return 'Hello, ' + args[0] + '!' }
print(greet(name))    // Hello, World!
```

### 点号前缀变量

五种命名空间，一个点号分发语义：

```
var.x = 10          // 局部可变（跨执行持久）
val.slot            // 宿主注入只读槽（脚本写入静默忽略）
global.score = 0    // 全局共享，线程安全
server.config        // 读取触发监听
client.name = 'A'   // 写入触发监听
```

### 箭头函数

所有函数只有一种形式：

```
var.fibonacci = -> {
    if (args[0] <= 1) {
        return args[0]
    }
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
    new = -> { super(args[0]) }
    speak = -> { return self.name + ' barks!' }
}

dog = Dog('Rex')
print(dog.speak())  // Rex barks!
```

### Java 互操作

```
HashMap = use('java.util.HashMap')
m = HashMap()
m.put('key', 'value')
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


## 技术栈

- Java 17
- ASM 9.6（JIT 字节码生成）
- JLine3（REPL）
- LSP4J（语言服务器）
- Gradle + JMH + JaCoCo

## 许可证

[Apache License 2.0](LICENSE)

Copyright 2026 17Artist
