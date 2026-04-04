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

package priv.seventeen.artist.aria.staticcompile;

import priv.seventeen.artist.aria.compiler.ir.IRProgram;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AriaPackageReader {
    private final Properties manifest = new Properties();
    private final Map<String, byte[]> modules = new LinkedHashMap<>();
    private final Map<String, byte[]> resources = new LinkedHashMap<>();

    public static AriaPackageReader read(Path input) throws IOException {
        AriaPackageReader reader = new AriaPackageReader();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(input.toFile())))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] data = zis.readAllBytes();
                String name = entry.getName();
                if (name.equals("META-INF/MANIFEST.ARIA")) {
                    reader.manifest.load(new ByteArrayInputStream(data));
                } else if (name.startsWith("modules/")) {
                    reader.modules.put(name.substring("modules/".length()), data);
                } else if (name.startsWith("resources/")) {
                    reader.resources.put(name.substring("resources/".length()), data);
                }
                zis.closeEntry();
            }
        }
        return reader;
    }

    public IRProgram getModule(String path) throws IOException {
        String key = path.endsWith(".aria") ? path : path + ".aria";
        byte[] data = modules.get(key);
        if (data == null) throw new IOException("Module not found: " + path);
        Path temp = Files.createTempFile("aria_", ".aria");
        try {
            Files.write(temp, data);
            return AriaFileReader.read(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public byte[] getResource(String path) { return resources.get(path); }
    public Properties getManifest() { return manifest; }
    public Set<String> getModuleNames() { return modules.keySet(); }
}
