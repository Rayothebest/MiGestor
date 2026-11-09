package com.gestor.repository;

import com.gestor.config.Database;
import com.gestor.model.CustomModule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ModuleRepository {
    private static final String MODULE_SELECT = """
            SELECT m.id, m.name, m.description, m.module_type, m.panel_action,
                   m.adjustment_column_id, adjustment.name AS adjustment_column_name,
                   m.adjustment_operation, m.allow_negative,
                   linked.id AS linked_id, linked.name AS linked_name
            FROM custom_modules m
            LEFT JOIN custom_modules linked
              ON linked.id = m.linked_module_id
             AND linked.business_id = m.business_id
             AND linked.module_type = 'TABLE'
            LEFT JOIN module_columns adjustment
              ON adjustment.id=m.adjustment_column_id
             AND adjustment.module_id=m.linked_module_id
             AND adjustment.business_id=m.business_id
            """;

    public List<CustomModule> list(long businessId) throws SQLException {
        return listWithFilter(businessId, " ORDER BY m.created_at, m.id");
    }

    public List<CustomModule> listTables(long businessId) throws SQLException {
        return listWithFilter(businessId, " AND m.module_type='TABLE' ORDER BY m.created_at, m.id");
    }

    private List<CustomModule> listWithFilter(long businessId, String suffix) throws SQLException {
        List<CustomModule> result = new ArrayList<>();
        String sql = MODULE_SELECT + " WHERE m.business_id=?" + suffix;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(map(rows));
            }
        }
        return result;
    }

    public CustomModule find(long id, long businessId) throws SQLException {
        String sql = MODULE_SELECT + " WHERE m.id=? AND m.business_id=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? map(rows) : null;
            }
        }
    }

    public long create(long businessId, String name, String description, String moduleType,
                       Long linkedModuleId, String panelAction)
            throws SQLException {
        if (!Set.of("TABLE", "PANEL").contains(moduleType)) {
            throw new IllegalArgumentException("Selecciona si el módulo será una Tabla o un Panel de registro.");
        }

        if ("TABLE".equals(moduleType)) {
            String sql = "INSERT INTO custom_modules" +
                    "(business_id,name,description,module_type,linked_module_id,panel_action) " +
                    "VALUES(?,?,?,'TABLE',NULL,NULL)";
            try (Connection connection = Database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, businessId);
                statement.setString(2, name);
                statement.setString(3, description);
                statement.executeUpdate();
                return generatedId(statement);
            }
        }

        if (linkedModuleId == null || linkedModuleId <= 0) {
            throw new IllegalArgumentException("Selecciona la tabla en la que se guardarán los registros del Panel.");
        }
        if (!Set.of("REGISTER", "REMOVE", "ADJUST").contains(panelAction)) {
            throw new IllegalArgumentException("Selecciona una operación válida para el Panel.");
        }

        String sql = """
                INSERT INTO custom_modules
                    (business_id,name,description,module_type,linked_module_id,panel_action)
                SELECT ?,?,?,'PANEL',source.id,?
                FROM custom_modules source
                WHERE source.id=? AND source.business_id=? AND source.module_type='TABLE'
                """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, businessId);
            statement.setString(2, name);
            statement.setString(3, description);
            statement.setString(4, panelAction);
            statement.setLong(5, linkedModuleId);
            statement.setLong(6, businessId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("La tabla seleccionada no existe o no pertenece a este negocio.");
            }
            return generatedId(statement);
        }
    }

    public boolean update(long id, long businessId, String name, String description) throws SQLException {
        String sql = "UPDATE custom_modules SET name=?,description=? WHERE id=? AND business_id=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, description);
            statement.setLong(3, id);
            statement.setLong(4, businessId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean updatePanelSettings(long id, long businessId, long linkedModuleId, String panelAction,
                                       Long adjustmentColumnId, String adjustmentOperation,
                                       boolean allowNegative) throws SQLException {
        if (!Set.of("REGISTER", "REMOVE", "ADJUST").contains(panelAction)) {
            throw new IllegalArgumentException("Operación de Panel no válida.");
        }
        if ("ADJUST".equals(panelAction) && !Set.of("ADD", "SUBTRACT", "SET").contains(adjustmentOperation)) {
            throw new IllegalArgumentException("Selecciona si la existencia se sumará, restará o reemplazará.");
        }
        String sql = """
                UPDATE custom_modules panel
                JOIN custom_modules target
                  ON target.id=? AND target.business_id=panel.business_id AND target.module_type='TABLE'
                LEFT JOIN module_columns quantity
                  ON quantity.id=? AND quantity.module_id=target.id
                 AND quantity.business_id=panel.business_id AND quantity.data_type='NUMBER'
                SET panel.linked_module_id=target.id,panel.panel_action=?,
                    panel.adjustment_column_id=CASE WHEN ?='ADJUST' THEN quantity.id ELSE NULL END,
                    panel.adjustment_operation=CASE WHEN ?='ADJUST' THEN ? ELSE NULL END,
                    panel.allow_negative=CASE WHEN ?='ADJUST' THEN ? ELSE FALSE END
                WHERE panel.id=? AND panel.business_id=? AND panel.module_type='PANEL'
                  AND (?<>'ADJUST' OR quantity.id IS NOT NULL)
                """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, linkedModuleId);
            if (adjustmentColumnId == null) statement.setNull(2, java.sql.Types.BIGINT);
            else statement.setLong(2, adjustmentColumnId);
            statement.setString(3, panelAction);
            statement.setString(4, panelAction);
            statement.setString(5, panelAction);
            statement.setString(6, adjustmentOperation);
            statement.setString(7, panelAction);
            statement.setBoolean(8, allowNegative);
            statement.setLong(9, id);
            statement.setLong(10, businessId);
            statement.setString(11, panelAction);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("La configuración no es válida para la Tabla seleccionada.");
            }
            return true;
        }
    }

    public boolean delete(long id, long businessId) throws SQLException {
        String dependencySql = "SELECT name FROM custom_modules WHERE business_id=? AND linked_module_id=? LIMIT 1";
        try (Connection connection = Database.getConnection();
             PreparedStatement dependency = connection.prepareStatement(dependencySql)) {
            dependency.setLong(1, businessId);
            dependency.setLong(2, id);
            try (ResultSet rows = dependency.executeQuery()) {
                if (rows.next()) {
                    throw new IllegalArgumentException(
                            "No puedes eliminar esta tabla porque el Panel «" + rows.getString("name") +
                                    "» está vinculado a ella. Elimina primero ese Panel."
                    );
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM custom_modules WHERE id=? AND business_id=?")) {
                statement.setLong(1, id);
                statement.setLong(2, businessId);
                return statement.executeUpdate() == 1;
            }
        }
    }

    public int count(long businessId, String table) throws SQLException {
        if (!Set.of("custom_modules", "module_rows").contains(table)) throw new IllegalArgumentException();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE business_id=?")) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new SQLException("No fue posible obtener el ID del módulo.");
            return keys.getLong(1);
        }
    }

    private CustomModule map(ResultSet rows) throws SQLException {
        long linkedId = rows.getLong("linked_id");
        Long nullableLinkedId = rows.wasNull() ? null : linkedId;
        long adjustmentId = rows.getLong("adjustment_column_id");
        Long nullableAdjustmentId = rows.wasNull() ? null : adjustmentId;
        return new CustomModule(
                rows.getLong("id"),
                rows.getString("name"),
                rows.getString("description"),
                rows.getString("module_type"),
                nullableLinkedId,
                rows.getString("linked_name"),
                rows.getString("panel_action"),
                nullableAdjustmentId,
                rows.getString("adjustment_column_name"),
                rows.getString("adjustment_operation"),
                rows.getBoolean("allow_negative")
        );
    }
}
