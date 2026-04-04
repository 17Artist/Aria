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

package priv.seventeen.artist.aria.animations;

import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler;
import priv.seventeen.artist.aria.annotation.java.AriaObjectConstructor;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.object.IAriaObject;

public class Expo implements IAriaObject {
    private final double start;
    private final double end;
    private final int type;
    private final long transferTime;
    private long startTime;
    private boolean reverse;

    @AriaObjectConstructor("Expo")
    public Expo(InvocationData data) {
        if (data.size() >= 4) {
            this.start = data.get(0).doubleValue();
            this.end = data.get(1).doubleValue();
            this.type = data.get(2).intValue();
            this.transferTime = Math.max(data.get(3).longValue(),1);
        } else {
            this.start = 0;
            this.end = 0;
            this.type = 0;
            this.transferTime = 1000;
        }
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("get")
    public double get(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        double progress = (double) elapsedTime / transferTime;

        if (progress >= 1.0) {
            return reverse ? start : end;
        }

        double distance = end - start;
        if (reverse) {
            progress = 1 - progress;
        }


        double value = switch (type) {
            case 1 -> expoOut(progress);
            case 2 -> expoInOut(progress);
            default -> expoIn(progress);
        };

        value = start + (value * distance);
        return reverse ? end - (value - start) : value;
    }

    private double expoIn(double t) {
        return t == 0 ? 0 : Math.pow(2, 10 * (t - 1));
    }

    private double expoOut(double t) {
        return t == 1 ? 1 : 1 - Math.pow(2, -10 * t);
    }

    private double expoInOut(double t) {
        if (t == 0) return 0;
        if (t == 1) return 1;
        if (t < 0.5) {
            return 0.5 * Math.pow(2, 20 * t - 10);
        } else {
            return 0.5 * (-Math.pow(2, -20 * t + 10) + 2);
        }
    }

    @AriaInvokeHandler("reset")
    public void reset(InvocationData data) {
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("reverse")
    public void reverse(InvocationData data) {
        this.reverse = true;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public double numberValue() {
        return this.get(null);
    }

    @Override
    public boolean booleanValue() {
        return numberValue() > 0;
    }

    @Override
    public boolean canMath() {
        return true;
    }

    @Override
    public String stringValue() {
        return String.valueOf(get(null));
    }

    @Override
    public String getTypeName() {
        return "expo";
    }
}