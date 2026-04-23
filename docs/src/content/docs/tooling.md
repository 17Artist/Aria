---
title: "工具链"
description: "REPL、LSP 语言服务器与静态编译"
order: 14
---

# 工具链

## REPL 交互模式

Aria 提供了基于 JLine3 的交互式命令行（REPL），支持 Tab 补全、历史导航和多行编辑。

### 启动方式

```bash
java -cp aria.jar priv.seventeen.artist.aria.AriaREPL
```

启动后显示：

```
Aria REPL v1.0 (JLine3)
Type 'exit' to quit, 'reset' to clear context.

>>>
```

### 快捷键

| 快捷键       | 说明              |
|-----------|-----------------|
| `Tab`     | 补全关键字、命名空间、内置函数 |
| `↑` / `↓` | 历史命令导航          |
| `Ctrl+C`  | 取消当前行输入         |
| `Ctrl+D`  | 退出 REPL         |

### 历史记录

REPL 自动将输入历史持久化到 `~/.aria_history`，下次启动时自动加载，支持跨会话的历史导航。

### 基本使用

输入表达式后回车即可执行，结果以 `= ` 前缀显示：

```
>>> var.x = 42
>>> var.y = x + 8
= 50.0
```

### 多行输入

当输入行以 `{` 结尾时，REPL 自动进入多行模式（提示符变为 `...`），直到花括号匹配完成：

```
>>> if (x > 10) {
...     return 'big'
... } else {
...     return 'small'
... }
= big
```

REPL 通过计算 `{` 和 `}` 的数量来判断多行输入是否完成。

### 内置命令

| 命令      | 说明                  |
|---------|---------------------|
| `exit`  | 退出 REPL             |
| `quit`  | 退出 REPL（同 exit）     |
| `reset` | 重置上下文，清除所有已定义的变量和函数 |

执行 `reset` 后会创建一个全新的 `Context`，之前定义的所有变量和函数都会被清除。

### 错误处理

执行出错时，REPL 会打印错误信息并重置多行输入状态，不会退出：

```
>>> var.x = 1 / 0
Error: Division by zero
>>>
```

## LSP 语言服务器

Aria 提供了基于 LSP4J 的语言服务器（`aria-lsp` 模块），支持在编辑器中获得语法检查、代码补全等功能。

### 支持的功能

| 功能    | 说明                          |
|-------|-----------------------------|
| 实时诊断  | 文档打开、修改、保存时自动进行语法分析并报告错误    |
| 自动补全  | 支持 `.`、`:`、`@` 触发字符的上下文补全   |
| 悬停提示  | 鼠标悬停在标识符上显示类型和定义信息          |
| 跳转到定义 | 跳转到变量、函数、类的定义位置             |
| 文档符号  | 显示文档中的变量、函数、类、字段、方法、导入等符号大纲 |

文档同步采用全量更新模式（`TextDocumentSyncKind.Full`），每次变更发送完整文档内容。

### 启动方式

LSP 服务器通过 stdio 与客户端通信：

```bash
java -cp aria-lsp.jar priv.seventeen.artist.aria.lsp.AriaLspLauncher
```

服务器启动后通过标准输入/输出与编辑器交换 JSON-RPC 消息。

### VSCode 集成配置

在 VSCode 扩展的 `package.json` 中配置语言客户端：

```json
{
  "contributes": {
    "languages": [{
      "id": "aria",
      "extensions": [".aria"],
      "aliases": ["Aria"]
    }]
  }
}
```

在扩展的 `extension.ts` 中启动语言客户端：

```typescript
import { LanguageClient, ServerOptions, TransportKind } from 'vscode-languageclient/node';

const serverOptions: ServerOptions = {
  command: 'java',
  args: ['-cp', 'aria-lsp.jar', 'priv.seventeen.artist.aria.lsp.AriaLspLauncher'],
  transport: TransportKind.stdio
};

const clientOptions = {
  documentSelector: [{ scheme: 'file', language: 'aria' }]
};

const client = new LanguageClient('aria', 'Aria Language Server', serverOptions, clientOptions);
client.start();
```

