---
title: "Java 互操作"
description: "use() 加载类、Java.extend() 实现接口、注解注册"
order: 11
---

# Java 互操作

Aria 运行在 JVM 上，提供了完整的 Java 互操作能力。通过 `use()` 函数可以加载 Java 类、创建实例、调用方法，通过 `Java` 命名空间可以进行类型转换和接口实现。

---

## use — 加载 Java 类

```aria
val.HashMap = use('java.util.HashMap')
val.Math = use('java.lang.Math')
val.ArrayList = use('java.util.ArrayList')
```

加载后返回一个类镜像对象（JavaClassMirror），可以访问静态字段、静态方法，以及创建实例。

支持基本类型名称：`int`、`long`、`double`、`float`、`boolean`、`byte`、`short`、`char`、`void`。

支持数组类型：`use('int[]')`、`use('java.lang.String[]')`。

> `Java.type()` 仍然可用，作为 `use()` 的别名保留，以确保向后兼容。

---

## 创建实例

直接调用类名创建 Java 对象实例：

```aria
val.StringBuilder = use('java.lang.StringBuilder')
val.sb = StringBuilder('hello')
sb.append(' world')
println(sb.toString())  // "hello world"

val.HashMap = use('java.util.HashMap')
val.map = HashMap()
map.put('key', 'value')
println(map.get('key'))  // "value"
```

构造器支持重载解析，会根据参数数量和类型自动选择最佳匹配。

---

## 静态字段和静态方法

```aria
val.Math = use('java.lang.Math')

// 静态字段
println(Math.PI)       // 3.141592653589793

// 静态方法
println(Math.abs(-42)) // 42
println(Math.max(3, 7)) // 7
```

---

## 实例方法调用

```aria
val.ArrayList = use('java.util.ArrayList')
val.list = ArrayList()
list.add('a')
list.add('b')
list.add('c')
println(list.size())  // 3
println(list.get(1))  // "b"
```

方法重载会自动解析。参数类型匹配评分规则：
- 精确类型匹配优先（如 `double` 参数传 NumberValue 得分最高）
- 参数数量精确匹配优先
- 支持可变参数方法

---

## JavaBean 属性访问

对于符合 JavaBean 规范的对象，可以直接通过属性名访问 getter：

```aria
val.Date = use('java.util.Date')
val.d = Date(0)
println(d.time)  // 0 — 等价于 d.getTime()
```

支持 `getXxx()` 和 `isXxx()`（布尔属性）两种 getter 模式。

---

## 字段访问

公共实例字段和静态字段可以直接通过名称访问：

```aria
val.Math = use('java.lang.Math')
println(Math.PI)  // 静态字段
```

---

## List/Map 下标访问

Java 的 List 和 Map 对象支持下标访问：

```aria
val.ArrayList = use('java.util.ArrayList')
val.list = ArrayList()
list.add('x')
list.add('y')
// list[0] 等价于 list.get(0)

val.HashMap = use('java.util.HashMap')
val.map = HashMap()
map.put('key', 'value')
// map['key'] 等价于 map.get('key')
```

---

## Java.from — Java → Aria 转换

将 Java 集合/数组转换为 Aria 原生值：

```aria
val.ArrayList = use('java.util.ArrayList')
val.javaList = ArrayList()
javaList.add(1)
javaList.add(2)
javaList.add(3)

val.ariaList = Java.from(javaList)  // 转为 Aria ListValue
println(type.isList(ariaList))      // true
```

转换规则：
- `java.util.List` → ListValue
- `java.util.Map` → MapValue
- Java 数组 → ListValue
- 其他类型原样返回

---

## Java.to — Aria → Java 转换

将 Aria 值转换为 Java 集合对象：

```aria
val.list = [10, 20, 30]
val.javaList = Java.to(list)  // 转为 Java ArrayList（包装为 JavaObjectMirror）

val.map = {"a": 1, "b": 2}
val.javaMap = Java.to(map)    // 转为 Java LinkedHashMap
```

---

## Java.extend — 实现 Java 接口

