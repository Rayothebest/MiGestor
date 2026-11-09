package com.gestor.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/** Evaluador aritmético deliberadamente pequeño: números, {columnId}, +, -, *, / y paréntesis. */
public final class FormulaService {
    private static final MathContext MATH = MathContext.DECIMAL128;

    public BigDecimal evaluate(String expression, Function<Long, BigDecimal> valueProvider) {
        if (expression == null || expression.isBlank()) return BigDecimal.ZERO;
        Parser parser = new Parser(expression, valueProvider);
        BigDecimal result = parser.expression();
        parser.skipSpaces();
        if (!parser.finished()) throw parser.error("Símbolo inesperado");
        return result.stripTrailingZeros();
    }

    public Set<Long> referencedColumns(String expression) {
        Set<Long> result = new HashSet<>();
        if (expression == null) return result;
        for (int i = 0; i < expression.length(); i++) {
            if (expression.charAt(i) != '{') continue;
            int end = expression.indexOf('}', i + 1);
            if (end < 0) throw new IllegalArgumentException("Falta cerrar una referencia de columna con }.");
            String id = expression.substring(i + 1, end).trim();
            try {
                result.add(Long.parseLong(id));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("La referencia {" + id + "} no contiene un ID de columna válido.");
            }
            i = end;
        }
        return result;
    }

    private static final class Parser {
        private final String input;
        private final Function<Long, BigDecimal> values;
        private int index;

        private Parser(String input, Function<Long, BigDecimal> values) {
            this.input = input;
            this.values = values;
        }

        private BigDecimal expression() {
            BigDecimal value = term();
            while (true) {
                skipSpaces();
                if (take('+')) value = value.add(term(), MATH);
                else if (take('-')) value = value.subtract(term(), MATH);
                else return value;
            }
        }

        private BigDecimal term() {
            BigDecimal value = factor();
            while (true) {
                skipSpaces();
                if (take('*')) value = value.multiply(factor(), MATH);
                else if (take('/')) {
                    BigDecimal divisor = factor();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) throw error("No se puede dividir entre cero");
                    value = value.divide(divisor, MATH);
                } else return value;
            }
        }

        private BigDecimal factor() {
            skipSpaces();
            if (take('+')) return factor();
            if (take('-')) return factor().negate(MATH);
            if (take('(')) {
                BigDecimal value = expression();
                skipSpaces();
                if (!take(')')) throw error("Falta cerrar el paréntesis");
                return value;
            }
            if (take('{')) {
                int start = index;
                while (!finished() && Character.isDigit(input.charAt(index))) index++;
                if (start == index || !take('}')) throw error("Referencia de columna inválida; usa {ID}");
                long columnId;
                try {
                    columnId = Long.parseLong(input.substring(start, index - 1));
                } catch (NumberFormatException e) {
                    throw error("ID de columna inválido");
                }
                BigDecimal value = values.apply(columnId);
                return value == null ? BigDecimal.ZERO : value;
            }
            return number();
        }

        private BigDecimal number() {
            skipSpaces();
            int start = index;
            boolean decimal = false;
            while (!finished()) {
                char current = input.charAt(index);
                if (Character.isDigit(current)) index++;
                else if ((current == '.' || current == ',') && !decimal) {
                    decimal = true;
                    index++;
                } else break;
            }
            if (start == index) throw error("Se esperaba un número, una referencia {ID} o un paréntesis");
            try {
                return new BigDecimal(input.substring(start, index).replace(',', '.'), MATH);
            } catch (NumberFormatException e) {
                throw error("Número inválido");
            }
        }

        private void skipSpaces() {
            while (!finished() && Character.isWhitespace(input.charAt(index))) index++;
        }

        private boolean take(char expected) {
            if (!finished() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private boolean finished() { return index >= input.length(); }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " en la posición " + (index + 1) + ".");
        }
    }
}
