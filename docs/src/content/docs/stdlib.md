---
title: "标准库参考"
description: "内置函数与命名空间完整参考"
order: 10
---

# 标准库参考

Aria 内置了丰富的标准库函数，按命名空间组织。调用方式为 `命名空间.函数名(参数)`。

---

## 全局函数

无需命名空间前缀，直接调用。

| 函数        | 签名                 | 说明        |
|-----------|--------------------|-----------|
| `print`   | `print(args...)`   | 输出内容（不换行） |
| `println` | `println(args...)` | 输出内容（换行）  |

```aria
print("hello", "world")   // hello world
println("done")           // done\n
```

---

## console — 控制台

| 函数      | 签名                       | 说明            |
|---------|--------------------------|---------------|
| `log`   | `console.log(args...)`   | 输出到标准输出       |
| `error` | `console.error(args...)` | 输出到标准错误       |
| `warn`  | `console.warn(args...)`  | 输出警告（同 error） |

```aria
console.log("info message")
console.error("something went wrong")
```

---

## math — 数学

### 常量

| 函数          | 说明     |
|-------------|--------|
| `math.PI()` | 圆周率 π  |
| `math.E()`  | 自然常数 e |

### 基础运算

| 函数       | 签名                 | 说明            |
|----------|--------------------|---------------|
| `abs`    | `math.abs(x)`      | 绝对值           |
| `floor`  | `math.floor(x)`    | 向下取整          |
| `ceil`   | `math.ceil(x)`     | 向上取整          |
| `round`  | `math.round(x)`    | 四舍五入          |
| `sqrt`   | `math.sqrt(x)`     | 平方根           |
| `cbrt`   | `math.cbrt(x)`     | 立方根           |
| `pow`    | `math.pow(x, y)`   | x 的 y 次方      |
| `min`    | `math.min(a, b)`   | 最小值           |
| `max`    | `math.max(a, b)`   | 最大值           |
| `random` | `math.random()`    | [0, 1) 随机数    |
| `signum` | `math.signum(x)`   | 符号函数          |
| `rint`   | `math.rint(x)`     | 最接近的整数（银行家舍入） |
| `hypot`  | `math.hypot(a, b)` | 斜边长度 √(a²+b²) |

### 三角函数

| 函数      | 签名                 | 说明     |
|---------|--------------------|--------|
| `sin`   | `math.sin(x)`      | 正弦     |
| `cos`   | `math.cos(x)`      | 余弦     |
| `tan`   | `math.tan(x)`      | 正切     |
| `asin`  | `math.asin(x)`     | 反正弦    |
| `acos`  | `math.acos(x)`     | 反余弦    |
| `atan`  | `math.atan(x)`     | 反正切    |
| `atan2` | `math.atan2(y, x)` | 二参数反正切 |

### 双曲函数

| 函数     | 签名             | 说明   |
|--------|----------------|------|
| `sinh` | `math.sinh(x)` | 双曲正弦 |
| `cosh` | `math.cosh(x)` | 双曲余弦 |
| `tanh` | `math.tanh(x)` | 双曲正切 |

### 指数与对数

| 函数      | 签名              | 说明             |
|---------|-----------------|----------------|
| `log`   | `math.log(x)`   | 自然对数           |
| `log10` | `math.log10(x)` | 以 10 为底的对数     |
| `log1p` | `math.log1p(x)` | ln(1+x)，小值精度更高 |
| `exp`   | `math.exp(x)`   | e^x            |
| `expm1` | `math.expm1(x)` | e^x - 1，小值精度更高 |

### 角度转换

| 函数          | 签名                    | 说明      |
|-------------|-----------------------|---------|
| `toDegrees` | `math.toDegrees(rad)` | 弧度 → 角度 |
| `toRadians` | `math.toRadians(deg)` | 角度 → 弧度 |

### 浮点操作

