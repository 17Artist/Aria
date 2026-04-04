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

package priv.seventeen.artist.aria.service.net;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import priv.seventeen.artist.aria.runtime.ThreadPoolManager;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpService {

    private static final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("net", "get", HttpService::httpGet);
        manager.registerStaticFunction("net", "post", HttpService::httpPost);
        manager.registerStaticFunction("net", "put", HttpService::httpPut);
        manager.registerStaticFunction("net", "delete", HttpService::httpDelete);
        manager.registerStaticFunction("net", "request", HttpService::httpRequest);
        manager.registerStaticFunction("net", "asyncGet", HttpService::asyncGet);
        manager.registerStaticFunction("net", "asyncPost", HttpService::asyncPost);
    }

    // net.get(url) 或 net.get(url, headers)
    public static IValue<?> httpGet(InvocationData data) throws AriaException {
        return doRequest("GET", data.get(0).stringValue(),
            data.argCount() > 1 ? data.get(1) : null, null);
    }

    // net.post(url, body) 或 net.post(url, body, headers)
    public static IValue<?> httpPost(InvocationData data) throws AriaException {
        return doRequest("POST", data.get(0).stringValue(),
            data.argCount() > 2 ? data.get(2) : null,
            data.argCount() > 1 ? data.get(1).stringValue() : "");
    }

    // net.put(url, body) 或 net.put(url, body, headers)
    public static IValue<?> httpPut(InvocationData data) throws AriaException {
        return doRequest("PUT", data.get(0).stringValue(),
            data.argCount() > 2 ? data.get(2) : null,
            data.argCount() > 1 ? data.get(1).stringValue() : "");
    }

    // net.delete(url) 或 net.delete(url, headers)
    public static IValue<?> httpDelete(InvocationData data) throws AriaException {
        return doRequest("DELETE", data.get(0).stringValue(),
            data.argCount() > 1 ? data.get(1) : null, null);
    }

    // net.request(options) — 完整控制
    // options = { 'url': '...', 'method': 'POST', 'body': '...', 'headers': {...}, 'timeout': 5000 }
    public static IValue<?> httpRequest(InvocationData data) throws AriaException {
        if (!(data.get(0) instanceof MapValue opts)) {
            throw new AriaRuntimeException("net.request: argument must be a map");
        }
        Map<IValue<?>, IValue<?>> map = opts.jvmValue();
        String url = getMapStr(map, "url", "");
        String method = getMapStr(map, "method", "GET").toUpperCase();
        String body = getMapStr(map, "body", null);
        IValue<?> headers = map.get(new StringValue("headers"));
        return doRequest(method, url, headers, body);
    }

    private static IValue<?> doRequest(String method, String url, IValue<?> headers, String body) throws AriaException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));

            // 设置请求头
            if (headers instanceof MapValue hm) {
                for (Map.Entry<IValue<?>, IValue<?>> entry : hm.jvmValue().entrySet()) {
                    builder.header(entry.getKey().stringValue(), entry.getValue().stringValue());
                }
            }

            // 设置方法和 body
            switch (method) {
                case "GET" -> builder.GET();
                case "DELETE" -> builder.DELETE();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                default -> builder.method(method, body != null ?
                    HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return buildResponse(response);
        } catch (Exception e) {
            throw new AriaRuntimeException("net." + method.toLowerCase() + " error: " + e.getMessage());
        }
    }

    private static MapValue buildResponse(HttpResponse<String> response) {
        LinkedHashMap<IValue<?>, IValue<?>> map = new LinkedHashMap<>();
        map.put(new StringValue("status"), new NumberValue(response.statusCode()));
        map.put(new StringValue("body"), new StringValue(response.body()));

        // 响应头
        LinkedHashMap<IValue<?>, IValue<?>> headerMap = new LinkedHashMap<>();
        response.headers().map().forEach((k, v) ->
            headerMap.put(new StringValue(k), new StringValue(String.join(", ", v))));
        map.put(new StringValue("headers"), new MapValue(headerMap));

        return new MapValue(map);
    }

    private static String getMapStr(Map<IValue<?>, IValue<?>> map, String key, String defaultVal) {
        IValue<?> val = map.get(new StringValue(key));
        return val != null && !(val instanceof NoneValue) ? val.stringValue() : defaultVal;
    }


    // net.asyncGet(url, callback) 或 net.asyncGet(url, headers, callback)
    public static IValue<?> asyncGet(InvocationData data) throws AriaException {
        String url = data.get(0).stringValue();
        FunctionValue callback;
        IValue<?> headers = null;
        if (data.argCount() > 2) {
            headers = data.get(1);
            IValue<?> cbVal = data.get(2);
            if (!(cbVal instanceof FunctionValue)) {
                throw new AriaRuntimeException("Expected function argument");
            }
            callback = (FunctionValue) cbVal;
        } else {
            IValue<?> cbVal = data.get(1);
            if (!(cbVal instanceof FunctionValue)) {
                throw new AriaRuntimeException("Expected function argument");
            }
            callback = (FunctionValue) cbVal;
        }
        final IValue<?> finalHeaders = headers;
        doAsync("GET", url, finalHeaders, null, callback, data.getContext());
        return NoneValue.NONE;
    }

    // net.asyncPost(url, body, callback) 或 net.asyncPost(url, body, headers, callback)
    public static IValue<?> asyncPost(InvocationData data) throws AriaException {
        String url = data.get(0).stringValue();
        String body = data.get(1).stringValue();
        FunctionValue callback;
        IValue<?> headers = null;
        if (data.argCount() > 3) {
            headers = data.get(2);
            IValue<?> cbVal = data.get(3);
            if (!(cbVal instanceof FunctionValue)) {
                throw new AriaRuntimeException("Expected function argument");
            }
            callback = (FunctionValue) cbVal;
        } else {
            IValue<?> cbVal = data.get(2);
            if (!(cbVal instanceof FunctionValue)) {
                throw new AriaRuntimeException("Expected function argument");
            }
            callback = (FunctionValue) cbVal;
        }
        final IValue<?> finalHeaders = headers;
        doAsync("POST", url, finalHeaders, body, callback, data.getContext());
        return NoneValue.NONE;
    }

    private static void doAsync(String method, String url, IValue<?> headers, String body, FunctionValue callback, priv.seventeen.artist.aria.context.Context context) {
        ThreadPoolManager.INSTANCE.submitTask(() -> {
            try {
                IValue<?> response = doRequest(method, url, headers, body);
                callback.getCallable().invoke(new InvocationData(context, null, new IValue<?>[]{ NoneValue.NONE, response }));
            } catch (Exception e) {
                try {
                    callback.getCallable().invoke(new InvocationData(context, null, new IValue<?>[]{ new StringValue(e.getMessage()), NoneValue.NONE }));
                } catch (Exception ignored) {}
            }
        });
    }
}
