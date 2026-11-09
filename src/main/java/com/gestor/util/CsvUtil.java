package com.gestor.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public final class CsvUtil {
    private static final int MAX_ROWS = 20_000;
    private static final int MAX_CELL_LENGTH = 100_000;

    private CsvUtil() {}

    public static List<List<String>> parse(Reader source) throws IOException {
        BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean firstCharacter = true;
        int current;

        while ((current = reader.read()) != -1) {
            char ch = (char) current;
            if (firstCharacter && ch == '\uFEFF') {
                firstCharacter = false;
                continue;
            }
            firstCharacter = false;

            if (quoted) {
                if (ch == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') cell.append('"');
                    else {
                        quoted = false;
                        if (next != -1) reader.reset();
                    }
                } else cell.append(ch);
            } else if (ch == '"' && cell.length() == 0) {
                quoted = true;
            } else if (ch == ',') {
                row.add(finishCell(cell));
            } else if (ch == '\n') {
                row.add(finishCell(cell));
                rows.add(row);
                if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("El archivo supera el límite de " + MAX_ROWS + " filas.");
                row = new ArrayList<>();
            } else if (ch != '\r') {
                cell.append(ch);
                if (cell.length() > MAX_CELL_LENGTH) throw new IllegalArgumentException("Una celda del CSV es demasiado extensa.");
            }
        }

        if (quoted) throw new IllegalArgumentException("El CSV termina dentro de un texto entre comillas.");
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(finishCell(cell));
            rows.add(row);
        }
        return rows;
    }

    public static void writeRow(Writer writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(',');
            String value = protectSpreadsheet(values.get(i));
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }

    public static String protectSpreadsheet(String value) {
        if (value == null) return "";
        String leadingTrimmed = value.stripLeading();
        if (!leadingTrimmed.isEmpty() && "=+-@".indexOf(leadingTrimmed.charAt(0)) >= 0) return "'" + value;
        return value;
    }

    private static String finishCell(StringBuilder cell) {
        String value = cell.toString();
        cell.setLength(0);
        return value;
    }
}
