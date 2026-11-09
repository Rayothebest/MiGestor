package com.gestor.repository;

import com.gestor.config.Database;
import java.sql.*;

public class AuthRepository {
    public record UserSession(long userId, long businessId, String userName, String businessName,
                              String passwordHash, String role, boolean active) {}

    public UserSession findByEmail(String email) throws SQLException {
        String sql = "SELECT u.id,u.business_id,u.name,b.name business_name,u.password_hash,u.role,u.active " +
                "FROM users u JOIN businesses b ON b.id=u.business_id WHERE LOWER(u.email)=LOWER(?)";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new UserSession(rs.getLong("id"), rs.getLong("business_id"), rs.getString("name"),
                        rs.getString("business_name"), rs.getString("password_hash"), rs.getString("role"),
                        rs.getBoolean("active")) : null;
            }
        }
    }

    public UserSession register(String userName, String businessName, String email, String hash) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long businessId;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO businesses(name) VALUES(?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, businessName); ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); businessId = rs.getLong(1); }
                }
                long userId;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO users(business_id,name,email,password_hash,role,active) VALUES(?,?,?,?,'OWNER',TRUE)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, businessId); ps.setString(2, userName); ps.setString(3, email); ps.setString(4, hash); ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); userId = rs.getLong(1); }
                }
                c.commit();
                return new UserSession(userId, businessId, userName, businessName, hash, "OWNER", true);
            } catch (SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        }
    }
}
