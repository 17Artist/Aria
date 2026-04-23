---
title: "JVM 嵌入指南"
description: "在 Java 项目中集成 Aria 引擎"
order: 12
---

# JVM 嵌入指南

Aria 可以轻松嵌入到任何 JVM 应用中。本章介绍如何在 Java 项目中集成 Aria 引擎。

---

## 依赖配置

Aria 发布在 ArcartX Maven 仓库，需要先添加仓库地址。

### 仓库配置

Gradle (Kotlin DSL)：

```kotlin
repositories {
    maven {
        name = "arcartx-repo"
        url = uri("https://repo.arcartx.com/repository/maven-public/")
    }
}
```

Gradle (Groovy DSL)：

```groovy
repositories {
    maven {
        name = 'arcartx-repo'
        url = 'https://repo.arcartx.com/repository/maven-public/'
    }
}
```

Maven：

```xml
<repositories>
    <repository>
        <id>arcartx-repo</id>
        <url>https://repo.arcartx.com/repository/maven-public/</url>
    </repository>
</repositories>
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("priv.seventeen.artist.aria:aria:1.0.0")

    // 可选：数据库模块
    implementation("priv.seventeen.artist.aria:aria-db:1.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'priv.seventeen.artist.aria:aria:1.0.0'

    // 可选：数据库模块
    implementation 'priv.seventeen.artist.aria:aria-db:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>priv.seventeen.artist.aria</groupId>
    <artifactId>aria</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 可选：数据库模块 -->
<dependency>
    <groupId>priv.seventeen.artist.aria</groupId>
    <artifactId>aria-db</artifactId>
    <version>1.0.0</version>
</dependency>
```

要求 Java 17+。Aria 依赖 ASM 9.6 进行字节码生成（JIT 编译）。

---

## 快速开始

最简单的用法 — 一行代码执行脚本：

```java
import priv.seventeen.artist.aria.Aria;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.value.IValue;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        Context ctx = Aria.createContext();
        IValue<?> result = Aria.eval("return 1 + 2", ctx);
        System.out.println(result.numberValue()); // 3.0
    }
}
```

`Aria` 类在首次使用时会自动初始化默认引擎（注册所有内置函数和服务）。

---

## Aria.eval — 一步执行

```java
public static IValue<?> eval(String code, Context context) throws AriaException
```

编译并立即执行代码，返回结果值。内部流程：解析 → 编译 IR → 优化 → 解释执行。

```java
Context ctx = Aria.createContext();

// 执行表达式
IValue<?> result = Aria.eval("return math.sqrt(16)", ctx);
System.out.println(result.numberValue()); // 4.0

// 执行多行代码
IValue<?> result2 = Aria.eval("""
    val.list = [1, 2, 3, 4, 5]
    val.sum = list.reduce(-> { return args[0] + args[1] }, 0)
    return sum
    """, ctx);
System.out.println(result2.numberValue()); // 15.0
```

### 指定模式

默认 `Aria.eval(code, ctx)` 走 Aria 原生模式。如需使用 JavaScript 模式语法（箭头函数、模板字符串、`let`/`const` 等），使用三参数重载：

```java
public static IValue<?> eval(String code, Context context, Mode mode) throws AriaException
```

```java
Context ctx = Aria.createContext();
IValue<?> result = Aria.eval(
    "let n = 'World'; return `Hello, ${n}!`;",
    ctx, Aria.Mode.JAVASCRIPT);
System.out.println(result.stringValue()); // Hello, World!
```

对于长期持有的脚本用 `Aria.compile("script.js", ctx, code)` 更合适（`.js` 后缀自动识别为 JS 模式），`eval` 重载主要用于一次性片段。

### 沙箱模式执行

```java
public static IValue<?> eval(String code, Context context, SandboxConfig sandbox) throws AriaException
```

在沙箱限制下执行代码：

```java
import priv.seventeen.artist.aria.runtime.SandboxConfig;

SandboxConfig sandbox = SandboxConfig.builder()
    .maxExecutionTime(5000)
    .maxCallDepth(100)
    .allowFileSystem(false)
    .allowNetwork(false)
    .build();

IValue<?> result = Aria.eval("return 1 + 1", ctx, sandbox);
```

---

## Aria.compile — 编译 API

提供两种编译重载。

### 编译并绑定上下文

```java
public static AriaCompilationUnit compile(String name, Context context, String code) throws CompileException
```

返回 `AriaCompilationUnit`，绑定了 Context，可直接执行：

```java
Context ctx = Aria.createContext();
AriaCompilationUnit unit = Aria.compile("myScript", ctx, """
    val.x = 10
    val.y = 20
    return x + y
    """);

IValue<?> result = unit.execute();
System.out.println(result.numberValue()); // 30.0

// 可以多次执行（共享同一个 Context）
IValue<?> result2 = unit.execute();
```

