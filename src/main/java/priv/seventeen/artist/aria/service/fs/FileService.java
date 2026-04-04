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

package priv.seventeen.artist.aria.service.fs;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public class FileService {

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("fs", "read", FileService::read);
        manager.registerStaticFunction("fs", "write", FileService::write);
        manager.registerStaticFunction("fs", "exists", FileService::exists);
        manager.registerStaticFunction("fs", "list", FileService::list);
        manager.registerStaticFunction("fs", "mkdir", FileService::mkdir);
        manager.registerStaticFunction("fs", "delete", FileService::delete);
        manager.registerStaticFunction("fs", "copy", FileService::copy);
        manager.registerStaticFunction("fs", "info", FileService::info);
        manager.registerStaticFunction("fs", "append", FileService::append);
        manager.registerStaticFunction("fs", "readLines", FileService::readLines);
    }

    public static IValue<?> read(InvocationData data) throws AriaException {
        try {
            String path = data.get(0).stringValue();
            String content = Files.readString(Path.of(path));
            return new StringValue(content);
        } catch (IOException e) {
            throw new AriaRuntimeException("fs.read error: " + e.getMessage());
        }
    }

    public static IValue<?> write(InvocationData data) throws AriaException {
        try {
            String path = data.get(0).stringValue();
            String content = data.get(1).stringValue();
            Files.writeString(Path.of(path), content);
            return NoneValue.NONE;
        } catch (IOException e) {
            throw new AriaRuntimeException("fs.write error: " + e.getMessage());
        }
    }

    public static IValue<?> exists(InvocationData data) {
        return BooleanValue.of(Files.exists(Path.of(data.get(0).stringValue())));
    }

    public static IValue<?> list(InvocationData data) throws AriaException {
        try {
            Path dir = Path.of(data.get(0).stringValue());
            List<IValue<?>> files = Files.list(dir)
                .map(p -> (IValue<?>) new StringValue(p.getFileName().toString()))
                .collect(Collectors.toList());
            return new ListValue(files);
        } catch (IOException e) {
            throw new AriaRuntimeException("fs.list error: " + e.getMessage());
        }
    }

    public static IValue<?> mkdir(InvocationData data) throws AriaException {
        try {
            Files.createDirectories(Path.of(data.get(0).stringValue()));
            return NoneValue.NONE;
        } catch (IOException e) {
            throw new AriaRuntimeException("fs.mkdir error: " + e.getMessage());
        }
    }

    public static IValue<?> delete(InvocationData data) throws AriaException {
        try {
            Files.deleteIfExists(Path.of(data.get(0).stringValue()));
            return NoneValue.NONE;
        } catch (IOException e) {
            throw new AriaRuntimeException("fs.delete error: " + e.getMessage());
        }
    }

    public static IValue<?> copy(InvocationData data) throws AriaException {
        try {
            Files.copy(Path.of(data.get(0).stringValue()), Path.of(data.get(1).stringValue()), StandardCopyOption.REPLACE_EXISTING);
            return NoneValue.NONE;
        } catch (IOException e) {
            throw new AriaRuntimeException("fs.copy error: " + e.getMessage());
        }
    }

    public static IValue<?> append(InvocationData data) throws AriaException {
        try {
            String path = data.get(0).stringValue();
            String content = data.get(1).stringValue();
            Files.writeString(Path.of(path), content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return NoneValue.NONE;
        } catch (IOException e) {
            throw new AriaRuntimeException("fs.append error: " + e.getMessage());
        }
    }

    public static IValue<?> readLines(InvocationData data) throws AriaException {
        try {
            String path = data.get(0).stringValue();
            List<String> lines = Files.readAllLines(Path.of(path));
            List<IValue<?>> result = new ArrayList<>(lines.size());
            for (String line : lines) {
                result.add(new StringValue(line));
            }
            return new ListValue(result);
        } catch (IOException e) {
            throw new AriaRuntimeException("fs.readLines error: " + e.getMessage());
        }
    }

    public static IValue<?> info(InvocationData data) throws AriaException {
        try {
            Path path = Path.of(data.get(0).stringValue());
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            LinkedHashMap<IValue<?>, IValue<?>> map = new LinkedHashMap<>();
            map.put(new StringValue("size"), new NumberValue(attrs.size()));
            map.put(new StringValue("isDir"), BooleanValue.of(attrs.isDirectory()));
            map.put(new StringValue("modified"), new NumberValue(attrs.lastModifiedTime().toMillis()));
            map.put(new StringValue("created"), new NumberValue(attrs.creationTime().toMillis()));
            return new MapValue(map);
        } catch (IOException e) {
            throw new AriaRuntimeException("fs.info error: " + e.getMessage());
        }
    }
}
