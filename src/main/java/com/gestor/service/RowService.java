package com.gestor.service;

import com.gestor.config.Database;
import com.gestor.model.ModuleRow;
import com.gestor.repository.AuditRepository;
import com.gestor.repository.GridRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

public class RowService {
    private final GridRepository grid = new GridRepository();
    private final AuditRepository audit = new AuditRepository();
    private final WorkflowService workflows = new WorkflowService();

    public long create(long moduleId, long businessId, long userId, Long sourcePanelId,
                       Map<Long, String> values) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long rowId = grid.saveRow(connection, 0, moduleId, businessId, values);
                audit.log(connection, businessId, userId, moduleId, rowId, "ROW_CREATED", "ROW", rowId,
                        sourcePanelId == null ? "Fila creada." : "Registro creado desde el Panel #" + sourcePanelId + ".",
                        null, snapshot(values));
                workflows.onRowCreated(connection, businessId, userId, moduleId, rowId);
                connection.commit();
                return rowId;
            } catch (Exception e) {
                connection.rollback();
                rethrow(e);
                return -1;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public void update(long rowId, long moduleId, long businessId, long userId,
                       Map<Long, String> values) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ModuleRow before = grid.rowForUpdate(connection, rowId, moduleId, businessId);
                grid.saveRow(connection, rowId, moduleId, businessId, values);
                audit.log(connection, businessId, userId, moduleId, rowId, "ROW_UPDATED", "ROW", rowId,
                        "Fila actualizada.", snapshot(before.cells()), snapshot(values));
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                rethrow(e);
            } finally { connection.setAutoCommit(true); }
        }
    }

    public boolean delete(long rowId, long moduleId, long businessId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ModuleRow before = grid.rowForUpdate(connection, rowId, moduleId, businessId);
                audit.log(connection, businessId, userId, moduleId, rowId, "ROW_DELETED", "ROW", rowId,
                        "Fila eliminada manualmente.", snapshot(before.cells()), null);
                boolean deleted = grid.deleteRow(connection, rowId, moduleId, businessId);
                connection.commit();
                return deleted;
            } catch (Exception e) {
                connection.rollback();
                rethrow(e);
                return false;
            } finally { connection.setAutoCommit(true); }
        }
    }

    static String snapshot(Map<Long, String> values) {
        if (values == null) return null;
        StringBuilder output = new StringBuilder();
        new TreeMap<>(values).forEach((key, value) -> {
            if (!output.isEmpty()) output.append(" | ");
            output.append(key).append('=').append(value == null ? "" : value.replace("|", "\\|"));
        });
        return output.toString();
    }

    private void rethrow(Exception e) throws SQLException {
        if (e instanceof SQLException sql) throw sql;
        if (e instanceof RuntimeException runtime) throw runtime;
        throw new SQLException(e);
    }
}