| 函数              | 签名                         | 说明          |
|-----------------|----------------------------|-------------|
| `ulp`           | `math.ulp(x)`              | 最小精度单位      |
| `copySign`      | `math.copySign(mag, sign)` | 复制符号        |
| `scalb`         | `math.scalb(x, exp)`       | x × 2^exp   |
| `nextUp`        | `math.nextUp(x)`           | 下一个浮点数（正方向） |
| `nextDown`      | `math.nextDown(x)`         | 下一个浮点数（负方向） |
| `IEEEremainder` | `math.IEEEremainder(a, b)` | IEEE 余数     |
| `getExponent`   | `math.getExponent(x)`      | 获取指数部分      |

```aria
val.r = math.sqrt(16)          // 4.0
val.angle = math.atan2(1, 1)   // 0.7853... (π/4)
val.d = math.toDegrees(angle)  // 45.0
```

---

## type — 类型

| 函数           | 签名                       | 说明        |
|--------------|--------------------------|-----------|
| `typeof`     | `type.typeof(value)`     | 返回类型名称字符串 |
| `isNone`     | `type.isNone(value)`     | 是否为 none  |
| `isNumber`   | `type.isNumber(value)`   | 是否为数字     |
| `isString`   | `type.isString(value)`   | 是否为字符串    |
| `isList`     | `type.isList(value)`     | 是否为列表     |
| `isMap`      | `type.isMap(value)`      | 是否为字典     |
| `isFunction` | `type.isFunction(value)` | 是否为函数     |
| `toNumber`   | `type.toNumber(value)`   | 转换为数字     |
| `toString`   | `type.toString(value)`   | 转换为字符串    |
| `toBoolean`  | `type.toBoolean(value)`  | 转换为布尔值    |

类型名称对照：`none`、`number`、`boolean`、`string`、`object`、`store`、`class`、`function`、`list`、`map`。

```aria
val.t = type.typeof(42)       // "number"
val.ok = type.isString("hi")  // true
val.n = type.toNumber("3.14") // 3.14
```

---

## regex — 正则表达式

| 函数             | 签名                                              | 说明                                            |
|----------------|-------------------------------------------------|-----------------------------------------------|
| `match`        | `regex.match(pattern, str)`                     | 返回第一个匹配 `[fullMatch, group1, ...]`，无匹配返回 none |
| `matchAll`     | `regex.matchAll(pattern, str)`                  | 返回所有匹配的列表                                     |
| `test`         | `regex.test(pattern, str)`                      | 是否匹配                                          |
| `replace`      | `regex.replace(pattern, str, replacement)`      | 替换所有匹配                                        |
| `replaceFirst` | `regex.replaceFirst(pattern, str, replacement)` | 替换第一个匹配                                       |
| `split`        | `regex.split(pattern, str)`                     | 按正则分割                                         |

```aria
val.ok = regex.test("\\d+", "abc123")  // true
val.m = regex.match("(\\w+)@(\\w+)", "user@host")
// m = ["user@host", "user", "host"]
```

---

## crypto — 加密/编码

| 函数             | 签名                         | 说明            |
|----------------|----------------------------|---------------|
| `md5`          | `crypto.md5(str)`          | MD5 哈希        |
| `sha1`         | `crypto.sha1(str)`         | SHA-1 哈希      |
| `sha256`       | `crypto.sha256(str)`       | SHA-256 哈希    |
| `sha512`       | `crypto.sha512(str)`       | SHA-512 哈希    |
| `base64Encode` | `crypto.base64Encode(str)` | Base64 编码     |
| `base64Decode` | `crypto.base64Decode(str)` | Base64 解码     |
| `uuid`         | `crypto.uuid()`            | 生成随机 UUID     |
| `hashCode`     | `crypto.hashCode(str)`     | Java hashCode |

```aria
val.hash = crypto.sha256("hello")
val.encoded = crypto.base64Encode("hello world")
val.id = crypto.uuid()  // "550e8400-e29b-..."
```

