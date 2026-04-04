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

public class CircY implements IAriaObject {
    private final double centerX;
    private final double centerY;
    private final double radius;
    private final long transferTime;
    private long startTime;
    private boolean reverse;
    private boolean isX;

    @AriaObjectConstructor("CircY")
    public CircY(InvocationData data) {
        if (data.size() >= 4) {
            this.centerX = data.get(0).doubleValue();
            this.centerY = data.get(1).doubleValue();
            this.radius = data.get(2).doubleValue();
            this.transferTime = Math.max(data.get(3).longValue(),1);
        } else {
            this.centerX = 0; this.centerY = 0;
            this.radius = 100; this.transferTime = 1000;
        }
        this.startTime = System.currentTimeMillis();
        this.reverse = false; this.isX = false;
    }

    @AriaInvokeHandler("get")
    public double get(InvocationData data) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        double progress = (double) elapsedTime / transferTime;
        progress = progress % 1.0;
        if (reverse) { progress = 1 - progress; }
        double angle = progress * 2 * Math.PI;
        if (isX) { return centerX + radius * Math.cos(angle); }
        else { return centerY + radius * Math.sin(angle); }
    }

    @AriaInvokeHandler("reset")
    public void reset(InvocationData data) {
        this.startTime = System.currentTimeMillis(); this.reverse = false;
    }
    @AriaInvokeHandler("reverse")
    public void reverse(InvocationData data) {
        this.reverse = true; this.startTime = System.currentTimeMillis();
    }
    @Override public double numberValue() { return this.get(null); }
    @Override public boolean booleanValue() { return numberValue() > 0; }
    @Override public boolean canMath() { return true; }
    @Override public String stringValue() { return String.valueOf(get(null)); }
    @Override public String getTypeName() { return isX ? "circx" : "circy"; }
}