package com.gestor.service;

import com.gestor.config.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in test against a local migrated MySQL. Creates and removes only disposable businesses. */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class SalesServiceIntegrationTest {
    private final SalesService sales = new SalesService();
    private final List<Long> disposableBusinesses = new ArrayList<>();

    @Test
    void saleIsAtomicIdempotentAndBusinessScoped() throws Exception {
        try {
            long business = business();
            long outsider = business();
            long owner = user(business, "OWNER");
            long employee = user(business, "EMPLOYEE");
            long module = module(business);
            long labelColumn = column(business, module, "Producto", "TEXT", 0);
            long stockColumn = column(business, module, "Existencias", "NUMBER", 1);
            long priceColumn = column(business, module, "Precio", "NUMBER", 2);
            long first = row(business, module, labelColumn, stockColumn, priceColumn,
                    "Agua", "10", "5.00");
            long second = row(business, module, labelColumn, stockColumn, priceColumn,
                    "Pan", "8", "3.25");
            long foreignModule = module(outsider);
            long foreignLabel = column(outsider, foreignModule, "Producto", "TEXT", 0);
            long foreignStock = column(outsider, foreignModule, "Existencias", "NUMBER", 1);
            long foreignPrice = column(outsider, foreignModule, "Precio", "NUMBER", 2);
            long foreignRow = row(outsider, foreignModule, foreignLabel, foreignStock, foreignPrice,
                    "Ajeno", "100", "9.00");

            sales.configure(business, owner, module, labelColumn, stockColumn, priceColumn);
            assertEquals(2, sales.catalog(business, owner).size());
            assertThrows(IllegalArgumentException.class,
                    () -> sales.configure(business, owner, module, foreignLabel, stockColumn, priceColumn));
            permission(business, employee, module, true, true);
            assertTrue(sales.canSell(business, employee));
            assertFalse(sales.canOverridePrice(business, employee));

            String token = UUID.randomUUID().toString();
            List<SalesService.SaleRequestLine> lines = List.of(
                    new SalesService.SaleRequestLine(first, new BigDecimal("2"), new BigDecimal("5.00")),
                    new SalesService.SaleRequestLine(second, new BigDecimal("1.5"), new BigDecimal("3.25")));
            long saleId = sales.create(business, owner, token, "CASH", "Prueba integrada", lines);
            SalesService.SaleDetail detail = sales.detail(business, owner, saleId);
            assertNotNull(detail);
            assertEquals(new BigDecimal("14.88"), detail.summary().total());
            assertEquals(2, detail.items().size());
            assertEquals(2, scalar("SELECT COUNT(*) FROM inventory_movements WHERE business_id=? AND sale_id=?",
                    business, saleId));
            assertEquals(1, scalar("SELECT COUNT(*) FROM sales WHERE business_id=?", business));
            assertDecimal("8", cell(business, first, stockColumn));
            assertDecimal("6.5", cell(business, second, stockColumn));

            assertThrows(IllegalArgumentException.class,
                    () -> sales.create(business, owner, token, "CASH", "", lines));
            assertEquals(1, scalar("SELECT COUNT(*) FROM sales WHERE business_id=?", business));
            assertDecimal("8", cell(business, first, stockColumn));

            assertThrows(IllegalArgumentException.class, () -> sales.create(business, owner,
                    UUID.randomUUID().toString(), "CARD", "", List.of(
                            new SalesService.SaleRequestLine(first, BigDecimal.ONE, new BigDecimal("5")),
                            new SalesService.SaleRequestLine(second, new BigDecimal("100"), new BigDecimal("3.25")))));
            assertEquals(1, scalar("SELECT COUNT(*) FROM sales WHERE business_id=?", business));
            assertEquals(2, scalar("SELECT COUNT(*) FROM sale_items WHERE business_id=?", business));
            assertDecimal("8", cell(business, first, stockColumn));
            assertDecimal("6.5", cell(business, second, stockColumn));

            assertThrows(IllegalArgumentException.class, () -> sales.create(business, owner,
                    UUID.randomUUID().toString(), "CARD", "", List.of(
                            new SalesService.SaleRequestLine(first, BigDecimal.ONE, new BigDecimal("5")),
                            new SalesService.SaleRequestLine(foreignRow, BigDecimal.ONE, new BigDecimal("9")))));
            assertEquals(1, scalar("SELECT COUNT(*) FROM sales WHERE business_id=?", business));
            assertDecimal("8", cell(business, first, stockColumn));
            assertDecimal("100", cell(outsider, foreignRow, foreignStock));

            assertThrows(SecurityException.class, () -> sales.create(business, employee,
                    UUID.randomUUID().toString(), "CASH", "", List.of(
                            new SalesService.SaleRequestLine(first, BigDecimal.ONE, BigDecimal.ZERO))));
            assertDecimal("8", cell(business, first, stockColumn));
            long employeeSale = sales.create(business, employee, UUID.randomUUID().toString(), "CASH", "",
                    List.of(new SalesService.SaleRequestLine(first, BigDecimal.ONE, new BigDecimal("5.00"))));
            assertNotNull(sales.detail(business, employee, employeeSale));
            assertNull(sales.detail(business, employee, saleId));

            execute("DELETE FROM module_rows WHERE id=? AND module_id=? AND business_id=?",
                    first, module, business);
            SalesService.SaleDetail afterDeletion = sales.detail(business, owner, saleId);
            assertEquals("Agua", afterDeletion.items().get(0).productLabel());
            assertNull(afterDeletion.items().get(0).productRowId());

            execute("DELETE FROM custom_modules WHERE id=? AND business_id=?", module, business);
            assertEquals(0, scalar("SELECT COUNT(*) FROM sale_settings WHERE business_id=?", business));
            assertEquals(1, scalar("SELECT COUNT(*) FROM sales WHERE id=? AND business_id=? AND source_module_id IS NULL",
                    saleId, business));
            SalesService.SaleDetail afterTableDeletion = sales.detail(business, owner, saleId);
            assertNotNull(afterTableDeletion);
            assertEquals("Productos de prueba", afterTableDeletion.summary().sourceModuleName());
            assertEquals("Agua", afterTableDeletion.items().get(0).productLabel());
            assertEquals("Pan", afterTableDeletion.items().get(1).productLabel());
        } finally {
            for (long id : disposableBusinesses) {
                execute("DELETE FROM businesses WHERE id=?", id);
            }
        }
    }

    @Test
    void partialReturnsUseOriginalStockColumnAndReconcileRounding() throws Exception {
        try {
            long business = business();
            long other = business();
            long owner = user(business, "OWNER");
            long employee = user(business, "EMPLOYEE");
            long otherOwner = user(other, "OWNER");
            long module = module(business);
            long label = column(business, module, "Producto", "TEXT", 0);
            long stock = column(business, module, "Existencias", "NUMBER", 1);
            long price = column(business, module, "Precio", "NUMBER", 2);
            long alternativeStock = column(business, module, "Otra existencia", "NUMBER", 3);
            long product = row(business, module, label, stock, price, "Cuaderno", "10", "0.3333");
            execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                    product, alternativeStock, business, "100");
            sales.configure(business, owner, module, label, stock, price);
            long saleId = sales.create(business, owner, UUID.randomUUID().toString(), "CARD", "",
                    List.of(new SalesService.SaleRequestLine(product, new BigDecimal("3"),
                            new BigDecimal("0.3333"))));
            assertDecimal("7", cell(business, product, stock));
            long itemId = sales.detail(business, owner, saleId).items().get(0).id();
            sales.configure(business, owner, module, label, alternativeStock, price);

            String firstToken = UUID.randomUUID().toString();
            long firstReturn = sales.createReturn(business, owner, saleId, firstToken, "CASH", "Primera",
                    List.of(new SalesService.ReturnRequestLine(itemId, BigDecimal.ONE)));
            assertEquals(new BigDecimal("0.33"), sales.detail(business, owner, saleId).returns().get(0).total());
            assertDecimal("8", cell(business, product, stock));
            assertDecimal("100", cell(business, product, alternativeStock));
            assertThrows(IllegalArgumentException.class, () -> sales.createReturn(business, owner,
                    saleId, firstToken, "CASH", "", List.of(new SalesService.ReturnRequestLine(itemId, BigDecimal.ONE))));
            assertEquals(1, scalar("SELECT COUNT(*) FROM sale_returns WHERE id=? AND business_id=?",
                    firstReturn, business));
            assertThrows(SecurityException.class, () -> sales.createReturn(business, employee,
                    saleId, UUID.randomUUID().toString(), "CASH", "",
                    List.of(new SalesService.ReturnRequestLine(itemId, BigDecimal.ONE))));
            assertThrows(IllegalArgumentException.class, () -> sales.createReturn(other, otherOwner,
                    saleId, UUID.randomUUID().toString(), "CASH", "",
                    List.of(new SalesService.ReturnRequestLine(itemId, BigDecimal.ONE))));

            sales.createReturn(business, owner, saleId, UUID.randomUUID().toString(), "CARD", "Segunda",
                    List.of(new SalesService.ReturnRequestLine(itemId, BigDecimal.ONE)));
            sales.createReturn(business, owner, saleId, UUID.randomUUID().toString(), "OTHER", "Tercera",
                    List.of(new SalesService.ReturnRequestLine(itemId, BigDecimal.ONE)));
            SalesService.SaleDetail detail = sales.detail(business, owner, saleId);
            assertEquals(new BigDecimal("1.00"), detail.summary().total());
            assertEquals(new BigDecimal("1.00"), detail.returnedTotal());
            assertEquals(new BigDecimal("0.00"), detail.netTotal());
            assertDecimal("3", detail.items().get(0).returnedQuantity().toPlainString());
            assertDecimal("0", detail.items().get(0).returnableQuantity().toPlainString());
            assertDecimal("10", cell(business, product, stock));
            assertDecimal("100", cell(business, product, alternativeStock));
            assertEquals(3, scalar("SELECT COUNT(*) FROM inventory_movements WHERE business_id=? AND sale_return_id IS NOT NULL",
                    business));
            assertThrows(IllegalArgumentException.class, () -> sales.createReturn(business, owner,
                    saleId, UUID.randomUUID().toString(), "CASH", "",
                    List.of(new SalesService.ReturnRequestLine(itemId, BigDecimal.ONE))));
        } finally {
            for (long id : disposableBusinesses) execute("DELETE FROM businesses WHERE id=?", id);
        }
    }

    @Test
    void returnWorkflowCreatesLedgerRowAndFailureRollsBackStock() throws Exception {
        try {
            long business = business();
            long owner = user(business, "OWNER");
            long module = module(business);
            long label = column(business, module, "Producto", "TEXT", 0);
            long stock = column(business, module, "Existencias", "NUMBER", 1);
            long price = column(business, module, "Precio", "NUMBER", 2);
            long product = row(business, module, label, stock, price, "Agua", "10", "5");
            sales.configure(business, owner, module, label, stock, price);
            long saleId = sales.create(business, owner, UUID.randomUUID().toString(), "CASH", "",
                    List.of(new SalesService.SaleRequestLine(product, new BigDecimal("2"), new BigDecimal("5"))));
            long itemId = sales.detail(business, owner, saleId).items().get(0).id();
            long ledger = insert("INSERT INTO custom_modules(business_id,name,module_type) VALUES(?,?,'TABLE')",
                    business, "Registro de devoluciones");
            long returnNumber = column(business, ledger, "Devolución", "NUMBER", 0);
            long saleNumber = column(business, ledger, "Venta", "NUMBER", 1);
            long amount = column(business, ledger, "Importe", "NUMBER", 2);
            long method = column(business, ledger, "Método", "TEXT", 3);
            WorkflowService workflows = new WorkflowService();
            long rule = workflows.save(business, owner, new WorkflowService.RuleInput(
                    0, "Asiento de devolución", true, "RETURN_CREATED", null, null, null,
                    "CREATE_ROW", ledger, null, null, null, null, List.of(
                    new WorkflowService.MappingInput(returnNumber, "RETURN_ID", null, null),
                    new WorkflowService.MappingInput(saleNumber, "SALE_ID", null, null),
                    new WorkflowService.MappingInput(amount, "RETURN_TOTAL", null, null),
                    new WorkflowService.MappingInput(method, "RETURN_METHOD", null, null))));
            long returnId = sales.createReturn(business, owner, saleId, UUID.randomUUID().toString(),
                    "CARD", "", List.of(new SalesService.ReturnRequestLine(itemId, BigDecimal.ONE)));
            assertDecimal("9", cell(business, product, stock));
            assertEquals(1, scalar("SELECT COUNT(*) FROM module_rows WHERE business_id=? AND module_id=?", business, ledger));
            long ledgerRow = scalar("SELECT id FROM module_rows WHERE business_id=? AND module_id=?", business, ledger);
            assertEquals(Long.toString(returnId), cell(business, ledgerRow, returnNumber));
            assertEquals(Long.toString(saleId), cell(business, ledgerRow, saleNumber));
            assertDecimal("5", cell(business, ledgerRow, amount));
            assertEquals("CARD", cell(business, ledgerRow, method));

            workflows.save(business, owner, new WorkflowService.RuleInput(
                    rule, "Asiento de devolución", true, "RETURN_CREATED", null, null, null,
                    "CREATE_ROW", ledger, null, null, null, null, List.of(
                    new WorkflowService.MappingInput(amount, "LITERAL", null, "no-es-numero"))));
            long movementCount = scalar("SELECT COUNT(*) FROM inventory_movements WHERE business_id=?", business);
            long auditCount = scalar("SELECT COUNT(*) FROM audit_events WHERE business_id=?", business);
            String retryToken = UUID.randomUUID().toString();
            assertThrows(IllegalArgumentException.class, () -> sales.createReturn(business, owner,
                    saleId, retryToken, "CASH", "",
                    List.of(new SalesService.ReturnRequestLine(itemId, BigDecimal.ONE))));
            assertDecimal("9", cell(business, product, stock));
            assertEquals(1, scalar("SELECT COUNT(*) FROM sale_returns WHERE business_id=?", business));
            assertEquals(1, scalar("SELECT COUNT(*) FROM module_rows WHERE business_id=? AND module_id=?", business, ledger));
            assertEquals(movementCount, scalar("SELECT COUNT(*) FROM inventory_movements WHERE business_id=?", business));
            assertEquals(auditCount, scalar("SELECT COUNT(*) FROM audit_events WHERE business_id=?", business));
            workflows.setActive(business, owner, rule, false);
            sales.createReturn(business, owner, saleId, retryToken, "CASH", "",
                    List.of(new SalesService.ReturnRequestLine(itemId, BigDecimal.ONE)));
            assertDecimal("10", cell(business, product, stock));
            assertEquals(new BigDecimal("0.00"), sales.detail(business, owner, saleId).netTotal());
        } finally {
            for (long id : disposableBusinesses) execute("DELETE FROM businesses WHERE id=?", id);
        }
    }

    private long business() throws Exception {
        long id = insert("INSERT INTO businesses(name) VALUES(?)", "[INTEGRATION] " + UUID.randomUUID());
        disposableBusinesses.add(id);
        return id;
    }

    private long user(long businessId, String role) throws Exception {
        return insert("INSERT INTO users(business_id,name,email,password_hash,role,active) VALUES(?,?,?,?,?,TRUE)",
                businessId, "Prueba", "sales-" + UUID.randomUUID() + "@test.invalid", "unused", role);
    }

    private long module(long businessId) throws Exception {
        return insert("INSERT INTO custom_modules(business_id,name,module_type) VALUES(?,?,'TABLE')",
                businessId, "Productos de prueba");
    }

    private long column(long businessId, long moduleId, String name, String type, int position) throws Exception {
        return insert("INSERT INTO module_columns(module_id,business_id,name,data_type,position) VALUES(?,?,?,?,?)",
                moduleId, businessId, name, type, position);
    }

    private long row(long businessId, long moduleId, long labelColumn, long stockColumn,
                     long priceColumn, String name, String stock, String price) throws Exception {
        long rowId = insert("INSERT INTO module_rows(module_id,business_id) VALUES(?,?)", moduleId, businessId);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                rowId, labelColumn, businessId, name);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                rowId, stockColumn, businessId, stock);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                rowId, priceColumn, businessId, price);
        return rowId;
    }

    private void permission(long businessId, long userId, long moduleId, boolean view,
                            boolean edit) throws Exception {
        execute("INSERT INTO module_permissions(business_id,user_id,module_id,can_view,can_edit) VALUES(?,?,?,?,?)",
                businessId, userId, moduleId, view, edit);
    }

    private long insert(String sql, Object... values) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, values);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private void execute(String sql, Object... values) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private long scalar(String sql, Object... values) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private String cell(long businessId, long rowId, long columnId) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT value_text FROM cell_values WHERE business_id=? AND row_id=? AND column_id=?")) {
            bind(statement, businessId, rowId, columnId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    private void assertDecimal(String expected, String actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual)));
    }

    private void bind(PreparedStatement statement, Object... values) throws Exception {
        for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
    }
}
