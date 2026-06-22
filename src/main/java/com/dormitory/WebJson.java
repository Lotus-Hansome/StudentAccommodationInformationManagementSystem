package com.dormitory;

import java.util.Map;

public final class WebJson {
    private WebJson() {
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escape(value) + "\"";
    }

    public static String property(String name, String value) {
        return quote(name) + ":" + quote(value);
    }

    public static String numberProperty(String name, Number value) {
        return quote(name) + ":" + value;
    }

    public static String booleanProperty(String name, boolean value) {
        return quote(name) + ":" + value;
    }

    public static String departmentCounts(Map<String, Integer> departmentCounts, int totalStudents) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : departmentCounts.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            double ratio = totalStudents == 0 ? 0 : entry.getValue() * 100.0 / totalStudents;
            builder.append('{')
                    .append(property("department", entry.getKey())).append(',')
                    .append(numberProperty("count", entry.getValue())).append(',')
                    .append(numberProperty("ratio", Math.round(ratio * 10) / 10.0))
                    .append('}');
            first = false;
        }
        builder.append(']');
        return builder.toString();
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 32) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }
}