---

## datetime — 日期时间

内部使用 epoch 毫秒（数字）表示时间点。

| 函数           | 签名                                       | 说明                                          |
|--------------|------------------------------------------|---------------------------------------------|
| `now`        | `datetime.now()`                         | 当前时间戳（毫秒）                                   |
| `timestamp`  | `datetime.timestamp()`                   | 当前时间戳（秒，浮点）                                 |
| `format`     | `datetime.format(millis, pattern?)`      | 格式化时间，默认 `yyyy-MM-dd HH:mm:ss`              |
| `parse`      | `datetime.parse(str, pattern?)`          | 解析时间字符串为毫秒                                  |
| `diff`       | `datetime.diff(millis1, millis2, unit?)` | 时间差，unit: millis/seconds/minutes/hours/days |
| `year`       | `datetime.year(millis)`                  | 获取年份                                        |
| `month`      | `datetime.month(millis)`                 | 获取月份                                        |
| `day`        | `datetime.day(millis)`                   | 获取日                                         |
| `hour`       | `datetime.hour(millis)`                  | 获取小时                                        |
| `minute`     | `datetime.minute(millis)`                | 获取分钟                                        |
| `second`     | `datetime.second(millis)`                | 获取秒                                         |
| `dayOfWeek`  | `datetime.dayOfWeek(millis)`             | 星期几（1=周一，7=周日）                              |
| `addDays`    | `datetime.addDays(millis, n)`            | 加 n 天                                       |
| `addHours`   | `datetime.addHours(millis, n)`           | 加 n 小时                                      |
| `addMinutes` | `datetime.addMinutes(millis, n)`         | 加 n 分钟                                      |
| `addSeconds` | `datetime.addSeconds(millis, n)`         | 加 n 秒                                       |

```aria
val.now = datetime.now()
val.str = datetime.format(now)  // "2025-01-15 14:30:00"
val.tomorrow = datetime.addDays(now, 1)
val.diff = datetime.diff(now, tomorrow, "hours")  // 24.0
```

---

## scheduler — 定时调度

| 函数          | 签名                               | 说明             |
|-------------|----------------------------------|----------------|
| `delay`     | `scheduler.delay(millis, fn)`    | 延迟执行，返回 taskId |
| `interval`  | `scheduler.interval(millis, fn)` | 周期执行，返回 taskId |
| `cancel`    | `scheduler.cancel(taskId)`       | 取消任务，返回是否成功    |
| `cancelAll` | `scheduler.cancelAll()`          | 取消所有任务         |

```aria
val.id = scheduler.delay(1000, -> {
    println("1 秒后执行")
})

val.timerId = scheduler.interval(500, -> {
    println("每 500ms 执行一次")
})
scheduler.cancel(timerId)
```

---

## template — 模板引擎

支持 `{key}` 占位符替换，`{{` 和 `}}` 为转义。

| 函数       | 签名                                | 说明              |
|----------|-----------------------------------|-----------------|
| `render` | `template.render(template, data)` | 渲染模板，data 为 Map |

```aria
val.result = template.render("Hello, {name}! Age: {age}", {
    "name": "Alice",
    "age": 25
})
// result = "Hello, Alice! Age: 25"
```

---

## fs — 文件系统

| 函数          | 签名                         | 说明                                         |
|-------------|----------------------------|--------------------------------------------|
| `read`      | `fs.read(path)`            | 读取文件内容（字符串）                                |
| `write`     | `fs.write(path, content)`  | 写入文件                                       |
| `exists`    | `fs.exists(path)`          | 文件是否存在                                     |
| `list`      | `fs.list(dir)`             | 列出目录下的文件名                                  |
| `mkdir`     | `fs.mkdir(path)`           | 创建目录（含父目录）                                 |
| `delete`    | `fs.delete(path)`          | 删除文件                                       |
| `copy`      | `fs.copy(src, dst)`        | 复制文件                                       |
| `append`    | `fs.append(path, content)` | 追加内容到文件末尾                                  |
| `readLines` | `fs.readLines(path)`       | 按行读取文件，返回字符串列表                             |
| `info`      | `fs.info(path)`            | 文件信息，返回 `{size, isDir, modified, created}` |

