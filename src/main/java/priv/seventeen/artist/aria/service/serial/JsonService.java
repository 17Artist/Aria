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

package priv.seventeen.artist.aria.service.serial;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonService {

    public static void register(CallableManager manager) {
        manager.registerStaticFunction("json", "parse", JsonService::parse);
        manager.registerStaticFunction("json", "stringify", JsonService::stringify);
        manager.registerStaticFunction("JSON", "parse", JsonService::parse);
        manager.registerStaticFunction("JSON", "stringify", JsonService::stringify);
    }

    public static IValue<?> parse(InvocationData data) throws AriaException {
        String json = data.get(0).stringValue();
        try {
            return parseValue(json.trim(), new int[]{0});
        } catch (Exception e) {
            throw new AriaRuntimeException("json.parse error: " + e.getMessage());
        }
    }

    public static IValue<?> stringify(InvocationData data) {
        IValue<?> value = data.get(0);
        boolean pretty = data.argCount() > 1 && data.get(1).booleanValue();
        return new StringValue(toJson(value, pretty ? 0 : -1));
    }


    private static IValue<?> parseValue(String json, int[] pos) {
        skipWhitespace(json, pos);
        if (pos[0] >= json.length()) return NoneValue.NONE;
        char c = json.charAt(pos[0]);
        if (c == '"') return parseString(json, pos);
        if (c == '{') return parseObject(json, pos);
        if (c == '[') return parseArray(json, pos);
        if (c == 't' || c == 'f') return parseBoolean(json, pos);
        if (c == 'n') return parseNull(json, pos);
        if (c == '-' || Character.isDigit(c)) return parseNumber(json, pos);
        return NoneValue.NONE;
    }

    private static StringValue parseString(String json, int[] pos) {
        pos[0]++; // skip opening "
        StringBuilder sb = new StringBuilder();
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]);
            if (c == '"') { pos[0]++; break; }
            if (c == '\\') {
                pos[0]++;
                if (pos[0] < json.length()) {
                    char esc = json.charAt(pos[0]);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (pos[0] + 4 < json.length()) {
                                String hex = json.substring(pos[0] + 1, pos[0] + 5);
                                sb.append((char) Integer.parseInt(hex, 16));
                                pos[0] += 4;
                            }
                        }
                        default -> { sb.append('\\'); sb.append(esc); }
                    }
                }
            } else {
                sb.append(c);
            }
            pos[0]++;
        }
        return new StringValue(sb.toString());
    }

    private static MapValue parseObject(String json, int[] pos) {
        pos[0]++; // skip {
        LinkedHashMap<IValue<?>, IValue<?>> map = new LinkedHashMap<>();
        skipWhitespace(json, pos);
        if (pos[0] < json.length() && json.charAt(pos[0]) == '}') { pos[0]++; return new MapValue(map); }
        while (pos[0] < json.length()) {
            skipWhitespace(json, pos);
            StringValue key = parseString(json, pos);
            skipWhitespace(json, pos);
            if (pos[0] < json.length() && json.charAt(pos[0]) == ':') pos[0]++;
            IValue<?> value = parseValue(json, pos);
            map.put(key, value);
            skipWhitespace(json, pos);
            if (pos[0] < json.length() && json.charAt(pos[0]) == ',') { pos[0]++; continue; }
            if (pos[0] < json.length() && json.charAt(pos[0]) == '}') { pos[0]++; break; }
        }
        return new MapValue(map);
    }

    private static ListValue parseArray(String json, int[] pos) {
        pos[0]++; // skip [
        List<IValue<?>> list = new ArrayList<>();
        skipWhitespace(json, pos);
        if (pos[0] < json.length() && json.charAt(pos[0]) == ']') { pos[0]++; return new ListValue(list); }
        while (pos[0] < json.length()) {
            list.add(parseValue(json, pos));
            skipWhitespace(json, pos);
            if (pos[0] < json.length() && json.charAt(pos[0]) == ',') { pos[0]++; continue; }
            if (pos[0] < json.length() && json.charAt(pos[0]) == ']') { pos[0]++; break; }
        }
        return new ListValue(list);
    }

    private static NumberValue parseNumber(String json, int[] pos) {
        int start = pos[0];
        if (json.charAt(pos[0]) == '-') pos[0]++;
        while (pos[0] < json.length() && (Character.isDigit(json.charAt(pos[0])) || json.charAt(pos[0]) == '.' || json.charAt(pos[0]) == 'e' || json.charAt(pos[0]) == 'E' || json.charAt(pos[0]) == '+' || json.charAt(pos[0]) == '-')) {
            if ((json.charAt(pos[0]) == '+' || json.charAt(pos[0]) == '-') && pos[0] > start + 1 && json.charAt(pos[0]-1) != 'e' && json.charAt(pos[0]-1) != 'E') break;
            pos[0]++;
        }
        return new NumberValue(Double.parseDouble(json.substring(start, pos[0])));
    }

    private static BooleanValue parseBoolean(String json, int[] pos) {
        if (json.startsWith("true", pos[0])) { pos[0] += 4; return BooleanValue.TRUE; }
        if (json.startsWith("false", pos[0])) { pos[0] += 5; return BooleanValue.FALSE; }
        return BooleanValue.FALSE;
    }

    private static NoneValue parseNull(String json, int[] pos) {
        if (json.startsWith("null", pos[0])) pos[0] += 4;
        return NoneValue.NONE;
    }

    private static void skipWhitespace(String json, int[] pos) {
        while (pos[0] < json.length() && Character.isWhitespace(json.charAt(pos[0]))) pos[0]++;
    }


    private static String toJson(IValue<?> value, int indent) {
        if (value instanceof NoneValue) return "null";
        if (value instanceof BooleanValue bv) return bv.booleanValue() ? "true" : "false";
        if (value instanceof NumberValue nv) {
            double d = nv.numberValue();
            if (d == (long) d) return Long.toString((long) d);
            return Double.toString(d);
        }
        if (value instanceof StringValue sv) return escapeString(sv.stringValue());
        if (value instanceof ListValue lv) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (IValue<?> item : lv.jvmValue()) {
                if (!first) sb.append(",");
                if (indent >= 0) sb.append("\n").append("  ".repeat(indent + 1));
                sb.append(toJson(item, indent >= 0 ? indent + 1 : -1));
                first = false;
            }
            if (indent >= 0 && !lv.jvmValue().isEmpty()) sb.append("\n").append("  ".repeat(indent));
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof MapValue mv) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<IValue<?>, IValue<?>> entry : mv.jvmValue().entrySet()) {
                if (!first) sb.append(",");
                if (indent >= 0) sb.append("\n").append("  ".repeat(indent + 1));
                sb.append(escapeString(entry.getKey().stringValue())).append(":");
                if (indent >= 0) sb.append(" ");
                sb.append(toJson(entry.getValue(), indent >= 0 ? indent + 1 : -1));
                first = false;
            }
            if (indent >= 0 && !mv.jvmValue().isEmpty()) sb.append("\n").append("  ".repeat(indent));
            sb.append("}");
            return sb.toString();
        }
        return escapeString(value.stringValue());
    }

    private static String escapeString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
