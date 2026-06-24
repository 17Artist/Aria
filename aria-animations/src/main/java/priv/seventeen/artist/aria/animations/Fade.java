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

public class Fade implements IAriaObject {

    private enum FadeState {
        FADE_IN,
        STAY,
        FADE_OUT,
        COMPLETED
    }


    private final long fadeInDuration;
    private final long stayDuration;
    private final long fadeOutDuration;


    private FadeState currentState;
    private long startTime;
    private double opacity;


    @AriaObjectConstructor("Fade")
    public Fade(InvocationData data) {
        if (data.size() >= 3) {
            this.fadeInDuration = Math.max(data.get(0).longValue(),1);
            this.stayDuration = data.get(1).longValue();
            this.fadeOutDuration = Math.max(data.get(2).longValue(),1);
        } else {

            this.fadeInDuration = 500;
            this.stayDuration = 2000;
            this.fadeOutDuration = 500;
        }


        this.currentState = FadeState.FADE_IN;
        this.startTime = System.currentTimeMillis();
        this.opacity = 0.0;
    }

        private void update() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        switch (currentState) {
            case FADE_IN:
                if (elapsedTime >= fadeInDuration) {
                    currentState = FadeState.STAY;
                    startTime = currentTime;
                    opacity = 1.0;
                } else {
                    opacity = (double) elapsedTime / fadeInDuration;
                }
                break;

            case STAY:
                if (elapsedTime >= stayDuration) {
                    currentState = FadeState.FADE_OUT;
                    startTime = currentTime;
                } else {
                    opacity = 1.0;
                }
                break;

            case FADE_OUT:
                if (elapsedTime >= fadeOutDuration) {
                    currentState = FadeState.COMPLETED;
                    opacity = 0.0;
                } else {
                    opacity = 1.0 - ((double) elapsedTime / fadeOutDuration);
                }
                break;

            case COMPLETED:
                opacity = 0.0;
                break;
        }
    }

        @AriaInvokeHandler("get")
    public double getOpacity(InvocationData data) {
        update();
        return opacity;
    }


    @AriaInvokeHandler("reset")
    public void reset(InvocationData data) {
        this.currentState = FadeState.FADE_IN;
        this.startTime = System.currentTimeMillis();
        this.opacity = 0.0;
    }

    @Override
    public double numberValue() {
        return getOpacity(null);
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
        return String.valueOf(getOpacity(null));
    }

    @Override
    public String getTypeName() {
        return "fade";
    }
}