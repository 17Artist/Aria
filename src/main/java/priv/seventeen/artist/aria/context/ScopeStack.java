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

package priv.seventeen.artist.aria.context;

import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.util.HashMap;

@SuppressWarnings("unchecked")
public class ScopeStack {

    private HashMap<VariableKey, VariableReference>[] stack;
    private ScopeStack parent;
    private int top = -1;

    public ScopeStack() {
        this.stack = null;
        this.parent = null;
    }

    public ScopeStack(ScopeStack parent) {
        this.stack = null;
        this.parent = parent;
    }

    public ScopeStack getParent() { return parent; }

    public void push() {
        if (stack == null) {
            stack = new HashMap[8];
        }
        top++;
        if (top >= stack.length) {
            HashMap<VariableKey, VariableReference>[] newStack = new HashMap[stack.length + 64];
            System.arraycopy(stack, 0, newStack, 0, stack.length);
            stack = newStack;
        }
        if (stack[top] == null) {
            stack[top] = new HashMap<>();
        }
    }

    public void pop() {
        if (top >= 0) {
            stack[top].clear();
            top--;
        }
    }

        public HashMap<VariableKey, VariableReference> snapshot() {
        HashMap<VariableKey, VariableReference> snap = new HashMap<>();
        if (stack != null) {
            for (int i = 0; i <= top; i++) {
                if (stack[i] != null) snap.putAll(stack[i]);
            }
        }
        return snap;
    }

        public VariableReference get(VariableKey key) {
        if (stack != null) {
            for (int i = top; i >= 0; i--) {
                if (stack[i] == null) continue;
                VariableReference ref = stack[i].get(key);
                if (ref != null) return ref;
            }
        }
        if (parent != null) {
            VariableReference parentRef = parent.getExisting(key);
            if (parentRef != null) return parentRef;
        }
        if (top < 0) push();
        VariableReference ref = new VariableReference(NoneValue.NONE);
        stack[top].put(key, ref);
        return ref;
    }

        public VariableReference getExisting(VariableKey key) {
        if (stack != null) {
            for (int i = top; i >= 0; i--) {
                if (stack[i] == null) continue;
                VariableReference ref = stack[i].get(key);
                if (ref != null) return ref;
            }
        }
        if (parent != null) {
            return parent.getExisting(key);
        }
        return null;
    }

        public VariableReference getInCurrentLevel(VariableKey key) {
        if (top < 0) push();
        return stack[top].computeIfAbsent(key, k -> new VariableReference(NoneValue.NONE));
    }

        public void importAll(HashMap<VariableKey, VariableReference> snapshot) {
        if (top < 0) push();
        stack[top].putAll(snapshot);
    }

    public int depth() { return top + 1; }
}
