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

import java.util.ArrayList;


public final class RopeString extends IValue<String> {


    private ArrayList<String> segments;

    private int length;

    private String flat;


    public RopeString(String s) {
        this.flat = s;
        this.length = s.length();
        this.segments = null;
    }


    private RopeString(ArrayList<String> segments, int length) {
        this.segments = segments;
        this.length = length;
        this.flat = null;
    }


        public static RopeString concat(RopeString a, RopeString b) {
        ArrayList<String> segs = new ArrayList<>();
        a.collectSegments(segs);
        b.collectSegments(segs);
        return new RopeString(segs, a.length + b.length);
    }

        public static RopeString concat(RopeString a, String b) {
        ArrayList<String> segs = new ArrayList<>();
        a.collectSegments(segs);
        segs.add(b);
        return new RopeString(segs, a.length + b.length());
    }

        public void append(String s) {
        if (segments == null) {
            segments = new ArrayList<>();
            if (flat != null) segments.add(flat);
        } else {
        }
        segments.add(s);
        length += s.length();
        flat = null;
    }

    private void collectSegments(ArrayList<String> target) {
        if (flat != null) {
            target.add(flat);
        } else if (segments != null) {
            target.addAll(segments);
        }
    }

        private String flatten() {
        if (flat != null) return flat;
        if (segments == null || segments.isEmpty()) {
            flat = "";
            return flat;
        }
        if (segments.size() == 1) {
            flat = segments.get(0);
        } else {
            StringBuilder sb = new StringBuilder(length);
            for (String s : segments) sb.append(s);
            flat = sb.toString();
        }
        // 释放片段列表，帮助 GC
        segments = null;
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
