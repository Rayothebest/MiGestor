package com.gestor.service;

import com.gestor.config.Database;
import com.gestor.repository.AuditRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Cash shifts and immutable business cash ledger. A sale/return posting joins its caller's transaction. */
public class CashService {
    private static final Set<String> METHODS = Set.of("CASH", "CARD", "TRANSFER", "OTHER");
    private static final Set<String> MANUAL_KINDS = Set.of("INCOME", "EXPENSE");
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final AuditRepository audit = new AuditRepository();

    public record CashSession(long id, BigDecimal openingCash, LocalDateTime openedAt,
                              String openedByName, String note) {
        public long getId() { return id; }
        public BigDecimal getOpeningCash() { return openingCash; }
        public LocalDateTime getOpenedAt() { return openedAt; }
        public String getOpenedByName() { return openedByName; }
        public String getNote() { return note; }
    }

    public record CashSummary(CashSession activeSession, BigDecimal cashSales,
                              BigDecimal cashReturns, BigDecimal manualCashIncome,
                              BigDecimal manualCashExpense, BigDecimal expectedCash,
                              BigDecimal nonCashSales, BigDecimal nonCashReturns,
                              int movementCount, int outsideSessionCount) {
        public CashSession getActiveSession() { return activeSession; }
        public BigDecimal getCashSales() { return cashSales; }
        public BigDecimal getCashReturns() { return cashReturns; }
        public BigDecimal getManualCashIncome() { return manualCashIncome; }
        public BigDecimal getManualCashExpense() { return manualCashExpense; }
        public BigDecimal getExpectedCash() { return expectedCash; }
        public BigDecimal getNonCashSales() { return nonCashSales; }
        public BigDecimal getNonCashReturns() { return nonCashReturns; }
        public int getMovementCount() { return movementCount; }
        public int getOutsideSessionCount() { return outsideSessionCount; }
    }

    public record CashMovement(long id, Long sessionId, String kind, String category,
                               String method, BigDecimal amount, String note,
                               LocalDateTime createdAt, String createdByName,
                               Long saleId, Long returnId) {
        public long getId() { return id; }
        public Long getSessionId() { return sessionId; }
        public String getKind() { return kind; }
        public String getCategory() { return category; }
        public String getMethod() { return method; }
        public BigDecimal getAmount() { return amount; }
        public String getNote() { return note; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getCreatedByName() { return createdByName; }
        public Long getSaleId() { return saleId; }
        public Long getReturnId() { return returnId; }
    }

    public record CashClosing(long id, BigDecimal openingCash, BigDecimal cashSales,
                              BigDecimal cashReturns, BigDecimal manualCashIncome,
                              BigDecimal manualCashExpense, BigDecimal expectedCash,
                              BigDecimal countedCash, BigDecimal difference,
                              LocalDateTime openedAt, LocalDateTime closedAt,
                              String openedByName, String closedByName, String note) {
        public long getId() { return id; }
        public BigDecimal getOpeningCash() { return openingCash; }
        public BigDecimal getCashSales() { return cashSales; }
        public BigDecimal getCashReturns() { return cashReturns; }
        public BigDecimal getManualCashIncome() { return manualCashIncome; }
        public BigDecimal getManualCashExpense() { return manualCashExpense; }
        public BigDecimal getExpectedCash() { return expectedCash; }
        public BigDecimal getCountedCash() { return countedCash; }
        public BigDecimal getDifference() { return difference; }
        public LocalDateTime getOpenedAt() { return openedAt; }
        public LocalDateTime getClosedAt() { return closedAt; }
        public String getOpenedByName() { return openedByName; }
        public String getClosedByName() { return closedByName; }
        public String getNote() { return note; }
    }

    private record Totals(BigDecimal cashSales, BigDecimal cashReturns,
                          BigDecimal manualCashIncome, BigDecimal manualCashExpense,
                          BigDecimal nonCashSales, BigDecimal nonCashReturns,
                          int movementCount) {
        BigDecimal expected(BigDecimal opening) {
            return opening.add(cashSales).add(manualCashIncome)
                    .subtract(cashReturns).subtract(manualCashExpense);
        }
    }

    private record ClosedRow(long id, BigDecimal openingCash, BigDecimal expectedCash,
                             BigDecimal countedCash, BigDecimal difference,
                             LocalDateTime openedAt, LocalDateTime closedAt,
                             String openedByName, String closedByName, String note) {}

    public boolean canManage(long businessId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            return manager(connection, businessId, userId);
        }
    }

