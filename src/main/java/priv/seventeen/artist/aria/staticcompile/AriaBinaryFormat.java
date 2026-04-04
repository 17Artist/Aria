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

public final class AriaBinaryFormat {
    public static final byte[] MAGIC = { 'A', 'R', 0x00, 0x01 };
    public static final int VERSION = 1;
    
    // 常量池类型标记
    public static final byte CONST_NONE = 0;
    public static final byte CONST_NUMBER = 1;
    public static final byte CONST_BOOLEAN = 2;
    public static final byte CONST_STRING = 3;
    
    // 标志位
    public static final short FLAG_HAS_SOURCE_MAP = 0x01;
    public static final short FLAG_HAS_CLASSES = 0x02;
    
    private AriaBinaryFormat() {}
}
