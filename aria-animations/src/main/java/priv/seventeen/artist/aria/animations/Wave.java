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

public class Wave implements IAriaObject {
    private final double centerValue;
    private final double amplitude;
    private final long period;
    private final double phaseOffset;
    private final boolean damping;
    private final double dampingFactor;
    private long startTime;
    private boolean reverse;

    @AriaObjectConstructor("Wave")
    public Wave(InvocationData data) {
        if (data.size() >= 4) {
            this.centerValue = data.get(0).doubleValue();
            this.amplitude = data.get(1).doubleValue();
            this.period = Math.max(data.get(2).longValue(), 100);
            this.phaseOffset = data.get(3).doubleValue();
            this.damping = data.size() >= 5 && data.get(4).booleanValue();
            this.dampingFactor = data.size() >= 6 ? Math.max(0.001, data.get(5).doubleValue()) : 0.95;
        } else {
            this.centerValue = 0.0;
            this.amplitude = 50.0;
            this.period = 2000;
            this.phaseOffset = 0.0;
            this.damping = false;
            this.dampingFactor = 0.95;
        }
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("get")
    public double get(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        double timeInCycle = (double) elapsedTime / period;
        double angle = (timeInCycle * 2 * Math.PI) + phaseOffset;

        if (reverse) {
            angle = -angle;
        }

        double waveValue = Math.sin(angle);

        if (damping) {
            double timeInSeconds = elapsedTime / 1000.0;
            double dampingMultiplier = Math.pow(dampingFactor, timeInSeconds);
            waveValue *= dampingMultiplier;
        }

        return centerValue + (amplitude * waveValue);
    }

    @AriaInvokeHandler("normalized")
    public double getNormalized(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        double timeInCycle = (double) elapsedTime / period;
        double angle = (timeInCycle * 2 * Math.PI) + phaseOffset;

        if (reverse) {
            angle = -angle;
        }

        double waveValue = Math.sin(angle);

        if (damping) {
            double timeInSeconds = elapsedTime / 1000.0;
            double dampingMultiplier = Math.pow(dampingFactor, timeInSeconds);
            waveValue *= dampingMultiplier;
        }

        return waveValue;
    }

    @AriaInvokeHandler("phase")
    public double getPhase(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        double phase = ((double) elapsedTime / period) % 1.0;
        return reverse ? 1.0 - phase : phase;
    }

    @AriaInvokeHandler("cosine")
    public double getCosine(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        double timeInCycle = (double) elapsedTime / period;
        double angle = (timeInCycle * 2 * Math.PI) + phaseOffset;

        if (reverse) {
            angle = -angle;
        }

        double waveValue = Math.cos(angle);

        if (damping) {
            double timeInSeconds = elapsedTime / 1000.0;
            double dampingMultiplier = Math.pow(dampingFactor, timeInSeconds);
            waveValue *= dampingMultiplier;
        }

        return centerValue + (amplitude * waveValue);
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
        return "wave";
    }
}