```aria
fs.write("test.txt", "hello world")
val.content = fs.read("test.txt")  // "hello world"
val.files = fs.list(".")
val.info = fs.info("test.txt")     // {size: 11, isDir: false, ...}
```

---

## net — 网络/HTTP

| 函数          | 签名                                              | 说明            |
|-------------|-------------------------------------------------|---------------|
| `get`       | `net.get(url, headers?)`                        | HTTP GET      |
| `post`      | `net.post(url, body, headers?)`                 | HTTP POST     |
| `put`       | `net.put(url, body, headers?)`                  | HTTP PUT      |
| `delete`    | `net.delete(url, headers?)`                     | HTTP DELETE   |
| `request`   | `net.request(options)`                          | 完整控制的 HTTP 请求 |
| `asyncGet`  | `net.asyncGet(url, [headers,] callback)`        | 异步 GET        |
| `asyncPost` | `net.asyncPost(url, body, [headers,] callback)` | 异步 POST       |

所有同步请求返回 `{status, body, headers}` 格式的 Map。

`net.request` 的 options 格式：`{url, method, body, headers, timeout}`。

异步回调签名：`callback(error, response)`，error 为 none 表示成功。

```aria
val.resp = net.get("https://api.example.com/data")
println(resp.status)  // 200
println(resp.body)

val.resp2 = net.post("https://api.example.com/data", '{"key":"value"}', {
    "Content-Type": "application/json"
})

net.asyncGet("https://api.example.com/data", -> {
    println(args[1].body)
})
```

---

## event — 事件总线

| 函数     | 签名                               | 说明         |
|--------|----------------------------------|------------|
| `on`   | `event.on(eventName, handler)`   | 注册事件监听器    |
| `emit` | `event.emit(eventName, args...)` | 触发事件       |
| `off`  | `event.off(eventName)`           | 移除事件的所有监听器 |

```aria
event.on("user.login", -> {
    println("User logged in: " + args[0])
})
event.emit("user.login", "Alice")
event.off("user.login")
```

---

## json — JSON 序列化

| 函数          | 签名                               | 说明                                  |
|-------------|----------------------------------|-------------------------------------|
| `parse`     | `json.parse(str)`                | JSON 字符串 → Aria 值                   |
| `stringify` | `json.stringify(value, pretty?)` | Aria 值 → JSON 字符串，pretty=true 格式化输出 |

```aria
val.obj = json.parse('{"name": "Alice", "age": 25}')
println(obj.name)  // "Alice"

val.str = json.stringify({"key": "value"}, true)
```

---

## serial — 二进制序列化

| 函数       | 签名                     | 说明               |
|----------|------------------------|------------------|
| `encode` | `serial.encode(value)` | 将值编码为二进制（byte[]） |
| `decode` | `serial.decode(bytes)` | 从二进制解码为值         |

支持的类型：none、number、boolean、string、list、map。

```aria
val.data = {"name": "Alice", "scores": [90, 85, 92]}
val.bytes = serial.encode(data)
val.restored = serial.decode(bytes)
```

---

## db — 数据库

支持 SQLite、H2、MySQL。