`AriaCompilationUnit` 提供的方法：
- `execute()` — 执行并返回结果
- `getName()` — 编译单元名称
- `getProgram()` — 获取 IR 程序
- `getContext()` — 获取绑定的 Context
- `getTracker()` — 获取源码追踪器

### 编译为预编译例程（不绑定上下文）

```java
public static AriaCompiledRoutine compile(String name, String code) throws CompileException
```

返回 `AriaCompiledRoutine`，执行时需要外部传入 Context：

```java
// 预编译（可复用）
AriaCompiledRoutine routine = Aria.compile("template", """
    val.greeting = "Hello, " + args[0] + "!"
    return greeting
    """);

// 每次执行使用不同的 Context
Context ctx1 = Aria.createContext();
ctx1.setArgs(new IValue<?>[]{ new StringValue("Alice") });
IValue<?> r1 = routine.execute(ctx1); // "Hello, Alice!"

Context ctx2 = Aria.createContext();
ctx2.setArgs(new IValue<?>[]{ new StringValue("Bob") });
IValue<?> r2 = routine.execute(ctx2); // "Hello, Bob!"
```

`AriaCompiledRoutine` 提供的方法：
- `execute(Context context)` — 使用指定 Context 执行
- `getName()` — 例程名称
- `getProgram()` — 获取 IR 程序
- `getTracker()` — 获取源码追踪器

### 语言模式

两种 compile 方法都支持指定语言模式：

```java
// 自动检测（.js 后缀使用 JavaScript 模式，其他使用 Aria 模式）
Aria.compile("script.js", code);  // JavaScript 模式
Aria.compile("script.aria", code);  // Aria 模式

// 显式指定
Aria.compile("name", code, Aria.Mode.JAVASCRIPT);
Aria.compile("name", code, Aria.Mode.ARIA);
```

---

## AriaEngine 自定义

`AriaEngine` 负责引擎初始化和 Context 工厂。

```java
import priv.seventeen.artist.aria.api.AriaEngine;
import priv.seventeen.artist.aria.context.GlobalStorage;

// 使用默认引擎
AriaEngine engine = Aria.getEngine();

// 创建自定义引擎（独立的 GlobalStorage）
GlobalStorage customGlobal = new GlobalStorage();
AriaEngine customEngine = new AriaEngine(customGlobal);
customEngine.initialize();  // 注册所有内置函数

// 通过引擎创建 Context
Context ctx = customEngine.createContext();
```

`initialize()` 方法会注册：
- 所有内置函数（math、console、type、string、list、map 等）
- 对象构造器（Range 等）
- Java 互操作（Java 命名空间）
- 动画对象（aria-animations 在 classpath 中时自动通过反射加载）
- 服务层（fs、net、event、json、serial、scheduler、template、crypto、datetime、regex）
- 数据库模块（aria-db 在 classpath 中时自动通过反射加载，否则静默跳过）
- 模块加载函数（`__import__`）

`initialize()` 是幂等的，多次调用只会执行一次。

---

## Context 创建和配置

`Context` 是脚本执行的上下文环境，包含三层存储和作用域栈。

```java
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.value.*;

// 通过 Aria 创建（使用默认引擎的 GlobalStorage）
Context ctx = Aria.createContext();

// 通过引擎创建
Context ctx2 = engine.createContext();

// 设置全局变量
ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("config"))
    .setValue(new StringValue("production"));

// 设置 self 和 args
ctx.setSelf(someValue);
ctx.setArgs(new IValue<?>[]{ new StringValue("arg1"), new NumberValue(42) });
```

Context 提供的变量访问层级：
- `getGlobalVariable(key)` — 全局变量（`global.xxx`）
- `getClientVariable(key)` — 客户端变量（`client.xxx`）
- `getServerVariable(key)` — 服务端变量（`server.xxx`）
- `getLocalVariable(key)` — 局部变量（`var.xxx`）
- `getLocalValue(key)` — 局部常量（`val.xxx`）
- `getScopeVariable(key)` — 作用域变量

Context 还支持创建派生上下文：
- `createAsyncContext()` — 异步上下文（共享 globalStorage 和 localStorage，独立 scopeStack）
- `snapshotForClosure()` — 闭包快照
- `createCallContext(self, args)` — 函数调用上下文

### val 变量覆写

```java
// Java 端强制修改 val 变量
ctx.forceSetLocalValue(VariableKey.of("config"), new StringValue("production"));
```

### 注解注册表（AnnotationRegistry）

