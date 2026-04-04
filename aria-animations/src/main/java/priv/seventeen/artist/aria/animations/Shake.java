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

public class Shake implements IAriaObject {
    private final double centerValue;
    private final double intensity;
    private final long totalDuration;
    private final long shakeInterval;
    private final boolean damping;
    private final int shakeCount;
    private long startTime;
    private boolean reverse;


    @AriaObjectConstructor("Shake")
    public Shake(InvocationData data) {
        if (data.size() >= 5) {
            this.centerValue = data.get(0).doubleValue();
            this.intensity = Math.abs(data.get(1).doubleValue());
            this.totalDuration = Math.max(data.get(2).longValue(), 100);
            this.shakeInterval = Math.max(data.get(3).longValue(), 10);
            this.shakeCount = Math.max(data.get(4).intValue(), 1);
            this.damping = data.size() >= 6 && data.get(5).booleanValue();
        } else {
            this.centerValue = 0.0;
            this.intensity = 10.0;
            this.totalDuration = 600;
            this.shakeInterval = 50;
            this.shakeCount = 6;
            this.damping = true;
        }
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("get")
    public double get(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        if (elapsedTime >= totalDuration) {
            return centerValue;
        }

        double progress = (double) elapsedTime / totalDuration;
        if (reverse) {
            progress = 1.0 - progress;
        }

        long currentShakeIndex = elapsedTime / shakeInterval;

        if (currentShakeIndex >= shakeCount) {
            return centerValue;
        }

        double shakeValue = calculateShake(currentShakeIndex, elapsedTime);

        if (damping) {
            double dampingFactor = 1.0 - progress;
            shakeValue *= dampingFactor;
        }

        return centerValue + shakeValue;
    }

    private double calculateShake(long shakeIndex, long elapsedTime) {
        long seed = shakeIndex + (long)centerValue + (long)intensity;

        seed = (seed * 1103515245 + 12345) & 0x7fffffff;
        double randomValue = (double)seed / 0x7fffffff;

        randomValue = (randomValue * 2.0) - 1.0;

        return randomValue * intensity;
    }

    @AriaInvokeHandler("intensity")
    public double getCurrentIntensity(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        if (elapsedTime >= totalDuration) {
            return 0.0;
        }

        double progress = (double) elapsedTime / totalDuration;
        if (reverse) {
            progress = 1.0 - progress;
        }

        if (damping) {
            return intensity * (1.0 - progress);
        } else {
            return intensity;
        }
    }

    @AriaInvokeHandler("trigger")
    public void trigger(InvocationData data) {
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("isComplete")
    public boolean isComplete(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        return elapsedTime >= totalDuration;
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
        return Math.abs(numberValue() - centerValue) > 0.001;
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
        return "shake";
    }
}