支持两种方式实现 Java 接口。

### 函数式接口（单方法）

如果接口只有一个抽象方法，可以直接传入函数：

```aria
val.Runnable = use('java.lang.Runnable')
val.r = Java.extend(Runnable, -> {
    println("running!")
})
r.run()  // "running!"

val.Comparator = use('java.util.Comparator')
val.cmp = Java.extend(Comparator, -> {
    return args[0] - args[1]
})
```

### 多方法接口

使用 Map 为每个方法提供实现：

```aria
val.MyInterface = use('com.example.MyInterface')
val.impl = Java.extend(MyInterface, {
    "methodA": -> {
        return "hello"
    },
    "methodB": -> {
        return args[0] + args[1]
    }
})
```

未提供实现的方法会返回对应类型的默认值。

> 注意：`Java.extend` 仅支持接口，不支持扩展具体类。

### Java.super(obj)

返回对象自身。super 调用的实际语义由类系统处理，此方法主要用于兼容性。

```aria
val.obj = MyClass()
Java.super(obj)  // 返回 obj 自身
```

---

## ClassFilter 安全控制

通过 `ClassFilter` 可以控制脚本能访问哪些 Java 类：

```java
import priv.seventeen.artist.aria.interop.ClassFilter;
import priv.seventeen.artist.aria.interop.JavaInterop;

// 禁止访问 java.io 包
JavaInterop.setClassFilter(className -> !className.startsWith("java.io"));

// 只允许特定包
JavaInterop.setClassFilter(className ->
    className.startsWith("java.util.") ||
    className.startsWith("java.lang.")
);

// 恢复默认（允许所有）
JavaInterop.setClassFilter(ClassFilter.ALLOW_ALL);
```

当脚本尝试加载被禁止的类时，会抛出 `"Access denied to class: xxx"` 异常。

---

## 原生函数注册 — CallableManager API

`CallableManager` 是 Aria 的函数注册中心，提供三级注册机制。

### registerStaticFunction — 注册静态函数

```java
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.value.*;

CallableManager manager = CallableManager.INSTANCE;

// 注册到 "mylib" 命名空间
manager.registerStaticFunction("mylib", "greet", data -> {
    String name = data.get(0).stringValue();
    return new StringValue("Hello, " + name + "!");
});

// 注册到全局（无命名空间）
manager.registerStaticFunction("", "myGlobalFn", data -> {
    return new NumberValue(42);
});
```

脚本中调用：

```aria
println(mylib.greet("Alice"))  // "Hello, Alice!"
println(myGlobalFn())          // 42
```

### registerConstructor — 注册对象构造器

```java
manager.registerConstructor("MyObj", data -> {
    double x = data.get(0).numberValue();
    double y = data.get(1).numberValue();
    return new MyAriaObject(x, y);  // 需实现 IAriaObject
});
```

脚本中调用：

```aria
val.obj = MyObj(10, 20)
```

### registerObjectFunction — 注册对象方法

```java
// 为 StringValue 注册自定义方法
manager.registerObjectFunction(StringValue.class, "repeat", data -> {
    String s = data.get(0).stringValue();
    int count = (int) data.get(1).numberValue();
    return new StringValue(s.repeat(count));
});
```

脚本中调用：

```aria
val.s = "ha".repeat(3)  // "hahaha"
```

对象方法查找支持继承链：先查当前类，再查父类和接口。

### registerObject — 注解扫描注册

通过注解自动注册构造器和实例方法：

```java
manager.registerObject(MyCustomObject.class);
```

---

## 自定义对象注解

### @AriaObjectConstructor

标注在构造器上，注册为 Aria 对象构造器：

```java
import priv.seventeen.artist.aria.annotation.java.AriaObjectConstructor;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.callable.InvocationData;

public class Point implements IAriaObject {
    private double x, y;

    @AriaObjectConstructor("Point")
    public Point(InvocationData data) {
        this.x = data.get(0).numberValue();
        this.y = data.get(1).numberValue();
    }

    @Override
    public String getTypeName() { return "Point"; }

    @Override
    public String stringValue() { return "(" + x + ", " + y + ")"; }
}
```

