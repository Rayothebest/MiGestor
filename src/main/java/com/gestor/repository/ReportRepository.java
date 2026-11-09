package com.gestor.repository;

import com.gestor.config.Database;
import com.gestor.model.ReportDefinition;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** CRUD for reusable report configurations. Report data itself is calculated by ReportService. */
public class ReportRepository {
    private static final Set<String> REPORT_TYPES = Set.of("TABLE", "SUMMARY", "CHART");
    private static final Set<String> AGGREGATES = Set.of("COUNT", "SUM", "AVG", "MIN", "MAX");
    private static final Set<String> CHART_TYPES = Set.of("BAR", "LINE", "PIE");

    public record SavedReportView(long id, long businessId, long moduleId, Long userId,
                                  String name, String moduleName, String reportType,
                                  Long groupByColumnId, String groupByColumnName,
                                  Long valueColumnId, String valueColumnName,
                                  Long dateColumnId, String dateColumnName,
                                  String aggregateFunction, String chartType, String filtersText,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        public long getId() { return id; }
        public long getBusinessId() { return businessId; }
        public long getModuleId() { return moduleId; }
        public Long getUserId() { return userId; }
        public String getName() { return name; }
        public String getModuleName() { return moduleName; }
        public String getReportType() { return reportType; }
        public Long getGroupByColumnId() { return groupByColumnId; }
        public String getGroupByColumnName() { return groupByColumnName; }
        public Long getValueColumnId() { return valueColumnId; }
        public String getValueColumnName() { return valueColumnName; }
        public Long getDateColumnId() { return dateColumnId; }
        public String getDateColumnName() { return dateColumnName; }
        public String getAggregateFunction() { return aggregateFunction; }
        public String getChartType() { return chartType; }
        public String getFiltersText() { return filtersText; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }

    public List<SavedReportView> list(long businessId) throws SQLException {
        return listInternal(businessId, null, false);
    }

    public List<SavedReportView> list(long businessId, long userId) throws SQLException {
        return listInternal(businessId, userId, true);
    }

    public List<SavedReportView> listForUser(long businessId, long userId) throws SQLException {
        return list(businessId, userId);
    }

    public SavedReportView find(long id, long businessId) throws SQLException {
        String sql = viewSelect() + " WHERE r.id=? AND r.business_id=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? mapView(rows) : null;
            }
        }
    }

    public ReportDefinition findDefinition(long id, long businessId) throws SQLException {
        String sql = definitionSelect() + " WHERE id=? AND business_id=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? mapDefinition(rows) : null;
            }
        }
    }

    public List<ReportDefinition> listDefinitions(long businessId) throws SQLException {
        List<ReportDefinition> reports = new ArrayList<>();
        String sql = definitionSelect() + " WHERE business_id=? ORDER BY created_at,id";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) reports.add(mapDefinition(rows));
            }
        }
        return reports;
    }

    public long create(long businessId, long moduleId, Long userId, String name, String reportType,
                       Long groupByColumnId, Long valueColumnId, Long dateColumnId,
                       String aggregateFunction, String chartType, String filtersText) throws SQLException {
        NormalizedReport values = normalize(name, reportType, aggregateFunction, chartType, filtersText);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                validateReferences(connection, businessId, moduleId, userId, groupByColumnId,
                        valueColumnId, dateColumnId, values.aggregateFunction());
                String sql = """
                        INSERT INTO report_definitions
                            (business_id,module_id,user_id,name,report_type,group_by_column_id,
                             value_column_id,date_column_id,aggregate_function,chart_type,filters_text)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?)
                        """;
                long id;
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, businessId);
                    statement.setLong(2, moduleId);
                    setNullableLong(statement, 3, userId);
                    statement.setString(4, values.name());
                    statement.setString(5, values.reportType());
                    setNullableLong(statement, 6, groupByColumnId);
                    setNullableLong(statement, 7, valueColumnId);
                    setNullableLong(statement, 8, dateColumnId);
                    statement.setString(9, values.aggregateFunction());
                    statement.setString(10, values.chartType());
                    statement.setString(11, values.filtersText());
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No fue posible obtener el ID del reporte.");
                        id = keys.getLong(1);
                    }
                }
                connection.commit();
                return id;
            } catch (Exception error) {
                connection.rollback();
                if (error instanceof SQLException sql) throw sql;
                if (error instanceof RuntimeException runtime) throw runtime;
                throw new SQLException(error);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public long create(long businessId, long userId, long moduleId, String name, String reportType,
                       String aggregateFunction, Long valueColumnId, Long groupByColumnId,
                       String chartType) throws SQLException {
        return create(businessId, moduleId, userId, name, reportType, groupByColumnId,
                valueColumnId, null, aggregateFunction, chartType, null);
    }

    public boolean update(long id, long businessId, Long userId, long moduleId, String name,
                          String reportType, Long groupByColumnId, Long valueColumnId,
                          Long dateColumnId, String aggregateFunction, String chartType,
                          String filtersText) throws SQLException {
        NormalizedReport values = normalize(name, reportType, aggregateFunction, chartType, filtersText);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                validateReferences(connection, businessId, moduleId, userId, groupByColumnId,
                        valueColumnId, dateColumnId, values.aggregateFunction());
                String sql = """
                        UPDATE report_definitions
                        SET module_id=?,name=?,report_type=?,group_by_column_id=?,value_column_id=?,
                            date_column_id=?,aggregate_function=?,chart_type=?,filters_text=?
                        WHERE id=? AND business_id=? AND ((user_id=? ) OR (user_id IS NULL AND ? IS NULL))
                        """;
                boolean updated;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, moduleId);
                    statement.setString(2, values.name());
                    statement.setString(3, values.reportType());
                    setNullableLong(statement, 4, groupByColumnId);
                    setNullableLong(statement, 5, valueColumnId);
                    setNullableLong(statement, 6, dateColumnId);
                    statement.setString(7, values.aggregateFunction());
                    statement.setString(8, values.chartType());
                    statement.setString(9, values.filtersText());
                    statement.setLong(10, id);
                    statement.setLong(11, businessId);
                    setNullableLong(statement, 12, userId);
                    setNullableLong(statement, 13, userId);
                    updated = statement.executeUpdate() == 1;
                }
                connection.commit();
                return updated;
            } catch (Exception error) {
                connection.rollback();
                if (error instanceof SQLException sql) throw sql;
                if (error instanceof RuntimeException runtime) throw runtime;
                throw new SQLException(error);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean delete(long id, long businessId) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM report_definitions WHERE id=? AND business_id=?")) {
            statement.setLong(1, id);
            statement.setLong(2, businessId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean delete(long id, long businessId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM report_definitions WHERE id=? AND business_id=? AND user_id=?")) {
            statement.setLong(1, id);
            statement.setLong(2, businessId);
            statement.setLong(3, userId);
            return statement.executeUpdate() == 1;
        }
    }

    public static void validateAggregate(String aggregateFunction) {
        if (!AGGREGATES.contains(upper(aggregateFunction))) {
            throw new IllegalArgumentException("La operación del reporte no es válida.");
        }
    }

    private List<SavedReportView> listInternal(long businessId, Long userId, boolean filterUser) throws SQLException {
        List<SavedReportView> reports = new ArrayList<>();
        String sql = viewSelect() + " WHERE r.business_id=?" +
                (filterUser ? " AND (r.user_id=? OR r.user_id IS NULL)" : "") +
                " ORDER BY r.updated_at DESC,r.id DESC";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            if (filterUser) statement.setLong(2, userId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) reports.add(mapView(rows));
            }
        }
        return reports;
    }

    private void validateReferences(Connection connection, long businessId, long moduleId, Long userId,
                                    Long groupByColumnId, Long valueColumnId, Long dateColumnId,
                                    String aggregateFunction) throws SQLException {
        try (PreparedStatement module = connection.prepareStatement(
                "SELECT id FROM custom_modules WHERE id=? AND business_id=? AND module_type='TABLE'")) {
            module.setLong(1, moduleId);
            module.setLong(2, businessId);
            try (ResultSet rows = module.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("La tabla seleccionada no existe en este negocio.");
            }
        }
        if (userId != null) {
            try (PreparedStatement user = connection.prepareStatement(
                    "SELECT id FROM users WHERE id=? AND business_id=?")) {
                user.setLong(1, userId);
                user.setLong(2, businessId);
                try (ResultSet rows = user.executeQuery()) {
                    if (!rows.next()) throw new IllegalArgumentException("El usuario del reporte no pertenece a este negocio.");
                }
            }
        }
        validateColumn(connection, businessId, moduleId, groupByColumnId, null, "agrupación");
        validateColumn(connection, businessId, moduleId, dateColumnId, Set.of("DATE"), "fecha");
        if (!"COUNT".equals(aggregateFunction) && valueColumnId == null) {
            throw new IllegalArgumentException("Selecciona una columna numérica para realizar ese cálculo.");
        }
        validateColumn(connection, businessId, moduleId, valueColumnId,
                Set.of("NUMBER", "FORMULA"), "valor");
    }

    private void validateColumn(Connection connection, long businessId, long moduleId, Long columnId,
                                Set<String> acceptedTypes, String use) throws SQLException {
        if (columnId == null) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name,data_type FROM module_columns WHERE id=? AND module_id=? AND business_id=?")) {
            statement.setLong(1, columnId);
            statement.setLong(2, moduleId);
            statement.setLong(3, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("La columna de " + use + " no pertenece a la tabla.");
                if ("agrupación".equals(use) && "ID".equalsIgnoreCase(rows.getString("name").trim())) {
                    throw new IllegalArgumentException("El ID automático no puede usarse para agrupar.");
                }
                if (acceptedTypes != null && !acceptedTypes.contains(rows.getString("data_type"))) {
                    throw new IllegalArgumentException("La columna de " + use + " no tiene un tipo compatible.");
                }
            }
        }
    }

    private NormalizedReport normalize(String name, String reportType, String aggregateFunction,
                                       String chartType, String filtersText) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > 120) {
            throw new IllegalArgumentException("Escribe un nombre de reporte válido.");
        }
        String type = upper(reportType);
        if (!REPORT_TYPES.contains(type)) throw new IllegalArgumentException("El tipo de reporte no es válido.");
        String aggregate = upper(aggregateFunction);
        validateAggregate(aggregate);
        String chart = upper(chartType);
        if ("CHART".equals(type)) {
            if (chart.isEmpty()) chart = "BAR";
            if (!CHART_TYPES.contains(chart)) throw new IllegalArgumentException("El tipo de gráfica no es válido.");
        } else {
            chart = null;
        }
        String filters = filtersText == null || filtersText.isBlank() ? null : filtersText.trim();
        if (filters != null && filters.length() > 10_000) {
            throw new IllegalArgumentException("La configuración de filtros es demasiado grande.");
        }
        return new NormalizedReport(cleanName, type, aggregate, chart, filters);
    }

    private String viewSelect() {
        return """
                SELECT r.id,r.business_id,r.module_id,r.user_id,r.name,m.name AS module_name,
                       r.report_type,r.group_by_column_id,g.name AS group_name,
                       r.value_column_id,v.name AS value_name,r.date_column_id,d.name AS date_name,
                       r.aggregate_function,r.chart_type,r.filters_text,r.created_at,r.updated_at
                FROM report_definitions r
                JOIN custom_modules m ON m.id=r.module_id AND m.business_id=r.business_id
                LEFT JOIN module_columns g ON g.id=r.group_by_column_id AND g.module_id=r.module_id
                                          AND g.business_id=r.business_id
                LEFT JOIN module_columns v ON v.id=r.value_column_id AND v.module_id=r.module_id
                                          AND v.business_id=r.business_id
                LEFT JOIN module_columns d ON d.id=r.date_column_id AND d.module_id=r.module_id
                                          AND d.business_id=r.business_id
                """;
    }

    private String definitionSelect() {
        return "SELECT id,business_id,module_id,user_id,name,report_type,group_by_column_id," +
                "value_column_id,date_column_id,aggregate_function,chart_type,filters_text," +
                "created_at,updated_at FROM report_definitions";
    }

    private SavedReportView mapView(ResultSet rows) throws SQLException {
        return new SavedReportView(rows.getLong("id"), rows.getLong("business_id"),
                rows.getLong("module_id"), nullableLong(rows, "user_id"), rows.getString("name"),
                rows.getString("module_name"), rows.getString("report_type"),
                nullableLong(rows, "group_by_column_id"), rows.getString("group_name"),
                nullableLong(rows, "value_column_id"), rows.getString("value_name"),
                nullableLong(rows, "date_column_id"), rows.getString("date_name"),
                rows.getString("aggregate_function"), rows.getString("chart_type"),
                rows.getString("filters_text"), rows.getTimestamp("created_at").toLocalDateTime(),
                rows.getTimestamp("updated_at").toLocalDateTime());
    }

    private ReportDefinition mapDefinition(ResultSet rows) throws SQLException {
        return new ReportDefinition(rows.getLong("id"), rows.getLong("business_id"),
                rows.getLong("module_id"), nullableLong(rows, "user_id"), rows.getString("name"),
                rows.getString("report_type"), nullableLong(rows, "group_by_column_id"),
                nullableLong(rows, "value_column_id"), nullableLong(rows, "date_column_id"),
                rows.getString("aggregate_function"), rows.getString("chart_type"),
                rows.getString("filters_text"), rows.getTimestamp("created_at").toLocalDateTime(),
                rows.getTimestamp("updated_at").toLocalDateTime());
    }

    private static Long nullableLong(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record NormalizedReport(String name, String reportType, String aggregateFunction,
                                    String chartType, String filtersText) {}
}
