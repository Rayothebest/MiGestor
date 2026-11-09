package com.gestor.service;

import com.gestor.config.Database;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, requires migration 006. Never touches existing demo businesses. */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class WorkflowServiceIntegrationTest {
    private final WorkflowService workflows = new WorkflowService();
    private final RowService rows = new RowService();
    private final SalesService sales = new SalesService();
    private final List<Long> disposableBusinesses = new ArrayList<>();

    @AfterEach
    void cleanup() throws Exception {
        for (long id : disposableBusinesses) execute("DELETE FROM businesses WHERE id=?", id);
    }

    @Test
    void purchaseConditionPanelAndRollbackAreAtomicAndScoped() throws Exception {
        long business = business();
        long other = business();
        long owner = user(business, "OWNER");
        long employee = user(business, "EMPLOYEE");
        long otherOwner = user(other, "OWNER");
        long products = table(business, "Productos");
        long stock = column(business, products, "Existencias", "NUMBER", null, null);
        long productRow = insert("INSERT INTO module_rows(module_id,business_id) VALUES(?,?)", products, business);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)",
                productRow, stock, business, "10");
        long purchases = table(business, "Compras");
        long productRef = column(business, purchases, "Producto", "RELATION", products, null);
        long quantity = column(business, purchases, "Cantidad", "NUMBER", null, null);
        long state = column(business, purchases, "Estado", "SELECT", null, "Solicitada");
        execute("UPDATE module_columns SET options_text='Solicitada,Recibida' WHERE id=?", state);
        long panel = insert("""
                INSERT INTO custom_modules(business_id,name,module_type,linked_module_id,panel_action)
                VALUES(?,?,'PANEL',?,'REGISTER')
                """, business, unique("Recepción"), purchases);

        long ruleId = workflows.save(business, owner, new WorkflowService.RuleInput(
                0, "Recibir compra", true, "ROW_CREATED", purchases, state, "Recibida",
                "ADJUST_STOCK", products, productRef, quantity, stock, "ADD", List.of()));
        assertEquals(1, workflows.list(business, owner).size());
        assertEquals(ruleId, workflows.find(business, owner, ruleId).id());
        assertEquals(0, workflows.list(other, otherOwner).size());
        try (Connection connection = Database.getConnection()) {
            assertThrows(IllegalArgumentException.class,
                    () -> workflows.onRowCreated(connection, other, otherOwner, purchases, productRow));
        }
        assertThrows(SecurityException.class, () -> workflows.list(business, employee));
        assertThrows(SecurityException.class, () -> workflows.save(business, employee,
                new WorkflowService.RuleInput(0, "No", true, "ROW_CREATED", purchases, state,
                        "Recibida", "ADJUST_STOCK", products, productRef, quantity, stock, "ADD", List.of())));

        long requested = rows.create(purchases, business, owner, null,
                Map.of(productRef, Long.toString(productRow), quantity, "3"));
        assertEquals("Solicitada", cell(business, requested, state));
        assertDecimal("10", cell(business, productRow, stock));
        rows.update(requested, purchases, business, owner, Map.of(state, "Recibida"));
        assertDecimal("10", cell(business, productRow, stock)); // Updates are not triggers.

        long received = rows.create(purchases, business, owner, panel,
                Map.of(productRef, Long.toString(productRow), quantity, "2", state, "Recibida"));
        assertTrue(received > 0);
        assertDecimal("12", cell(business, productRow, stock));
        assertEquals(1, scalar("SELECT COUNT(*) FROM inventory_movements WHERE business_id=? AND workflow_rule_id=?",
                business, ruleId));

        long rowCount = scalar("SELECT COUNT(*) FROM module_rows WHERE module_id=? AND business_id=?", purchases, business);
        long auditCount = scalar("SELECT COUNT(*) FROM audit_events WHERE business_id=?", business);
        long movementCount = scalar("SELECT COUNT(*) FROM inventory_movements WHERE business_id=?", business);
        workflows.save(business, owner, new WorkflowService.RuleInput(
                ruleId, "Recibir compra", true, "ROW_CREATED", purchases, state, "Recibida",
                "ADJUST_STOCK", products, productRef, quantity, stock, "SUBTRACT", List.of()));
        auditCount = scalar("SELECT COUNT(*) FROM audit_events WHERE business_id=?", business);
        assertThrows(IllegalArgumentException.class, () -> rows.create(purchases, business, owner, panel,
                Map.of(productRef, Long.toString(productRow), quantity, "100", state, "Recibida")));
        assertEquals(rowCount, scalar("SELECT COUNT(*) FROM module_rows WHERE module_id=? AND business_id=?",
                purchases, business));
        assertEquals(auditCount, scalar("SELECT COUNT(*) FROM audit_events WHERE business_id=?", business));
        assertEquals(movementCount, scalar("SELECT COUNT(*) FROM inventory_movements WHERE business_id=?", business));
        assertDecimal("12", cell(business, productRow, stock));

        long foreignProducts = table(other, "Otros productos");
        long foreignStock = column(other, foreignProducts, "Existencias", "NUMBER", null, null);
        assertThrows(IllegalArgumentException.class, () -> workflows.save(business, owner,
                new WorkflowService.RuleInput(0, "Cruce", true, "ROW_CREATED", purchases, state,
                        "Recibida", "ADJUST_STOCK", foreignProducts, productRef, quantity,
                        foreignStock, "ADD", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> new GridRepository().deleteColumn(stock, products, business));
        new ModuleRepository().delete(panel, business);
        new ModuleRepository().delete(purchases, business);
        assertEquals(0, workflows.list(business, owner).size());
    }

    @Test
    void completeSaleCreatesOneRowWithoutCascadeAndRollsBackOnFailure() throws Exception {
        long business = business();
        long owner = user(business, "OWNER");
        long products = table(business, "Productos");
        long label = column(business, products, "Nombre", "TEXT", null, null);
        long stock = column(business, products, "Existencias", "NUMBER", null, null);
        long price = column(business, products, "Precio", "NUMBER", null, null);
        long first = product(business, products, label, stock, price, "Agua", "10", "5");
        long second = product(business, products, label, stock, price, "Pan", "10", "3");
        sales.configure(business, owner, products, label, stock, price);

        long ledger = table(business, "Libro de ventas");
        long saleNumber = column(business, ledger, "Venta", "NUMBER", null, null);
        long amount = column(business, ledger, "Total", "NUMBER", null, null);
        long method = column(business, ledger, "Pago", "TEXT", null, null);
        long downstream = table(business, "No cascada");
        long copyNumber = column(business, downstream, "Venta", "NUMBER", null, null);
        workflows.save(business, owner, new WorkflowService.RuleInput(0, "Cascada prohibida", true,
                "ROW_CREATED", ledger, null, null, "CREATE_ROW", downstream, null, null, null,
                null, List.of(new WorkflowService.MappingInput(copyNumber, "SOURCE_COLUMN", saleNumber, null))));
        long saleRule = workflows.save(business, owner, new WorkflowService.RuleInput(0, "Asiento venta", true,
                "SALE_CREATED", null, null, null, "CREATE_ROW", ledger, null, null, null,
                null, List.of(
                        new WorkflowService.MappingInput(saleNumber, "SALE_ID", null, null),
                        new WorkflowService.MappingInput(amount, "SALE_TOTAL", null, null),
                        new WorkflowService.MappingInput(method, "SALE_PAYMENT", null, null))));

        String token = UUID.randomUUID().toString();
        List<SalesService.SaleRequestLine> lines = List.of(
                new SalesService.SaleRequestLine(first, BigDecimal.ONE, new BigDecimal("5")),
                new SalesService.SaleRequestLine(second, new BigDecimal("2"), new BigDecimal("3")));
        long saleId = sales.create(business, owner, token, "CARD", "Prueba", lines);
        assertEquals(1, scalar("SELECT COUNT(*) FROM module_rows WHERE business_id=? AND module_id=?", business, ledger));
        assertEquals(0, scalar("SELECT COUNT(*) FROM module_rows WHERE business_id=? AND module_id=?", business, downstream));
        long ledgerRow = scalar("SELECT id FROM module_rows WHERE business_id=? AND module_id=?", business, ledger);
        assertEquals(Long.toString(saleId), cell(business, ledgerRow, saleNumber));
        assertDecimal("11", cell(business, ledgerRow, amount));
        assertEquals("CARD", cell(business, ledgerRow, method));
        assertThrows(IllegalArgumentException.class,
                () -> sales.create(business, owner, token, "CARD", "", lines));
        assertEquals(1, scalar("SELECT COUNT(*) FROM module_rows WHERE business_id=? AND module_id=?", business, ledger));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events WHERE business_id=? AND event_type='WORKFLOW_EXECUTED' AND entity_id=?",
                business, saleRule));

        workflows.save(business, owner, new WorkflowService.RuleInput(saleRule, "Asiento venta", true,
                "SALE_CREATED", null, null, null, "CREATE_ROW", ledger, null, null, null,
                null, List.of(new WorkflowService.MappingInput(amount, "LITERAL", null, "no-es-numero"))));
        long auditCount = scalar("SELECT COUNT(*) FROM audit_events WHERE business_id=?", business);
        long movementCount = scalar("SELECT COUNT(*) FROM inventory_movements WHERE business_id=?", business);
        assertThrows(IllegalArgumentException.class,
                () -> sales.create(business, owner, UUID.randomUUID().toString(), "CASH", "", lines));
        assertEquals(1, scalar("SELECT COUNT(*) FROM sales WHERE business_id=?", business));
        assertEquals(1, scalar("SELECT COUNT(*) FROM module_rows WHERE business_id=? AND module_id=?", business, ledger));
        assertEquals(auditCount, scalar("SELECT COUNT(*) FROM audit_events WHERE business_id=?", business));
        assertEquals(movementCount, scalar("SELECT COUNT(*) FROM inventory_movements WHERE business_id=?", business));
        assertDecimal("9", cell(business, first, stock));
        assertDecimal("8", cell(business, second, stock));
        new ModuleRepository().delete(ledger, business);
        assertNull(workflows.find(business, owner, saleRule));
        assertEquals(1, scalar("SELECT COUNT(*) FROM sales WHERE business_id=?", business));
    }

    private long business() throws Exception {
        long id = insert("INSERT INTO businesses(name) VALUES(?)", unique("[WORKFLOW TEST]"));
        disposableBusinesses.add(id);
        return id;
    }

    private long user(long business, String role) throws Exception {
        return insert("INSERT INTO users(business_id,name,email,password_hash,role,active) VALUES(?,?,?,?,?,TRUE)",
                business, "Prueba", UUID.randomUUID() + "@test.invalid", "unused", role);
    }

    private long table(long business, String name) throws Exception {
        return insert("INSERT INTO custom_modules(business_id,name,module_type) VALUES(?,?,'TABLE')",
                business, unique(name));
    }

    private long column(long business, long module, String name, String type,
                        Long relatedModule, String defaultValue) throws Exception {
        return insert("""
                INSERT INTO module_columns(module_id,business_id,name,data_type,related_module_id,default_value)
                VALUES(?,?,?,?,?,?)
                """, module, business, name, type, relatedModule, defaultValue);
    }

    private long product(long business, long module, long label, long stock, long price,
                         String name, String quantity, String unitPrice) throws Exception {
        long id = insert("INSERT INTO module_rows(module_id,business_id) VALUES(?,?)", module, business);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)", id, label, business, name);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)", id, stock, business, quantity);
        execute("INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?)", id, price, business, unitPrice);
        return id;
    }

    private String unique(String name) { return name + " " + UUID.randomUUID().toString().substring(0, 8); }

    private long insert(String sql, Object... args) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, args);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) { assertTrue(keys.next()); return keys.getLong(1); }
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
            try (ResultSet rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getLong(1); }
        }
    }

    private String cell(long business, long row, long column) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT value_text FROM cell_values WHERE business_id=? AND row_id=? AND column_id=?")) {
            bind(statement, business, row, column);
            try (ResultSet values = statement.executeQuery()) { assertTrue(values.next()); return values.getString(1); }
        }
    }

    private void assertDecimal(String expected, String actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual)));
    }

    private void bind(PreparedStatement statement, Object... args) throws Exception {
        for (int index = 0; index < args.length; index++) statement.setObject(index + 1, args[index]);
    }
}
