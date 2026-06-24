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

package priv.seventeen.artist.aria.callable.builtin;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.value.NumberValue;

public class MathFunctions {

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("math", "sin", d -> new NumberValue(Math.sin(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "cos", d -> new NumberValue(Math.cos(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "tan", d -> new NumberValue(Math.tan(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "abs", d -> new NumberValue(Math.abs(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "floor", d -> new NumberValue(Math.floor(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "ceil", d -> new NumberValue(Math.ceil(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "round", d -> new NumberValue(Math.round(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "sqrt", d -> new NumberValue(Math.sqrt(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "pow", d -> new NumberValue(Math.pow(d.get(0).numberValue(), d.get(1).numberValue())));
        manager.registerStaticFunction("math", "min", d -> new NumberValue(Math.min(d.get(0).numberValue(), d.get(1).numberValue())));
        manager.registerStaticFunction("math", "max", d -> new NumberValue(Math.max(d.get(0).numberValue(), d.get(1).numberValue())));
        manager.registerStaticFunction("math", "random", d -> new NumberValue(Math.random()));
        manager.registerStaticFunction("math", "log", d -> new NumberValue(Math.log(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "PI", d -> new NumberValue(Math.PI));
        manager.registerStaticFunction("math", "E", d -> new NumberValue(Math.E));
        // 反三角函数
        manager.registerStaticFunction("math", "asin", d -> new NumberValue(Math.asin(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "acos", d -> new NumberValue(Math.acos(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "atan", d -> new NumberValue(Math.atan(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "atan2", d -> new NumberValue(Math.atan2(d.get(0).numberValue(), d.get(1).numberValue())));
        // 幂与根
        manager.registerStaticFunction("math", "cbrt", d -> new NumberValue(Math.cbrt(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "hypot", d -> new NumberValue(Math.hypot(d.get(0).numberValue(), d.get(1).numberValue())));
        // 指数与对数
        manager.registerStaticFunction("math", "exp", d -> new NumberValue(Math.exp(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "expm1", d -> new NumberValue(Math.expm1(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "log10", d -> new NumberValue(Math.log10(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "log1p", d -> new NumberValue(Math.log1p(d.get(0).numberValue())));
        // 双曲函数
        manager.registerStaticFunction("math", "sinh", d -> new NumberValue(Math.sinh(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "cosh", d -> new NumberValue(Math.cosh(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "tanh", d -> new NumberValue(Math.tanh(d.get(0).numberValue())));
        // 角度转换
        manager.registerStaticFunction("math", "toDegrees", d -> new NumberValue(Math.toDegrees(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "toRadians", d -> new NumberValue(Math.toRadians(d.get(0).numberValue())));
        // 符号与舍入
        manager.registerStaticFunction("math", "signum", d -> new NumberValue(Math.signum(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "rint", d -> new NumberValue(Math.rint(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "ulp", d -> new NumberValue(Math.ulp(d.get(0).numberValue())));
        // 浮点操作
        manager.registerStaticFunction("math", "copySign", d -> new NumberValue(Math.copySign(d.get(0).numberValue(), d.get(1).numberValue())));
        manager.registerStaticFunction("math", "scalb", d -> new NumberValue(Math.scalb(d.get(0).numberValue(), (int) d.get(1).numberValue())));
        manager.registerStaticFunction("math", "nextUp", d -> new NumberValue(Math.nextUp(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "nextDown", d -> new NumberValue(Math.nextDown(d.get(0).numberValue())));
        manager.registerStaticFunction("math", "IEEEremainder", d -> new NumberValue(Math.IEEEremainder(d.get(0).numberValue(), d.get(1).numberValue())));
        manager.registerStaticFunction("math", "getExponent", d -> new NumberValue(Math.getExponent(d.get(0).numberValue())));
    }
}
