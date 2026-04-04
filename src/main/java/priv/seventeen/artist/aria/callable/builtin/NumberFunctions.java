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
import priv.seventeen.artist.aria.value.BooleanValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.StringValue;

import java.text.DecimalFormat;

public class NumberFunctions {

    public static void register(CallableManager manager) {
        manager.registerObjectFunction(NumberValue.class, "toInt", d ->
                new NumberValue((int) d.get(0).numberValue()));
        manager.registerObjectFunction(NumberValue.class, "toFixed", d -> {
            double val = d.get(0).numberValue();
            int digits = (int) d.get(1).numberValue();
            return new StringValue(String.format("%." + digits + "f", val));
        });
        manager.registerObjectFunction(NumberValue.class, "isNaN", d ->
                BooleanValue.of(Double.isNaN(d.get(0).numberValue())));
        manager.registerObjectFunction(NumberValue.class, "isInfinite", d ->
                BooleanValue.of(Double.isInfinite(d.get(0).numberValue())));
        manager.registerObjectFunction(NumberValue.class, "round", d -> {
            double val = d.get(0).numberValue();
            int places = d.argCount() > 1 ? (int) d.get(1).numberValue() : 0;
            if (places == 0) {
                return new StringValue(String.valueOf(Math.round(val)));
            }
            DecimalFormat df = new DecimalFormat("0." + "0".repeat(Math.max(0, places)));
            return new StringValue(df.format(val));
        });
        manager.registerObjectFunction(NumberValue.class, "abs", d ->
                new NumberValue(Math.abs(d.get(0).numberValue())));
        manager.registerObjectFunction(NumberValue.class, "ceil", d ->
                new NumberValue(Math.ceil(d.get(0).numberValue())));
        manager.registerObjectFunction(NumberValue.class, "floor", d ->
                new NumberValue(Math.floor(d.get(0).numberValue())));
    }
}
