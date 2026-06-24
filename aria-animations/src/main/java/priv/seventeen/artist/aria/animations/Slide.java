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

public class Slide implements IAriaObject {
    public enum EaseType {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT,
        BOUNCE
    }

    private final double startPosition;
    private final double endPosition;
    private final long transferTime;
    private final EaseType easeType;
    private long startTime;
    private boolean reverse;


    @AriaObjectConstructor("Slide")
    public Slide(InvocationData data) {
        if (data.size() >= 3) {
            this.startPosition = data.get(0).doubleValue();
            this.endPosition = data.get(1).doubleValue();
            this.transferTime = Math.max(data.get(2).longValue(), 1);

            int easeTypeIndex = data.size() >= 4 ? data.get(3).intValue() : 0;
            this.easeType = EaseType.values()[Math.max(0, Math.min(easeTypeIndex, EaseType.values().length - 1))];
        } else {
            this.startPosition = 0.0;
            this.endPosition = 100.0;
            this.transferTime = 500;
            this.easeType = EaseType.EASE_OUT;
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
            return reverse ? startPosition : endPosition;
        }

        if (reverse) {
            progress = 1.0 - progress;
        }

        double easedProgress = applyEasing(progress);

        return startPosition + (endPosition - startPosition) * easedProgress;
    }

    private double applyEasing(double t) {
        switch (easeType) {
            case LINEAR:
                return t;

            case EASE_IN:
                return t * t * t;

            case EASE_OUT:
                return 1 - Math.pow(1 - t, 3);

            case EASE_IN_OUT:
                if (t < 0.5) {
                    return 4 * t * t * t;
                } else {
                    return 1 - Math.pow(-2 * t + 2, 3) / 2;
                }

            case BOUNCE:
                return bounceOut(t);

            default:
                return t;
        }
    }

    private double bounceOut(double t) {
        double n1 = 7.5625;
        double d1 = 2.75;

        if (t < 1 / d1) {
            return n1 * t * t;
        } else if (t < 2 / d1) {
            return n1 * (t -= 1.5 / d1) * t + 0.75;
        } else if (t < 2.5 / d1) {
            return n1 * (t -= 2.25 / d1) * t + 0.9375;
        } else {
            return n1 * (t -= 2.625 / d1) * t + 0.984375;
        }
    }

    @AriaInvokeHandler("progress")
    public double getProgress(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        double progress = Math.min(1.0, (double) elapsedTime / transferTime);
        return reverse ? 1.0 - progress : progress;
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
        return Math.abs(numberValue() - startPosition) > 0.001;
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
        return "slide";
    }
}
