package com.gestor.repository;

import com.gestor.config.Database;
import com.gestor.model.ModulePermission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermissionRepository {
    public enum Capability { VIEW, CREATE, EDIT, DELETE, MANAGE }

    public record UserAccess(String role, boolean active) {}

    public boolean can(long userId, long businessId, long moduleId, Capability capability) throws SQLException {
        String sql = """
                SELECT u.role,u.active,p.can_view,p.can_create,p.can_edit,p.can_delete,p.can_manage
                FROM users u
                LEFT JOIN module_permissions p
                  ON p.user_id=u.id AND p.business_id=u.business_id AND p.module_id=?
                WHERE u.id=? AND u.business_id=?
                """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, moduleId);
            statement.setLong(2, userId);
            statement.setLong(3, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !rows.getBoolean("active")) return false;
                String role = rows.getString("role");
                if ("OWNER".equals(role) || "MANAGER".equals(role)) return true;
                return switch (capability) {
                    case VIEW -> rows.getBoolean("can_view");
                    case CREATE -> rows.getBoolean("can_create");
                    case EDIT -> rows.getBoolean("can_edit");
                    case DELETE -> rows.getBoolean("can_delete");
                    case MANAGE -> rows.getBoolean("can_manage");
                };
            }
        }
    }

    public void require(long userId, long businessId, long moduleId, Capability capability) throws SQLException {
        if (!can(userId, businessId, moduleId, capability)) {
            throw new SecurityException("No tienes permiso para realizar esta operación.");
        }
    }

    public boolean isOwner(long userId, long businessId) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT role,active FROM users WHERE id=? AND business_id=?")) {
            statement.setLong(1, userId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean("active") && "OWNER".equals(rows.getString("role"));
            }
        }
    }

    public List<Long> visibleModuleIds(long userId, long businessId) throws SQLException {
        String sql = """
                SELECT m.id
                FROM custom_modules m
                JOIN users u ON u.id=? AND u.business_id=m.business_id AND u.active=TRUE
                LEFT JOIN module_permissions p
                  ON p.business_id=m.business_id AND p.module_id=m.id AND p.user_id=u.id
                WHERE m.business_id=?
                  AND (u.role IN ('OWNER','MANAGER') OR p.can_view=TRUE)
                ORDER BY m.created_at,m.id
                """;
        List<Long> ids = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) ids.add(rows.getLong(1));
            }
        }
        return ids;
    }

    public Map<Long, Map<Long, ModulePermission>> matrix(long businessId) throws SQLException {
        Map<Long, Map<Long, ModulePermission>> matrix = new HashMap<>();
        String sql = "SELECT id,business_id,user_id,module_id,can_view,can_create,can_edit,can_delete,can_manage " +
                "FROM module_permissions WHERE business_id=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ModulePermission permission = new ModulePermission(
                            rows.getLong("id"), rows.getLong("business_id"), rows.getLong("user_id"),
                            rows.getLong("module_id"), rows.getBoolean("can_view"),
                            rows.getBoolean("can_create"), rows.getBoolean("can_edit"),
                            rows.getBoolean("can_delete"), rows.getBoolean("can_manage")
                    );
                    matrix.computeIfAbsent(permission.userId(), ignored -> new HashMap<>())
                            .put(permission.moduleId(), permission);
                }
            }
        }
        return matrix;
    }

    public void save(long businessId, long userId, long moduleId, boolean view, boolean create,
                     boolean edit, boolean delete, boolean manage) throws SQLException {
        String sql = """
                INSERT INTO module_permissions
                    (business_id,user_id,module_id,can_view,can_create,can_edit,can_delete,can_manage)
                SELECT ?,u.id,m.id,?,?,?,?,?
                FROM users u JOIN custom_modules m ON m.business_id=u.business_id
                WHERE u.id=? AND m.id=? AND u.business_id=?
                ON DUPLICATE KEY UPDATE can_view=VALUES(can_view),can_create=VALUES(can_create),
                    can_edit=VALUES(can_edit),can_delete=VALUES(can_delete),can_manage=VALUES(can_manage)
                """;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            statement.setBoolean(2, view);
            statement.setBoolean(3, create);
            statement.setBoolean(4, edit);
            statement.setBoolean(5, delete);
            statement.setBoolean(6, manage);
            statement.setLong(7, userId);
            statement.setLong(8, moduleId);
            statement.setLong(9, businessId);
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("Usuario o módulo no válido.");
        }
    }
}