```java
AnnotationRegistry registry = Aria.getEngine().getAnnotationRegistry();

// 注册处理器：脚本执行时遇到 @route 注解自动回调
registry.onAnnotation("route", (annotation, target) -> {
    String path = annotation.getArg(0).stringValue();
    String method = target.name();
    httpServer.register(path, method);
});

// 执行脚本
Aria.eval("""
    class API {
        @route('/api/users')
        getUsers = -> { return 'users' }
    }
    """, ctx);

// 手动查询
List<AnnotatedTarget> routes = registry.findByAnnotation("route");
List<AnnotatedTarget> services = registry.findClassesByAnnotation("service");
```

AnnotationRegistry API：
- `onAnnotation(name, handler)` — 注册注解处理器
- `findByAnnotation(name)` — 查找所有带指定注解的目标
- `findClassesByAnnotation(name)` — 只查类
- `findFunctionsByAnnotation(name)` — 只查函数/方法
- `getAll()` — 获取所有注解目标
- `clear()` — 清空注册表

### JIT 与 Context 变量访问

JIT 编译器对变量系统的处理方式：

| 命名空间       | JIT 是否处理 | 说明                                          |
|------------|----------|---------------------------------------------|
| var.xxx    | 是        | `fastDoubleVars` 路径中复制为 double 局部变量，执行完毕后写回 |
| val.xxx    | 否        | 包含 `LOAD_VAL` 的代码不会被 JIT 编译，始终走解释器          |
| global.xxx | 否        | 同上                                          |
| server.xxx | 否        | 同上                                          |
| client.xxx | 否        | 同上                                          |

**var 变量的并发风险：** 当 JIT 编译的代码正在执行时，var 变量被复制到 JVM 局部变量中运算，执行完毕后才写回 Context。如果 Java 端在 JIT 执行期间通过 Context 修改 var 变量，JIT 代码不会看到这个修改。这与 Java 自身的 JIT 行为一致。

**函数内联的假设：** JIT 会将简单的用户自定义函数（如 `var.inc = -> { return args[0] + 1 }`）内联为纯算术运算。内联基于编译期的函数体 IR，不会在运行时重新检查 `var.inc` 是否被重新赋值。如果脚本在循环中重新定义了同名函数，JIT 代码仍然执行旧的内联逻辑。这是一个已知的限制——JIT 优化的代码假设函数定义在热循环中不会改变。

**常量折叠：** 只折叠字面量常量（`LOAD_CONST`），不涉及任何命名空间变量。`val.PI = 3.14` 不会被折叠——`forceSetLocalValue` 覆写 val 是安全的。

**建议：**
- 避免在脚本执行期间从另一个线程修改同一个 Context 的 var 变量
- 如果需要跨线程通信，使用 `global.xxx`（GlobalStorage 是线程安全的，且不被 JIT 处理）
- 不要在热循环中重新定义已被 JIT 内联的函数

---

## 沙箱配置

`SandboxConfig` 通过 Builder 模式配置脚本执行的资源限制和能力权限。

```java
import priv.seventeen.artist.aria.runtime.SandboxConfig;

SandboxConfig sandbox = SandboxConfig.builder()
    .maxExecutionTime(5000)       // 最大执行时间（毫秒），0 = 无限制
    .maxCallDepth(256)            // 最大调用深度，默认 512
    .maxInstructions(1000000)     // 最大指令数，0 = 无限制
    .allowFileSystem(false)       // 禁止文件系统访问（fs 命名空间）
    .allowNetwork(false)          // 禁止网络访问（net 命名空间）
    .allowJavaInterop(false)      // 禁止 Java 互操作（Java 命名空间）
    .allowedNamespaces("math", "type", "console", "json")  // 白名单命名空间
    .build();

// 使用沙箱执行
Context ctx = Aria.createContext();
IValue<?> result = Aria.eval(code, ctx, sandbox);
```

### 配置项说明

| 配置项                 | 类型          | 默认值        | 说明                 |
|---------------------|-------------|------------|--------------------|
| `maxExecutionTime`  | `long`      | 0（无限制）     | 最大执行时间（毫秒）         |
| `maxCallDepth`      | `int`       | 512        | 最大函数调用深度           |
| `maxInstructions`   | `long`      | 0（无限制）     | 最大执行指令数            |
| `allowFileSystem`   | `boolean`   | true       | 是否允许 fs 命名空间       |
| `allowNetwork`      | `boolean`   | true       | 是否允许 net/http 命名空间 |
| `allowJavaInterop`  | `boolean`   | true       | 是否允许 Java 命名空间     |
| `allowedNamespaces` | `String...` | null（全部允许） | 命名空间白名单            |

预定义配置：
- `SandboxConfig.UNRESTRICTED` — 无任何限制（默认）

---

## 模块加载器配置

### ModuleResolver — 路径解析

