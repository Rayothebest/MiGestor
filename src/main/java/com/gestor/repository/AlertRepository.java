package com.gestor.repository;

import com.gestor.config.Database;
import com.gestor.model.AlertRule;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Persistence for alert rules. Every query is explicitly scoped by business_id. */
public class AlertRepository {
    private static final Set<String> RULE_TYPES = Set.of("NUMBER", "DATE", "VALUE");
    private static final Set<String> NUMBER_OPERATORS = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE");
    private static final Set<String> DATE_OPERATORS = Set.of("EQ", "NE", "BEFORE", "AFTER");
    private static final Set<String> VALUE_OPERATORS = Set.of("EQ", "NE", "CONTAINS");

    public record AlertRuleView(long id, long moduleId, long columnId, String name,
                                String moduleName, String columnName, String dataType,
                                String ruleType, String conditionOperator, String comparisonValue,
                                String messageTemplate, boolean active, long matchCount) {
        public long getId() { return id; }
        public long getModuleId() { return moduleId; }
        public long getColumnId() { return columnId; }
        public String getName() { return name; }
        public String getModuleName() { return moduleName; }
        public String getColumnName() { return columnName; }
        public String getDataType() { return dataType; }
        public String getRuleType() { return ruleType; }
        public String getConditionOperator() { return conditionOperator; }
        public String getComparisonValue() { return comparisonValue; }
        public String getMessageTemplate() { return messageTemplate; }
        public boolean isActive() { return active; }
        public boolean getActive() { return active; }
        public long getMatchCount() { return matchCount; }

        public AlertRuleView withMatchCount(long count) {
            return new AlertRuleView(id, moduleId, columnId, name, moduleName, columnName,
                    dataType, ruleType, conditionOperator, comparisonValue, messageTemplate,
                    active, Math.max(0, count));
        }
    }

    public record AlertOption(long moduleId, long columnId, String moduleName,
                              String columnName, String dataType, String ruleType) {
        public long getModuleId() { return moduleId; }
        public long getColumnId() { return columnId; }
        public String getModuleName() { return moduleName; }
        public String getColumnName() { return columnName; }
        public String getDataType() { return dataType; }
        public String getRuleType() { return ruleType; }
    }