| 函数            | 签名                                         | 说明                       |
|---------------|--------------------------------------------|--------------------------|
| `connect`     | `db.connect(type, path, [user, password])` | 连接数据库，返回连接对象             |
| `table`       | `db.table(conn, name, columns)`            | 创建表，columns 为 `{列名: 类型}` |
| `createTable` | `db.createTable(conn, name, columns)`      | 同 table                  |
| `dropTable`   | `db.dropTable(conn, name)`                 | 删除表                      |
| `insert`      | `db.insert(conn, table, data)`             | 插入数据，data 为 Map          |
| `select`      | `db.select(conn, table, where?)`           | 查询，where 为 Map 条件        |
| `update`      | `db.update(conn, table, data, where?)`     | 更新数据                     |
| `delete`      | `db.delete(conn, table, where?)`           | 删除数据                     |
| `execute`     | `db.execute(conn, sql, params...)`         | 执行原始 SQL                 |
| `transaction` | `db.transaction(conn, fn)`                 | 事务执行，异常自动回滚              |
| `close`       | `db.close(conn)`                           | 关闭连接                     |

```aria
val.conn = db.connect("sqlite", "test.db")
db.table(conn, "users", {"id": "INTEGER PRIMARY KEY", "name": "TEXT", "age": "INTEGER"})

db.insert(conn, "users", {"name": "Alice", "age": 25})
db.insert(conn, "users", {"name": "Bob", "age": 30})

val.rows = db.select(conn, "users", {"name": "Alice"})
println(rows)  // [{id: 1, name: "Alice", age: 25}]

db.update(conn, "users", {"age": 26}, {"name": "Alice"})
db.delete(conn, "users", {"name": "Bob"})

db.transaction(conn, -> {
    db.insert(conn, "users", {"name": "Charlie", "age": 35})
    db.insert(conn, "users", {"name": "Diana", "age": 28})
})

db.close(conn)
```

---

## 对象方法

以下方法通过 `.方法名()` 在对象上调用。

### 字符串方法

| 方法                 | 签名                                     | 说明                |
|--------------------|----------------------------------------|-------------------|
| `length`           | `str.length()`                         | 字符串长度             |
| `substring`        | `str.substring(start, end?)`           | 子串                |
| `replace`          | `str.replace(target, replacement)`     | 替换所有匹配（字面量）       |
| `replaceAll`       | `str.replaceAll(regex, replacement)`   | 正则替换所有            |
| `replaceFirst`     | `str.replaceFirst(regex, replacement)` | 正则替换第一个           |
| `split`            | `str.split(delimiter)`                 | 分割为列表             |
| `trim`             | `str.trim()`                           | 去除首尾空白            |
| `startsWith`       | `str.startsWith(prefix)`               | 是否以 prefix 开头     |
| `endsWith`         | `str.endsWith(suffix)`                 | 是否以 suffix 结尾     |
| `contains`         | `str.contains(sub)`                    | 是否包含子串            |
| `indexOf`          | `str.indexOf(sub)`                     | 子串首次出现位置，-1 表示未找到 |
| `lastIndexOf`      | `str.lastIndexOf(sub)`                 | 子串最后出现位置          |
| `toUpperCase`      | `str.toUpperCase()`                    | 转大写               |
| `toLowerCase`      | `str.toLowerCase()`                    | 转小写               |
| `charAt`           | `str.charAt(index)`                    | 获取指定位置字符          |
| `equals`           | `str.equals(other)`                    | 严格相等比较            |
| `equalsIgnoreCase` | `str.equalsIgnoreCase(other)`          | 忽略大小写比较           |
| `isEmpty`          | `str.isEmpty()`                        | 是否为空串             |
| `repeat`           | `str.repeat(count)`                    | 重复字符串 count 次     |

```aria
val.s = "Hello, World!"
println(s.length())        // 13
println(s.substring(0, 5)) // "Hello"
println(s.toUpperCase())   // "HELLO, WORLD!"
val.parts = s.split(", ")  // ["Hello", "World!"]
```

### 列表方法

#### 基础操作

