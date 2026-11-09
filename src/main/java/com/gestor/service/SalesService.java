package com.gestor.service;

import com.gestor.config.Database;
import com.gestor.model.ModuleColumn;
import com.gestor.model.ModuleRow;
import com.gestor.repository.AuditRepository;
import com.gestor.repository.GridRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Business-wide sales ledger. A sale and all stock movements share one InnoDB transaction. */
public class SalesService {
    private static final Set<String> PAYMENT_METHODS = Set.of("CASH", "CARD", "TRANSFER", "OTHER");
    private final GridRepository grid = new GridRepository();
    private final AuditRepository audit = new AuditRepository();
    private final WorkflowService workflows = new WorkflowService();
    private final CashService cash = new CashService();
    private final ProductAssetService productAssets = new ProductAssetService();

    public record SaleConfiguration(long moduleId, long displayColumnId, long stockColumnId,
                                    long priceColumnId, String moduleName, String displayColumnName,
                                    String stockColumnName, String priceColumnName) {
        public long getModuleId() { return moduleId; }
        public long getDisplayColumnId() { return displayColumnId; }
        public long getStockColumnId() { return stockColumnId; }
        public long getPriceColumnId() { return priceColumnId; }
        public String getModuleName() { return moduleName; }
        public String getDisplayColumnName() { return displayColumnName; }
        public String getStockColumnName() { return stockColumnName; }
        public String getPriceColumnName() { return priceColumnName; }
    }

    public record Product(long rowId, String label, BigDecimal stock, BigDecimal price,
                          String barcode, boolean hasPhoto) {
        public long getRowId() { return rowId; }
        public String getLabel() { return label; }
        public BigDecimal getStock() { return stock; }
        public BigDecimal getPrice() { return price; }
        public String getBarcode() { return barcode; }
        public boolean isHasPhoto() { return hasPhoto; }
    }

    public record SaleRequestLine(long rowId, BigDecimal quantity, BigDecimal unitPrice) {}

    public record ReturnRequestLine(long saleItemId, BigDecimal quantity) {}

