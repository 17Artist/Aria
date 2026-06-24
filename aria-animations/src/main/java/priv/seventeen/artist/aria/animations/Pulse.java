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

public class Pulse implements IAriaObject {
    private final double minValue;
    private final double maxValue;
    private final long pulseDuration;
    private final double intensity;
    private final boolean smooth;
    private long startTime;
    private boolean reverse;


    @AriaObjectConstructor("Pulse")
    public Pulse(InvocationData data) {
        if (data.size() >= 4) {
            this.minValue = data.get(0).doubleValue();
            this.maxValue = data.get(1).doubleValue();
            this.pulseDuration = Math.max(data.get(2).longValue(), 50);
            this.intensity = Math.max(0.0, Math.min(1.0, data.get(3).doubleValue()));
            this.smooth = data.size() >= 5 && data.get(4).booleanValue();
        } else {
            this.minValue = 0.5;
            this.maxValue = 1.0;
            this.pulseDuration = 800;
            this.intensity = 0.8;
            this.smooth = true;
        }
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("get")
    public double get(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        double cycleProgress = (double) (elapsedTime % pulseDuration) / pulseDuration;

        if (reverse) {
            cycleProgress = 1.0 - cycleProgress;
        }

        double pulseValue;

        if (smooth) {
            pulseValue = Math.sin(cycleProgress * Math.PI);
        } else {
            if (cycleProgress <= 0.5) {
                pulseValue = cycleProgress * 2.0;
            } else {
                pulseValue = 2.0 - cycleProgress * 2.0;
            }
        }

        pulseValue *= intensity;

        return minValue + (maxValue - minValue) * pulseValue;
    }

    @AriaInvokeHandler("phase")
    public double getPhase(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        return (double) (elapsedTime % pulseDuration) / pulseDuration;
    }

    @AriaInvokeHandler("sync")
    public void sync(InvocationData data) {
        this.startTime = System.currentTimeMillis();
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
        return numberValue() > ((minValue + maxValue) / 2.0);
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
        return "pulse";
    }
}