### 符号类型映射

LSP 服务器将 Aria 内部符号类型映射为标准 LSP 符号类型：

| Aria 符号   | LSP SymbolKind |
|-----------|----------------|
| VARIABLE  | Variable       |
| FUNCTION  | Function       |
| CLASS     | Class          |
| PARAMETER | Variable       |
| FIELD     | Field          |
| METHOD    | Method         |
| IMPORT    | Module         |

## 静态编译

Aria 支持将编译后的 IR 程序序列化为 `.aria` 二进制文件，实现预编译和快速加载。

### .aria 二进制格式

文件结构：

```
[Magic: 'A' 'R' 0x00 0x01]  (4 bytes)
[Flags]                       (2 bytes, short)
[Name]                        (UTF string)
[RegisterCount]               (4 bytes, int)
[Constants]                   (常量池)
[VariableKeys]                (变量键表)
[Instructions]                (指令序列)
[SubPrograms]                 (子程序，递归结构)
[SourceMap]                   (可选，由 FLAG_HAS_SOURCE_MAP 控制)
```

常量池类型标记：

| 标记 | 类型              |
|----|-----------------|
| 0  | None            |
| 1  | Number (double) |
| 2  | Boolean         |
| 3  | String (UTF)    |

标志位：

| 标志                  | 值    | 说明       |
|---------------------|------|----------|
| FLAG_HAS_SOURCE_MAP | 0x01 | 包含源码映射信息 |
| FLAG_HAS_CLASSES    | 0x02 | 包含类定义    |

### 编译 API

将 IR 程序写入 `.aria` 文件：

```java
import priv.seventeen.artist.aria.staticcompile.AriaFileWriter;

// 编译源码
AriaCompiledRoutine routine = Aria.compile("myScript", code);

// 写入二进制文件（不含源码映射）
AriaFileWriter.write(routine.getProgram(), Path.of("myScript.aria"));

// 写入二进制文件（含源码映射，用于调试）
AriaFileWriter.write(routine.getProgram(), Path.of("myScript.aria"), true);
```

### 加载 API

从 `.aria` 文件加载 IR 程序：

```java
import priv.seventeen.artist.aria.staticcompile.AriaFileReader;

IRProgram program = AriaFileReader.read(Path.of("myScript.aria"));
```

加载时会验证文件头的 Magic 字节（`AR\x00\x01`），格式不匹配时抛出 `IOException`。

## 模块打包

Aria 支持将多个编译后的模块打包为 `.ariapkg` 包文件，基于 ZIP 格式。

### .ariapkg 包结构

```
myPackage.ariapkg (ZIP)
├── META-INF/
│   └── MANIFEST.ARIA          (属性文件，包元数据)
├── modules/
│   ├── main.aria               (编译后的 .aria 二进制模块)
│   ├── utils.aria
│   └── ...
└── resources/
    ├── config.json            (资源文件)
    └── ...
```

### 打包 API

```java
import priv.seventeen.artist.aria.staticcompile.AriaPackager;

AriaPackager packager = new AriaPackager();

// 设置清单信息
packager.setManifestEntry("Main-Module", "main");
packager.setManifestEntry("Version", "1.0.0");

// 添加编译后的模块
packager.addModule("main", mainProgram);
packager.addModule("utils", utilsProgram);

// 添加资源文件
packager.addResource("config.json", configBytes);

// 写入包文件
packager.writeTo(Path.of("myPackage.ariapkg"));
```

### 读取 API

```java
import priv.seventeen.artist.aria.staticcompile.AriaPackageReader;

AriaPackageReader reader = AriaPackageReader.read(Path.of("myPackage.ariapkg"));

// 读取清单
Properties manifest = reader.getManifest();
String mainModule = manifest.getProperty("Main-Module");

// 列出所有模块
Set<String> modules = reader.getModuleNames();

// 加载指定模块
IRProgram main = reader.getModule("main");

// 读取资源
byte[] config = reader.getResource("config.json");
```

`getModule()` 方法会自动补全 `.aria` 后缀，内部通过临时文件调用 `AriaFileReader` 反序列化 IR 程序。
