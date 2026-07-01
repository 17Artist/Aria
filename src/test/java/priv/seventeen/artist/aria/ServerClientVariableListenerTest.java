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

package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.context.listener.ClientVariableListener;
import priv.seventeen.artist.aria.context.listener.ServerVariableListener;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.StringValue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 server/client 变量监听在补丁后携带 {@link VariableKey}、client 懒加载初值、以及 server 读取回退到
 * 已存储值（宿主推送模型）。对应把 Aria 用作 ArcartX Shimmer 替代引擎所需的"按变量名分发"能力。
 */
public class ServerClientVariableListenerTest {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    /** server 读：onVariableGet 收到正确的 VariableKey，返回非 null 即作为读取值。 */
    @Test
    void serverGetReceivesKeyAndValue() throws AriaException {
        VariableKey[] gotKey = {null};
        ServerVariableListener server = key -> {
            gotKey[0] = key;
            return new NumberValue(20);
        };
        Context ctx = new Context(new GlobalStorage((k, v) -> { }, server));

        IValue<?> result = Aria.eval("return server.player_hp\n", ctx);

        assertNotNull(gotKey[0], "onVariableGet 未被回调");
        assertEquals("player_hp", gotKey[0].getName(), "onVariableGet 收到的变量名不对");
        assertEquals(20.0, result.numberValue(), 1e-9);
    }

    /** server 读：listener 返回 null → 回退到宿主经 forceSetValue 推送的已存储值。 */
    @Test
    void serverGetFallsBackToPushedValue() throws AriaException {
        ServerVariableListener server = key -> null; // 仅通知、不作数据源
        GlobalStorage storage = new GlobalStorage((k, v) -> { }, server);
        // 宿主推送（对应 SPackPlaceholder 异步回包写回）
        storage.getServerVariable(VariableKey.of("mob_count")).forceSetValue(new NumberValue(7));
        Context ctx = new Context(storage);

        IValue<?> result = Aria.eval("return server.mob_count\n", ctx);

        assertEquals(7.0, result.numberValue(), 1e-9);
    }

    /** client 首次访问：onVariableCreate 携带 key 懒加载初值。 */
    @Test
    void clientFirstAccessLazyLoadsInitialValue() throws AriaException {
        VariableKey[] createdKey = {null};
        ClientVariableListener client = new ClientVariableListener() {
            @Override
            public IValue<?> onVariableCreate(VariableKey key) {
                createdKey[0] = key;
                return new StringValue("Steve");
            }

            @Override
            public void onVariableChange(VariableKey key, IValue<?> newValue) { }
        };
        Context ctx = new Context(new GlobalStorage(client, key -> null));

        IValue<?> result = Aria.eval("return client.nickname\n", ctx);

        assertNotNull(createdKey[0], "onVariableCreate 未被回调");
        assertEquals("nickname", createdKey[0].getName());
        assertEquals("Steve", result.stringValue());
    }

    /** client 写：onVariableChange 携带正确的 key 与新值（供持久化）。 */
    @Test
    void clientWriteFiresOnVariableChangeWithKey() throws AriaException {
        VariableKey[] gotKey = {null};
        IValue<?>[] gotVal = {null};
        ClientVariableListener client = (key, value) -> {
            gotKey[0] = key;
            gotVal[0] = value;
        };
        Context ctx = new Context(new GlobalStorage(client, key -> null));

        Aria.eval("client.nickname = 'Alex'\n", ctx);

        assertNotNull(gotKey[0], "onVariableChange 未被回调");
        assertEquals("nickname", gotKey[0].getName());
        assertEquals("Alex", gotVal[0].stringValue());
    }

    /** 无监听器时 server 读取不再 NPE，返回默认存储值（NONE）。 */
    @Test
    void serverGetWithoutListenerDoesNotThrow() throws AriaException {
        Context ctx = new Context(new GlobalStorage()); // 无监听器
        IValue<?> result = Aria.eval("return server.anything\n", ctx);
        assertNotNull(result); // NoneValue.NONE，不抛 NPE
    }
}
