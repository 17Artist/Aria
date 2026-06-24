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
        // 点号映射目录：import a.b.c 解析为 a/b/c（与文档一致）。
        // 同时保留字面候选，兼容 from 'a/b'（斜杠）与名字本身含点的边缘情况。
        String slashed = modulePath.replace('.', '/');
        for (Path base : searchPaths) {
            // 优先按目录映射形式查找
            Path found = tryCandidates(base, slashed);
            if (found != null) return found;
            // 回退字面形式（modulePath 不含点时与上面相同，tryCandidates 已查过则跳过）
            if (!slashed.equals(modulePath)) {
                found = tryCandidates(base, modulePath);
                if (found != null) return found;
            }
        }
        return null;
    }

    // 候选扩展名：.ariac（预编译二进制，优先）→ .aria（源码或二进制）→ 无扩展（原始路径）。
    // 实际是源码还是二进制由 ModuleLoader 按 magic bytes 判定，扩展名只影响查找顺序。
    private static final String[] EXTENSIONS = {".ariac", ".aria", ""};

    /** 对单个基路径依次尝试各候选扩展名。 */
    private Path tryCandidates(Path base, String name) {
        for (String ext : EXTENSIONS) {
            Path candidate = base.resolve(name + ext);
            if (candidate.toFile().exists()) return candidate;
        }
        return null;
    }
}
