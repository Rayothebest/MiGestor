package com.gestor.service;

import com.gestor.config.Database;
import com.gestor.repository.AlertRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, requires migration 010. Creates and removes only disposable businesses. */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class ScheduledAlertsAndChartsIntegrationTest {
    private final ScheduledAlertService scheduled = new ScheduledAlertService();
    private final List<Long> disposableBusinesses = new ArrayList<>();

    @AfterEach
    void cleanup() throws Exception {
        for (long businessId : disposableBusinesses) execute("DELETE FROM businesses WHERE id=?", businessId);
    }

    @Test
    void scheduledNoticeIsDeduplicatedAcknowledgedResolvedAndScoped() throws Exception {
        long businessId = business();
        long otherBusiness = business();
        long ownerId = user(businessId, "OWNER");
        long employeeId = user(businessId, "EMPLOYEE");
        long tableId = table(businessId);
        long stockColumn = column(businessId, tableId, "Existencias", "NUMBER");
        long rowId = row(businessId, tableId);
        long otherTable = table(otherBusiness);
        value(businessId, rowId, stockColumn, "3");
        long ruleId = new AlertRepository().create(businessId, "Pocas existencias", stockColumn,
                "LTE", "5", "Quedan {value} unidades en la fila {rowId}.");

        assertThrows(IllegalArgumentException.class, () -> scheduled.setSchedule(otherBusiness, ruleId, 15));
        assertThrows(IllegalArgumentException.class, () -> scheduled.setSchedule(businessId, ruleId, 7));
        scheduled.setSchedule(businessId, ruleId, 15);
        assertEquals(15, scheduled.schedules(businessId).get(ruleId).intervalMinutes());
        assertTrue(scheduled.scanDue() >= 1);
        var notices = scheduled.notifications(businessId, Set.of(tableId));
        assertEquals(1, notices.size());
        assertEquals(rowId, notices.get(0).rowId());
        assertTrue(notices.get(0).message().contains("3 unidades"));
        assertTrue(Math.abs(Duration.between(notices.get(0).lastSeenAt(), LocalDateTime.now()).toMinutes()) < 5,
                "El aviso debe mostrar la hora local guardada por MariaDB.");
        assertTrue(scheduled.notifications(businessId, Set.of(otherTable)).isEmpty());
        assertTrue(scheduled.notifications(otherBusiness, Set.of(otherTable)).isEmpty());
        assertFalse(scheduled.acknowledge(otherBusiness, notices.get(0).id(), ownerId));
        assertFalse(scheduled.acknowledge(businessId, notices.get(0).id(), employeeId));
        assertTrue(scheduled.acknowledge(businessId, notices.get(0).id(), ownerId));

        scheduled.runNow(businessId, ruleId);
        notices = scheduled.notifications(businessId, Set.of(tableId));
        assertEquals(1, notices.size());
        assertTrue(notices.get(0).isAcknowledged());
        assertEquals(1, scalar("SELECT COUNT(*) FROM alert_notifications WHERE business_id=?", businessId));

        execute("UPDATE cell_values SET value_text='8' WHERE business_id=? AND row_id=? AND column_id=?",
                businessId, rowId, stockColumn);
        scheduled.runNow(businessId, ruleId);
        assertTrue(scheduled.notifications(businessId, Set.of(tableId)).isEmpty());

        execute("UPDATE cell_values SET value_text='2' WHERE business_id=? AND row_id=? AND column_id=?",
                businessId, rowId, stockColumn);
        scheduled.runNow(businessId, ruleId);
        notices = scheduled.notifications(businessId, Set.of(tableId));
        assertEquals(1, notices.size());
        assertFalse(notices.get(0).isAcknowledged());
        assertEquals(1, scalar("SELECT COUNT(*) FROM alert_notifications WHERE business_id=?", businessId));

        scheduled.setSchedule(businessId, ruleId, 0);
        assertTrue(scheduled.schedules(businessId).isEmpty());
        assertTrue(scheduled.notifications(businessId, Set.of(tableId)).isEmpty());
    }

    @Test
    void savedChartCalculationsUseSelectedColumnsAndBusinessRows() throws Exception {
        long businessId = business();
        long otherBusiness = business();
        long tableId = table(businessId);
        long categoryColumn = column(businessId, tableId, "Categoría", "TEXT");
        long amountColumn = column(businessId, tableId, "Monto", "NUMBER");
        dataRow(businessId, tableId, categoryColumn, amountColumn, "Bebidas", "10");
        dataRow(businessId, tableId, categoryColumn, amountColumn, "Bebidas", "20");
        dataRow(businessId, tableId, categoryColumn, amountColumn, "Comida", "5");
        long otherTable = table(otherBusiness);
        long otherCategory = column(otherBusiness, otherTable, "Categoría", "TEXT");
        long otherAmount = column(otherBusiness, otherTable, "Monto", "NUMBER");
        dataRow(otherBusiness, otherTable, otherCategory, otherAmount, "Bebidas", "1000");

        ReportService reports = new ReportService();
        var sum = reports.chart(tableId, businessId, categoryColumn, amountColumn, "SUM", "BAR");
        assertEquals("BAR", sum.chartType());
        assertEquals("Bebidas", sum.points().get(0).label());
        assertDecimal("30", sum.points().get(0).value());
        assertDecimal("5", sum.points().get(1).value());
        var average = reports.chart(tableId, businessId, categoryColumn, amountColumn, "AVG", "PIE");
        assertEquals("PIE", average.chartType());
        assertDecimal("15", average.points().get(0).value());
        var count = reports.chart(tableId, businessId, categoryColumn, null, "COUNT", "BAR");
        assertDecimal("2", count.points().get(0).value());
        assertThrows(IllegalArgumentException.class,
                () -> reports.chart(tableId, otherBusiness, categoryColumn, amountColumn, "SUM", "BAR"));
    }

    private long business() throws Exception {
        long id = insert("INSERT INTO businesses(name) VALUES(?)", "[ALERT CHART TEST] " + UUID.randomUUID());
        disposableBusinesses.add(id);
        return id;
    }

    private long user(long businessId, String role) throws Exception {
        return insert("INSERT INTO users(business_id,name,email,password_hash,role,active) VALUES(?,?,?,?,?,TRUE)",
                businessId, "Prueba", UUID.randomUUID() + "@test.invalid", "unused", role);
    }

    private long table(long businessId) throws Exception {
        return insert("INSERT INTO custom_modules(business_id,name,module_type) VALUES(?,?,'TABLE')",
                businessId, "Prueba " + UUID.randomUUID());
    }

    private long column(long businessId, long tableId, String name, String type) throws Exception {
        return insert("INSERT INTO module_columns(module_id,business_id,name,data_type) VALUES(?,?,?,?)",
                tableId, businessId, name, type);
    }

    private long row(long businessId, long tableId) throws Exception {
        return insert("INSERT INTO module_rows(module_id,business_id) VALUES(?,?)", tableId, businessId);
    }

    private void value(long businessId, long rowId, long columnId, String text) throws Exception {
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                rowId, columnId, businessId, text);
    }

    private void dataRow(long businessId, long tableId, long categoryColumn, long amountColumn,
                         String category, String amount) throws Exception {
        long rowId = row(businessId, tableId);
        value(businessId, rowId, categoryColumn, category);
        value(businessId, rowId, amountColumn, amount);
    }

    private long insert(String sql, Object... args) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, args);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private void execute(String sql, Object... args) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            statement.executeUpdate();
        }
    }

    private long scalar(String sql, Object... args) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private void bind(PreparedStatement statement, Object... args) throws Exception {
        for (int index = 0; index < args.length; index++) statement.setObject(index + 1, args[index]);
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
