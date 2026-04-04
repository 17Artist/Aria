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
public class TwoLerp implements IAriaObject {
    private final double initialValue;
    private final double middleValue;
    private final double finalValue;
    private final long firstStageTime;
    private final long secondStageTime;
    private final long totalTime;
    private long startTime;

    @AriaObjectConstructor("TwoLerp")
    public TwoLerp(InvocationData data) {
        if (data.size() >= 5) {
            this.initialValue = data.get(0).doubleValue();
            this.middleValue = data.get(1).doubleValue();
            this.finalValue = data.get(2).doubleValue();
            this.firstStageTime = Math.max(data.get(3).longValue(),1);;
            this.secondStageTime = Math.max(data.get(4).longValue(),1);;
        } else if (data.size() >= 2) {
            this.initialValue = 1.0;
            this.middleValue = 2.0;
            this.finalValue = 1.0;
            this.firstStageTime = Math.max(data.get(0).longValue(),1);
            this.secondStageTime = Math.max(data.get(1).longValue(),1);;
        } else {
            this.initialValue = 0.0;
            this.middleValue = 1.0;
            this.finalValue = 0.0;
            this.firstStageTime = 1000;
            this.secondStageTime = 1000;
        }
        this.totalTime = firstStageTime + secondStageTime;
        this.startTime = System.currentTimeMillis();
    }

    @AriaInvokeHandler("get")
    public double get(InvocationData data) {
        long elapsedTime = System.currentTimeMillis() - startTime;

        if (elapsedTime >= totalTime) {
            return finalValue;
        }

        if (elapsedTime <= firstStageTime) {
            double progress = (double) elapsedTime / firstStageTime;
            return initialValue + progress * (middleValue - initialValue);
        }
        else {
            double progress = (double) (elapsedTime - firstStageTime) / secondStageTime;
            return middleValue + progress * (finalValue - middleValue);
        }
    }

    @AriaInvokeHandler("reset")
    public void reset(InvocationData data) {
        this.startTime = System.currentTimeMillis();
    }

    @AriaInvokeHandler("isFinished")
    public boolean isFinished(InvocationData data) {
        return System.currentTimeMillis() - startTime >= totalTime;
    }

    @AriaInvokeHandler("getProgress")
    public double getProgress(InvocationData data) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        return Math.min(1.0, (double) elapsedTime / totalTime);
    }

    @AriaInvokeHandler("getCurrentStage")
    public int getCurrentStage(InvocationData data) {
        long elapsedTime = System.currentTimeMillis() - startTime;

        if (elapsedTime >= totalTime) {
            return 2;
        } else if (elapsedTime <= firstStageTime) {
            return 0;
        } else {
            return 1;
        }
    }

    @AriaInvokeHandler("getStageProgress")
    public double getStageProgress(InvocationData data) {
        long elapsedTime = System.currentTimeMillis() - startTime;

        if (elapsedTime >= totalTime) {
            return 1.0;
        } else if (elapsedTime <= firstStageTime) {
            return (double) elapsedTime / firstStageTime;
        } else {
            return (double) (elapsedTime - firstStageTime) / secondStageTime;
        }
    }

    @AriaInvokeHandler("getTotalTime")
    public long getTotalTime(InvocationData data) {
        return totalTime;
    }

    @AriaInvokeHandler("getFirstStageTime")
    public long getFirstStageTime(InvocationData data) {
        return firstStageTime;
    }

    @AriaInvokeHandler("getSecondStageTime")
    public long getSecondStageTime(InvocationData data) {
        return secondStageTime;
    }

    @Override
    public double numberValue() {
        return this.get(null);
    }

    @Override
    public boolean booleanValue() {
        return !isFinished(null);
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
        return "twolerp";
    }
}