### @AriaInvokeHandler

标注在实例方法上，注册为对象方法：

```java
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NumberValue;

public class Point implements IAriaObject {
    // ... 构造器省略

    @AriaInvokeHandler("distance")
    public IValue<?> distance(InvocationData data) {
        Point other = (Point) ((ObjectValue<?>) data.get(1)).jvmValue();
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return new NumberValue(Math.sqrt(dx * dx + dy * dy));
    }

    @AriaInvokeHandler("getX")
    public IValue<?> getX(InvocationData data) {
        return new NumberValue(this.x);
    }
}
```

`@AriaInvokeHandler` 还支持 `target` 属性指定目标类（默认为 `Void.class`，表示当前类）。

注册后在脚本中使用：

```aria
val.p1 = Point(0, 0)
val.p2 = Point(3, 4)
println(p1.distance(p2))  // 5.0
println(p2.getX())        // 3.0
```

---

## 注解驱动的静态函数注册

### @AriaNamespace + @AriaInvokeHandler

除了手动调用 `registerStaticFunction(namespace, name, callable)`，还可以用注解声明整个命名空间：

```java
import priv.seventeen.artist.aria.annotation.java.AriaNamespace;
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.value.*;

@AriaNamespace({"Math", "math"})  // 支持多个别名
public class MathFunctions {

    @AriaInvokeHandler("add")
    public static IValue<?> add(InvocationData data) {
        return new NumberValue(data.get(0).numberValue() + data.get(1).numberValue());
    }

    @AriaInvokeHandler("multiply")
    public static IValue<?> multiply(InvocationData data) {
        return new NumberValue(data.get(0).numberValue() * data.get(1).numberValue());
    }
}
```

注册：

```java
CallableManager.INSTANCE.registerStaticFunction(MathFunctions.class);
```

脚本中两种命名空间都能用：

```aria
println(Math.add(1, 2))       // 3
println(math.multiply(3, 4))  // 12
```

规则：
- `@AriaNamespace` 标注在类上，`value` 是命名空间名数组，第一个为主名，其余为别名
- `@AriaInvokeHandler` 标注在 `public static` 方法上，参数必须是 `InvocationData`
- 返回值自动包装：`IValue` 直接返回，`Double`/`Number` → `NumberValue`，`String` → `StringValue`，`Boolean` → `BooleanValue`，`null` → `NoneValue`
- 内部使用 `MethodHandle` 调用，性能接近直接方法调用

### aliasNamespace — 命名空间别名

也可以在注册后手动添加别名：

```java
CallableManager.INSTANCE.aliasNamespace("Math", "math");
// 现在 Math.sin() 和 math.sin() 指向同一个函数表
```

---

## 完整 Java 嵌入示例

```java
import priv.seventeen.artist.aria.Aria;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.interop.ClassFilter;
import priv.seventeen.artist.aria.interop.JavaInterop;
import priv.seventeen.artist.aria.value.*;

public class JavaInteropExample {
    public static void main(String[] args) throws Exception {
        // 1. 设置类过滤器（安全控制）
        JavaInterop.setClassFilter(className ->
            className.startsWith("java.util.") ||
            className.startsWith("java.lang.")
        );

        // 2. 注册自定义函数
        CallableManager manager = CallableManager.INSTANCE;
        manager.registerStaticFunction("app", "version", data ->
            new StringValue("1.0.0")
        );
        manager.registerStaticFunction("app", "add", data -> {
            double a = data.get(0).numberValue();
            double b = data.get(1).numberValue();
            return new NumberValue(a + b);
        });

        // 3. 执行脚本
        Context ctx = Aria.createContext();
        IValue<?> result = Aria.eval("""
            val.HashMap = use('java.util.HashMap')
            val.map = HashMap()
            map.put('version', app.version())
            map.put('sum', app.add(10, 20))
            return Java.from(map)
            """, ctx);

        System.out.println(result);
    }
}
```
