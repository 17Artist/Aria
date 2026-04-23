---
title: "类与继承"
description: "类声明、字段、方法、继承与 super 调用"
order: 7
---

# 类与继承

Aria 支持基于类的面向对象编程。采用单继承模型，没有接口、抽象类或访问修饰符。

## 类声明

使用 `class` 关键字声明类。类体内使用 `var.xxx` / `val.xxx` 声明字段，使用 `name = -> { ... }` 声明方法：

```aria
class Point {
    var.x = 0
    var.y = 0
}
```

## 字段声明

- `var.name = value` — 可变字段，实例创建后可修改
- `val.name = value` — 不可变字段，实例创建后不可修改

字段声明时可以指定默认值：

```aria
class Config {
    var.host = 'localhost'
    var.port = 8080
    val.version = '1.0'
}

val.cfg = Config()
print(cfg.host)       // localhost
print(cfg.version)    // 1.0
```

## 构造函数

构造函数使用 `new = -> { ... }` 语法定义。参数通过 `args` 数组访问：

```aria
class Point {
    var.x = 0
    var.y = 0
    new = -> {
        self.x = args[0]
        self.y = args[1]
    }
}

val.p = Point(10, 20)
print(p.x + p.y)    // 30
```

## 方法声明

方法使用 `name = -> { ... }` 语法定义。方法参数同样通过 `args` 数组访问：

```aria
class Calculator {
    add = -> {
        return args[0] + args[1]
    }
    mul = -> {
        return args[0] * args[1]
    }
}

val.calc = Calculator()
print(calc.add(3, 4) + calc.mul(5, 6))    // 37
```

## self 关键字

在方法和构造函数中，使用 `self` 引用当前实例。通过 `self.fieldName` 访问或修改实例字段：

```aria
class Counter {
    var.count = 0
    increment = -> {
        self.count = self.count + 1
    }
}

val.c = Counter()
c.increment()
c.increment()
c.increment()
print(c.count)    // 3
```

## 实例化

使用 `ClassName(args)` 创建类的实例：

```aria
class Box {
    var.value = 0
    new = -> {
        self.value = args[0]
    }
}

val.a = Box(10)
val.b = Box(20)
print(a.value + b.value)    // 30
```

每个实例拥有独立的字段副本，互不影响。

## 继承

使用 `extends` 关键字实现单继承。子类继承父类的所有字段和方法：

```aria
class Animal {
    var.name = 'unknown'
    new = -> {
        self.name = args[0]
    }
    speak = -> {
        return self.name + ' says hello'
    }
}

class Dog extends Animal {
    var.breed = 'unknown'
    new = -> {
        self.name = args[0]
        self.breed = args[1]
    }
    speak = -> {
        return self.name + ' barks!'
    }
}

val.dog = Dog('Rex', 'Labrador')
print(dog.speak())     // Rex barks!
print(dog.name)        // Rex
print(dog.breed)       // Labrador
```

子类可以访问从父类继承的字段：

```aria
class Animal {
    var.name = 'unknown'
    new = -> {
        self.name = args[0]
    }
}

class Dog extends Animal {
    var.breed = 'mutt'
    new = -> {
        self.name = args[0]
        self.breed = args[1]
    }
}

val.dog = Dog('Rex', 'Labrador')
print(dog.name + ' ' + dog.breed)    // Rex Labrador
```

## super 调用

在子类构造函数中，使用 `super(args)` 调用父类构造函数：

```aria
class Animal {
    var.name = 'unknown'
    val.type = 'animal'
    var.age = 0

    new = -> {
        self.name = args[0]
        self.age = args[1]
    }

    speak = -> {
        return self.name + ' says hello'
    }
}

class Dog extends Animal {
    var.breed = 'unknown'

    new = -> {
        super(args[0], args[1])
        self.breed = args[2]
    }

    speak = -> {
        return self.name + ' barks!'
    }
}

val.dog = Dog('Rex', 3, 'Labrador')
print(dog.speak())    // Rex barks!
print(dog.name)       // Rex
```

### super 方法调用

子类可以通过 `super.methodName()` 调用父类的方法：

```aria
class Animal {
    var.name = 'unknown'
    new = -> { self.name = args[0] }
    describe = -> { return self.name + ' is an animal' }
}

class Dog extends Animal {
    new = -> { self.name = args[0] }
    describe = -> { return super.describe() + ' (dog)' }
}

val.d = Dog('Rex')
print(d.describe())  // Rex is an animal (dog)
```

## static 字段与方法（JS 模式）

JavaScript 模式下的 `class` 支持 `static` 字段和方法。静态成员挂在类本身上，不属于实例：

```javascript
class Counter {
    static count = 0
    static inc() { Counter.count = Counter.count + 1; return Counter.count; }
}

Counter.inc();  // 1
Counter.inc();  // 2
Counter.count;  // 2
```

特性：

- **静态字段**：可读可写，初始值在 `DEFINE_CLASS` 时执行一次（非每次实例化）
- **静态方法**：通过 `ClassName.method(args)` 调用，不绑定 `self`（方法内 `self` 为 null，使用时直接用 `ClassName.field`）
- **继承**：`class Derived extends Base` 时 `Derived.staticField` 和 `Derived.staticMethod()` 会沿父类链查找

```javascript
class Base { static k = 7 }
class Derived extends Base {}
Derived.k;        // 7（继承自 Base）
```

静态与实例共存：

```javascript
class Point {
    static origin = 0
    constructor(x) { this.x = x; }
    offset() { return this.x - Point.origin; }
}

Point.origin = 10;
let p = new Point(25);
p.offset();  // 15
```

Aria 原生语法目前未暴露 `static` 关键字（所有字段/方法都是实例级），需要静态成员时请用 JS 模式。

## 完整示例

```aria
// 定义基类
class Shape {
    var.color = 'black'

    new = -> {
        self.color = args[0]
    }

    area = -> {
        return 0
    }

    describe = -> {
        return self.color + ' shape, area = ' + self.area()
    }
}

// 定义子类
class Circle extends Shape {
    var.radius = 0

    new = -> {
        super(args[0])
        self.radius = args[1]
    }

    area = -> {
        return 3.14159 * self.radius * self.radius
    }
}

class Rectangle extends Shape {
    var.width = 0
    var.height = 0

    new = -> {
        super(args[0])
        self.width = args[1]
        self.height = args[2]
    }

    area = -> {
        return self.width * self.height
    }
}

// 使用
val.c = Circle('red', 5)
val.r = Rectangle('blue', 4, 6)

print(c.describe())    // red shape, area = 78.53975
print(r.describe())    // blue shape, area = 24
print(c.radius)        // 5
```