    public CashSummary summary(long businessId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            int originalIsolation = connection.getTransactionIsolation();
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                requireManager(connection, businessId, userId);
                Long sessionId = currentSession(connection, businessId, false);
                int outsideCount = outsideCount(connection, businessId);
                CashSummary result;
                if (sessionId == null) {
                    result = new CashSummary(null, ZERO, ZERO, ZERO, ZERO, ZERO,
                            ZERO, ZERO, 0, outsideCount);
                } else {
                    CashSession session = activeSession(connection, businessId, sessionId);
                    Totals totals = totals(connection, businessId, sessionId);
                    result = new CashSummary(session, totals.cashSales(), totals.cashReturns(),
                            totals.manualCashIncome(), totals.manualCashExpense(),
                            totals.expected(session.openingCash()), totals.nonCashSales(),
                            totals.nonCashReturns(), totals.movementCount(), outsideCount);
                }
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
                connection.setTransactionIsolation(originalIsolation);
            }
        }
    }

    public List<CashMovement> movements(long businessId, long userId, int limit) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            requireManager(connection, businessId, userId);
            List<CashMovement> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT m.*,COALESCE(u.name,'Usuario eliminado') AS creator_name
                    FROM cash_movements m
                    LEFT JOIN users u ON u.id=m.created_by AND u.business_id=m.business_id
                    WHERE m.business_id=? ORDER BY m.id DESC LIMIT ?
                    """)) {
                statement.setLong(1, businessId);
                statement.setInt(2, Math.max(1, Math.min(limit, 200)));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(new CashMovement(rows.getLong("id"),
                            nullableLong(rows, "session_id"), rows.getString("kind"),
                            rows.getString("category"), rows.getString("payment_method"),
                            rows.getBigDecimal("amount"), rows.getString("note"),
                            time(rows, "created_at"), rows.getString("creator_name"),
                            nullableLong(rows, "sale_id"), nullableLong(rows, "sale_return_id")));
                }
            }
            return List.copyOf(result);
        }
    }

    public List<CashClosing> closings(long businessId, long userId, int limit) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            requireManager(connection, businessId, userId);
            List<ClosedRow> closed = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT s.*,COALESCE(opener.name,'Usuario eliminado') AS opener_name,
                           COALESCE(closer.name,'Usuario eliminado') AS closer_name
                    FROM cash_sessions s
                    LEFT JOIN users opener ON opener.id=s.opened_by AND opener.business_id=s.business_id
                    LEFT JOIN users closer ON closer.id=s.closed_by AND closer.business_id=s.business_id
                    WHERE s.business_id=? AND s.closed_at IS NOT NULL
                    ORDER BY s.id DESC LIMIT ?
                    """)) {
                statement.setLong(1, businessId);
                statement.setInt(2, Math.max(1, Math.min(limit, 100)));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) closed.add(new ClosedRow(rows.getLong("id"),
                            rows.getBigDecimal("opening_cash"), rows.getBigDecimal("expected_cash"),
                            rows.getBigDecimal("counted_cash"), rows.getBigDecimal("difference_cash"),
                            time(rows, "opened_at"), time(rows, "closed_at"),
                            rows.getString("opener_name"), rows.getString("closer_name"),
                            rows.getString("close_note")));
                }
            }
            List<CashClosing> result = new ArrayList<>(closed.size());
            for (ClosedRow row : closed) {
                Totals totals = totals(connection, businessId, row.id());
                result.add(new CashClosing(row.id(), row.openingCash(), totals.cashSales(),
                        totals.cashReturns(), totals.manualCashIncome(), totals.manualCashExpense(),
                        row.expectedCash(), row.countedCash(), row.difference(), row.openedAt(),
                        row.closedAt(), row.openedByName(), row.closedByName(), row.note()));
            }
            return List.copyOf(result);
        }
    }

    public long open(long businessId, long userId, String token,
                     BigDecimal openingCash, String note) throws SQLException {
        requireToken(token);
        BigDecimal opening = money(openingCash, false, "Fondo inicial");
        String safeNote = note(note);
        try (Connection connection = Database.getConnection()) {
            int originalIsolation = connection.getTransactionIsolation();
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                requireManager(connection, businessId, userId);
                if (lockRegister(connection, businessId) != null)
                    throw new IllegalArgumentException("Ya hay un turno de caja abierto.");
                long id;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO cash_sessions
                          (business_id,opened_by,open_token,opening_cash,open_note)
                        VALUES(?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, businessId);
                    statement.setLong(2, userId);
                    statement.setString(3, token);
                    statement.setBigDecimal(4, opening);
                    setText(statement, 5, safeNote);
                    statement.executeUpdate();
                    id = generatedId(statement);
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE cash_registers SET current_session_id=?
                        WHERE business_id=? AND current_session_id IS NULL
                        """)) {
                    statement.setLong(1, id);
                    statement.setLong(2, businessId);
                    if (statement.executeUpdate() != 1) throw new SQLException("No se abrió el turno de caja.");
                }
                audit.log(connection, businessId, userId, null, null,
                        "CASH_OPENED", "CASH_SESSION", id,
                        "Turno de caja #" + id + " abierto con fondo " + opening.toPlainString() + ".",
                        null, "opening_cash=" + opening.toPlainString());
                connection.commit();
                return id;
            } catch (SQLException error) {
                connection.rollback();
                throw duplicateOrSql(error, "Esta apertura ya fue procesada.");
            } catch (RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
                connection.setTransactionIsolation(originalIsolation);
            }
        }
    }

    public long createMovement(long businessId, long userId, String token,
                               String kind, String category, String method,
                               BigDecimal amount, String note) throws SQLException {
        requireToken(token);
        if (kind == null || !MANUAL_KINDS.contains(kind))
            throw new IllegalArgumentException("Selecciona un ingreso o un gasto.");
        requireMethod(method);
        String safeCategory = category == null ? "" : category.trim();
        if (safeCategory.isEmpty() || safeCategory.length() > 80)
            throw new IllegalArgumentException("La categoría debe tener entre 1 y 80 caracteres.");
        BigDecimal safeAmount = money(amount, true, "Importe");
        String safeNote = note(note);
        try (Connection connection = Database.getConnection()) {
            int originalIsolation = connection.getTransactionIsolation();
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                requireManager(connection, businessId, userId);
                Long sessionId = lockRegister(connection, businessId);
                long id = insertMovement(connection, businessId, sessionId, userId, kind,
                        safeCategory, method, safeAmount, safeNote, token, null, null);
                audit.log(connection, businessId, userId, null, null,
                        "INCOME".equals(kind) ? "CASH_INCOME" : "CASH_EXPENSE",
                        "CASH_MOVEMENT", id,
                        ("INCOME".equals(kind) ? "Ingreso" : "Gasto") + " #" + id +
                                " registrado por " + safeAmount.toPlainString() + ".",
                        null, "method=" + method + ",session=" + sessionId);
                connection.commit();
                return id;
            } catch (SQLException error) {
                connection.rollback();
                throw duplicateOrSql(error, "Este movimiento ya fue procesado.");
            } catch (RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
                connection.setTransactionIsolation(originalIsolation);
            }
        }
    }

    public long close(long businessId, long userId, String token,
                      BigDecimal countedCash, String note) throws SQLException {
        requireToken(token);
        BigDecimal counted = money(countedCash, false, "Efectivo contado");
        String safeNote = note(note);
        try (Connection connection = Database.getConnection()) {
            int originalIsolation = connection.getTransactionIsolation();
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                requireManager(connection, businessId, userId);
                Long sessionId = lockRegister(connection, businessId);
                if (sessionId == null) throw new IllegalArgumentException("No hay un turno de caja abierto.");
                CashSession session = activeSession(connection, businessId, sessionId);
                Totals totals = totals(connection, businessId, sessionId);
                BigDecimal expected = money(totals.expected(session.openingCash()),
                        false, "Efectivo esperado", true);
                BigDecimal difference = money(counted.subtract(expected), false,
                        "Diferencia de caja", true);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE cash_sessions
                        SET closed_by=?,close_token=?,expected_cash=?,counted_cash=?,
                            difference_cash=?,close_note=?,closed_at=CURRENT_TIMESTAMP
                        WHERE id=? AND business_id=? AND closed_at IS NULL
                        """)) {
                    statement.setLong(1, userId);
                    statement.setString(2, token);
                    statement.setBigDecimal(3, expected);
                    statement.setBigDecimal(4, counted);
                    statement.setBigDecimal(5, difference);
                    setText(statement, 6, safeNote);
                    statement.setLong(7, sessionId);
                    statement.setLong(8, businessId);
                    if (statement.executeUpdate() != 1) throw new SQLException("No se cerró el turno de caja.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE cash_registers SET current_session_id=NULL
                        WHERE business_id=? AND current_session_id=?
                        """)) {
                    statement.setLong(1, businessId);
                    statement.setLong(2, sessionId);
                    if (statement.executeUpdate() != 1) throw new SQLException("No se liberó el turno de caja.");
                }
                audit.log(connection, businessId, userId, null, null,
                        "CASH_CLOSED", "CASH_SESSION", sessionId,
                        "Turno de caja #" + sessionId + " cerrado; diferencia " +
                                difference.toPlainString() + ".",
                        "expected=" + expected.toPlainString(),
                        "counted=" + counted.toPlainString());
                connection.commit();
                return sessionId;
            } catch (SQLException error) {
                connection.rollback();
                throw duplicateOrSql(error, "Este cierre ya fue procesado.");
            } catch (RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
                connection.setTransactionIsolation(originalIsolation);
            }
        }
    }

    /** Called after a sale is saved, before its transaction commits. */
    public void recordSale(Connection connection, long businessId, long userId,
                           long saleId, BigDecimal amount, String method) throws SQLException {
        requireTransaction(connection);
        requireActive(connection, businessId, userId);
        requireMethod(method);
        BigDecimal safeAmount = money(amount, false, "Total de venta");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT total_amount,payment_method FROM sales WHERE id=? AND business_id=?
                """)) {
            statement.setLong(1, saleId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || safeAmount.compareTo(rows.getBigDecimal(1)) != 0 ||
                        !method.equals(rows.getString(2)))
                    throw new IllegalArgumentException("La venta no corresponde al movimiento de caja.");
            }
        }
        Long sessionId = lockRegister(connection, businessId);
        insertMovement(connection, businessId, sessionId, userId, "SALE", "Venta", method,
                safeAmount, "Venta #" + saleId, null, saleId, null);
    }

    /** Called after a return is saved, before its transaction commits. */
    public void recordReturn(Connection connection, long businessId, long userId,
                             long returnId, long saleId, BigDecimal amount,
                             String method) throws SQLException {
        requireTransaction(connection);
        requireActive(connection, businessId, userId);
        requireMethod(method);
        BigDecimal safeAmount = money(amount, false, "Importe devuelto");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sale_id,total_amount,refund_method FROM sale_returns
                WHERE id=? AND business_id=?
                """)) {
            statement.setLong(1, returnId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getLong(1) != saleId ||
                        safeAmount.compareTo(rows.getBigDecimal(2)) != 0 ||
                        !method.equals(rows.getString(3)))
                    throw new IllegalArgumentException("La devolución no corresponde al movimiento de caja.");
            }
        }
        Long sessionId = lockRegister(connection, businessId);
        insertMovement(connection, businessId, sessionId, userId, "RETURN", "Devolución",
                method, safeAmount, "Devolución #" + returnId + " de venta #" + saleId,
                null, null, returnId);
    }

    private long insertMovement(Connection connection, long businessId, Long sessionId,
                                long userId, String kind, String category, String method,
                                BigDecimal amount, String note, String token,
                                Long saleId, Long returnId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO cash_movements
                  (business_id,session_id,created_by,kind,category,payment_method,
                   amount,note,operation_token,sale_id,sale_return_id)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, businessId);
            setLong(statement, 2, sessionId);
            statement.setLong(3, userId);
            statement.setString(4, kind);
            statement.setString(5, category);
            statement.setString(6, method);
            statement.setBigDecimal(7, amount);
            setText(statement, 8, note);
            setText(statement, 9, token);
            setLong(statement, 10, saleId);
            setLong(statement, 11, returnId);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private Totals totals(Connection connection, long businessId, long sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) AS movement_count,
                  COALESCE(SUM(CASE WHEN kind='SALE' AND payment_method='CASH' THEN amount ELSE 0 END),0) AS cash_sales,
                  COALESCE(SUM(CASE WHEN kind='RETURN' AND payment_method='CASH' THEN amount ELSE 0 END),0) AS cash_returns,
                  COALESCE(SUM(CASE WHEN kind='INCOME' AND payment_method='CASH' THEN amount ELSE 0 END),0) AS manual_income,
                  COALESCE(SUM(CASE WHEN kind='EXPENSE' AND payment_method='CASH' THEN amount ELSE 0 END),0) AS manual_expense,
                  COALESCE(SUM(CASE WHEN kind='SALE' AND payment_method<>'CASH' THEN amount ELSE 0 END),0) AS noncash_sales,
                  COALESCE(SUM(CASE WHEN kind='RETURN' AND payment_method<>'CASH' THEN amount ELSE 0 END),0) AS noncash_returns
                FROM cash_movements WHERE business_id=? AND session_id=?
                """)) {
            statement.setLong(1, businessId);
            statement.setLong(2, sessionId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return new Totals(rows.getBigDecimal("cash_sales"),
                        rows.getBigDecimal("cash_returns"), rows.getBigDecimal("manual_income"),
                        rows.getBigDecimal("manual_expense"), rows.getBigDecimal("noncash_sales"),
                        rows.getBigDecimal("noncash_returns"), rows.getInt("movement_count"));
            }
        }
    }

    private CashSession activeSession(Connection connection, long businessId,
                                      long sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.id,s.opening_cash,s.open_note,s.opened_at,
                       COALESCE(u.name,'Usuario eliminado') AS opener_name
                FROM cash_sessions s
                LEFT JOIN users u ON u.id=s.opened_by AND u.business_id=s.business_id
                WHERE s.id=? AND s.business_id=? AND s.closed_at IS NULL
                """)) {
            statement.setLong(1, sessionId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("El turno abierto de caja no está disponible.");
                return new CashSession(rows.getLong("id"), rows.getBigDecimal("opening_cash"),
                        time(rows, "opened_at"), rows.getString("opener_name"),
                        rows.getString("open_note"));
            }
        }
    }

    private int outsideCount(Connection connection, long businessId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM cash_movements WHERE business_id=? AND session_id IS NULL
                """)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private Long currentSession(Connection connection, long businessId,
                                boolean lock) throws SQLException {
        String sql = "SELECT current_session_id FROM cash_registers WHERE business_id=?" +
                (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? nullableLong(rows, "current_session_id") : null;
            }
        }
    }

    private Long lockRegister(Connection connection, long businessId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO cash_registers(business_id,current_session_id) VALUES(?,NULL)
                ON DUPLICATE KEY UPDATE current_session_id=current_session_id
                """)) {
            statement.setLong(1, businessId);
            statement.executeUpdate();
        }
        return currentSession(connection, businessId, true);
    }

    private boolean manager(Connection connection, long businessId, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT role,active FROM users WHERE id=? AND business_id=?
                """)) {
            statement.setLong(1, userId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean("active") &&
                        ("OWNER".equals(rows.getString("role")) ||
                         "MANAGER".equals(rows.getString("role")));
            }
        }
    }

    private void requireManager(Connection connection, long businessId,
                                long userId) throws SQLException {
        if (!manager(connection, businessId, userId))
            throw new SecurityException("Solo propietarios y gerentes pueden consultar o gestionar Caja.");
    }

    private void requireActive(Connection connection, long businessId,
                               long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT active FROM users WHERE id=? AND business_id=?
                """)) {
            statement.setLong(1, userId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !rows.getBoolean(1))
                    throw new SecurityException("El usuario no está activo en este negocio.");
            }
        }
    }

    private void requireTransaction(Connection connection) throws SQLException {
        if (connection == null || connection.getAutoCommit())
            throw new IllegalArgumentException("El movimiento automático requiere una transacción abierta.");
    }

    private void requireMethod(String method) {
        if (method == null || !METHODS.contains(method))
            throw new IllegalArgumentException("Selecciona un método de pago válido.");
    }

    private void requireToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9-]{16,64}"))
            throw new IllegalArgumentException("La operación caducó. Recarga la página e inténtalo de nuevo.");
    }

    private BigDecimal money(BigDecimal value, boolean positive, String label) {
        return money(value, positive, label, false);
    }

    private BigDecimal money(BigDecimal value, boolean positive, String label,
                             boolean allowNegative) {
        if (value == null || (positive ? value.signum() <= 0 : !allowNegative && value.signum() < 0))
            throw new IllegalArgumentException(label + " debe ser " +
                    (positive ? "mayor que cero." : "cero o mayor."));
        try {
            BigDecimal scaled = value.setScale(2, RoundingMode.UNNECESSARY);
            if (scaled.precision() > 19)
                throw new IllegalArgumentException(label + " excede el límite permitido.");
            return scaled;
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException(label + " admite hasta dos decimales.");
        }
    }

    private String note(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() > 255)
            throw new IllegalArgumentException("La nota no puede superar 255 caracteres.");
        return safe;
    }

    private SQLException duplicateOrSql(SQLException error, String message) throws SQLException {
        if (error.getErrorCode() == 1062) throw new IllegalArgumentException(message);
        return error;
    }

    private long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new SQLException("No se obtuvo el número de operación de caja.");
            return keys.getLong(1);
        }
    }

    private Long nullableLong(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private LocalDateTime time(ResultSet rows, String column) throws SQLException {
        Timestamp value = rows.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }

    private void setText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }
}
