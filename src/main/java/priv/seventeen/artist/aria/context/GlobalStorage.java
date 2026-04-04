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

import priv.seventeen.artist.aria.context.listener.ClientVariableListener;
import priv.seventeen.artist.aria.context.listener.ServerVariableListener;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.reference.ClientReference;
import priv.seventeen.artist.aria.value.reference.ServerReference;
import priv.seventeen.artist.aria.value.reference.VariableReference;

import java.util.concurrent.ConcurrentHashMap;

public class GlobalStorage {

    private final ConcurrentHashMap<VariableKey, VariableReference> globalVariables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VariableKey, ClientReference> clientVariables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VariableKey, ServerReference> serverVariables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> metadata = new ConcurrentHashMap<>();

    private ClientVariableListener clientListener;
    private ServerVariableListener serverListener;

    public GlobalStorage() {}

    public GlobalStorage(ClientVariableListener clientListener, ServerVariableListener serverListener) {
        this.clientListener = clientListener;
        this.serverListener = serverListener;
    }

    public VariableReference getGlobalVariable(VariableKey key) {
        return globalVariables.computeIfAbsent(key, k -> new VariableReference(NoneValue.NONE));
    }

    public ClientReference getClientVariable(VariableKey key) {
        return clientVariables.computeIfAbsent(key, k -> new ClientReference(NoneValue.NONE, clientListener));
    }

    public ServerReference getServerVariable(VariableKey key) {
        return serverVariables.computeIfAbsent(key, k -> new ServerReference(NoneValue.NONE, serverListener));
    }

    public void setClientListener(ClientVariableListener listener) { this.clientListener = listener; }
    public void setServerListener(ServerVariableListener listener) { this.serverListener = listener; }


    public void putMeta(String key, Object value) { metadata.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T getMeta(String key) { return (T) metadata.get(key); }
}
