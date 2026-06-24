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

package priv.seventeen.artist.aria.value;

/**
 * 不可变 rope 字符串：内部为二叉树（leaf 持有 flat 字符串，internal 持有 left/right 子树）。
 * concat 是 O(1)（不复制内容），首次 stringValue() 时才 flatten 成扁平 String 并缓存，
 * 后续访问 O(1)。
 *
 * 旧实现使用 ArrayList&lt;String&gt;，每次 concat 都复制片段列表，长链拼接退化为 O(N²) 内存操作。
 */
public final class RopeString extends IValue<String> {

    // leaf：flat != null
    // internal：left != null && right != null
    private String flat;
    private RopeString left;
    private RopeString right;

    private final int length;


    public RopeString(String s) {
        this.flat = s == null ? "" : s;
        this.length = this.flat.length();
    }

    private RopeString(RopeString left, RopeString right) {
        this.left = left;
        this.right = right;
        this.length = left.length + right.length;
    }


        public static RopeString concat(RopeString a, RopeString b) {
        return new RopeString(a, b);
    }

        public static RopeString concat(RopeString a, String b) {
        return new RopeString(a, new RopeString(b));
    }

    private void appendTo(StringBuilder sb) {
        if (flat != null) {
            sb.append(flat);
        } else {
            left.appendTo(sb);
            right.appendTo(sb);
        }
    }

        private String flatten() {
        if (flat != null) return flat;
        StringBuilder sb = new StringBuilder(length);
        appendTo(sb);
        flat = sb.toString();
        // 释放子树，帮助 GC，并使后续访问走 leaf 快速路径
        left = null;
        right = null;
        return flat;
    }


    @Override public String jvmValue() { return stringValue(); }
    @Override public double numberValue() { return 0; }
    @Override public String stringValue() { return flat != null ? flat : flatten(); }
    @Override public boolean booleanValue() { return length > 0; }
    @Override public int typeID() { return 3; }
    @Override public boolean canMath() { return false; }
    @Override public boolean isBaseType() { return true; }

    public int length() { return length; }


    @Override
    protected IValue<?> addValue(IValue<?> other) {
        if (other instanceof RopeString rs) return concat(this, rs);
        return concat(this, other.stringValue());
    }

    @Override
    protected IValue<?> subValue(IValue<?> other) {
        return new StringValue(stringValue(), true).sub(other);
    }


    @Override public String toString() { return stringValue(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof RopeString rs) return stringValue().equals(rs.stringValue());
        if (o instanceof StringValue sv) return stringValue().equals(sv.stringValue());
        if (o instanceof MutableStringValue ms) return stringValue().equals(ms.stringValue());
        return false;
    }

    @Override
    public int hashCode() { return stringValue().hashCode(); }
}
