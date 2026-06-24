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

public class Swing implements IAriaObject {
    private final double centerAngle;
    private final double maxAngle;
    private final long period;
    private final boolean damping;
    private final double dampingFactor;
    private final double gravity;
    private long startTime;
    private boolean reverse;


    @AriaObjectConstructor("Swing")
    public Swing(InvocationData data) {
        if (data.size() >= 3) {
            this.centerAngle = data.get(0).doubleValue();
            this.maxAngle = Math.abs(data.get(1).doubleValue());
            this.period = Math.max(data.get(2).longValue(), 100);
            this.damping = data.size() >= 4 && data.get(3).booleanValue();
            this.dampingFactor = data.size() >= 5 ?
                    Math.max(0.001, Math.min(1.0, data.get(4).doubleValue())) : 0.98;
            this.gravity = data.size() >= 6 ? data.get(5).doubleValue() : 1.0;
        } else {
            this.centerAngle = 0.0;
            this.maxAngle = 30.0;
            this.period = 2000;
            this.damping = true;
            this.dampingFactor = 0.98;
            this.gravity = 1.0;
        }
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("get")
    public double get(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        double timeInSeconds = elapsedTime / 1000.0;

        double adjustedPeriod = period * Math.sqrt(1.0 / Math.max(0.1, gravity));

        double timeInCycle = (elapsedTime % (long)adjustedPeriod) / adjustedPeriod;
        if (reverse) {
            timeInCycle = 1.0 - timeInCycle;
        }

        double angle = Math.cos(timeInCycle * 2 * Math.PI);

        if (damping) {
            double dampingMultiplier = Math.pow(dampingFactor, timeInSeconds);
            angle *= dampingMultiplier;
        }

        return centerAngle + (maxAngle * angle);
    }

    @AriaInvokeHandler("velocity")
    public double getVelocity(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        double timeInSeconds = elapsedTime / 1000.0;

        double adjustedPeriod = period * Math.sqrt(1.0 / Math.max(0.1, gravity));
        double timeInCycle = (elapsedTime % (long)adjustedPeriod) / adjustedPeriod;

        if (reverse) {
            timeInCycle = 1.0 - timeInCycle;
        }

        double velocity = -Math.sin(timeInCycle * 2 * Math.PI) * (2 * Math.PI / (adjustedPeriod / 1000.0));

        if (damping) {
            double dampingMultiplier = Math.pow(dampingFactor, timeInSeconds);
            velocity *= dampingMultiplier;
        }

        return maxAngle * velocity * (reverse ? -1 : 1);
    }

    @AriaInvokeHandler("energy")
    public double getKineticEnergy(InvocationData data) {
        double velocity = getVelocity(null);
        return 0.5 * velocity * velocity;
    }

    @AriaInvokeHandler("amplitude")
    public double getCurrentAmplitude(InvocationData data) {
        if (!damping) {
            return maxAngle;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        double timeInSeconds = elapsedTime / 1000.0;

        double dampingMultiplier = Math.pow(dampingFactor, timeInSeconds);
        return maxAngle * dampingMultiplier;
    }

    @AriaInvokeHandler("restart")
    public void restart(InvocationData data) {
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("reset")
    public void reset(InvocationData data) {
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("reverse")
    public void reverse(InvocationData data) {
        this.reverse = true;
    }

    @Override
    public double numberValue() {
        return this.get(null);
    }

    @Override
    public boolean booleanValue() {
        return Math.abs(numberValue() - centerAngle) > 0.001;
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
        return "swing";
    }
}