```java
import priv.seventeen.artist.aria.module.ModuleResolver;

ModuleResolver resolver = new ModuleResolver();
// 默认搜索当前目录 "."
resolver.addSearchPath(Path.of("/path/to/modules"));
resolver.addSearchPath(Path.of("/another/path"));

// 解析模块路径（自动尝试 .aria 扩展名）
Path resolved = resolver.resolve("myModule");
// 搜索顺序：./myModule.aria → ./myModule → /path/to/modules/myModule.aria → ...
```

### ModuleLoader — 模块加载

```java
import priv.seventeen.artist.aria.module.ModuleLoader;
import priv.seventeen.artist.aria.module.ModuleResolver;
import priv.seventeen.artist.aria.module.ModuleCache;

// 使用默认配置
ModuleLoader loader = new ModuleLoader();

// 自定义 resolver 和 cache
ModuleResolver resolver = new ModuleResolver();
resolver.addSearchPath(Path.of("src/scripts"));
ModuleCache cache = new ModuleCache();
ModuleLoader customLoader = new ModuleLoader(resolver, cache);

// 加载单个模块
IRProgram program = loader.load("utils");

// 并行加载多个模块
Map<String, IRProgram> modules = loader.loadAll(List.of("utils", "config", "routes"));
```

ModuleLoader 特性：
- 内存缓存：已加载的模块不会重复编译
- 增量编译：源码哈希未变时跳过重新编译
- 并行编译：`loadAll` 使用线程池并行加载
- 支持 `.aria` 预编译文件和源码文件

通过引擎配置模块搜索路径：

```java
AriaEngine engine = Aria.getEngine();
engine.getModuleLoader().getResolver().addSearchPath(Path.of("scripts"));
```

---

## 预编译例程

`AriaCompiledRoutine` 适用于需要多次执行同一段代码的场景（如模板渲染、规则引擎）：

```java
// 编译一次
AriaCompiledRoutine routine = Aria.compile("rule", """
    val.score = args[0]
    if score >= 90 {
        return "A"
    } elif score >= 80 {
        return "B"
    } elif score >= 70 {
        return "C"
    } else {
        return "D"
    }
    """);

// 多次执行，每次传入不同参数
for (int score : new int[]{95, 82, 71, 55}) {
    Context ctx = Aria.createContext();
    ctx.setArgs(new IValue<?>[]{ new NumberValue(score) });
    IValue<?> grade = routine.execute(ctx);
    System.out.println(score + " → " + grade.stringValue());
}
// 输出：
// 95 → A
// 82 → B
// 71 → C
// 55 → D
```

---

## 完整嵌入示例

```java
import priv.seventeen.artist.aria.Aria;
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.api.AriaEngine;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.interop.JavaInterop;
import priv.seventeen.artist.aria.runtime.SandboxConfig;
import priv.seventeen.artist.aria.value.*;

public class EmbeddingExample {
    public static void main(String[] args) throws Exception {
        JavaInterop.setClassFilter(className ->
            className.startsWith("java.util.") ||
            className.startsWith("java.lang.Math")
        );

        CallableManager manager = CallableManager.INSTANCE;
        manager.registerStaticFunction("app", "getUser", data -> {
            String id = data.get(0).stringValue();
            // 模拟数据库查询
            return new StringValue("User-" + id);
        });

        AriaEngine engine = Aria.getEngine();
        engine.getModuleLoader().getResolver()
            .addSearchPath(java.nio.file.Path.of("scripts"));

        Context ctx = Aria.createContext();
        IValue<?> result = Aria.eval("""
            val.user = app.getUser("123")
            return "Hello, " + user
            """, ctx);
        System.out.println(result.stringValue()); // "Hello, User-123"

        AriaCompiledRoutine routine = Aria.compile("sandbox-test", """
            val.result = math.pow(args[0], 2) + math.pow(args[1], 2)
            return math.sqrt(result)
            """);

        SandboxConfig sandbox = SandboxConfig.builder()
            .maxExecutionTime(1000)
            .maxCallDepth(50)
            .allowFileSystem(false)
            .allowNetwork(false)
            .allowJavaInterop(false)
            .allowedNamespaces("math", "type")
            .build();

        Context sandboxCtx = Aria.createContext();
        sandboxCtx.setArgs(new IValue<?>[]{ new NumberValue(3), new NumberValue(4) });
        IValue<?> distance = Aria.eval(
            "return math.sqrt(math.pow(args[0], 2) + math.pow(args[1], 2))",
            sandboxCtx, sandbox
        );
        System.out.println(distance.numberValue()); // 5.0

        Context ctx1 = Aria.createContext();
        Aria.eval("global.counter = 0", ctx1);

        Context ctx2 = Aria.createContext();
        Aria.eval("global.counter = global.counter + 1", ctx2);

        Context ctx3 = Aria.createContext();
        IValue<?> counter = Aria.eval("return global.counter", ctx3);
        System.out.println(counter.numberValue()); // 1.0
    }
}
```