| 方法            | 签名                          | 说明               |
|---------------|-----------------------------|------------------|
| `add`         | `list.add(item)`            | 添加元素             |
| `remove`      | `list.remove(index)`        | 移除指定索引元素，返回被移除的值 |
| `get`         | `list.get(index)`           | 获取指定索引元素         |
| `set`         | `list.set(index, value)`    | 设置指定索引元素         |
| `size`        | `list.size()`               | 列表长度             |
| `contains`    | `list.contains(item)`       | 是否包含元素           |
| `indexOf`     | `list.indexOf(item)`        | 元素首次出现位置         |
| `lastIndexOf` | `list.lastIndexOf(item)`    | 元素最后出现位置         |
| `sort`        | `list.sort()`               | 按数值排序（原地）        |
| `reverse`     | `list.reverse()`            | 反转（原地）           |
| `clear`       | `list.clear()`              | 清空               |
| `isEmpty`     | `list.isEmpty()`            | 是否为空             |
| `subList`     | `list.subList(from, to?)`   | 子列表              |
| `addAll`      | `list.addAll(otherList)`    | 添加另一个列表的所有元素     |
| `removeAll`   | `list.removeAll(otherList)` | 移除另一个列表中的所有元素    |
| `join`        | `list.join(separator?)`     | 拼接为字符串，默认 `,`    |

#### 高阶函数

| 方法          | 签名                          | 说明                         |
|-------------|-----------------------------|----------------------------|
| `map`       | `list.map(fn)`              | 映射，`fn(item, index)` → 新列表 |
| `filter`    | `list.filter(fn)`           | 过滤，`fn(item, index)` → 新列表 |
| `reduce`    | `list.reduce(fn, initial?)` | 累积，`fn(acc, item, index)`  |
| `forEach`   | `list.forEach(fn)`          | 遍历，`fn(item, index)`       |
| `find`      | `list.find(fn)`             | 查找第一个满足条件的元素               |
| `findIndex` | `list.findIndex(fn)`        | 查找第一个满足条件的索引               |
| `every`     | `list.every(fn)`            | 是否所有元素都满足条件                |
| `some`      | `list.some(fn)`             | 是否至少一个元素满足条件               |
| `flatMap`   | `list.flatMap(fn)`          | map + flatten              |
| `sortBy`    | `list.sortBy(fn)`           | 按 fn 返回值排序（原地）             |

```aria
val.nums = [3, 1, 4, 1, 5]
val.doubled = nums.map(-> { return args[0] * 2 })     // [6, 2, 8, 2, 10]
val.evens = nums.filter(-> { return args[0] % 2 == 0 }) // [4]
val.sum = nums.reduce(-> { return args[0] + args[1] }, 0) // 14
val.found = nums.find(-> { return args[0] > 3 })       // 4
println(nums.join("-"))  // "3-1-4-1-5"
```

### 字典方法

#### 基础操作

| 方法              | 签名                               | 说明                          |
|-----------------|----------------------------------|-----------------------------|
| `put`           | `map.put(key, value)`            | 设置键值对                       |
| `get`           | `map.get(key)`                   | 获取值                         |
| `remove`        | `map.remove(key)`                | 移除键值对，返回被移除的值               |
| `size`          | `map.size()`                     | 键值对数量                       |
| `keys`          | `map.keys()`                     | 所有键的列表                      |
| `values`        | `map.values()`                   | 所有值的列表                      |
| `entries`       | `map.entries()`                  | 所有键值对 `[[key, value], ...]` |
| `containsKey`   | `map.containsKey(key)`           | 是否包含键                       |
| `containsValue` | `map.containsValue(value)`       | 是否包含值                       |
| `clear`         | `map.clear()`                    | 清空                          |
| `isEmpty`       | `map.isEmpty()`                  | 是否为空                        |
| `putAll`        | `map.putAll(otherMap)`           | 合并另一个字典                     |
| `putIfAbsent`   | `map.putIfAbsent(key, value)`    | 键不存在时设置                     |
| `getOrDefault`  | `map.getOrDefault(key, default)` | 获取值，不存在返回默认值                |

#### 高阶函数

