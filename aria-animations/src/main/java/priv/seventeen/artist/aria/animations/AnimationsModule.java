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

import priv.seventeen.artist.aria.callable.CallableManager;

public class AnimationsModule {

    public static void register(CallableManager manager) {
        manager.registerObject(Back.class);
        manager.registerObject(Bezier.class);
        manager.registerObject(Blink.class);
        manager.registerObject(Bounce.class);
        manager.registerObject(Breathe.class);
        manager.registerObject(CircX.class);
        manager.registerObject(CircY.class);
        manager.registerObject(Elastic.class);
        manager.registerObject(Expo.class);
        manager.registerObject(Fade.class);
        manager.registerObject(Lerp.class);
        manager.registerObject(Pulse.class);
        manager.registerObject(Q2.class);
        manager.registerObject(Q3.class);
        manager.registerObject(Q4.class);
        manager.registerObject(Q5.class);
        manager.registerObject(Shake.class);
        manager.registerObject(Sine.class);
        manager.registerObject(Slide.class);
        manager.registerObject(Smooth.class);
        manager.registerObject(Spring.class);
        manager.registerObject(Swing.class);
        manager.registerObject(TwoLerp.class);
        manager.registerObject(Wave.class);
    }
}
