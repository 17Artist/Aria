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

package priv.seventeen.artist.aria.api;

import priv.seventeen.artist.aria.annotation.AnnotationRegistry;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.ICallable;
import priv.seventeen.artist.aria.callable.IObjectConstructor;
import priv.seventeen.artist.aria.callable.builtin.*;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.interop.JavaInterop;
import priv.seventeen.artist.aria.module.ModuleLoader;
import priv.seventeen.artist.aria.object.RangeObject;
import priv.seventeen.artist.aria.object.UUIDObject;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.service.fs.FileService;
import priv.seventeen.artist.aria.service.net.EventBus;
import priv.seventeen.artist.aria.service.net.HttpService;
import priv.seventeen.artist.aria.service.serial.BinarySerializer;
import priv.seventeen.artist.aria.service.serial.JsonService;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.ObjectValue;

public class AriaEngine {
    private final GlobalStorage globalStorage;
    private final ModuleLoader moduleLoader;
    private final AnnotationRegistry annotationRegistry;
    private boolean initialized = false;

    public AriaEngine() {
        this.globalStorage = new GlobalStorage();
        this.moduleLoader = new ModuleLoader();
        this.annotationRegistry = new AnnotationRegistry();
    }

    public AriaEngine(GlobalStorage globalStorage) {
        this.globalStorage = globalStorage;
        this.moduleLoader = new ModuleLoader();
        this.annotationRegistry = new AnnotationRegistry();
    }

    public void initialize() {
        if (initialized) return;
        initialized = true;
        MathFunctions.register(CallableManager.INSTANCE);
        ConsoleFunctions.register(CallableManager.INSTANCE);
        TypeFunctions.register(CallableManager.INSTANCE);
        StringFunctions.register(CallableManager.INSTANCE);
        ListFunctions.register(CallableManager.INSTANCE);
        MapFunctions.register(CallableManager.INSTANCE);
        NumberFunctions.register(CallableManager.INSTANCE);
        RegexFunctions.register(CallableManager.INSTANCE);
        CryptoFunctions.register(CallableManager.INSTANCE);
        DateTimeFunctions.register(CallableManager.INSTANCE);
        SchedulerFunctions.register(CallableManager.INSTANCE);
        TemplateFunctions.register(CallableManager.INSTANCE);
        priv.seventeen.artist.aria.callable.builtin.PromiseFunctions.register(CallableManager.INSTANCE);

        CallableManager.INSTANCE.aliasNamespace("math", "Math");
        CallableManager.INSTANCE.aliasNamespace("console", "Console");
        CallableManager.INSTANCE.aliasNamespace("type", "Type");
        CallableManager.INSTANCE.aliasNamespace("string", "String");
        CallableManager.INSTANCE.aliasNamespace("regex", "Regex");
        CallableManager.INSTANCE.aliasNamespace("crypto", "Crypto");
        CallableManager.INSTANCE.aliasNamespace("datetime", "DateTime");
        CallableManager.INSTANCE.aliasNamespace("scheduler", "Scheduler");
        CallableManager.INSTANCE.aliasNamespace("template", "Template");

        IObjectConstructor<?> rangeCtor = data -> {
            double start = data.get(0).numberValue();
            double end = data.get(1).numberValue();
            if (data.argCount() >= 3) {
                return new RangeObject(start, end, data.get(2).numberValue());
            }
            return new RangeObject(start, end);
        };
        CallableManager.INSTANCE.registerConstructor("Range", rangeCtor);
        CallableManager.INSTANCE.registerConstructor("range", rangeCtor);

        Interpreter.registerConstructor("UUID", data -> {
            if (data.argCount() > 0) {
                return new ObjectValue<>(new UUIDObject(data.get(0).stringValue()));
            }
            return new ObjectValue<>(new UUIDObject());
        });

        JavaInterop.register(CallableManager.INSTANCE);

        // 可选：注册动画模块（如果 aria-animations 在 classpath 中）
        try {
            Class<?> animClass = Class.forName("priv.seventeen.artist.aria.animations.AnimationsModule");
            java.lang.reflect.Method registerMethod = animClass.getMethod("register", CallableManager.class);
            registerMethod.invoke(null, CallableManager.INSTANCE);
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            System.err.println("[Aria] Failed to register animations module: " + e.getMessage());
        }

        FileService.register(CallableManager.INSTANCE);
        HttpService.register(CallableManager.INSTANCE);
        EventBus.register(CallableManager.INSTANCE);
        JsonService.register(CallableManager.INSTANCE);
        BinarySerializer.register(CallableManager.INSTANCE);


        CallableManager.INSTANCE.aliasNamespace("fs", "Fs");
        CallableManager.INSTANCE.aliasNamespace("net", "Net");
        CallableManager.INSTANCE.aliasNamespace("event", "Event");
        CallableManager.INSTANCE.aliasNamespace("json", "Json");
        CallableManager.INSTANCE.aliasNamespace("serial", "Serial");


        // 注册模块加载函数到 GlobalStorage（每个引擎独立）
        globalStorage.putMeta("__import__", (ICallable) data -> {
            String modulePath = data.get(0).stringValue();
            try {
                var program = moduleLoader.load(modulePath);
                Context moduleCtx = createContext();
                Interpreter interpreter = new Interpreter();
                var result = interpreter.execute(program, moduleCtx);
                return result.getValue();
            } catch (Exception e) {
                throw new AriaRuntimeException(
                    "Failed to import module '" + modulePath + "': " + e.getMessage());
            }
        });
    }

    public Context createContext() {
        if (!initialized) initialize();
        return new Context(globalStorage);
    }

    public GlobalStorage getGlobalStorage() { return globalStorage; }
    public ModuleLoader getModuleLoader() { return moduleLoader; }
    public AnnotationRegistry getAnnotationRegistry() { return annotationRegistry; }
}
