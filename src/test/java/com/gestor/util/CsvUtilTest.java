package com.gestor.util;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvUtilTest {
    @Test
    void parsesUtf8AndRemovesAnInitialBom() throws Exception {
        List<List<String>> rows = CsvUtil.parse(new StringReader(
                "\uFEFFNombre,Ciudad\r\nJosé,México\r\nZoë,Bogotá\r\n"
        ));

        assertEquals(List.of(
                List.of("Nombre", "Ciudad"),
                List.of("José", "México"),
                List.of("Zoë", "Bogotá")
        ), rows);
    }

    @Test
    void parsesCommasEscapedQuotesAndLineBreaksInsideQuotedCells() throws Exception {
        String csv = "Producto,Descripción\n"
                + "\"Café, premium\",\"Dijo \"\"hola\"\"\"\n"
                + "Normal,\"línea 1\nlínea 2\"\n";

        List<List<String>> rows = CsvUtil.parse(new StringReader(csv));

        assertEquals("Café, premium", rows.get(1).get(0));
        assertEquals("Dijo \"hola\"", rows.get(1).get(1));
        assertEquals("línea 1\nlínea 2", rows.get(2).get(1));
    }

    @Test
    void writesRfcStyleQuotedCellsAndCanReadThemBack() throws Exception {
        List<String> original = List.of("Café, premium", "Dijo \"hola\"", "línea 1\nlínea 2");
        StringWriter output = new StringWriter();

        CsvUtil.writeRow(output, original);

        assertEquals("\"Café, premium\",\"Dijo \"\"hola\"\"\",\"línea 1\nlínea 2\"\r\n", output.toString());
        assertEquals(List.of(original), CsvUtil.parse(new StringReader(output.toString())));
    }

    @Test
    void protectsSpreadsheetFormulaPrefixesWhenExporting() throws Exception {
        StringWriter output = new StringWriter();

        CsvUtil.writeRow(output, List.of(
                "=1+1", "+SUM(A1:A2)", "-2+3", "@cmd", "  =oculta", "seguro"
        ));

        assertEquals(
                "\"'=1+1\",\"'+SUM(A1:A2)\",\"'-2+3\",\"'@cmd\",\"'  =oculta\",\"seguro\"\r\n",
                output.toString()
        );
    }

    @Test
    void rejectsFilesBeyondTheRowLimit() {
        String csv = "dato\n".repeat(20_001);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> CsvUtil.parse(new StringReader(csv))
        );

        assertTrue(error.getMessage().contains("20,000") || error.getMessage().contains("20000"));
    }

    @Test
    void rejectsOversizedCellsAndUnterminatedQuotes() {
        assertThrows(IllegalArgumentException.class,
                () -> CsvUtil.parse(new StringReader("x".repeat(100_001))));
        assertThrows(IllegalArgumentException.class,
                () -> CsvUtil.parse(new StringReader("nombre,\"texto sin cierre")));
    }
}
