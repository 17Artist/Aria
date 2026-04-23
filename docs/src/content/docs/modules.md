---
title: "模块系统"
description: "import/export、模块缓存与增量编译"
order: 9
---

# 模块系统

Aria 提供模块系统来组织和复用代码，支持导入、导出、模块缓存和增量编译。

## import 语法

### 路径导入

使用点号分隔的路径导入模块：

```aria
import math
import utils.helper
```

模块路径中的点号对应文件系统中的目录分隔符。`import utils.helper` 会查找 `utils/helper.aria` 文件。

### 别名导入

使用 `as` 关键字为导入的模块指定别名：

```aria
import utils.helper as h
```

### 命名导入

从模块中导入指定的名称，只引入需要的符号到当前作用域：

```aria
import { parse, stringify } from 'json'
```

命名导入已完整支持（解析器 + 编译器 + 运行时）。导入的名称直接绑定到当前作用域，无需通过模块名前缀访问。

## export 语法

使用 `export` 关键字导出变量、常量或类，使其可被其他模块导入：

```aria
export val.PI = 3.14159
export var.name = 'aria'

export class MyClass {
    var.value = 0
    new = -> {
        self.value = args[0]
    }
}
```

`export` 可以修饰任何顶层语句，包括 `var` / `val` 声明和 `class` 声明。

## 模块解析规则

`ModuleResolver` 负责将模块路径解析为实际文件路径。解析过程：

1. 遍历搜索路径列表（默认包含当前目录 `.`）
2. 对每个搜索路径，依次尝试：
   - `{搜索路径}/{模块路径}.aria`（查找编译后的二进制文件）
   - `{搜索路径}/{模块路径}`（原始路径）
3. 返回第一个存在的文件路径，如果都不存在则返回 `null`

可以通过 `addSearchPath` 添加额外的搜索路径：

```
搜索路径: [., lib/, vendor/]
import math        → 依次查找 ./math.aria, ./math, lib/math.aria, lib/math, vendor/math.aria, vendor/math
import utils.helper → 依次查找 ./utils/helper.aria, ./utils/helper, lib/utils/helper.aria, ...
```

## 模块缓存

`ModuleCache` 使用 `ConcurrentHashMap` 在内存中缓存已加载的模块。同一模块路径只会被加载和编译一次：

```
第一次 import math  → 解析、编译、缓存
第二次 import math  → 直接从缓存返回
```

缓存支持并发访问，多个线程可以安全地同时读写。

## 增量编译

`ModuleLoader` 使用 SHA-256 哈希实现增量编译。加载模块时的完整流程：

1. 检查内存缓存（`ModuleCache`），命中则直接返回
2. 通过 `ModuleResolver` 解析文件路径
3. 如果是 `.aria` 二进制文件，直接通过 `AriaFileReader` 读取
4. 如果是源码文件：
   - 计算源码的 SHA-256 哈希
   - 检查哈希缓存，如果哈希匹配则跳过编译
   - 否则执行完整编译流程：词法分析 → 语法解析 → 编译 → 优化
   - 将编译结果存入哈希缓存

这意味着即使缓存被清除，只要源码内容未变，也不会重新编译。

## 并行模块加载

`ModuleLoader.loadAll` 支持并行加载多个模块。使用固定大小的线程池（线程数为 CPU 核心数的一半，最少 2 个），线程名为 `aria-compile`，设置为守护线程。

每个模块的加载任务提交到线程池并行执行，超时时间为 30 秒。如果任何模块加载失败或超时，会抛出 `CompileException`。

## .aria 二进制格式

`.aria` 是 Aria 的预编译二进制文件格式，可以跳过解析和编译步骤直接加载。

文件结构：

| 区段             | 内容                                                             |
|----------------|----------------------------------------------------------------|
| Magic          | `AR\x00\x01`（4 字节）                                             |
| Flags          | 标志位（2 字节），`0x01` = 包含源码映射，`0x02` = 包含类信息                       |
| Name           | 模块名称（UTF 字符串）                                                  |
| Register Count | 寄存器数量（4 字节整数）                                                  |
| Constants      | 常量池：每个常量以类型标记开头（`0`=none, `1`=number, `2`=boolean, `3`=string） |
| Variable Keys  | 变量键名列表                                                         |
| Instructions   | 指令序列：每条指令包含 opcode(2字节) + dst/a/b/c(各4字节) + name(UTF字符串)       |
| Sub Programs   | 子程序列表（递归结构）                                                    |
| Source Map     | 源码映射（仅当 flags 包含 `0x01` 时存在）                                   |

## .ariapkg 打包格式

`.ariapkg` 是 Aria 的包文件格式，基于 ZIP 压缩。用于将多个模块和资源打包为一个可分发的文件。

包内目录结构：

```
META-INF/
    MANIFEST.ARIA        — 包清单文件（Properties 格式）
modules/
    module1.aria         — 编译后的模块（.aria 二进制格式）
    utils/helper.aria    — 支持子目录
resources/
    config.txt         — 资源文件
```

打包示例（Java API）：

```java
AriaPackager packager = new AriaPackager();
packager.setManifestEntry("name", "my-package");
packager.setManifestEntry("version", "1.0");
packager.addModule("main", compiledProgram);
packager.addModule("utils/helper", helperProgram);
packager.addResource("config.txt", configBytes);
packager.writeTo(Path.of("output.ariapkg"));
```

读取包文件：

```java
AriaPackageReader reader = AriaPackageReader.read(Path.of("output.ariapkg"));
IRProgram main = reader.getModule("main");
byte[] config = reader.getResource("config.txt");
Set<String> modules = reader.getModuleNames();
```
