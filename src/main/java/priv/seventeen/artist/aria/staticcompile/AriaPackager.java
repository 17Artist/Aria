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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AriaPackager {
    private final Map<String, byte[]> entries = new LinkedHashMap<>();
    private final Properties manifest = new Properties();

    public void setManifestEntry(String key, String value) {
        manifest.setProperty(key, value);
    }

    public void addModule(String path, IRProgram program) throws IOException {
        Path temp = Files.createTempFile("aria_", ".aria");
        try {
            AriaFileWriter.write(program, temp);
            entries.put("modules/" + path + ".aria", Files.readAllBytes(temp));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public void addResource(String path, byte[] data) {
        entries.put("resources/" + path, data);
    }

    public void writeTo(Path output) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output.toFile())))) {
            // Manifest
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.ARIA"));
            manifest.store(zos, "Aria Package");
            zos.closeEntry();

            // Entries
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
    }
}
