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

package priv.seventeen.artist.aria.callable.builtin;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.StringValue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

public class CryptoFunctions {

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("crypto", "md5", CryptoFunctions::md5);
        manager.registerStaticFunction("crypto", "sha1", CryptoFunctions::sha1);
        manager.registerStaticFunction("crypto", "sha256", CryptoFunctions::sha256);
        manager.registerStaticFunction("crypto", "sha512", CryptoFunctions::sha512);
        manager.registerStaticFunction("crypto", "base64Encode", CryptoFunctions::base64Encode);
        manager.registerStaticFunction("crypto", "base64Decode", CryptoFunctions::base64Decode);
        manager.registerStaticFunction("crypto", "uuid", d -> new StringValue(UUID.randomUUID().toString()));
        manager.registerStaticFunction("crypto", "hashCode", d -> new NumberValue(d.get(0).stringValue().hashCode()));
    }

    public static IValue<?> md5(InvocationData data) throws AriaException {
        return hash("MD5", data.get(0).stringValue());
    }

    public static IValue<?> sha1(InvocationData data) throws AriaException {
        return hash("SHA-1", data.get(0).stringValue());
    }

    public static IValue<?> sha256(InvocationData data) throws AriaException {
        return hash("SHA-256", data.get(0).stringValue());
    }

    public static IValue<?> sha512(InvocationData data) throws AriaException {
        return hash("SHA-512", data.get(0).stringValue());
    }

    public static IValue<?> base64Encode(InvocationData data) throws AriaException {
        String input = data.get(0).stringValue();
        return new StringValue(Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8)));
    }

    public static IValue<?> base64Decode(InvocationData data) throws AriaException {
        String input = data.get(0).stringValue();
        try {
            return new StringValue(new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AriaRuntimeException("Invalid base64: " + e.getMessage());
        }
    }

    private static IValue<?> hash(String algorithm, String input) throws AriaException {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return new StringValue(hex.toString());
        } catch (Exception e) {
            throw new AriaRuntimeException("Hash error: " + e.getMessage());
        }
    }
}
