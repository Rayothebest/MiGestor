package com.gestor.service;

import com.gestor.config.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in MySQL integration tests; every fixture belongs to a disposable business. */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class CashServiceIntegrationTest {
    private final List<Long> disposableBusinesses = new ArrayList<>();
    private final CashService cash = new CashService();
    private final SalesService sales = new SalesService();

    @Test
    void manualMovementsCloseAndPermissionsRemainScoped() throws Exception {
        try {
            long business = business();
            long other = business();
            long owner = user(business, "OWNER");
            long manager = user(business, "MANAGER");
            long employee = user(business, "EMPLOYEE");
            long outsider = user(other, "OWNER");
            assertTrue(cash.canManage(business, owner));
            assertTrue(cash.canManage(business, manager));
            assertFalse(cash.canManage(business, employee));
            assertFalse(cash.canManage(business, outsider));
            assertThrows(SecurityException.class, () -> cash.summary(business, employee));
            assertThrows(SecurityException.class, () -> cash.movements(business, employee, 20));
            assertThrows(SecurityException.class, () -> cash.closings(business, employee, 20));
            assertThrows(SecurityException.class, () -> cash.open(business, outsider,
                    UUID.randomUUID().toString(), new BigDecimal("100"), ""));

            String openingToken = UUID.randomUUID().toString();
            long sessionId = cash.open(business, owner, openingToken, new BigDecimal("100"), "Inicio");
            assertTrue(sessionId > 0);
            assertThrows(IllegalArgumentException.class, () -> cash.open(business, owner,
                    openingToken, new BigDecimal("100"), "Inicio"));
            assertEquals(1, scalar("SELECT COUNT(*) FROM cash_sessions WHERE business_id=?", business));
            assertEquals(0, scalar("SELECT COUNT(*) FROM cash_sessions WHERE business_id=?", other));

            String incomeToken = UUID.randomUUID().toString();
            cash.createMovement(business, manager, incomeToken, "INCOME", "Aporte", "CASH",
                    new BigDecimal("20"), "Cambio para caja");
            assertThrows(IllegalArgumentException.class, () -> cash.createMovement(business,
                    manager, incomeToken, "INCOME", "Aporte", "CASH", new BigDecimal("20"), ""));
            cash.createMovement(business, owner, UUID.randomUUID().toString(), "EXPENSE",
                    "Papelería", "CASH", new BigDecimal("7.25"), "Compra local");
            cash.createMovement(business, owner, UUID.randomUUID().toString(), "EXPENSE",
                    "Licencia", "CARD", new BigDecimal("5"), "No afecta efectivo");
            assertThrows(SecurityException.class, () -> cash.createMovement(business, employee,
                    UUID.randomUUID().toString(), "EXPENSE", "Prueba", "CASH", BigDecimal.ONE, ""));
            assertThrows(IllegalArgumentException.class, () -> cash.createMovement(business, owner,
                    UUID.randomUUID().toString(), "EXPENSE", "Prueba", "CASH", new BigDecimal("-1"), ""));
            assertEquals(3, scalar("SELECT COUNT(*) FROM cash_movements WHERE business_id=?", business));
            assertEquals(3, scalar("SELECT COUNT(*) FROM cash_movements WHERE business_id=? AND session_id=?",
                    business, sessionId));
            CashService.CashSummary summary = cash.summary(business, owner);
            assertNotNull(summary.activeSession());
            assertMoney("20", summary.manualCashIncome());
            assertMoney("7.25", summary.manualCashExpense());
            assertMoney("112.75", summary.expectedCash());
            assertEquals(3, summary.movementCount());
            assertEquals(3, cash.movements(business, owner, 20).size());

            String closingToken = UUID.randomUUID().toString();
            long closingId = cash.close(business, manager, closingToken, new BigDecimal("111.75"),
                    "Falta una unidad");
            assertEquals(sessionId, closingId);
            assertThrows(IllegalArgumentException.class, () -> cash.close(business, manager,
                    closingToken, new BigDecimal("111.75"), ""));
            assertNull(cash.summary(business, owner).activeSession());
            assertEquals(1, cash.closings(business, owner, 20).size());
            assertMoney("112.75", new BigDecimal(value(
                    "SELECT expected_cash FROM cash_sessions WHERE id=? AND business_id=?", sessionId, business)));
            assertMoney("-1.00", new BigDecimal(value(
                    "SELECT difference_cash FROM cash_sessions WHERE id=? AND business_id=?", sessionId, business)));

            cash.createMovement(business, owner, UUID.randomUUID().toString(), "INCOME", "Fuera",
                    "CASH", BigDecimal.ONE, "Sin turno");
            assertEquals(1, scalar("SELECT COUNT(*) FROM cash_movements WHERE business_id=? AND session_id IS NULL",
                    business));
            assertEquals(1, cash.summary(business, owner).outsideSessionCount());
        } finally {
            cleanup();
        }
    }

    @Test
    void salesAndReturnsPostToTheSameSessionWithoutDoubleCounting() throws Exception {
        try {
            long business = business();
            long owner = user(business, "OWNER");
            long employee = user(business, "EMPLOYEE");
            long module = productModule(business);
            long label = column(business, module, "Producto", "TEXT", 0);
            long stock = column(business, module, "Existencias", "NUMBER", 1);
            long price = column(business, module, "Precio", "NUMBER", 2);
            long row = product(business, module, label, stock, price, "Agua", "10", "5");
            sales.configure(business, owner, module, label, stock, price);
            execute("INSERT INTO module_permissions(business_id,user_id,module_id,can_view,can_edit) VALUES(?,?,?,TRUE,TRUE)",
                    business, employee, module);
            long session = cash.open(business, owner, UUID.randomUUID().toString(),
                    new BigDecimal("50"), "Turno");

            String cashSaleToken = UUID.randomUUID().toString();
            long cashSale = sales.create(business, employee, cashSaleToken, "CASH", "",
                    List.of(new SalesService.SaleRequestLine(row, new BigDecimal("2"), new BigDecimal("5"))));
            assertThrows(IllegalArgumentException.class, () -> sales.create(business, employee,
                    cashSaleToken, "CASH", "", List.of(new SalesService.SaleRequestLine(row,
                            new BigDecimal("2"), new BigDecimal("5")))));
            long cardSale = sales.create(business, owner, UUID.randomUUID().toString(), "CARD", "",
                    List.of(new SalesService.SaleRequestLine(row, BigDecimal.ONE, new BigDecimal("5"))));
            long cashItem = sales.detail(business, owner, cashSale).items().get(0).id();
            long cardItem = sales.detail(business, owner, cardSale).items().get(0).id();
            String refundToken = UUID.randomUUID().toString();
            sales.createReturn(business, owner, cashSale, refundToken, "CASH", "",
                    List.of(new SalesService.ReturnRequestLine(cashItem, BigDecimal.ONE)));
            assertThrows(IllegalArgumentException.class, () -> sales.createReturn(business, owner,
                    cashSale, refundToken, "CASH", "",
                    List.of(new SalesService.ReturnRequestLine(cashItem, BigDecimal.ONE))));
            sales.createReturn(business, owner, cardSale, UUID.randomUUID().toString(), "CARD", "",
                    List.of(new SalesService.ReturnRequestLine(cardItem, BigDecimal.ONE)));

            CashService.CashSummary summary = cash.summary(business, owner);
            assertMoney("10", summary.cashSales());
            assertMoney("5", summary.cashReturns());
            assertMoney("5", summary.nonCashSales());
            assertMoney("5", summary.nonCashReturns());
            assertMoney("55", summary.expectedCash());
            assertEquals(4, summary.movementCount());
            assertEquals(4, scalar("SELECT COUNT(*) FROM cash_movements WHERE business_id=? AND session_id=?",
                    business, session));
            assertThrows(SecurityException.class, () -> cash.summary(business, employee));

            cash.close(business, owner, UUID.randomUUID().toString(), new BigDecimal("55"), "Cuadra");
            sales.create(business, owner, UUID.randomUUID().toString(), "CASH", "Fuera de turno",
                    List.of(new SalesService.SaleRequestLine(row, BigDecimal.ONE, new BigDecimal("5"))));
            assertEquals(1, scalar("SELECT COUNT(*) FROM cash_movements WHERE business_id=? AND session_id IS NULL",
                    business));
            assertEquals(1, cash.summary(business, owner).outsideSessionCount());
            assertMoney("55", new BigDecimal(value(
                    "SELECT expected_cash FROM cash_sessions WHERE id=? AND business_id=?", session, business)));
        } finally {
            cleanup();
        }
    }

    @RepeatedTest(8)
    void closeAndConcurrentIncomeRemainInOneConsistentPeriod() throws Exception {
        try {
            long business = business();
            long owner = user(business, "OWNER");
            long session = cash.open(business, owner, UUID.randomUUID().toString(),
                    BigDecimal.ZERO, "Concurrencia");
            int incomeCount = 4;
            CountDownLatch start = new CountDownLatch(1);
            List<Callable<Long>> actions = new ArrayList<>();
            for (int i = 0; i < incomeCount; i++) {
                actions.add(() -> {
                    start.await();
                    return cash.createMovement(business, owner, UUID.randomUUID().toString(),
                            "INCOME", "Prueba", "CASH", BigDecimal.ONE, "");
                });
            }
            actions.add(() -> {
                start.await();
                return cash.close(business, owner, UUID.randomUUID().toString(),
                        BigDecimal.ZERO, "Cierre concurrente");
            });
            ExecutorService pool = Executors.newFixedThreadPool(actions.size());
            try {
                List<Future<Long>> futures = new ArrayList<>();
                for (Callable<Long> action : actions) futures.add(pool.submit(action));
                start.countDown();
                for (Future<Long> future : futures) assertTrue(future.get(30, TimeUnit.SECONDS) > 0);
            } finally {
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }
            long assigned = scalar("SELECT COUNT(*) FROM cash_movements WHERE business_id=? AND session_id=?",
                    business, session);
            long unassigned = scalar("SELECT COUNT(*) FROM cash_movements WHERE business_id=? AND session_id IS NULL",
                    business);
            assertEquals(incomeCount, assigned + unassigned);
            assertMoney(Long.toString(assigned), new BigDecimal(value(
                    "SELECT expected_cash FROM cash_sessions WHERE id=? AND business_id=?", session, business)));
            assertNull(cash.summary(business, owner).activeSession());
        } finally {
            cleanup();
        }
    }

    private long business() throws Exception {
        long id = insert("INSERT INTO businesses(name) VALUES(?)", "[CASH TEST] " + UUID.randomUUID());
        disposableBusinesses.add(id);
        return id;
    }

    private long user(long businessId, String role) throws Exception {
        return insert("INSERT INTO users(business_id,name,email,password_hash,role,active) VALUES(?,?,?,?,?,TRUE)",
                businessId, "Prueba", UUID.randomUUID() + "@test.invalid", "unused", role);
    }

    private long productModule(long businessId) throws Exception {
        return insert("INSERT INTO custom_modules(business_id,name,module_type) VALUES(?,?,'TABLE')",
                businessId, "Productos prueba");
    }

    private long column(long businessId, long moduleId, String name, String dataType, int position) throws Exception {
        return insert("INSERT INTO module_columns(module_id,business_id,name,data_type,position) VALUES(?,?,?,?,?)",
                moduleId, businessId, name, dataType, position);
    }

    private long product(long businessId, long moduleId, long labelColumnId, long stockColumnId,
                         long priceColumnId, String name, String stock, String price) throws Exception {
        long rowId = insert("INSERT INTO module_rows(module_id,business_id) VALUES(?,?)", moduleId, businessId);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                rowId, labelColumnId, businessId, name);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                rowId, stockColumnId, businessId, stock);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                rowId, priceColumnId, businessId, price);
        return rowId;
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

    private String value(String sql, Object... args) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void bind(PreparedStatement statement, Object... args) throws Exception {
        for (int index = 0; index < args.length; index++) statement.setObject(index + 1, args[index]);
    }

    private void cleanup() throws Exception {
        for (long id : disposableBusinesses) execute("DELETE FROM businesses WHERE id=?", id);
    }
}
