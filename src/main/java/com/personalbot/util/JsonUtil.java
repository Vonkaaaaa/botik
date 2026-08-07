package com.personalbot.util;

import java.util.*;

/**
 * Lightweight pure Java JSON parser and serializer for REST APIs and local data storage.
 */
public class JsonUtil {

    @SuppressWarnings("unchecked")
    public static Object parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        json = json.trim();
        int[] index = new int[]{0};
        return parseValue(json, index);
    }

    private static Object parseValue(String s, int[] idx) {
        skipWhitespace(s, idx);
        if (idx[0] >= s.length()) return null;

        char c = s.charAt(idx[0]);
        if (c == '{') {
            return parseObject(s, idx);
        } else if (c == '[') {
            return parseArray(s, idx);
        } else if (c == '"') {
            return parseString(s, idx);
        } else if (c == 't' || c == 'f') {
            return parseBoolean(s, idx);
        } else if (c == 'n') {
            return parseNull(s, idx);
        } else if (Character.isDigit(c) || c == '-') {
            return parseNumber(s, idx);
        }
        return null;
    }

    private static Map<String, Object> parseObject(String s, int[] idx) {
        Map<String, Object> map = new LinkedHashMap<>();
        idx[0]++; // skip '{'
        skipWhitespace(s, idx);

        if (idx[0] < s.length() && s.charAt(idx[0]) == '}') {
            idx[0]++;
            return map;
        }

        while (idx[0] < s.length()) {
            skipWhitespace(s, idx);
            if (s.charAt(idx[0]) != '"') break;
            String key = parseString(s, idx);
            skipWhitespace(s, idx);

            if (idx[0] < s.length() && s.charAt(idx[0]) == ':') {
                idx[0]++; // skip ':'
            }

            Object val = parseValue(s, idx);
            map.put(key, val);
            skipWhitespace(s, idx);

            if (idx[0] < s.length() && s.charAt(idx[0]) == ',') {
                idx[0]++;
            } else if (idx[0] < s.length() && s.charAt(idx[0]) == '}') {
                idx[0]++;
                break;
            }
        }
        return map;
    }

    private static List<Object> parseArray(String s, int[] idx) {
        List<Object> list = new ArrayList<>();
        idx[0]++; // skip '['
        skipWhitespace(s, idx);

        if (idx[0] < s.length() && s.charAt(idx[0]) == ']') {
            idx[0]++;
            return list;
        }

        while (idx[0] < s.length()) {
            Object val = parseValue(s, idx);
            list.add(val);
            skipWhitespace(s, idx);

            if (idx[0] < s.length() && s.charAt(idx[0]) == ',') {
                idx[0]++;
            } else if (idx[0] < s.length() && s.charAt(idx[0]) == ']') {
                idx[0]++;
                break;
            }
        }
        return list;
    }

    private static String parseString(String s, int[] idx) {
        idx[0]++; // skip opening quote
        StringBuilder sb = new StringBuilder();
        while (idx[0] < s.length()) {
            char c = s.charAt(idx[0]++);
            if (c == '"') {
                return sb.toString();
            } else if (c == '\\' && idx[0] < s.length()) {
                char esc = s.charAt(idx[0]++);
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (idx[0] + 4 <= s.length()) {
                            String hex = s.substring(idx[0], idx[0] + 4);
                            idx[0] += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ignored) {}
                        }
                        break;
                    default: sb.append(esc); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Boolean parseBoolean(String s, int[] idx) {
        if (s.startsWith("true", idx[0])) {
            idx[0] += 4;
            return Boolean.TRUE;
        } else if (s.startsWith("false", idx[0])) {
            idx[0] += 5;
            return Boolean.FALSE;
        }
        return false;
    }

    private static Object parseNull(String s, int[] idx) {
        if (s.startsWith("null", idx[0])) {
            idx[0] += 4;
        }
        return null;
    }

    private static Number parseNumber(String s, int[] idx) {
        int start = idx[0];
        boolean isDouble = false;
        if (s.charAt(idx[0]) == '-') idx[0]++;
        while (idx[0] < s.length()) {
            char c = s.charAt(idx[0]);
            if (Character.isDigit(c)) {
                idx[0]++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                if (c == '.') isDouble = true;
                idx[0]++;
            } else {
                break;
            }
        }
        String numStr = s.substring(start, idx[0]);
        try {
            if (isDouble) {
                return Double.parseDouble(numStr);
            } else {
                return Long.parseLong(numStr);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static void skipWhitespace(String s, int[] idx) {
        while (idx[0] < s.length()) {
            char c = s.charAt(idx[0]);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                idx[0]++;
            } else {
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Object getPath(Object obj, String path) {
        if (obj == null || path == null) return null;
        String[] parts = path.split("\\.");
        Object curr = obj;
        for (String part : parts) {
            if (curr instanceof Map) {
                curr = ((Map<String, Object>) curr).get(part);
            } else {
                return null;
            }
        }
        return curr;
    }

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) {
            return "\"" + escape((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(String.valueOf(entry.getKey()))).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escape(obj.toString()) + "\"";
    }

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