    public record ReturnItem(long id, long saleItemId, String productLabel, BigDecimal quantity,
                             BigDecimal unitPrice, BigDecimal lineTotal, BigDecimal stockBefore,
                             BigDecimal stockAfter) {
        public long getId() { return id; }
        public long getSaleItemId() { return saleItemId; }
        public String getProductLabel() { return productLabel; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public BigDecimal getLineTotal() { return lineTotal; }
        public BigDecimal getStockBefore() { return stockBefore; }
        public BigDecimal getStockAfter() { return stockAfter; }
    }

    public record ReturnSummary(long id, long saleId, BigDecimal total, String refundMethod,
                                String note, String createdByName, LocalDateTime createdAt,
                                List<ReturnItem> items) {
        public long getId() { return id; }
        public long getSaleId() { return saleId; }
        public BigDecimal getTotal() { return total; }
        public String getRefundMethod() { return refundMethod; }
        public String getNote() { return note; }
        public String getCreatedByName() { return createdByName; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public List<ReturnItem> getItems() { return items; }
    }

    public record SaleSummary(long id, String sourceModuleName, BigDecimal total,
                              String paymentMethod, String note, String createdByName,
                              LocalDateTime createdAt, int itemCount) {
        public long getId() { return id; }
        public String getSourceModuleName() { return sourceModuleName; }
        public BigDecimal getTotal() { return total; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getNote() { return note; }
        public String getCreatedByName() { return createdByName; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public int getItemCount() { return itemCount; }
    }

    public record SaleItem(long id, Long productRowId, String productLabel,
                           BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal,
                           BigDecimal stockBefore, BigDecimal stockAfter,
                           BigDecimal returnedQuantity, BigDecimal returnableQuantity) {
        public long getId() { return id; }
        public Long getProductRowId() { return productRowId; }
        public String getProductLabel() { return productLabel; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public BigDecimal getLineTotal() { return lineTotal; }
        public BigDecimal getStockBefore() { return stockBefore; }
        public BigDecimal getStockAfter() { return stockAfter; }
        public BigDecimal getReturnedQuantity() { return returnedQuantity; }
        public BigDecimal getReturnableQuantity() { return returnableQuantity; }
    }

    public record SaleDetail(SaleSummary summary, List<SaleItem> items,
                             List<ReturnSummary> returns, BigDecimal returnedTotal,
                             BigDecimal netTotal) {
        public SaleSummary getSummary() { return summary; }
        public List<SaleItem> getItems() { return items; }
        public List<ReturnSummary> getReturns() { return returns; }
        public BigDecimal getReturnedTotal() { return returnedTotal; }
        public BigDecimal getNetTotal() { return netTotal; }
    }

    private record UserAccess(String role, boolean active) {
        boolean manager() { return active && ("OWNER".equals(role) || "MANAGER".equals(role)); }
    }

    public SaleConfiguration configuration(long businessId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            return configuration(connection, businessId, false);
        }
    }

    public boolean canConfigure(long businessId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            UserAccess access = access(connection, businessId, userId);
            return access != null && access.manager();
        }
    }

    public boolean canOverridePrice(long businessId, long userId) throws SQLException {
        return canConfigure(businessId, userId);
    }

    public boolean canReturn(long businessId, long userId) throws SQLException {
        return canConfigure(businessId, userId);
    }

    public void configure(long businessId, long userId, long moduleId, long displayColumnId,
                          long stockColumnId, long priceColumnId) throws SQLException {
        if (moduleId <= 0 || displayColumnId <= 0 || stockColumnId <= 0 || priceColumnId <= 0 ||
                displayColumnId == stockColumnId || displayColumnId == priceColumnId ||
                stockColumnId == priceColumnId) {
            throw new IllegalArgumentException("Selecciona una Tabla y tres columnas diferentes.");
        }
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UserAccess user = access(connection, businessId, userId);
                if (user == null || !user.manager()) throw new SecurityException("Solo gerencia puede configurar Ventas.");
                try (PreparedStatement table = connection.prepareStatement(
                        "SELECT id FROM custom_modules WHERE id=? AND business_id=? AND module_type='TABLE'")) {
                    table.setLong(1, moduleId);
                    table.setLong(2, businessId);
                    try (ResultSet rows = table.executeQuery()) {
                        if (!rows.next()) throw new IllegalArgumentException("La Tabla de productos no existe en este negocio.");
                    }
                }
                ModuleColumn display = grid.column(connection, displayColumnId, moduleId, businessId);
                ModuleColumn stock = grid.column(connection, stockColumnId, moduleId, businessId);
                ModuleColumn price = grid.column(connection, priceColumnId, moduleId, businessId);
                if (display == null || display.isSystemId() ||
                        !Set.of("TEXT", "SELECT").contains(display.dataType()) ||
                        stock == null || stock.isSystemId() || !"NUMBER".equals(stock.dataType()) ||
                        price == null || price.isSystemId() || !"NUMBER".equals(price.dataType())) {
                    throw new IllegalArgumentException("El nombre debe ser texto o lista; existencias y precio deben ser números de la misma Tabla.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO sale_settings
                          (business_id,module_id,display_column_id,stock_column_id,price_column_id,updated_by)
                        VALUES(?,?,?,?,?,?)
                        ON DUPLICATE KEY UPDATE module_id=VALUES(module_id),
                          display_column_id=VALUES(display_column_id),stock_column_id=VALUES(stock_column_id),
                          price_column_id=VALUES(price_column_id),updated_by=VALUES(updated_by)
                        """)) {
                    statement.setLong(1, businessId);
                    statement.setLong(2, moduleId);
                    statement.setLong(3, displayColumnId);
                    statement.setLong(4, stockColumnId);
                    statement.setLong(5, priceColumnId);
                    statement.setLong(6, userId);
                    statement.executeUpdate();
                }
                audit.log(connection, businessId, userId, moduleId, null, "SALES_CONFIGURED",
                        "SALES", null, "Ventas vinculadas a la tabla de productos.", null,
                        "display=" + displayColumnId + ",stock=" + stockColumnId + ",price=" + priceColumnId);
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean canSell(long businessId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            SaleConfiguration config = configuration(connection, businessId, false);
            return config != null && canSell(connection, businessId, userId, config);
        }
    }

    public List<Product> catalog(long businessId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            SaleConfiguration config = configuration(connection, businessId, false);
            if (config == null) return List.of();
            requireView(connection, businessId, userId, config.moduleId());
            Map<Long, ProductAssetService.AssetMeta> assetMeta =
                    productAssets.listMetadata(businessId, userId, config.moduleId());
            List<Product> products = new ArrayList<>();
            for (ModuleRow row : grid.rows(connection, config.moduleId(), businessId)) {
                BigDecimal stock;
                BigDecimal price;
                try {
                    stock = decimal(row.value(config.stockColumnId()), "Existencias", true);
                    price = decimal(row.value(config.priceColumnId()), "Precio", false);
                    requireDecimal(stock, 4, 19, "Existencias", false);
                    requireDecimal(price, 4, 19, "Precio", false);
                } catch (IllegalArgumentException invalidProduct) {
                    continue; // Rows without valid numeric stock/price are not sellable.
                }
                ProductAssetService.AssetMeta meta = assetMeta.get(row.id());
                products.add(new Product(row.id(), productLabel(row, config), stock, price,
                        meta == null ? "" : meta.barcode(), meta != null && meta.hasPhoto()));
            }
            return products;
        }
    }

    public long create(long businessId, long userId, String token, String paymentMethod,
                       String note, List<SaleRequestLine> lines) throws SQLException {
        if (token == null || !token.matches("[A-Za-z0-9-]{16,64}")) {
            throw new IllegalArgumentException("La venta caducó. Recarga la página e inténtalo de nuevo.");
        }
        if (!PAYMENT_METHODS.contains(paymentMethod)) {
            throw new IllegalArgumentException("Selecciona una forma de pago válida.");
        }
        if (lines == null || lines.isEmpty() || lines.size() > 100) {
            throw new IllegalArgumentException("Agrega entre 1 y 100 productos a la venta.");
        }
        String safeNote = note == null ? "" : note.trim();
        if (safeNote.length() > 255) throw new IllegalArgumentException("La nota no puede superar 255 caracteres.");
        Set<Long> seenRows = new HashSet<>();
        for (SaleRequestLine line : lines) {
            if (line == null || line.rowId() <= 0 || !seenRows.add(line.rowId())) {
                throw new IllegalArgumentException("Selecciona productos distintos en cada renglón.");
            }
            requireDecimal(line.quantity(), 4, 19, "Cantidad", true);
            requireDecimal(line.unitPrice(), 4, 19, "Precio unitario", false);
        }
        List<SaleRequestLine> sorted = lines.stream().sorted(Comparator.comparingLong(SaleRequestLine::rowId)).toList();

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                SaleConfiguration config = configuration(connection, businessId, true);
                if (config == null) throw new IllegalArgumentException("Configura primero la Tabla de productos para Ventas.");
                if (!canSell(connection, businessId, userId, config)) {
                    throw new SecurityException("No tienes permiso para registrar ventas de esta Tabla.");
                }
                UserAccess seller = requireActive(connection, businessId, userId);
                // The unique token is inserted before any stock change. A duplicate request waits
                // for the first transaction and cannot create another sale or decrement twice.
                long saleId = insertSale(connection, businessId, userId, config, token, paymentMethod, safeNote);
                BigDecimal total = BigDecimal.ZERO.setScale(2);
                for (SaleRequestLine line : sorted) {
                    ModuleRow row = grid.rowForUpdate(connection, line.rowId(), config.moduleId(), businessId);
                    BigDecimal catalogPrice = decimal(row.value(config.priceColumnId()), "Precio del producto", false);
                    requireDecimal(catalogPrice, 4, 19, "Precio del producto", false);
                    if (!seller.manager() && line.unitPrice().compareTo(catalogPrice) != 0) {
                        throw new SecurityException("Solo gerencia puede cambiar el precio de un producto.");
                    }
                    BigDecimal before = decimal(row.value(config.stockColumnId()), "Existencias", true);
                    requireDecimal(before, 4, 19, "Existencias", false);
                    BigDecimal after = before.subtract(line.quantity());
                    if (after.signum() < 0) {
                        throw new IllegalArgumentException("No hay existencias suficientes para " +
                                productLabel(row, config) + ". Disponible: " + plain(before) + ".");
                    }
                    requireDecimal(after, 4, 19, "Existencias finales", false);
                    BigDecimal lineTotal = line.quantity().multiply(line.unitPrice())
                            .setScale(2, RoundingMode.HALF_UP);
                    requireDecimal(lineTotal, 2, 19, "Total del producto", false);
                    total = total.add(lineTotal);
                    requireDecimal(total, 2, 19, "Total de la venta", false);
                    grid.updateSingleValue(connection, row.id(), config.moduleId(), businessId,
                            config.stockColumnId(), plain(after));
                    String label = productLabel(row, config);
                    insertItem(connection, saleId, businessId, row.id(), label,
                            line.quantity(), line.unitPrice(), lineTotal, before, after);
                    long auditId = audit.log(connection, businessId, userId, config.moduleId(), row.id(),
                            "STOCK_ADJUSTED", "ROW", row.id(),
                            clip("Venta #" + saleId + ": " + label + " pasó de " + plain(before) + " a " + plain(after) + ".", 255),
                            config.stockColumnId() + "=" + plain(before),
                            config.stockColumnId() + "=" + plain(after));
                    insertMovement(connection, saleId, businessId, config, row.id(), userId,
                            auditId, line.quantity(), before, after);
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE sales SET total_amount=? WHERE id=? AND business_id=?")) {
                    update.setBigDecimal(1, total);
                    update.setLong(2, saleId);
                    update.setLong(3, businessId);
                    if (update.executeUpdate() != 1) throw new SQLException("No se guardó el total de la venta.");
                }
                audit.log(connection, businessId, userId, config.moduleId(), null,
                        "SALE_CREATED", "SALE", saleId,
                        "Venta #" + saleId + " registrada por " + total.toPlainString() + ".",
                        null, "items=" + sorted.size() + ",total=" + total.toPlainString());
                workflows.onSaleCreated(connection, businessId, userId, saleId, total, paymentMethod);
                cash.recordSale(connection, businessId, userId, saleId, total, paymentMethod);
                connection.commit();
                return saleId;
            } catch (SQLException error) {
                connection.rollback();
                if (error.getErrorCode() == 1062 && saleTokenExists(connection, businessId, token)) {
                    throw new IllegalArgumentException("Esta venta ya fue procesada; no se descontó dos veces.");
                }
                throw error;
            } catch (RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Records an internal refund and restores stock; no external payment is executed. */
    public long createReturn(long businessId, long userId, long saleId, String token,
                             String refundMethod, String note,
                             List<ReturnRequestLine> lines) throws SQLException {
        if (saleId <= 0) throw new IllegalArgumentException("Selecciona una venta válida.");
        if (token == null || !token.matches("[A-Za-z0-9-]{16,64}"))
            throw new IllegalArgumentException("La devolución caducó. Recarga la página e inténtalo de nuevo.");
        if (refundMethod == null || !PAYMENT_METHODS.contains(refundMethod))
            throw new IllegalArgumentException("Selecciona un método de reembolso válido.");
        String safeNote = note == null ? "" : note.trim();
        if (safeNote.length() > 255) throw new IllegalArgumentException("La nota no puede superar 255 caracteres.");
        if (lines == null || lines.isEmpty() || lines.size() > 100)
            throw new IllegalArgumentException("Selecciona entre 1 y 100 artículos para devolver.");
        Set<Long> seen = new HashSet<>();
        for (ReturnRequestLine line : lines) {
            if (line == null || line.saleItemId() <= 0 || !seen.add(line.saleItemId()))
                throw new IllegalArgumentException("Cada artículo de la venta debe aparecer una sola vez.");
            requireDecimal(line.quantity(), 4, 19, "Cantidad a devolver", true);
        }
        List<ReturnRequestLine> sorted = lines.stream()
                .sorted(Comparator.comparingLong(ReturnRequestLine::saleItemId)).toList();

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UserAccess user = requireActive(connection, businessId, userId);
                if (!user.manager()) throw new SecurityException("Solo gerencia puede registrar devoluciones.");
                long sourceModuleId;
                long stockColumnId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT source_module_id,stock_column_id FROM sales
                        WHERE id=? AND business_id=? FOR UPDATE
                        """)) {
                    statement.setLong(1, saleId);
                    statement.setLong(2, businessId);
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) throw new IllegalArgumentException("La venta no existe en este negocio.");
                        sourceModuleId = rows.getLong("source_module_id");
                        if (rows.wasNull()) sourceModuleId = 0;
                        stockColumnId = rows.getLong("stock_column_id");
                        if (rows.wasNull()) stockColumnId = 0;
                    }
                }
                if (sourceModuleId <= 0 || stockColumnId <= 0 ||
                        grid.column(connection, stockColumnId, sourceModuleId, businessId) == null)
                    throw new IllegalArgumentException("La tabla o columna de existencias original ya no está disponible para reponer productos.");
                ModuleColumn stockColumn = grid.column(connection, stockColumnId, sourceModuleId, businessId);
                if (!"NUMBER".equals(stockColumn.dataType()) || stockColumn.isReadOnly())
                    throw new IllegalArgumentException("La columna de existencias original ya no es numérica.");

                Map<Long, OriginalSaleItem> original = originalSaleItems(connection, businessId, saleId);
                Map<Long, PriorReturn> prior = priorReturns(connection, businessId, saleId);
                for (ReturnRequestLine request : sorted) {
                    OriginalSaleItem item = original.get(request.saleItemId());
                    if (item == null) throw new IllegalArgumentException("Un artículo no pertenece a esta venta.");
                    if (item.productRowId() == null)
                        throw new IllegalArgumentException("El producto «" + item.label() + "» fue eliminado y no puede reponerse automáticamente.");
                    PriorReturn before = prior.getOrDefault(item.id(), new PriorReturn(BigDecimal.ZERO, BigDecimal.ZERO));
                    if (before.quantity().add(request.quantity()).compareTo(item.quantity()) > 0)
                        throw new IllegalArgumentException("La cantidad devuelta de «" + item.label() + "» excede la venta original.");
                }

                long returnId = insertReturn(connection, businessId, saleId, userId,
                        token, refundMethod, safeNote);
                BigDecimal total = BigDecimal.ZERO.setScale(2);
                // Product locks are acquired in stable order across concurrent returns and sales.
                List<ReturnRequestLine> stockOrder = sorted.stream().sorted(Comparator.comparingLong(
                        request -> original.get(request.saleItemId()).productRowId())).toList();
                for (ReturnRequestLine request : stockOrder) {
                    OriginalSaleItem item = original.get(request.saleItemId());
                    PriorReturn previous = prior.getOrDefault(item.id(), new PriorReturn(BigDecimal.ZERO, BigDecimal.ZERO));
                    ModuleRow product;
                    try {
                        product = grid.rowForUpdate(connection, item.productRowId(), sourceModuleId, businessId);
                    } catch (IllegalArgumentException missing) {
                        throw new IllegalArgumentException("El producto «" + item.label() + "» ya no existe en la tabla original.");
                    }
                    BigDecimal stockBefore = decimal(product.value(stockColumnId), "Existencias", true);
                    requireDecimal(stockBefore, 4, 19, "Existencias", false);
                    BigDecimal stockAfter = stockBefore.add(request.quantity());
                    requireDecimal(stockAfter, 4, 19, "Existencias finales", false);
                    BigDecimal cumulativeQuantity = previous.quantity().add(request.quantity());
                    BigDecimal cumulativeAmount = item.lineTotal().multiply(cumulativeQuantity)
                            .divide(item.quantity(), 2, RoundingMode.HALF_UP);
                    BigDecimal lineAmount = cumulativeAmount.subtract(previous.amount());
                    requireDecimal(lineAmount, 2, 19, "Importe de devolución", false);
                    if (cumulativeAmount.compareTo(item.lineTotal()) > 0)
                        throw new IllegalArgumentException("La devolución supera el importe de la venta.");
                    grid.updateSingleValue(connection, item.productRowId(), sourceModuleId,
                            businessId, stockColumnId, plain(stockAfter));
                    insertReturnItem(connection, businessId, returnId, item, stockColumnId,
                            request.quantity(), lineAmount, stockBefore, stockAfter);
                    long auditId = audit.log(connection, businessId, userId, sourceModuleId,
                            item.productRowId(), "STOCK_ADJUSTED", "ROW", item.productRowId(),
                            clip("Devolución #" + returnId + " de venta #" + saleId + ": " + item.label() +
                                    " pasó de " + plain(stockBefore) + " a " + plain(stockAfter) + ".", 255),
                            stockColumnId + "=" + plain(stockBefore),
                            stockColumnId + "=" + plain(stockAfter));
                    insertReturnMovement(connection, businessId, sourceModuleId, item.productRowId(),
                            stockColumnId, saleId, returnId, userId, auditId,
                            request.quantity(), stockBefore, stockAfter);
                    total = total.add(lineAmount);
                    requireDecimal(total, 2, 19, "Total de devolución", false);
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE sale_returns SET total_amount=? WHERE id=? AND business_id=?
                        """)) {
                    statement.setBigDecimal(1, total);
                    statement.setLong(2, returnId);
                    statement.setLong(3, businessId);
                    if (statement.executeUpdate() != 1) throw new SQLException("No se guardó el total de devolución.");
                }
                audit.log(connection, businessId, userId, sourceModuleId, null,
                        "RETURN_CREATED", "SALE_RETURN", returnId,
                        "Devolución #" + returnId + " de venta #" + saleId + " por " + total.toPlainString() + ".",
                        null, "items=" + sorted.size() + ",total=" + total.toPlainString());
                workflows.onReturnCreated(connection, businessId, userId, returnId,
                        saleId, total, refundMethod);
                cash.recordReturn(connection, businessId, userId, returnId,
                        saleId, total, refundMethod);
                connection.commit();
                return returnId;
            } catch (SQLException error) {
                connection.rollback();
                if (error.getErrorCode() == 1062 && returnTokenExists(connection, businessId, token))
                    throw new IllegalArgumentException("Esta devolución ya fue procesada; no se repuso dos veces.");
                throw error;
            } catch (RuntimeException error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        }
    }

    private record OriginalSaleItem(long id, Long productRowId, String label,
                                    BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
    private record PriorReturn(BigDecimal quantity, BigDecimal amount) {}

    private Map<Long, OriginalSaleItem> originalSaleItems(Connection connection, long businessId,
                                                          long saleId) throws SQLException {
        Map<Long, OriginalSaleItem> items = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,product_row_id,product_label,quantity,unit_price,line_total
                FROM sale_items WHERE business_id=? AND sale_id=? ORDER BY id FOR UPDATE
                """)) {
            statement.setLong(1, businessId);
            statement.setLong(2, saleId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long rowId = rows.getLong("product_row_id");
                    Long productId = rows.wasNull() ? null : rowId;
                    OriginalSaleItem item = new OriginalSaleItem(rows.getLong("id"), productId,
                            rows.getString("product_label"), rows.getBigDecimal("quantity"),
                            rows.getBigDecimal("unit_price"), rows.getBigDecimal("line_total"));
                    items.put(item.id(), item);
                }
            }
        }
        return items;
    }

    private Map<Long, PriorReturn> priorReturns(Connection connection, long businessId,
                                                long saleId) throws SQLException {
        Map<Long, PriorReturn> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT ri.sale_item_id,SUM(ri.quantity) AS returned_quantity,
                       SUM(ri.line_total) AS returned_amount
                FROM sale_return_items ri
                JOIN sale_items i ON i.id=ri.sale_item_id AND i.business_id=ri.business_id
                WHERE ri.business_id=? AND i.sale_id=? GROUP BY ri.sale_item_id
                """)) {
            statement.setLong(1, businessId);
            statement.setLong(2, saleId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.put(rows.getLong("sale_item_id"),
                        new PriorReturn(rows.getBigDecimal("returned_quantity"),
                                rows.getBigDecimal("returned_amount")));
            }
        }
        return result;
    }

    private long insertReturn(Connection connection, long businessId, long saleId, long userId,
                              String token, String method, String note) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sale_returns
                  (business_id,sale_id,created_by,operation_token,refund_method,total_amount,note)
                VALUES(?,?,?,?,?,0,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, businessId);
            statement.setLong(2, saleId);
            statement.setLong(3, userId);
            statement.setString(4, token);
            statement.setString(5, method);
            if (note.isBlank()) statement.setNull(6, Types.VARCHAR);
            else statement.setString(6, note);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No se obtuvo el número de devolución.");
                return keys.getLong(1);
            }
        }
    }

    private void insertReturnItem(Connection connection, long businessId, long returnId,
                                  OriginalSaleItem item, long stockColumnId, BigDecimal quantity,
                                  BigDecimal amount, BigDecimal before, BigDecimal after) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sale_return_items
                  (return_id,business_id,sale_item_id,product_row_id,stock_column_id,
                   product_label,quantity,unit_price,line_total,stock_before,stock_after)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setLong(1, returnId);
            statement.setLong(2, businessId);
            statement.setLong(3, item.id());
            statement.setLong(4, item.productRowId());
            statement.setLong(5, stockColumnId);
            statement.setString(6, item.label());
            statement.setBigDecimal(7, quantity);
            statement.setBigDecimal(8, item.unitPrice());
            statement.setBigDecimal(9, amount);
            statement.setBigDecimal(10, before);
            statement.setBigDecimal(11, after);
            statement.executeUpdate();
        }
    }

    private void insertReturnMovement(Connection connection, long businessId, long moduleId,
                                      long productRowId, long stockColumnId, long saleId,
                                      long returnId, long userId, long auditId,
                                      BigDecimal quantity, BigDecimal before, BigDecimal after) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO inventory_movements
                  (business_id,module_id,row_id,column_id,sale_id,sale_return_id,audit_event_id,
                   created_by,operation,quantity_before,quantity_change,quantity_after,note)
                VALUES(?,?,?,?,?,?,?,?,'ADD',?,?,?,?)
                """)) {
            statement.setLong(1, businessId);
            statement.setLong(2, moduleId);
            statement.setLong(3, productRowId);
            statement.setLong(4, stockColumnId);
            statement.setLong(5, saleId);
            statement.setLong(6, returnId);
            statement.setLong(7, auditId);
            statement.setLong(8, userId);
            statement.setBigDecimal(9, before);
            statement.setBigDecimal(10, quantity);
            statement.setBigDecimal(11, after);
            statement.setString(12, "Devolución #" + returnId + " de venta #" + saleId);
            statement.executeUpdate();
        }
    }

    private boolean returnTokenExists(Connection connection, long businessId, String token) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sale_returns WHERE business_id=? AND operation_token=?")) {
            statement.setLong(1, businessId);
            statement.setString(2, token);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    public List<SaleSummary> recent(long businessId, long userId, int limit) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            UserAccess user = requireActive(connection, businessId, userId);
            String sql = summarySelect() + " WHERE s.business_id=?" +
                    (user.manager() ? "" : " AND s.created_by=?") +
                    " ORDER BY s.created_at DESC,s.id DESC LIMIT ?";
            List<SaleSummary> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, businessId);
                int next = 2;
                if (!user.manager()) statement.setLong(next++, userId);
                statement.setInt(next, Math.max(1, Math.min(limit, 100)));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(summary(rows));
                }
            }
            return result;
        }
    }

    public SaleDetail detail(long businessId, long userId, long saleId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            UserAccess user = requireActive(connection, businessId, userId);
            String sql = summarySelect() + " WHERE s.business_id=? AND s.id=?" +
                    (user.manager() ? "" : " AND s.created_by=?");
            SaleSummary sale;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, businessId);
                statement.setLong(2, saleId);
                if (!user.manager()) statement.setLong(3, userId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return null;
                    sale = summary(rows);
                }
            }
            List<SaleItem> items = new ArrayList<>();
            Map<Long, PriorReturn> returned = priorReturns(connection, businessId, saleId);
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id,product_row_id,product_label,quantity,unit_price,line_total,stock_before,stock_after
                    FROM sale_items WHERE sale_id=? AND business_id=? ORDER BY id
                    """)) {
                statement.setLong(1, saleId);
                statement.setLong(2, businessId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        long rawRowId = rows.getLong("product_row_id");
                        Long rowId = rows.wasNull() ? null : rawRowId;
                        long itemId = rows.getLong("id");
                        BigDecimal quantity = rows.getBigDecimal("quantity");
                        BigDecimal returnedQuantity = returned.getOrDefault(itemId,
                                new PriorReturn(BigDecimal.ZERO, BigDecimal.ZERO)).quantity();
                        items.add(new SaleItem(itemId, rowId, rows.getString("product_label"),
                                quantity, rows.getBigDecimal("unit_price"),
                                rows.getBigDecimal("line_total"), rows.getBigDecimal("stock_before"),
                                rows.getBigDecimal("stock_after"), returnedQuantity,
                                quantity.subtract(returnedQuantity)));
                    }
                }
            }
            List<ReturnSummary> returns = new ArrayList<>();
            BigDecimal returnedTotal = BigDecimal.ZERO.setScale(2);
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT r.id,r.total_amount,r.refund_method,r.note,r.created_at,
                           COALESCE(u.name,'Usuario eliminado') AS user_name
                    FROM sale_returns r LEFT JOIN users u ON u.id=r.created_by AND u.business_id=r.business_id
                    WHERE r.business_id=? AND r.sale_id=? ORDER BY r.created_at DESC,r.id DESC
                    """)) {
                statement.setLong(1, businessId);
                statement.setLong(2, saleId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        long returnId = rows.getLong("id");
                        BigDecimal amount = rows.getBigDecimal("total_amount");
                        returnedTotal = returnedTotal.add(amount);
                        returns.add(new ReturnSummary(returnId, saleId, amount,
                                rows.getString("refund_method"), rows.getString("note"),
                                rows.getString("user_name"), rows.getTimestamp("created_at").toLocalDateTime(),
                                returnItems(connection, businessId, returnId)));
                    }
                }
            }
            return new SaleDetail(sale, List.copyOf(items), List.copyOf(returns),
                    returnedTotal, sale.total().subtract(returnedTotal));
        }
    }

    private List<ReturnItem> returnItems(Connection connection, long businessId,
                                          long returnId) throws SQLException {
        List<ReturnItem> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,sale_item_id,product_label,quantity,unit_price,line_total,
                       stock_before,stock_after
                FROM sale_return_items WHERE business_id=? AND return_id=? ORDER BY id
                """)) {
            statement.setLong(1, businessId);
            statement.setLong(2, returnId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) items.add(new ReturnItem(rows.getLong("id"),
                        rows.getLong("sale_item_id"), rows.getString("product_label"),
                        rows.getBigDecimal("quantity"), rows.getBigDecimal("unit_price"),
                        rows.getBigDecimal("line_total"), rows.getBigDecimal("stock_before"),
                        rows.getBigDecimal("stock_after")));
            }
        }
        return List.copyOf(items);
    }

    private SaleConfiguration configuration(Connection connection, long businessId, boolean lock) throws SQLException {
        String sql = """
                SELECT s.module_id,s.display_column_id,s.stock_column_id,s.price_column_id,
                       m.name AS module_name,d.name AS display_name,q.name AS stock_name,p.name AS price_name
                FROM sale_settings s
                JOIN custom_modules m ON m.id=s.module_id AND m.business_id=s.business_id AND m.module_type='TABLE'
                JOIN module_columns d ON d.id=s.display_column_id AND d.module_id=m.id
                  AND d.business_id=s.business_id AND d.data_type IN ('TEXT','SELECT')
                JOIN module_columns q ON q.id=s.stock_column_id AND q.module_id=m.id
                  AND q.business_id=s.business_id AND q.data_type='NUMBER'
                JOIN module_columns p ON p.id=s.price_column_id AND p.module_id=m.id
                  AND p.business_id=s.business_id AND p.data_type='NUMBER'
                WHERE s.business_id=?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new SaleConfiguration(rows.getLong("module_id"), rows.getLong("display_column_id"),
                        rows.getLong("stock_column_id"), rows.getLong("price_column_id"),
                        rows.getString("module_name"), rows.getString("display_name"),
                        rows.getString("stock_name"), rows.getString("price_name"));
            }
        }
    }

    private boolean saleTokenExists(Connection connection, long businessId, String token) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sales WHERE business_id=? AND operation_token=?")) {
            statement.setLong(1, businessId);
            statement.setString(2, token);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private UserAccess access(Connection connection, long businessId, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT role,active FROM users WHERE id=? AND business_id=?")) {
            statement.setLong(1, userId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new UserAccess(rows.getString("role"), rows.getBoolean("active")) : null;
            }
        }
    }

    private UserAccess requireActive(Connection connection, long businessId, long userId) throws SQLException {
        UserAccess user = access(connection, businessId, userId);
        if (user == null || !user.active()) throw new SecurityException("No tienes acceso a las ventas de este negocio.");
        return user;
    }

    private void requireView(Connection connection, long businessId, long userId, long moduleId) throws SQLException {
        UserAccess user = requireActive(connection, businessId, userId);
        if (user.manager()) return;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT can_view FROM module_permissions
                WHERE business_id=? AND user_id=? AND module_id=?
                """)) {
            statement.setLong(1, businessId);
            statement.setLong(2, userId);
            statement.setLong(3, moduleId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !rows.getBoolean(1)) {
                    throw new SecurityException("No tienes permiso para ver esta Tabla de productos.");
                }
            }
        }
    }

    private boolean canSell(Connection connection, long businessId, long userId,
                            SaleConfiguration config) throws SQLException {
        UserAccess user = access(connection, businessId, userId);
        if (user == null || !user.active()) return false;
        if (user.manager()) return true;
        String sql = """
                SELECT p.can_view,p.can_edit,
                  EXISTS(
                    SELECT 1 FROM custom_modules panel
                    JOIN module_permissions pp ON pp.module_id=panel.id
                      AND pp.business_id=panel.business_id AND pp.user_id=?
                    WHERE panel.business_id=? AND panel.linked_module_id=?
                      AND panel.module_type='PANEL' AND panel.panel_action='ADJUST'
                      AND panel.adjustment_operation='SUBTRACT'
                      AND panel.adjustment_column_id=? AND pp.can_edit=TRUE
                  ) AS can_subtract
                FROM module_permissions p
                WHERE p.business_id=? AND p.user_id=? AND p.module_id=?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, businessId);
            statement.setLong(3, config.moduleId());
            statement.setLong(4, config.stockColumnId());
            statement.setLong(5, businessId);
            statement.setLong(6, userId);
            statement.setLong(7, config.moduleId());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean("can_view") &&
                        (rows.getBoolean("can_edit") || rows.getBoolean("can_subtract"));
            }
        }
    }

    private long insertSale(Connection connection, long businessId, long userId,
                            SaleConfiguration config, String token, String paymentMethod,
                            String note) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sales
                  (business_id,source_module_id,stock_column_id,source_module_name,created_by,
                   operation_token,payment_method,total_amount,note)
                VALUES(?,?,?,?,?,?,?,0,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, businessId);
            statement.setLong(2, config.moduleId());
            statement.setLong(3, config.stockColumnId());
            statement.setString(4, config.moduleName());
            statement.setLong(5, userId);
            statement.setString(6, token);
            statement.setString(7, paymentMethod);
            if (note.isBlank()) statement.setNull(8, Types.VARCHAR);
            else statement.setString(8, note);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No se obtuvo el número de venta.");
                return keys.getLong(1);
            }
        }
    }

    private void insertItem(Connection connection, long saleId, long businessId, long rowId,
                            String label, BigDecimal quantity, BigDecimal price, BigDecimal lineTotal,
                            BigDecimal before, BigDecimal after) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sale_items
                  (sale_id,business_id,product_row_id,product_label,quantity,unit_price,line_total,stock_before,stock_after)
                VALUES(?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setLong(1, saleId);
            statement.setLong(2, businessId);
            statement.setLong(3, rowId);
            statement.setString(4, label);
            statement.setBigDecimal(5, quantity);
            statement.setBigDecimal(6, price);
            statement.setBigDecimal(7, lineTotal);
            statement.setBigDecimal(8, before);
            statement.setBigDecimal(9, after);
            statement.executeUpdate();
        }
    }

    private void insertMovement(Connection connection, long saleId, long businessId,
                                SaleConfiguration config, long rowId, long userId, long auditId,
                                BigDecimal quantity, BigDecimal before, BigDecimal after) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO inventory_movements
                  (business_id,module_id,row_id,column_id,source_panel_id,sale_id,audit_event_id,created_by,
                   operation,quantity_before,quantity_change,quantity_after,note)
                VALUES(?,?,?,?,NULL,?,?,?,'SUBTRACT',?,?,?,?)
                """)) {
            statement.setLong(1, businessId);
            statement.setLong(2, config.moduleId());
            statement.setLong(3, rowId);
            statement.setLong(4, config.stockColumnId());
            statement.setLong(5, saleId);
            statement.setLong(6, auditId);
            statement.setLong(7, userId);
            statement.setBigDecimal(8, before);
            statement.setBigDecimal(9, quantity.negate());
            statement.setBigDecimal(10, after);
            statement.setString(11, "Venta #" + saleId);
            statement.executeUpdate();
        }
    }

    private String summarySelect() {
        return """
                SELECT s.id,s.source_module_name,s.total_amount,s.payment_method,s.note,s.created_at,
                       COALESCE(u.name,'Usuario eliminado') AS user_name,
                       (SELECT COUNT(*) FROM sale_items i WHERE i.sale_id=s.id AND i.business_id=s.business_id) AS item_count
                FROM sales s LEFT JOIN users u ON u.id=s.created_by AND u.business_id=s.business_id
                """;
    }

    private SaleSummary summary(ResultSet rows) throws SQLException {
        return new SaleSummary(rows.getLong("id"), rows.getString("source_module_name"),
                rows.getBigDecimal("total_amount"), rows.getString("payment_method"),
                rows.getString("note"), rows.getString("user_name"),
                rows.getTimestamp("created_at").toLocalDateTime(), rows.getInt("item_count"));
    }

    private String productLabel(ModuleRow row, SaleConfiguration config) {
        String raw = row.value(config.displayColumnId());
        String label = raw == null || raw.isBlank() ? "Producto #" + row.id() : raw.trim();
        return label.length() > 255 ? label.substring(0, 255) : label;
    }

    private BigDecimal decimal(String raw, String label, boolean blankIsZero) {
        if (raw == null || raw.isBlank()) {
            if (blankIsZero) return BigDecimal.ZERO;
            throw new IllegalArgumentException(label + " no contiene un número válido.");
        }
        try {
            return new BigDecimal(raw.trim().replace(',', '.'));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + " no contiene un número válido.");
        }
    }

    private void requireDecimal(BigDecimal value, int scale, int precision, String label,
                                boolean mustBePositive) {
        if (value == null || (mustBePositive && value.signum() <= 0) || (!mustBePositive && value.signum() < 0)) {
            throw new IllegalArgumentException(label + (mustBePositive ? " debe ser mayor que cero." : " no puede ser negativo."));
        }
        BigDecimal normalized = value.stripTrailingZeros();
        int fractional = Math.max(0, normalized.scale());
        int integer = Math.max(0, normalized.precision() - normalized.scale());
        if (fractional > scale || integer > precision - scale) {
            throw new IllegalArgumentException(label + " excede el tamaño permitido (" + (precision - scale) +
                    " enteros y " + scale + " decimales).");
        }
    }

    private String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private String clip(String text, int max) { return text.length() <= max ? text : text.substring(0, max); }
}
