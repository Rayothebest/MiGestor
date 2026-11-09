package com.gestor.repository;

import com.gestor.config.Database;
import com.gestor.model.BusinessUser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UserRepository {
    private static final Set<String> ROLES = Set.of("OWNER", "MANAGER", "EMPLOYEE");

    public List<BusinessUser> list(long businessId) throws SQLException {
        List<BusinessUser> users = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id,business_id,name,email,role,active,created_at FROM users WHERE business_id=? ORDER BY created_at,id")) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) users.add(new BusinessUser(
                        rows.getLong("id"), rows.getLong("business_id"), rows.getString("name"),
                        rows.getString("email"), rows.getString("role"), rows.getBoolean("active"),
                        rows.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        }
        return users;
    }

    public long create(long businessId, String name, String email, String passwordHash, String role) throws SQLException {
        validateRole(role);
        String sql = "INSERT INTO users(business_id,name,email,password_hash,role,active) VALUES(?,?,?,?,?,TRUE)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, businessId);
            statement.setString(2, name);
            statement.setString(3, email);
            statement.setString(4, passwordHash);
            statement.setString(5, role);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No fue posible crear el usuario.");
                return keys.getLong(1);
            }
        }
    }

    public void update(long businessId, long userId, String name, String role, boolean active,
                       long currentUserId) throws SQLException {
        update(businessId, userId, name, null, role, active, currentUserId);
    }

    public void update(long businessId, long userId, String name, String email, String role, boolean active,
                       long currentUserId) throws SQLException {
        validateRole(role);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String currentRole;
                boolean currentActive;
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT role,active FROM users WHERE id=? AND business_id=? FOR UPDATE")) {
                    lock.setLong(1, userId);
                    lock.setLong(2, businessId);
                    try (ResultSet row = lock.executeQuery()) {
                        if (!row.next()) throw new IllegalArgumentException("El usuario no existe.");
                        currentRole = row.getString("role");
                        currentActive = row.getBoolean("active");
                    }
                }
                boolean removingOwner = "OWNER".equals(currentRole) && (!"OWNER".equals(role) || !active);
                if (removingOwner && countActiveOwners(connection, businessId) <= 1) {
                    throw new IllegalArgumentException("El negocio debe conservar al menos un propietario activo.");
                }
                if (userId == currentUserId && currentActive && !active) {
                    throw new IllegalArgumentException("No puedes desactivar tu propia cuenta.");
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE users SET name=?,email=COALESCE(?,email),role=?,active=? WHERE id=? AND business_id=?")) {
                    update.setString(1, name);
                    update.setString(2, email == null || email.isBlank() ? null : email.trim());
                    update.setString(3, role);
                    update.setBoolean(4, active);
                    update.setLong(5, userId);
                    update.setLong(6, businessId);
                    update.executeUpdate();
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

    private int countActiveOwners(Connection connection, long businessId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE business_id=? AND role='OWNER' AND active=TRUE")) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private void validateRole(String role) {
        if (!ROLES.contains(role)) throw new IllegalArgumentException("Rol de usuario no válido.");
    }
}
