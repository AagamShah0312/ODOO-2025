package com.skillswap.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    public static Object parse(String text) {
        return new Parser(text == null ? "" : text).parseValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        Object parsed = parse(text);
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    public static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public static int num(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> stringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item).trim());
                }
            }
        } else if (value instanceof String s) {
            for (String part : s.split(",")) {
                if (!part.isBlank()) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(out, s);
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(out, item);
            }
            out.append(']');
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private static final int MAX_DEPTH = 8;
        private static final int MAX_STRING = 8000;
        private final String text;
        private int i;
        private int depth;

        Parser(String text) {
            this.text = text.length() > 80_000 ? text.substring(0, 80_000) : text;
        }

        Object parseValue() {
            skip();
            if (i >= text.length()) {
                return null;
            }
            char c = text.charAt(i);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f') {
                return parseBoolean();
            }
            if (c == 'n') {
                parseLiteral("null");
                return null;
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            enter();
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skip();
            if (peek('}')) {
                i++;
                leave();
                return map;
            }
            while (true) {
                skip();
                String key = parseString();
                skip();
                expect(':');
                map.put(key, parseValue());
                skip();
                if (peek('}')) {
                    i++;
                    break;
                }
                expect(',');
            }
            leave();
            return map;
        }

        private List<Object> parseArray() {
            enter();
            List<Object> list = new ArrayList<>();
            expect('[');
            skip();
            if (peek(']')) {
                i++;
                leave();
                return list;
            }
            while (true) {
                list.add(parseValue());
                skip();
                if (peek(']')) {
                    i++;
                    break;
                }
                expect(',');
            }
            leave();
            return list;
        }

        private void enter() {
            depth++;
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("JSON too deep");
            }
        }

        private void leave() {
            depth--;
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (i < text.length()) {
                char c = text.charAt(i++);
                if (c == '"') {
                    return out.toString();
                }
                if (out.length() >= MAX_STRING) {
                    throw new IllegalArgumentException("String too long");
                }
                if (c == '\\' && i < text.length()) {
                    char n = text.charAt(i++);
                    out.append(switch (n) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case 'u' -> parseHex();
                        default -> n;
                    });
                } else {
                    out.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private char parseHex() {
            if (i + 4 > text.length()) {
                return '?';
            }
            String hex = text.substring(i, i + 4);
            i += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                return '?';
            }
        }

        private Object parseNumber() {
            int start = i;
            if (peek('-')) {
                i++;
            }
            while (i < text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                i++;
            }
            String raw = text.substring(start, i);
            if (raw.isEmpty() || "-".equals(raw) || ".".equals(raw) || "-.".equals(raw)) {
                throw new IllegalArgumentException("Invalid number");
            }
            try {
                if (raw.contains(".")) {
                    return Double.parseDouble(raw);
                }
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number");
            }
        }

        private Boolean parseBoolean() {
            if (text.startsWith("true", i)) {
                i += 4;
                return true;
            }
            parseLiteral("false");
            return false;
        }

        private void parseLiteral(String literal) {
            if (!text.startsWith(literal, i)) {
                throw new IllegalArgumentException("Expected " + literal);
            }
            i += literal.length();
        }

        private void expect(char c) {
            skip();
            if (i >= text.length() || text.charAt(i) != c) {
                throw new IllegalArgumentException("Expected " + c);
            }
            i++;
        }

        private boolean peek(char c) {
            return i < text.length() && text.charAt(i) == c;
        }

        private void skip() {
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
        }
    }
}
