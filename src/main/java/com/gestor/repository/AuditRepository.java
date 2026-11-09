package com.gestor.repository;

import com.gestor.config.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuditRepository {
    public record HistoryEntry(long id, String userName, String moduleName, Long rowId,
                               String action, String details, LocalDateTime createdAt) {
        public long getId() { return id; }
        public String getUserName() { return userName; }
        public String getModuleName() { return moduleName; }
        public Long getRowId() { return rowId; }
        public String getAction() { return action; }
        public String getDetails() { return details; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    public long log(Connection connection, long businessId, Long userId, Long moduleId, Long rowId,
                    String eventType, String entityType, Long entityId, String summary,
                    String beforeValues, String afterValues) throws SQLException {
        String sql = """
                INSERT INTO audit_events
                    (business_id,user_id,module_id,row_id,event_type,entity_type,entity_id,summary,before_values,after_values)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, businessId);
            setNullableLong(statement, 2, userId);
            setNullableLong(statement, 3, moduleId);
            setNullableLong(statement, 4, rowId);
            statement.setString(5, eventType);
            statement.setString(6, entityType);
            setNullableLong(statement, 7, entityId);
            statement.setString(8, summary);
            statement.setString(9, beforeValues);
            statement.setString(10, afterValues);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No se pudo registrar el historial.");
                return keys.getLong(1);
            }
        }
    }

    public List<HistoryEntry> list(long businessId, int limit) throws SQLException {
        String sql = """
                SELECT a.id,COALESCE(u.name,'Sistema') user_name,COALESCE(m.name,'Elemento eliminado') module_name,
                       a.row_id,a.event_type,a.summary,a.created_at
                FROM audit_events a
                LEFT JOIN users u ON u.id=a.user_id AND u.business_id=a.business_id
                LEFT JOIN custom_modules m ON m.id=a.module_id AND m.business_id=a.business_id
                WHERE a.business_id=? ORDER BY a.created_at DESC,a.id DESC LIMIT ?
                """;
        List<HistoryEntry> events = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            statement.setInt(2, Math.max(1, Math.min(limit, 500)));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long rawRowId = rows.getLong("row_id");
                    events.add(new HistoryEntry(rows.getLong("id"), rows.getString("user_name"),
                            rows.getString("module_name"), rows.wasNull() ? null : rawRowId,
                            rows.getString("event_type"), rows.getString("summary"),
                            rows.getTimestamp("created_at").toLocalDateTime()));
                }
            }
        }
        return events;
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.BIGINT);
        else statement.setLong(index, value);
    }
}
