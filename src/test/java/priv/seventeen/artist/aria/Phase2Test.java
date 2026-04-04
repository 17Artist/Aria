/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.annotation.AriaAnnotation;
import priv.seventeen.artist.aria.classystem.AriaClass;
import priv.seventeen.artist.aria.classystem.AriaField;
import priv.seventeen.artist.aria.classystem.AriaMethod;
import priv.seventeen.artist.aria.compiler.Optimizer;
import priv.seventeen.artist.aria.compiler.ir.IRInstruction;
import priv.seventeen.artist.aria.compiler.ir.IROpCode;
import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.object.ClassInstance;
import priv.seventeen.artist.aria.object.RangeObject;
import priv.seventeen.artist.aria.value.*;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Phase2Test {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    @Test
    void testAriaClassCreation() {
        AriaClass animal = new AriaClass("Animal");
        animal.addField(new AriaField("name", true, null, null));
        animal.addField(new AriaField("type", false, null, null));

        assertEquals("Animal", animal.getName());
        assertEquals(2, animal.getFields().size());
        assertTrue(animal.getFields().get("name").isMutable());
        assertFalse(animal.getFields().get("type").isMutable());
    }

    @Test
    void testAriaClassInheritance() {
        AriaClass animal = new AriaClass("Animal");
        animal.addField(new AriaField("name", true, null, null));
        animal.addMethod(new AriaMethod("speak", null, null));

        AriaClass dog = new AriaClass("Dog");
        dog.setParent(animal);
        dog.addField(new AriaField("breed", true, null, null));

        // 继承的字段
        var allFields = dog.collectAllFields();
        assertEquals(2, allFields.size());
        assertTrue(allFields.containsKey("name"));
        assertTrue(allFields.containsKey("breed"));

        // 继承的方法
        assertNotNull(dog.findMethod("speak"));
    }

    @Test
    void testAriaClassMethodOverride() {
        AriaClass animal = new AriaClass("Animal");
        AriaMethod animalSpeak = new AriaMethod("speak", null, null);
        animal.addMethod(animalSpeak);

        AriaClass dog = new AriaClass("Dog");
        dog.setParent(animal);
        AriaMethod dogSpeak = new AriaMethod("speak", null, null);
        dog.addMethod(dogSpeak);

        // 子类方法覆盖父类
        assertSame(dogSpeak, dog.findMethod("speak"));
    }

    @Test
    void testAriaAnnotation() {
        AriaAnnotation deprecated = new AriaAnnotation("deprecated",
            new IValue<?>[]{ new StringValue("use newMethod instead") });

        assertEquals("deprecated", deprecated.name());
        assertTrue(deprecated.hasArgs());
        assertEquals("use newMethod instead", deprecated.getArg(0).stringValue());

        AriaAnnotation override = new AriaAnnotation("override");
        assertFalse(override.hasArgs());
    }

    @Test
    void testAnnotationOnField() {
        AriaAnnotation readonly = new AriaAnnotation("readonly");
        AriaField field = new AriaField("id", false, null, List.of(readonly));

        assertTrue(field.hasAnnotation("readonly"));
        assertFalse(field.hasAnnotation("deprecated"));
    }

    @Test
    void testRangeObject() {
        RangeObject range = new RangeObject(0, 10);
        assertEquals(0, range.getStart());
        assertEquals(10, range.getEnd());
        assertEquals(1, range.getStep());
        assertTrue(range.contains(5));
        assertFalse(range.contains(10)); // exclusive end
        assertFalse(range.contains(-1));
    }

    @Test
    void testOptimizerConstantFolding() {
        // 构造一个简单的 IR: LOAD_CONST r0, 2; LOAD_CONST r1, 3; ADD r2, r0, r1
        IRProgram program = new IRProgram("test");
        program.setConstants(new IValue<?>[]{ new NumberValue(2), new NumberValue(3) });
        program.setRegisterCount(3);
        program.setInstructions(new IRInstruction[]{
            IRInstruction.of(IROpCode.LOAD_CONST, 0, 0),
            IRInstruction.of(IROpCode.LOAD_CONST, 1, 1),
            IRInstruction.of(IROpCode.ADD, 2, 0, 1)
        });
        program.setSubPrograms(new IRProgram[0]);

        Optimizer optimizer = new Optimizer();
        IRProgram optimized = optimizer.optimize(program);

        // 应该被折叠为 LOAD_CONST r2, 5
        IRInstruction[] code = optimized.getInstructions();
        // 找到最后一条有效指令
        boolean foundFolded = false;
        for (IRInstruction inst : code) {
            if (inst.opcode == IROpCode.LOAD_CONST && inst.dst == 2) {
                IValue<?> val = optimized.getConstants()[inst.a];
                assertEquals(5.0, val.numberValue());
                foundFolded = true;
            }
        }
        assertTrue(foundFolded, "Constant folding should produce LOAD_CONST with value 5");
    }

    @Test
    void testOptimizerDeadCodeElimination() {
        IRProgram program = new IRProgram("test");
        program.setConstants(new IValue<?>[]{ new NumberValue(42) });
        program.setRegisterCount(2);
        program.setInstructions(new IRInstruction[]{
            IRInstruction.of(IROpCode.LOAD_CONST, 0, 0),
            IRInstruction.of(IROpCode.RETURN, 0),
            IRInstruction.of(IROpCode.LOAD_CONST, 1, 0),  // dead code
            IRInstruction.of(IROpCode.RETURN, 1),           // dead code
        });
        program.setSubPrograms(new IRProgram[0]);

        Optimizer optimizer = new Optimizer();
        IRProgram optimized = optimizer.optimize(program);

        // 死代码应该被消除
        assertTrue(optimized.getInstructions().length < 4);
    }

    @Test
    void testClassInstanceFields() {
        AriaClass cls = new AriaClass("Point");
        cls.addField(new AriaField("x", true, null, null));
        cls.addField(new AriaField("y", true, null, null));

        ClassInstance instance = new ClassInstance("Point");
        instance.getFields().put("x", new VariableReference(new NumberValue(10)));
        instance.getFields().put("y", new VariableReference(new NumberValue(20)));

        assertEquals("Point", instance.getTypeName());
        Variable xVar = instance.getVariable("x");
        assertNotNull(xVar.ariaValue());
        assertEquals(10.0, xVar.ariaValue().numberValue());
    }

    @Test
    void testAriaClassValueType() {
        ClassInstance instance = new ClassInstance("Test");
        AriaClassValue cv = new AriaClassValue(instance);
        assertEquals(6, cv.typeID());
        assertFalse(cv.canMath());
        assertSame(instance, cv.jvmValue());
    }

    @Test
    void testFunctionValueType() {
        FunctionValue fv = new FunctionValue(data -> new NumberValue(42));
        assertEquals(7, fv.typeID());
        assertFalse(fv.canMath());
        assertTrue(fv.booleanValue());
        assertEquals("function", fv.stringValue());
    }


    private IValue<?> eval(String code) throws AriaException {
        Context ctx = Aria.createContext();
        return Aria.eval(code, ctx);
    }

    @Test
    void testClassDeclAndInstantiate() throws AriaException {
        IValue<?> result = eval("""
            class Point {
                var.x = 0
                var.y = 0
            }
            val.p = Point()
            return p.x
            """);
        assertEquals(0.0, result.numberValue());
    }

    @Test
    void testClassConstructor() throws AriaException {
        IValue<?> result = eval("""
            class Point {
                var.x = 0
                var.y = 0
                new = -> {
                    self.x = args[0]
                    self.y = args[1]
                }
            }
            val.p = Point(10, 20)
            return p.x + p.y
            """);
        assertEquals(30.0, result.numberValue());
    }

    @Test
    void testClassMethod() throws AriaException {
        IValue<?> result = eval("""
            class Greeter {
                var.name = 'World'
                new = -> {
                    self.name = args[0]
                }
                greet = -> {
                    return 'Hello, ' + self.name + '!'
                }
            }
            val.g = Greeter('Aria')
            return g.greet()
            """);
        assertEquals("Hello, Aria!", result.stringValue());
    }

    @Test
    void testClassFieldDefault() throws AriaException {
        IValue<?> result = eval("""
            class Counter {
                var.count = 42
            }
            val.c = Counter()
            return c.count
            """);
        assertEquals(42.0, result.numberValue());
    }

    @Test
    void testClassInheritance() throws AriaException {
        IValue<?> result = eval("""
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
            return dog.speak()
            """);
        assertEquals("Rex barks!", result.stringValue());
    }

    @Test
    void testClassInheritedField() throws AriaException {
        IValue<?> result = eval("""
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
            return dog.name + ' ' + dog.breed
            """);
        assertEquals("Rex Labrador", result.stringValue());
    }

    @Test
    void testClassMethodWithArgs() throws AriaException {
        IValue<?> result = eval("""
            class Calculator {
                add = -> {
                    return args[0] + args[1]
                }
                mul = -> {
                    return args[0] * args[1]
                }
            }
            val.calc = Calculator()
            return calc.add(3, 4) + calc.mul(5, 6)
            """);
        assertEquals(37.0, result.numberValue());
    }

    @Test
    void testClassFieldMutation() throws AriaException {
        IValue<?> result = eval("""
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
            return c.count
            """);
        assertEquals(3.0, result.numberValue());
    }

    @Test
    void testMultipleInstances() throws AriaException {
        IValue<?> result = eval("""
            class Box {
                var.value = 0
                new = -> {
                    self.value = args[0]
                }
            }
            val.a = Box(10)
            val.b = Box(20)
            return a.value + b.value
            """);
        assertEquals(30.0, result.numberValue());
    }

    @Test
    void testClassConstructionWithoutNew() throws AriaException {
        IValue<?> result = eval("""
            class Box {
                var.value = 0
                new = -> { self.value = args[0] }
            }
            val.b = Box(42)
            return b.value
            """);
        assertEquals(42.0, result.numberValue());
    }

    @Test
    void testSuperMethodCall() throws AriaException {
        IValue<?> result = eval("""
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
            return d.describe()
            """);
        assertEquals("Rex is an animal (dog)", result.stringValue());
    }
}
