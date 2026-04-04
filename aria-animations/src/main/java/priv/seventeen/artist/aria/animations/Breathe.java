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

public class Breathe implements IAriaObject {
    private final double minValue;
    private final double maxValue;
    private final long inhaleTime;
    private final long exhaleTime;
    private final long holdTime;
    private final boolean naturalCurve;
    private long startTime;
    private boolean reverse;

    private enum BreathState {
        INHALE,
        HOLD_IN,
        EXHALE,
        HOLD_OUT
    }


    @AriaObjectConstructor("Breathe")
    public Breathe(InvocationData data) {
        if (data.size() >= 5) {
            this.minValue = data.get(0).doubleValue();
            this.maxValue = data.get(1).doubleValue();
            this.inhaleTime = Math.max(data.get(2).longValue(), 100);
            this.exhaleTime = Math.max(data.get(3).longValue(), 100);
            this.holdTime = Math.max(data.get(4).longValue(), 0);
            this.naturalCurve = data.size() >= 6 && data.get(5).booleanValue();
        } else {
            this.minValue = 0.8;
            this.maxValue = 1.2;
            this.inhaleTime = 2000;
            this.exhaleTime = 2500;
            this.holdTime = 500;
            this.naturalCurve = true;
        }
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("get")
    public double get(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;


        long fullCycle = inhaleTime + holdTime + exhaleTime + holdTime;
        long timeInCycle = elapsedTime % fullCycle;

        if (reverse) {
            timeInCycle = fullCycle - timeInCycle;
        }

        BreathState currentState;
        long stateElapsed;

        if (timeInCycle < inhaleTime) {
            currentState = BreathState.INHALE;
            stateElapsed = timeInCycle;
        } else if (timeInCycle < inhaleTime + holdTime) {
            currentState = BreathState.HOLD_IN;
            stateElapsed = timeInCycle - inhaleTime;
        } else if (timeInCycle < inhaleTime + holdTime + exhaleTime) {
            currentState = BreathState.EXHALE;
            stateElapsed = timeInCycle - inhaleTime - holdTime;
        } else {
            currentState = BreathState.HOLD_OUT;
            stateElapsed = timeInCycle - inhaleTime - holdTime - exhaleTime;
        }

        return calculateBreathValue(currentState, stateElapsed);
    }

    private double calculateBreathValue(BreathState state, long elapsed) {
        switch (state) {
            case INHALE:
                double inhaleProgress = (double) elapsed / inhaleTime;
                if (naturalCurve) {
                    inhaleProgress = easeInOut(inhaleProgress);
                }
                return minValue + (maxValue - minValue) * inhaleProgress;

            case HOLD_IN:
                return maxValue;

            case EXHALE:
                double exhaleProgress = (double) elapsed / exhaleTime;
                if (naturalCurve) {
                    exhaleProgress = easeOut(exhaleProgress);
                }
                return maxValue - (maxValue - minValue) * exhaleProgress;

            case HOLD_OUT:
                return minValue;

            default:
                return minValue;
        }
    }

    private double easeInOut(double t) {
        if (t < 0.5) {
            return 2 * t * t;
        } else {
            return -1 + (4 - 2 * t) * t;
        }
    }

    private double easeOut(double t) {
        return 1 - Math.pow(1 - t, 2);
    }

    @AriaInvokeHandler("phase")
    public String getBreathPhase(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        long fullCycle = inhaleTime + holdTime + exhaleTime + holdTime;
        long timeInCycle = elapsedTime % fullCycle;

        if (reverse) {
            timeInCycle = fullCycle - timeInCycle;
        }

        if (timeInCycle < inhaleTime) {
            return "inhale";
        } else if (timeInCycle < inhaleTime + holdTime) {
            return "hold_in";
        } else if (timeInCycle < inhaleTime + holdTime + exhaleTime) {
            return "exhale";
        } else {
            return "hold_out";
        }
    }

    @AriaInvokeHandler("cycleProgress")
    public double getCycleProgress(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        long fullCycle = inhaleTime + holdTime + exhaleTime + holdTime;
        double progress = (double)(elapsedTime % fullCycle) / fullCycle;
        return reverse ? 1.0 - progress : progress;
    }

    @AriaInvokeHandler("intensity")
    public double getIntensity(InvocationData data) {
        double currentValue = get(null);
        return (currentValue - minValue) / (maxValue - minValue);
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
        return getIntensity(null) > 0.5;
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
        return "breathe";
    }
}