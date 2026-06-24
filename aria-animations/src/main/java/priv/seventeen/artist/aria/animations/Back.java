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

public class Back implements IAriaObject {
    private final double start;
    private final double end;
    private final double overshoot;
    private final int type;
    private final long transferTime;
    private long startTime;
    private boolean reverse;

    @AriaObjectConstructor("Back")
    public Back(InvocationData data) {
        if (data.size() >= 5) {
            this.start = data.get(0).doubleValue();
            this.end = data.get(1).doubleValue();
            this.overshoot = data.get(2).doubleValue();
            this.type = data.get(3).intValue();
            this.transferTime = Math.max(data.get(4).longValue(),1);
        } else {
            this.start = 0;
            this.end = 0;
            this.overshoot = 1.70158;
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
            case 1 -> backOut(progress);
            case 2 -> backInOut(progress);
            default -> backIn(progress);
        };

        value = start + (value * distance);
        return reverse ? end - (value - start) : value;
    }

    private double backIn(double t) {
        return t * t * ((overshoot + 1) * t - overshoot);
    }

    private double backOut(double t) {
        t = t - 1;
        return t * t * ((overshoot + 1) * t + overshoot) + 1;
    }

    private double backInOut(double t) {
        double s = overshoot * 1.525;
        t *= 2;
        if (t < 1) {
            return 0.5 * (t * t * ((s + 1) * t - s));
        } else {
            t -= 2;
            return 0.5 * (t * t * ((s + 1) * t + s) + 2);
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
        return "back";
    }
}