| 方法          | 签名                  | 说明                         |
|-------------|---------------------|----------------------------|
| `forEach`   | `map.forEach(fn)`   | 遍历，`fn(key, value)`        |
| `filter`    | `map.filter(fn)`    | 过滤，`fn(key, value)` → 新字典  |
| `mapValues` | `map.mapValues(fn)` | 映射值，`fn(value, key)` → 新字典 |

```aria
val.m = {"name": "Alice", "age": 25}
println(m.keys())    // ["name", "age"]
println(m.size())    // 2

m.forEach(-> {
    println(args[0] + " = " + args[1])
})

val.filtered = m.filter(-> { return args[0] != "age" })
```

### 数字方法

| 方法           | 签名                    | 说明             |
|--------------|-----------------------|----------------|
| `toInt`      | `num.toInt()`         | 转为整数（截断小数）     |
| `toFixed`    | `num.toFixed(digits)` | 保留指定小数位（返回字符串） |
| `isNaN`      | `num.isNaN()`         | 是否为 NaN        |
| `isInfinite` | `num.isInfinite()`    | 是否为无穷大         |
| `round`      | `num.round(places)`   | 四舍五入到指定小数位     |
| `abs`        | `num.abs()`           | 绝对值            |
| `ceil`       | `num.ceil()`          | 向上取整           |
| `floor`      | `num.floor()`         | 向下取整           |

```aria
val.pi = 3.14159
println(pi.toFixed(2))  // "3.14"
println(pi.round(3))    // 3.142
println(pi.toInt())     // 3
```

---

### UUID

通过构造器创建 UUID 对象：

| 用法                                                      | 说明          |
|---------------------------------------------------------|-------------|
| `val.id = UUID()`                                       | 生成随机 UUID   |
| `val.id = UUID('550e8400-e29b-41d4-a716-446655440000')` | 从字符串解析 UUID |

```aria
val.id = UUID()
print(id)  // 550e8400-e29b-41d4-a716-446655440000
```

---

### Range

通过构造器创建范围对象，支持可选的步长参数：

| 用法                        | 说明           |
|---------------------------|--------------|
| `Range(start, end)`       | 创建范围，步长默认为 1 |
| `Range(start, end, step)` | 创建范围，指定步长    |

```aria
val.r = Range(1, 10)
for i in r {
    print(i)  // 1, 2, 3, ..., 9
}

val.r2 = Range(0, 10, 2)
for i in r2 {
    print(i)  // 0, 2, 4, 6, 8
}
```

---

## 动画缓动函数

动画缓动对象位于独立模块 `aria-animations`，需要将其加入 classpath 后自动注册。共 24 个缓动对象：

| 对象                  | 说明     |
|---------------------|--------|
| `Back(duration)`    | 回弹缓动   |
| `Bezier(...)`       | 贝塞尔曲线  |
| `Blink(...)`        | 闪烁效果   |
| `Bounce(duration)`  | 弹跳缓动   |
| `Breathe(...)`      | 呼吸效果   |
| `CircX(duration)`   | 圆形缓动 X |
| `CircY(duration)`   | 圆形缓动 Y |
| `Elastic(duration)` | 弹性缓动   |
| `Expo(duration)`    | 指数缓动   |
| `Fade(...)`         | 淡入淡出   |
| `Lerp(from, to)`    | 线性插值   |
| `Pulse(...)`        | 脉冲效果   |
| `Q2(duration)`      | 二次缓动   |
| `Q3(duration)`      | 三次缓动   |
| `Q4(duration)`      | 四次缓动   |
| `Q5(duration)`      | 五次缓动   |
| `Shake(...)`        | 抖动效果   |
| `Sine(duration)`    | 正弦缓动   |
| `Slide(...)`        | 滑动效果   |
| `Smooth(duration)`  | 平滑缓动   |
| `Spring(...)`       | 弹簧效果   |
| `Swing(duration)`   | 摆动缓动   |
| `TwoLerp(...)`      | 双线性插值  |
| `Wave(...)`         | 波浪效果   |
