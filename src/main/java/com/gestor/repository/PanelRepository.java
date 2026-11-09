package com.gestor.repository;

import com.gestor.config.Database;
import com.gestor.model.ModuleColumn;
import com.gestor.model.PanelField;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PanelRepository {
    public List<PanelField> fields(long panelId, long businessId, List<ModuleColumn> inherited) throws SQLException {
        Map<Long, PanelField> configured = new HashMap<>();
        String sql = """
                SELECT pf.id,pf.business_id,pf.panel_module_id,pf.column_id,pf.name,pf.data_type,pf.options_text,
                       pf.required_field,pf.default_value,pf.related_module_id,pf.related_display_column_id,
                       pf.visible,pf.position
                FROM panel_fields pf
                JOIN custom_modules p ON p.id=pf.panel_module_id AND p.business_id=pf.business_id
                JOIN module_columns c ON c.id=pf.column_id AND c.business_id=pf.business_id
                WHERE pf.panel_module_id=? AND pf.business_id=? ORDER BY pf.position,pf.id
                """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, panelId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    PanelField field = map(rows);
                    configured.put(field.columnId(), field);
                }
            }
        }

        List<PanelField> result = new ArrayList<>();
        int fallbackPosition = 0;
        for (ModuleColumn column : inherited) {
            if (column.isSystemId() || column.isReadOnly()) continue;
            PanelField field = configured.get(column.id());
            if (field == null) field = new PanelField(0, businessId, panelId, column.id(), column.name(),
                    column.dataType(), column.optionsText(), column.requiredField(), column.defaultValue(),
                    column.relatedModuleId(), column.relatedDisplayColumnId(), true, fallbackPosition);
            result.add(field);
            fallbackPosition++;
        }
        result.sort(Comparator.comparingInt(PanelField::position).thenComparingLong(PanelField::columnId));
        return result;
    }

    public void save(long panelId, long businessId, List<PanelField> fields) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement validate = connection.prepareStatement(
                        "SELECT linked_module_id FROM custom_modules WHERE id=? AND business_id=? AND module_type='PANEL' FOR UPDATE")) {
                    validate.setLong(1, panelId);
                    validate.setLong(2, businessId);
                    try (ResultSet row = validate.executeQuery()) {
                        if (!row.next()) throw new IllegalArgumentException("El Panel no existe.");
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM panel_fields WHERE panel_module_id=? AND business_id=?")) {
                    delete.setLong(1, panelId);
                    delete.setLong(2, businessId);
                    delete.executeUpdate();
                }
                String insertSql = """
                        INSERT INTO panel_fields
                            (business_id,panel_module_id,column_id,name,data_type,options_text,required_field,
                             default_value,related_module_id,related_display_column_id,visible,position)
                        SELECT ?,?,c.id,c.name,c.data_type,c.options_text,?,?,c.related_module_id,
                               c.related_display_column_id,?,?
                        FROM module_columns c JOIN custom_modules p ON p.linked_module_id=c.module_id
                        WHERE p.id=? AND p.business_id=? AND c.id=? AND c.business_id=?
                        """;
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    for (PanelField field : fields) {
                        insert.setLong(1, businessId);
                        insert.setLong(2, panelId);
                        insert.setBoolean(3, field.requiredField());
                        insert.setString(4, field.defaultValue());
                        insert.setBoolean(5, field.visible());
                        insert.setInt(6, field.position());
                        insert.setLong(7, panelId);
                        insert.setLong(8, businessId);
                        insert.setLong(9, field.columnId());
                        insert.setLong(10, businessId);
                        if (insert.executeUpdate() != 1) throw new IllegalArgumentException("Una columna ya no pertenece a la Tabla vinculada.");
                    }
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException sql) throw sql;
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new SQLException(e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private PanelField map(ResultSet rows) throws SQLException {
        return new PanelField(rows.getLong("id"), rows.getLong("business_id"),
                rows.getLong("panel_module_id"), rows.getLong("column_id"), rows.getString("name"),
                rows.getString("data_type"), rows.getString("options_text"), rows.getBoolean("required_field"),
                rows.getString("default_value"), nullableLong(rows, "related_module_id"),
                nullableLong(rows, "related_display_column_id"), rows.getBoolean("visible"), rows.getInt("position"));
    }

    private Long nullableLong(ResultSet rows, String name) throws SQLException {
        long value = rows.getLong(name);
        return rows.wasNull() ? null : value;
    }
}
