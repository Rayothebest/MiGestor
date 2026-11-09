package com.gestor.service;

import com.gestor.config.Database;
import com.gestor.repository.AlertRepository;
import com.gestor.repository.AlertRepository.AlertRuleView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Periodic in-app alerts. A matching rule/row has one notification until it stops matching. */
public class ScheduledAlertService {
    private static final Logger LOG = Logger.getLogger(ScheduledAlertService.class.getName());
    private static final Set<Integer> INTERVALS = Set.of(0, 15, 60, 1440);
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final AlertRepository rules = new AlertRepository();
    private final AlertService evaluator = new AlertService();

    public record Schedule(long ruleId, int intervalMinutes, LocalDateTime lastRunAt) {
        public long getRuleId() { return ruleId; }
        public int getIntervalMinutes() { return intervalMinutes; }
        public LocalDateTime getLastRunAt() { return lastRunAt; }
        public String getLastRunText() { return lastRunAt == null ? "Pendiente" : DISPLAY_TIME.format(lastRunAt); }
    }

    public record Notification(long id, long ruleId, long moduleId, long rowId,
                               String ruleName, String moduleName, String message,
                               String currentValue, LocalDateTime firstSeenAt,
                               LocalDateTime lastSeenAt, LocalDateTime acknowledgedAt) {
        public long getId() { return id; }
        public long getRuleId() { return ruleId; }
        public long getModuleId() { return moduleId; }
        public long getRowId() { return rowId; }
        public String getRuleName() { return ruleName; }
        public String getModuleName() { return moduleName; }
        public String getMessage() { return message; }
        public String getCurrentValue() { return currentValue; }
        public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
        public LocalDateTime getLastSeenAt() { return lastSeenAt; }
        public String getFirstSeenText() { return DISPLAY_TIME.format(firstSeenAt); }
        public String getLastSeenText() { return DISPLAY_TIME.format(lastSeenAt); }
        public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
        public boolean isAcknowledged() { return acknowledgedAt != null; }
    }

