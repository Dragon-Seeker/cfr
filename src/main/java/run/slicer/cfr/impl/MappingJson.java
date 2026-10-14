package run.slicer.cfr.impl;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Serializes decompiled output plus mappings as JSON. Plain Java on purpose: it runs the same on
 * the JVM (for testing) and under TeaVM, and the JS side only needs one JSON.parse.
 *
 * <pre>{
 *   "com/example/Foo": {
 *     "source": "...",
 *     "methods": [
 *       { "owner": "com/example/Foo$Inner", "name": "run", "descriptor": "()V",
 *         "lines": [[0, 12], [7, 13]], "original": [[0, 40]] | null }
 *     ]
 *   }
 * }</pre>
 * {@code lines} and {@code original} are [offset, line] pairs sorted by offset.
 */
public final class MappingJson {
    private MappingJson() {
    }

    public static String write(Map<String, String> output, Map<String, List<OutputSinkFactoryImpl.MethodMapping>> mappings) {
        final var sb = new StringBuilder(64 * 1024);
        sb.append('{');
        boolean firstClass = true;
        for (final var entry : output.entrySet()) {
            if (!firstClass) sb.append(',');
            firstClass = false;

            string(sb, entry.getKey());
            sb.append(":{\"source\":");
            string(sb, entry.getValue());
            sb.append(",\"methods\":[");

            final var methods = mappings.getOrDefault(entry.getKey(), List.of());
            for (int i = 0; i < methods.size(); i++) {
                final var m = methods.get(i);
                if (i > 0) sb.append(',');
                sb.append("{\"owner\":");
                string(sb, m.owner());
                sb.append(",\"name\":");
                string(sb, m.name());
                sb.append(",\"descriptor\":");
                string(sb, m.descriptor());
                sb.append(",\"lines\":");
                pairs(sb, m.lines());
                sb.append(",\"original\":");
                pairs(sb, m.original());
                sb.append('}');
            }
            sb.append("]}");
        }
        sb.append('}');
        return sb.toString();
    }

    private static void pairs(StringBuilder sb, NavigableMap<Integer, Integer> map) {
        if (map == null) {
            sb.append("null");
            return;
        }
        sb.append('[');
        boolean first = true;
        for (final var e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('[').append(e.getKey().intValue()).append(',').append(e.getValue().intValue()).append(']');
        }
        sb.append(']');
    }

    private static void string(StringBuilder sb, String s) {
        if (s == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append("\\u");
                        final String hex = Integer.toHexString(c);
                        for (int p = hex.length(); p < 4; p++) sb.append('0');
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