    public List<AlertRuleView> list(long businessId) throws SQLException {
        List<AlertRuleView> rules = new ArrayList<>();
        String sql = """
                SELECT a.id,a.module_id,a.column_id,a.name,m.name AS module_name,
                       c.name AS column_name,c.data_type,a.rule_type,a.condition_operator,
                       a.comparison_value,a.message_template,a.active
                FROM alert_rules a
                JOIN custom_modules m ON m.id=a.module_id AND m.business_id=a.business_id
                JOIN module_columns c ON c.id=a.column_id AND c.module_id=a.module_id
                                     AND c.business_id=a.business_id
                WHERE a.business_id=?
                ORDER BY a.active DESC,a.name,a.id
                """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) rules.add(mapView(rows));
            }
        }
        return rules;
    }

    public List<AlertRuleView> listActive(long businessId) throws SQLException {
        return list(businessId).stream().filter(AlertRuleView::active).toList();
    }

    public AlertRuleView find(long id, long businessId) throws SQLException {
        String sql = """
                SELECT a.id,a.module_id,a.column_id,a.name,m.name AS module_name,
                       c.name AS column_name,c.data_type,a.rule_type,a.condition_operator,
                       a.comparison_value,a.message_template,a.active
                FROM alert_rules a
                JOIN custom_modules m ON m.id=a.module_id AND m.business_id=a.business_id
                JOIN module_columns c ON c.id=a.column_id AND c.module_id=a.module_id
                                     AND c.business_id=a.business_id
                WHERE a.id=? AND a.business_id=?
                """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? mapView(rows) : null;
            }
        }
    }

    public List<AlertOption> options(long businessId) throws SQLException {
        List<AlertOption> options = new ArrayList<>();
        String sql = """
                SELECT m.id AS module_id,c.id AS column_id,m.name AS module_name,
                       c.name AS column_name,c.data_type
                FROM custom_modules m
                JOIN module_columns c ON c.module_id=m.id AND c.business_id=m.business_id
                WHERE m.business_id=? AND m.module_type='TABLE'
                  AND LOWER(TRIM(c.name))<>'id'
                ORDER BY m.name,c.position,c.id
                """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String dataType = rows.getString("data_type");
                    options.add(new AlertOption(rows.getLong("module_id"), rows.getLong("column_id"),
                            rows.getString("module_name"), rows.getString("column_name"), dataType,
                            ruleTypeFor(dataType)));
                }
            }
        }
        return options;
    }

    public List<AlertOption> listOptions(long businessId) throws SQLException {
        return options(businessId);
    }

    public List<AlertRule> listDefinitions(long businessId) throws SQLException {
        List<AlertRule> rules = new ArrayList<>();
        String sql = "SELECT id,business_id,module_id,column_id,name,rule_type,condition_operator," +
                "comparison_value,message_template,active,created_at,updated_at " +
                "FROM alert_rules WHERE business_id=? ORDER BY created_at,id";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) rules.add(mapDefinition(rows));
            }
        }
        return rules;
    }

    public AlertRule findDefinition(long id, long businessId) throws SQLException {
        String sql = "SELECT id,business_id,module_id,column_id,name,rule_type,condition_operator," +
                "comparison_value,message_template,active,created_at,updated_at " +
                "FROM alert_rules WHERE id=? AND business_id=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? mapDefinition(rows) : null;
            }
        }
    }

    public long create(long businessId, String name, long columnId, String conditionOperator,
                       String comparisonValue, String messageTemplate) throws SQLException {
        AlertOption option = findOption(businessId, columnId);
        if (option == null) throw new IllegalArgumentException("La columna seleccionada no pertenece a una tabla de este negocio.");
        return createValidated(businessId, option, cleanName(name), normalize(conditionOperator),
                cleanComparison(comparisonValue), cleanNullable(messageTemplate), true);
    }

    public long create(long businessId, long moduleId, long columnId, String name, String ruleType,
                       String conditionOperator, String comparisonValue, String messageTemplate,
                       boolean active) throws SQLException {
        AlertOption option = findOption(businessId, columnId);
        if (option == null || option.moduleId() != moduleId) {
            throw new IllegalArgumentException("El módulo o la columna de la alerta no son válidos.");
        }
        String normalizedType = normalize(ruleType);
        if (!option.ruleType().equals(normalizedType)) {
            throw new IllegalArgumentException("El tipo de alerta no corresponde con la columna seleccionada.");
        }
        return createValidated(businessId, option, cleanName(name), normalize(conditionOperator),
                cleanComparison(comparisonValue), cleanNullable(messageTemplate), active);
    }

    public boolean toggle(long id, long businessId) throws SQLException {
        return toggleActive(id, businessId);
    }

    public boolean toggleActive(long id, long businessId) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE alert_rules SET active=NOT active WHERE id=? AND business_id=?")) {
            statement.setLong(1, id);
            statement.setLong(2, businessId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean setActive(long id, long businessId, boolean active) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE alert_rules SET active=? WHERE id=? AND business_id=?")) {
            statement.setBoolean(1, active);
            statement.setLong(2, id);
            statement.setLong(3, businessId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean delete(long id, long businessId) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM alert_rules WHERE id=? AND business_id=?")) {
            statement.setLong(1, id);
            statement.setLong(2, businessId);
            return statement.executeUpdate() == 1;
        }
    }

    public static String ruleTypeFor(String dataType) {
        String type = normalize(dataType);
        if ("NUMBER".equals(type) || "FORMULA".equals(type)) return "NUMBER";
        if ("DATE".equals(type)) return "DATE";
        return "VALUE";
    }

    public static void validateCondition(String ruleType, String operator, String comparisonValue) {
        String type = normalize(ruleType);
        String normalizedOperator = normalize(operator);
        if (!RULE_TYPES.contains(type)) throw new IllegalArgumentException("Tipo de alerta no válido.");
        Set<String> allowed = switch (type) {
            case "NUMBER" -> NUMBER_OPERATORS;
            case "DATE" -> DATE_OPERATORS;
            default -> VALUE_OPERATORS;
        };
        if (!allowed.contains(normalizedOperator)) {
            throw new IllegalArgumentException("La condición no es válida para el tipo de columna seleccionado.");
        }
        String comparison = cleanComparison(comparisonValue);
        try {
            if ("NUMBER".equals(type)) new BigDecimal(comparison.replace(',', '.'));
            if ("DATE".equals(type)) LocalDate.parse(comparison);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("El valor de comparación no tiene el formato esperado para " + type + ".");
        }
    }

    private long createValidated(long businessId, AlertOption option, String name, String operator,
                                 String comparison, String message, boolean active) throws SQLException {
        validateCondition(option.ruleType(), operator, comparison);
        String sql = """
                INSERT INTO alert_rules
                    (business_id,module_id,column_id,name,rule_type,condition_operator,
                     comparison_value,message_template,active)
                SELECT m.business_id,m.id,c.id,?,?,?,?,?,?
                FROM custom_modules m
                JOIN module_columns c ON c.module_id=m.id AND c.business_id=m.business_id
                WHERE m.id=? AND c.id=? AND m.business_id=? AND m.module_type='TABLE'
                """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, option.ruleType());
            statement.setString(3, operator);
            statement.setString(4, comparison);
            statement.setString(5, message);
            statement.setBoolean(6, active);
            statement.setLong(7, option.moduleId());
            statement.setLong(8, option.columnId());
            statement.setLong(9, businessId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("La columna seleccionada ya no está disponible.");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No fue posible obtener el ID de la alerta.");
                return keys.getLong(1);
            }
        }
    }

    private AlertOption findOption(long businessId, long columnId) throws SQLException {
        String sql = """
                SELECT m.id AS module_id,c.id AS column_id,m.name AS module_name,
                       c.name AS column_name,c.data_type
                FROM custom_modules m
                JOIN module_columns c ON c.module_id=m.id AND c.business_id=m.business_id
                WHERE c.id=? AND m.business_id=? AND m.module_type='TABLE'
                  AND LOWER(TRIM(c.name))<>'id'
                """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, columnId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                String dataType = rows.getString("data_type");
                return new AlertOption(rows.getLong("module_id"), rows.getLong("column_id"),
                        rows.getString("module_name"), rows.getString("column_name"), dataType,
                        ruleTypeFor(dataType));
            }
        }
    }

    private AlertRuleView mapView(ResultSet rows) throws SQLException {
        return new AlertRuleView(rows.getLong("id"), rows.getLong("module_id"),
                rows.getLong("column_id"), rows.getString("name"), rows.getString("module_name"),
                rows.getString("column_name"), rows.getString("data_type"), rows.getString("rule_type"),
                rows.getString("condition_operator"), rows.getString("comparison_value"),
                rows.getString("message_template"), rows.getBoolean("active"), 0);
    }

    private AlertRule mapDefinition(ResultSet rows) throws SQLException {
        return new AlertRule(rows.getLong("id"), rows.getLong("business_id"), rows.getLong("module_id"),
                rows.getLong("column_id"), rows.getString("name"), rows.getString("rule_type"),
                rows.getString("condition_operator"), rows.getString("comparison_value"),
                rows.getString("message_template"), rows.getBoolean("active"),
                rows.getTimestamp("created_at").toLocalDateTime(),
                rows.getTimestamp("updated_at").toLocalDateTime());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String cleanName(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty() || clean.length() > 120) throw new IllegalArgumentException("Escribe un nombre de alerta válido.");
        return clean;
    }

    private static String cleanComparison(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty() || clean.length() > 255) throw new IllegalArgumentException("Escribe un valor de comparación válido.");
        return clean;
    }

    private static String cleanNullable(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > 255) throw new IllegalArgumentException("El mensaje de la alerta es demasiado largo.");
        return clean;
    }
}
