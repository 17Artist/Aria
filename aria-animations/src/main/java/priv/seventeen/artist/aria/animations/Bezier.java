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

public class Bezier implements IAriaObject {
    private final double start;
    private final double end;
    private final double x1;
    private final double y1;
    private final double x2;
    private final double y2;
    private final long transferTime;
    private long startTime;
    private boolean reverse;

    @AriaObjectConstructor("Bezier")
    public Bezier(InvocationData data) {
        if (data.size() >= 7) {
            this.start = data.get(0).doubleValue();
            this.end = data.get(1).doubleValue();
            this.x1 = data.get(2).doubleValue();
            this.y1 = data.get(3).doubleValue();
            this.x2 = data.get(4).doubleValue();
            this.y2 = data.get(5).doubleValue();
            this.transferTime = data.get(6).longValue();
        } else {
            this.start = 0;
            this.end = 0;
            this.x1 = 0.25;
            this.y1 = 0.1;
            this.x2 = 0.25;
            this.y2 = 1.0;
            this.transferTime = 1000;
        }
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    public Bezier(double start, double end, double x1, double y1, double x2, double y2, long time) {
        this.start = start;
        this.end = end;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.transferTime = Math.max(time, 1);
        this.startTime = System.currentTimeMillis();
        this.reverse = false;
    }

    @AriaInvokeHandler("get")
    public double get(InvocationData data) {

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        double t = (double) elapsedTime / transferTime;

        if (t >= 1.0) {
            return reverse ? start : end;
        }

        double distance = end - start;
        if (reverse) {
            t = 1 - t;
        }

        double value = calculateBezierPoint(t);
        value = start + (value * distance);

        return reverse ? end - (value - start) : value;
    }

    private double calculateBezierPoint(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;

        double epsilon = 0.0001;
        double x = solveBezierX(t, epsilon);

        return calculateBezierY(x);
    }

    private double solveBezierX(double t, double epsilon) {
        double start = 0.0;
        double end = 1.0;
        double target = t;

        while (true) {
            double mid = (start + end) / 2.0;
            double x = calculateBezierX(mid);

            if (Math.abs(x - target) < epsilon) {
                return mid;
            }

            if (x < target) {
                start = mid;
            } else {
                end = mid;
            }
        }
    }

    private double calculateBezierX(double t) {
        return 3 * t * (1 - t) * (1 - t) * x1 +
                3 * t * t * (1 - t) * x2 +
                t * t * t;
    }

    private double calculateBezierY(double t) {
        return 3 * t * (1 - t) * (1 - t) * y1 +
                3 * t * t * (1 - t) * y2 +
                t * t * t;
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
        return "bezier";
    }
}