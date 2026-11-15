package com.synexia.m3.codeql;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Strict streaming CSV input and reversible TSV escaping; neither format is parsed with split(','). */
final class Delimited {
    private static final int MAX_FIELD_CHARS = 1_048_576;
    private Delimited() {}

    @FunctionalInterface
    interface RowConsumer {
        void accept(List<String> row) throws IOException;
    }

    static void csv(Reader reader, RowConsumer consumer) throws IOException {
        PushbackReader input = new PushbackReader(reader, 1);
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        boolean fieldStarted = false;
        boolean rowStarted = false;
        int value;
        while ((value = input.read()) != -1) {
            char c = (char) value;
            rowStarted = true;
            if (quoted) {
                if (c == '"') {
                    int next = input.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        closedQuote = true;
                        if (next != -1) input.unread(next);
                    }
                } else {
                    field.append(c);
                }
            } else if (c == ',' || c == '\n' || c == '\r') {
                fields.add(field.toString());
                if (fields.size() > 64) throw new IOException("CSV row exceeds 64 fields");
                field.setLength(0);
                closedQuote = false;
                fieldStarted = false;
                if (c != ',') {
                    if (c == '\r') {
                        int next = input.read();
                        if (next != '\n' && next != -1) input.unread(next);
                    }
                    consumer.accept(List.copyOf(fields));
                    fields.clear();
                    rowStarted = false;
                }
            } else if (closedQuote) {
                throw new IOException("Unexpected text after closing CSV quote");
            } else if (c == '"') {
                if (fieldStarted) throw new IOException("Quote inside unquoted CSV field");
                quoted = true;
                fieldStarted = true;
            } else {
                fieldStarted = true;
                field.append(c);
            }
            if (field.length() > MAX_FIELD_CHARS) throw new IOException("CSV field exceeds size limit");
        }
        if (quoted) throw new IOException("Unterminated quoted CSV field");
        if (rowStarted) {
            fields.add(field.toString());
            if (fields.size() > 64) throw new IOException("CSV row exceeds 64 fields");
            consumer.accept(List.copyOf(fields));
        }
    }

    static String tsv(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    static String untsv(String value) throws IOException {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\') {
                result.append(c);
            } else {
                if (++i == value.length()) throw new IOException("Incomplete TSV escape");
                result.append(switch (value.charAt(i)) {
                    case '\\' -> '\\';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case 'n' -> '\n';
                    default -> throw new IOException("Invalid TSV escape");
                });
            }
        }
        return result.toString();
    }

    static String line(String... values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append('\t');
            result.append(tsv(values[i]));
        }
        return result.append('\n').toString();
    }
}
