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

public class Blink implements IAriaObject {
    private final double start;
    private final double end;
    private final long transferTime;
    private long startTime;
    private boolean reverse;
    private boolean autoReverse;

    @AriaObjectConstructor("Blink")
    public Blink(InvocationData data) {
        if (data.size() >= 3) {
            this.start = data.get(0).doubleValue();
            this.end = data.get(1).doubleValue();
            this.transferTime = Math.max(data.get(2).longValue(),1);
        } else {
            this.start = 0;
            this.end = 1;
            this.transferTime = 1000;
        }
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
        this.autoReverse = true;
    }

    @AriaInvokeHandler("get")
    public double get(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        double progress = (double) elapsedTime / transferTime;

        if (progress >= 1.0) {
            if (autoReverse) {
                this.reverse = !this.reverse;
                this.startTime = currentTime;
                progress = 0.0;
            } else {
                return reverse ? start : end;
            }
        }

        if (reverse) {
            return end - (progress * (end - start));
        } else {
            return start + (progress * (end - start));
        }
    }

    @AriaInvokeHandler("reset")
    public void reset(InvocationData data) {
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("reverse")
    public void reverse(InvocationData data) {
        this.reverse = !this.reverse;
        this.startTime = System.currentTimeMillis();
    }

    @AriaInvokeHandler("setAutoReverse")
    public void setAutoReverse(InvocationData data) {
        if (data.size() > 0) {
            this.autoReverse = data.get(0).booleanValue();
        }
    }

    @AriaInvokeHandler("isReversed")
    public boolean isReversed(InvocationData data) {
        return this.reverse;
    }

    @AriaInvokeHandler("getProgress")
    public double getProgress(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        return Math.min(1.0, (double) elapsedTime / transferTime);
    }

    @Override
    public double numberValue() {
        return this.get(null);
    }

    @Override
    public boolean booleanValue() {
        return numberValue() > ((start + end) / 2);
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
        return "blink";
    }
}