    public Map<Long, Schedule> schedules(long businessId) throws SQLException {
        Map<Long, Schedule> result = new HashMap<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT s.rule_id,s.interval_minutes,s.last_run_at FROM alert_schedules s
                     JOIN alert_rules r ON r.id=s.rule_id AND r.business_id=s.business_id
                     WHERE s.business_id=?
                     """)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Schedule schedule = new Schedule(rows.getLong("rule_id"), rows.getInt("interval_minutes"),
                            rows.getObject("last_run_at", LocalDateTime.class));
                    result.put(schedule.ruleId(), schedule);
                }
            }
        }
        return Map.copyOf(result);
    }

    public List<Notification> notifications(long businessId, Set<Long> visibleModuleIds) throws SQLException {
        if (visibleModuleIds == null || visibleModuleIds.isEmpty()) return List.of();
        List<Notification> result = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT n.id,n.rule_id,n.module_id,n.row_id,r.name AS rule_name,
                            m.name AS module_name,n.message,n.current_value,n.first_seen_at,
                            n.last_seen_at,n.acknowledged_at
                     FROM alert_notifications n
                     JOIN alert_rules r ON r.id=n.rule_id AND r.business_id=n.business_id
                     JOIN custom_modules m ON m.id=n.module_id AND m.business_id=n.business_id
                     WHERE n.business_id=? AND n.resolved_at IS NULL
                     ORDER BY n.acknowledged_at IS NULL DESC,n.last_seen_at DESC,n.id DESC
                     """)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long moduleId = rows.getLong("module_id");
                    if (!visibleModuleIds.contains(moduleId)) continue;
                    result.add(new Notification(rows.getLong("id"), rows.getLong("rule_id"), moduleId,
                            rows.getLong("row_id"), rows.getString("rule_name"), rows.getString("module_name"),
                            rows.getString("message"), rows.getString("current_value"),
                            rows.getObject("first_seen_at", LocalDateTime.class),
                            rows.getObject("last_seen_at", LocalDateTime.class),
                            rows.getObject("acknowledged_at", LocalDateTime.class)));
                    if (result.size() == 200) break;
                }
            }
        }
        return result;
    }

    /** Zero disables the schedule and resolves its generated notifications. */
    public void setSchedule(long businessId, long ruleId, int intervalMinutes) throws SQLException {
        if (!INTERVALS.contains(intervalMinutes)) throw new IllegalArgumentException("Elige una frecuencia válida.");
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireRule(connection, businessId, ruleId);
                if (intervalMinutes == 0) {
                    try (PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM alert_schedules WHERE rule_id=? AND business_id=?")) {
                        delete.setLong(1, ruleId);
                        delete.setLong(2, businessId);
                        delete.executeUpdate();
                    }
                    resolveNotifications(connection, businessId, ruleId);
                } else {
                    try (PreparedStatement upsert = connection.prepareStatement("""
                            INSERT INTO alert_schedules (rule_id,business_id,interval_minutes,last_run_at)
                            VALUES (?,?,?,NULL)
                            ON DUPLICATE KEY UPDATE interval_minutes=VALUES(interval_minutes),last_run_at=NULL
                            """)) {
                        upsert.setLong(1, ruleId);
                        upsert.setLong(2, businessId);
                        upsert.setInt(3, intervalMinutes);
                        upsert.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    public void runNow(long businessId, long ruleId) throws SQLException {
        AlertRuleView rule = rules.find(ruleId, businessId);
        if (rule == null) throw new IllegalArgumentException("La regla no pertenece al negocio.");
        if (!rule.active()) throw new IllegalArgumentException("Activa la regla antes de comprobarla.");
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE alert_schedules SET last_run_at=NOW()
                     WHERE rule_id=? AND business_id=?
                     """)) {
            statement.setLong(1, ruleId);
            statement.setLong(2, businessId);
            if (statement.executeUpdate() == 0 && !schedules(businessId).containsKey(ruleId)) {
                throw new IllegalArgumentException("Configura primero la frecuencia de esta regla.");
            }
        }
        evaluateAndPersist(businessId, rule);
    }

    public void resolveRule(long businessId, long ruleId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            resolveNotifications(connection, businessId, ruleId);
        }
    }

    public boolean acknowledge(long businessId, long notificationId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE alert_notifications n
                     JOIN users u ON u.id=? AND u.business_id=n.business_id AND u.active=TRUE
                       AND u.role IN ('OWNER','MANAGER')
                     SET n.acknowledged_at=NOW(),n.acknowledged_by=u.id
                     WHERE n.id=? AND n.business_id=? AND n.resolved_at IS NULL
                     """)) {
            statement.setLong(1, userId);
            statement.setLong(2, notificationId);
            statement.setLong(3, businessId);
            return statement.executeUpdate() == 1;
        }
    }

    /** Run by the Tomcat background thread. Atomic claims avoid duplicate scans after redeploy. */
    public int scanDue() throws SQLException {
        List<long[]> due = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT s.business_id,s.rule_id FROM alert_schedules s
                     JOIN alert_rules r ON r.id=s.rule_id AND r.business_id=s.business_id
                     WHERE r.active=TRUE AND (s.last_run_at IS NULL OR
                           TIMESTAMPDIFF(MINUTE,s.last_run_at,NOW())>=s.interval_minutes)
                     ORDER BY s.last_run_at,s.rule_id LIMIT 100
                     """)) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) due.add(new long[]{rows.getLong(1), rows.getLong(2)});
            }
        }
        int completed = 0;
        for (long[] candidate : due) {
            try {
                if (!claim(candidate[0], candidate[1])) continue;
                AlertRuleView rule = rules.find(candidate[1], candidate[0]);
                if (rule == null || !rule.active()) continue;
                evaluateAndPersist(candidate[0], rule);
                completed++;
            } catch (SQLException | RuntimeException error) {
                LOG.log(Level.WARNING, "Falló la revisión programada de una alerta", error);
            }
        }
        return completed;
    }

    private boolean claim(long businessId, long ruleId) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE alert_schedules s
                     JOIN alert_rules r ON r.id=s.rule_id AND r.business_id=s.business_id AND r.active=TRUE
                     SET s.last_run_at=NOW()
                     WHERE s.business_id=? AND s.rule_id=? AND
                           (s.last_run_at IS NULL OR
                            TIMESTAMPDIFF(MINUTE,s.last_run_at,NOW())>=s.interval_minutes)
                     """)) {
            statement.setLong(1, businessId);
            statement.setLong(2, ruleId);
            return statement.executeUpdate() == 1;
        }
    }

    private void evaluateAndPersist(long businessId, AlertRuleView rule) throws SQLException {
        List<AlertService.AlertMatch> matches = evaluator.evaluate(businessId, rule);
        String token = UUID.randomUUID().toString();
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!activeRule(connection, businessId, rule.id())) {
                    resolveNotifications(connection, businessId, rule.id());
                    connection.commit();
                    return;
                }
                try (PreparedStatement upsert = connection.prepareStatement("""
                        INSERT INTO alert_notifications
                            (business_id,rule_id,module_id,row_id,message,current_value,scan_token)
                        VALUES (?,?,?,?,?,?,?)
                        ON DUPLICATE KEY UPDATE message=VALUES(message),current_value=VALUES(current_value),
                          scan_token=VALUES(scan_token),
                          first_seen_at=IF(resolved_at IS NULL,first_seen_at,NOW()),
                          acknowledged_at=IF(resolved_at IS NULL,acknowledged_at,NULL),
                          acknowledged_by=IF(resolved_at IS NULL,acknowledged_by,NULL),
                          resolved_at=NULL,last_seen_at=NOW()
                        """)) {
                    for (AlertService.AlertMatch match : matches) {
                        upsert.setLong(1, businessId);
                        upsert.setLong(2, rule.id());
                        upsert.setLong(3, match.moduleId());
                        upsert.setLong(4, match.rowId());
                        upsert.setString(5, compact(match.message(), 500));
                        upsert.setString(6, compact(match.currentValue(), 255));
                        upsert.setString(7, token);
                        upsert.addBatch();
                    }
                    if (!matches.isEmpty()) upsert.executeBatch();
                }
                try (PreparedStatement resolve = connection.prepareStatement("""
                        UPDATE alert_notifications SET resolved_at=NOW()
                        WHERE business_id=? AND rule_id=? AND resolved_at IS NULL AND scan_token<>?
                        """)) {
                    resolve.setLong(1, businessId);
                    resolve.setLong(2, rule.id());
                    resolve.setString(3, token);
                    resolve.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private boolean activeRule(Connection connection, long businessId, long ruleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT active FROM alert_rules WHERE id=? AND business_id=? FOR UPDATE")) {
            statement.setLong(1, ruleId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        }
    }

    private void requireRule(Connection connection, long businessId, long ruleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM alert_rules WHERE id=? AND business_id=?")) {
            statement.setLong(1, ruleId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("La regla no pertenece al negocio.");
            }
        }
    }

    private void resolveNotifications(Connection connection, long businessId, long ruleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE alert_notifications SET resolved_at=NOW()
                WHERE business_id=? AND rule_id=? AND resolved_at IS NULL
                """)) {
            statement.setLong(1, businessId);
            statement.setLong(2, ruleId);
            statement.executeUpdate();
        }
    }

    private String compact(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
