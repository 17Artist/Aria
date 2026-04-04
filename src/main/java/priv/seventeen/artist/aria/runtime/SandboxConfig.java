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

package priv.seventeen.artist.aria.runtime;

import java.util.Set;

public class SandboxConfig {
    private final long maxExecutionTimeMs;
    private final int maxCallDepth;
    private final long maxInstructions;
    private final boolean allowFileSystem;
    private final boolean allowNetwork;
    private final boolean allowJavaInterop;
    private final Set<String> allowedNamespaces;  // null = 全部允许

    private SandboxConfig(Builder builder) {
        this.maxExecutionTimeMs = builder.maxExecutionTimeMs;
        this.maxCallDepth = builder.maxCallDepth;
        this.maxInstructions = builder.maxInstructions;
        this.allowFileSystem = builder.allowFileSystem;
        this.allowNetwork = builder.allowNetwork;
        this.allowJavaInterop = builder.allowJavaInterop;
        this.allowedNamespaces = builder.allowedNamespaces;
    }

    public long getMaxExecutionTimeMs() { return maxExecutionTimeMs; }
    public int getMaxCallDepth() { return maxCallDepth; }
    public long getMaxInstructions() { return maxInstructions; }
    public boolean isAllowFileSystem() { return allowFileSystem; }
    public boolean isAllowNetwork() { return allowNetwork; }
    public boolean isAllowJavaInterop() { return allowJavaInterop; }
    public Set<String> getAllowedNamespaces() { return allowedNamespaces; }

    public boolean isNamespaceAllowed(String namespace) {
        if (allowedNamespaces == null) return true;
        // 检查能力限制
        if (!allowFileSystem && "fs".equals(namespace)) return false;
        if (!allowNetwork && ("net".equals(namespace) || "http".equals(namespace))) return false;
        if (!allowJavaInterop && "Java".equals(namespace)) return false;
        return allowedNamespaces.contains(namespace);
    }


    public static final SandboxConfig UNRESTRICTED = builder().build();

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private long maxExecutionTimeMs = 0;       // 0 = 无限制
        private int maxCallDepth = 512;
        private long maxInstructions = 0;           // 0 = 无限制
        private boolean allowFileSystem = true;
        private boolean allowNetwork = true;
        private boolean allowJavaInterop = true;
        private Set<String> allowedNamespaces = null; // null = 全部允许

        public Builder maxExecutionTime(long ms) { this.maxExecutionTimeMs = ms; return this; }
        public Builder maxCallDepth(int depth) { this.maxCallDepth = depth; return this; }
        public Builder maxInstructions(long count) { this.maxInstructions = count; return this; }
        public Builder allowFileSystem(boolean allow) { this.allowFileSystem = allow; return this; }
        public Builder allowNetwork(boolean allow) { this.allowNetwork = allow; return this; }
        public Builder allowJavaInterop(boolean allow) { this.allowJavaInterop = allow; return this; }
        public Builder allowedNamespaces(String... namespaces) {
            this.allowedNamespaces = Set.of(namespaces);
            return this;
        }

        public SandboxConfig build() { return new SandboxConfig(this); }
    }
}
