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
    // 模块单例缓存：同一模块只执行一次，多次 import 共享同一导出实例（顶层副作用单例）
    private final java.util.Map<String, priv.seventeen.artist.aria.value.IValue<?>> moduleInstances =
            new java.util.concurrent.ConcurrentHashMap<>();
    // 当前线程的模块加载栈：用于检测循环依赖（A → B → A）
    private final ThreadLocal<java.util.LinkedHashSet<String>> moduleLoadingStack =
            ThreadLocal.withInitial(java.util.LinkedHashSet::new);
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
            if (data.argCount() < 2) {
                return new RangeObject(0, 0);
            }
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

        CallableManager.INSTANCE.registerConstructor("UUID", (IObjectConstructor<UUIDObject>) data ->
                data.argCount() > 0 ? new UUIDObject(data.get(0).stringValue()) : new UUIDObject());
        CallableManager.INSTANCE.registerObject(UUIDObject.class);

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
            // 沙箱激活时，禁止路径穿越/绝对路径 import（防止越出搜索路径读取任意文件作为模块）
            var sb = priv.seventeen.artist.aria.runtime.Interpreter.currentSandbox();
            if (sb != null) {
                String mp = modulePath.replace('\\', '/');
                boolean absolute = mp.startsWith("/") || (mp.length() >= 2 && mp.charAt(1) == ':');
                boolean traversal = mp.equals("..") || mp.startsWith("../") || mp.contains("/../") || mp.endsWith("/..");
                if (absolute || traversal) {
                    throw new AriaRuntimeException(
                            "Sandbox: import path not allowed (absolute or traversal): " + modulePath);
                }
            }
            // 模块单例：命中缓存直接返回，模块顶层只执行一次
            var instance = moduleInstances.get(modulePath);
            if (instance != null) return instance;
            // 循环依赖检测：模块尚未加载完就被自己的依赖链再次 import → 报清晰错误而非栈溢出
            var loadingStack = moduleLoadingStack.get();
            if (loadingStack.contains(modulePath)) {
                throw new AriaRuntimeException("Circular module dependency: "
                        + String.join(" -> ", loadingStack) + " -> " + modulePath);
            }
            loadingStack.add(modulePath);
            try {
                var program = moduleLoader.load(modulePath);
                Context moduleCtx = createContext();
                Interpreter interpreter = new Interpreter();
                var result = interpreter.execute(program, moduleCtx);
                // export 语句把符号收集进模块上下文的 __exports__ map。
                // 若存在则包装为 ModuleObject 作为模块接口（dot/命名导入均可访问）。
                var exportsRef = moduleCtx.getLocalStorage()
                        .getVarVariableExisting(priv.seventeen.artist.aria.context.VariableKey.of("__exports__"));
                priv.seventeen.artist.aria.value.IValue<?> moduleValue;
                if (exportsRef != null
                        && exportsRef.getValue() instanceof priv.seventeen.artist.aria.value.MapValue mv) {
                    java.util.Map<String, priv.seventeen.artist.aria.value.IValue<?>> exports =
                            new java.util.LinkedHashMap<>();
                    for (var e : mv.jvmValue().entrySet()) {
                        exports.put(e.getKey().stringValue(), e.getValue());
                    }
                    moduleValue = new ObjectValue<>(
                            new priv.seventeen.artist.aria.object.ModuleObject(modulePath, exports));
                } else {
                    // 无 export：回退到模块返回值（兼容 `return {map}` / `return value` 的旧写法）
                    moduleValue = result.getValue();
                }
                moduleInstances.put(modulePath, moduleValue);
                return moduleValue;
            } catch (AriaRuntimeException e) {
                // 循环依赖等已是清晰的运行时错误，原样上抛，避免逐层包裹淹没信息
                throw e;
            } catch (Exception e) {
                throw new AriaRuntimeException(
                    "Failed to import module '" + modulePath + "': " + e.getMessage());
            } finally {
                loadingStack.remove(modulePath);
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
