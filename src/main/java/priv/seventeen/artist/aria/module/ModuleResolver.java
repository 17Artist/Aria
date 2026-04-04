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

package priv.seventeen.artist.aria.module;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ModuleResolver {
    private final List<Path> searchPaths = new ArrayList<>();

    public ModuleResolver() {
        searchPaths.add(Paths.get("."));
    }

    public void addSearchPath(Path path) { searchPaths.add(path); }

    public Path resolve(String modulePath) {
        // Try with .aria extension
        for (Path base : searchPaths) {
            Path candidate = base.resolve(modulePath + ".aria");
            if (candidate.toFile().exists()) return candidate;
            candidate = base.resolve(modulePath);
            if (candidate.toFile().exists()) return candidate;
        }
        return null;
    }
}
