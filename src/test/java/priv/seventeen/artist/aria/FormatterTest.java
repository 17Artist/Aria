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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FormatterTest {

    private final AriaFormatter formatter = new AriaFormatter();

    //  缩进规范化

    @Test
    void testIndentationNormalization() {
        String input = """
            if (true) {
            var.x = 1
            }""";
        String result = formatter.format(input);
        // 验证 { 后的内容被缩进
        assertTrue(result.contains("    var.x"), "Body should be indented with 4 spaces");
    }

    //  空行规范化

    @Test
    void testBlankLineNormalization() {
        String input = "var.x = 1\n\n\n\n\nvar.y = 2\n";
        String result = formatter.format(input);
        // 连续空行应被压缩为最多一个
        assertFalse(result.contains("\n\n\n"), "Multiple blank lines should be collapsed");
    }

    //  运算符间距

    @Test
    void testOperatorSpacing() {
        String input = "var.x=1+2\n";
        String result = formatter.format(input);
        // = 两侧应有空格
        assertTrue(result.contains("= "), "Assignment operator should have space after");
    }

    //  空输入

    @Test
    void testEmptyInput() {
        String result = formatter.format("");
        assertEquals("", result);
    }

    //  已格式化代码不变

    @Test
    void testAlreadyFormattedCodeUnchanged() {
        String input = "var.x = 1\nreturn var.x\n";
        String result = formatter.format(input);
        assertEquals(input, result);
    }
}
