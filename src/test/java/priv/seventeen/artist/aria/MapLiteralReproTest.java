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
import priv.seventeen.artist.aria.api.AriaCompiledRoutine;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 用户真实失败脚本的最小复现(验收用例)：
 *   itemA = { 'id':i.round(), 'b':itemNames[0], 'icon':icons[0] }
 * NEW_MAP 按 baseReg 起的 2n 连续寄存器取键值对，而键/值是复杂表达式(IndexExpr 等)时
 * 其中间操作数寄存器会挤进窗口——第三个条目被顶出，map 里出现 {itemNames列表:0.0}。
 * 修复：compileMap 快速路径与 compileList/CONCAT 一致，预分配窗口后 MOVE 进入。
 */
public class MapLiteralReproTest {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
    }

    private String eval(String code) throws AriaException {
        return Aria.eval(code, Aria.createContext()).stringValue();
    }

    /** 原始用户脚本形态：for 循环内多行 map 字面量，值含索引表达式。 */
    @Test
    void mapLiteralWithIndexedValuesInLoop() throws AriaException {
        String out = eval("""
                icons=['aa','bb']
                itemNames=['A','B','C']
                out=[]
                for(i in range(0,3)){
                  itemA={
                  'id':i.round(),
                  'b':itemNames[0],
                  'icon':icons[0]
                  }
                  out.add(''+itemA)
                }
                return ''+out
                """);
        assertEquals("[{id:0.0,b:A,icon:aa}, {id:1.0,b:A,icon:aa}, {id:2.0,b:A,icon:aa}, {id:3.0,b:A,icon:aa}]",
                out, "map 字面量中含索引表达式的条目不得挤占后续键值对的寄存器窗口");
    }

    /** 无循环最小形态：第 2 个条目的值是索引表达式，第 3 个条目曾被顶出窗口。 */
    @Test
    void mapLiteralEntryAfterIndexedValue() throws AriaException {
        assertEquals("{id:1.0,b:A,icon:aa}", eval("""
                icons=['aa','bb']
                itemNames=['A','B','C']
                itemA={'id':1,'b':itemNames[0],'icon':icons[0]}
                return ''+itemA
                """));
    }

    /** 键本身是复杂表达式。 */
    @Test
    void mapLiteralComplexKeys() throws AriaException {
        assertEquals("{x:1.0,y:2.0}", eval("""
                lst = ['x','y']
                m = {lst[0]: 1, lst[1]: 2}
                return ''+m
                """));
    }

    /** 嵌套：map 值是 map/list 字面量，内部同样含索引表达式。 */
    @Test
    void mapLiteralNested() throws AriaException {
        assertEquals("{a:[x, {b:y}],c:x}", eval("""
                lst = ['x','y']
                m = {'a': [lst[0], {'b': lst[1]}], 'c': lst[0]}
                return ''+m
                """));
    }

    /** spread 路径(逐条 SET_INDEX)不受窗口影响，回归保护。 */
    @Test
    void mapLiteralSpreadWithIndexedValue() throws AriaException {
        assertEquals("{a:1.0,b:y}", eval("""
                lst = ['x','y']
                base = {'a': 1}
                m = {...base, 'b': lst[1]}
                return ''+m
                """));
    }

    /** JIT 路径与解释器一致(NEW_MAP 在 JIT 白名单内，读同一 IR 窗口)。 */
    @Test
    void mapLiteralJitParity() throws AriaException {
        String code = """
                icons=['aa','bb']
                itemNames=['A','B','C']
                out=[]
                for(i in range(0,3)){
                  itemA={'id':i.round(),'b':itemNames[0],'icon':icons[0]}
                  out.add(''+itemA)
                }
                return ''+out
                """;
        AriaCompiledRoutine r = Aria.compile("mapLiteralJitParity", code);
        IValue<?> first = r.execute(Aria.createContext());
        IValue<?> last = first;
        for (int i = 0; i < 300; i++) last = r.execute(Aria.createContext());
        assertEquals(first.stringValue(), last.stringValue(), "JIT 与解释器输出必须一致");
        assertEquals("[{id:0.0,b:A,icon:aa}, {id:1.0,b:A,icon:aa}, {id:2.0,b:A,icon:aa}, {id:3.0,b:A,icon:aa}]",
                last.stringValue());
    }
}
