package com.gestor.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormulaServiceTest {
    private final FormulaService formulas = new FormulaService();

    @Test
    void evaluatesBasicOperationsWithExpectedPrecedence() {
        assertDecimal("14", formulas.evaluate("2 + 3 * 4", ignored -> null));
        assertDecimal("20", formulas.evaluate("(2 + 3) * 4", ignored -> null));
        assertDecimal("-7", formulas.evaluate("-3 * 2 - 1", ignored -> null));
        assertDecimal("3.75", formulas.evaluate("7,5 / 2", ignored -> null));
    }

    @Test
    void resolvesColumnReferencesAndTreatsMissingValuesAsZero() {
        Map<Long, BigDecimal> values = Map.of(
                10L, new BigDecimal("12.50"),
                20L, new BigDecimal("4")
        );

        assertDecimal("50", formulas.evaluate("{10} * {20} + {999}", values::get));
    }

    @Test
    void extractsUniqueReferencedColumnIds() {
        assertEquals(Set.of(7L, 25L), formulas.referencedColumns("{7} * ({25} + {7})"));
        assertEquals(Set.of(), formulas.referencedColumns(null));
    }

    @Test
    void rejectsDivisionByZero() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> formulas.evaluate("15 / (3 - 3)", ignored -> null)
        );

        assertTrue(error.getMessage().contains("dividir entre cero"));
    }

    @Test
    void rejectsInvalidFormulaSyntax() {
        assertThrows(IllegalArgumentException.class,
                () -> formulas.evaluate("2 +", ignored -> null));
        assertThrows(IllegalArgumentException.class,
                () -> formulas.evaluate("(2 + 3", ignored -> null));
        assertThrows(IllegalArgumentException.class,
                () -> formulas.evaluate("{abc} + 1", ignored -> null));
        assertThrows(IllegalArgumentException.class,
                () -> formulas.referencedColumns("{12"));
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "Se esperaba " + expected + " pero se obtuvo " + actual.toPlainString());
    }
}
