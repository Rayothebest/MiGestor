package com.gestor.service;

import com.gestor.config.Database;
import com.gestor.model.CustomModule;
import com.gestor.model.ModuleColumn;
import com.gestor.model.ModuleRow;
import com.gestor.repository.AuditRepository;
import com.gestor.repository.GridRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public class AutomationService {
    private final GridRepository grid = new GridRepository();
    private final AuditRepository audit = new AuditRepository();

    public BigDecimal adjust(CustomModule panel, long selectedRowId, BigDecimal requestedQuantity,
                             String note, String token, long businessId, long userId) throws SQLException {
        if (!panel.isAdjustPanel() || panel.linkedModuleId() == null || panel.adjustmentColumnId() == null) {
            throw new IllegalArgumentException("Configura la columna de existencias y la operación de este Panel.");
        }
        if (requestedQuantity == null || requestedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }
        if (token == null || !token.matches("[A-Za-z0-9-]{16,64}")) {
            throw new IllegalArgumentException("La operación caducó. Recarga el Panel e inténtalo nuevamente.");
        }

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long operationId = beginOperation(connection, businessId, panel.id(), userId, token);
                ModuleColumn quantityColumn = grid.column(connection, panel.adjustmentColumnId(),
                        panel.linkedModuleId(), businessId);
                if (quantityColumn == null || !"NUMBER".equals(quantityColumn.dataType())) {
                    throw new IllegalArgumentException("La columna configurada para existencias ya no es numérica.");
                }
                ModuleRow row = grid.rowForUpdate(connection, selectedRowId, panel.linkedModuleId(), businessId);
                BigDecimal before = decimal(row.value(quantityColumn.id()));
                String operation = panel.adjustmentOperation();
                BigDecimal after = switch (operation) {
                    case "ADD" -> before.add(requestedQuantity);
                    case "SUBTRACT" -> before.subtract(requestedQuantity);
                    case "SET" -> requestedQuantity;
                    default -> throw new IllegalArgumentException("La operación automática no es válida.");
                };
                if (!panel.allowNegative() && after.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("No hay existencias suficientes. Disponible: " + before.stripTrailingZeros().toPlainString() + ".");
                }
                grid.updateSingleValue(connection, selectedRowId, panel.linkedModuleId(), businessId,
                        quantityColumn.id(), after.stripTrailingZeros().toPlainString());
                BigDecimal change = after.subtract(before);
                long auditId = audit.log(connection, businessId, userId, panel.linkedModuleId(), selectedRowId,
                        "STOCK_ADJUSTED", "ROW", selectedRowId,
                        panel.name() + ": " + quantityColumn.name() + " pasó de " + before.stripTrailingZeros().toPlainString() +
                                " a " + after.stripTrailingZeros().toPlainString() + ".",
                        quantityColumn.id() + "=" + before, quantityColumn.id() + "=" + after);
                insertMovement(connection, businessId, panel, selectedRowId, quantityColumn.id(), auditId,
                        userId, operation, before, change, after, note);
                finishOperation(connection, operationId, selectedRowId,
                        "Existencia actualizada a " + after.stripTrailingZeros().toPlainString() + ".");
                connection.commit();
                return after;
            } catch (SQLException e) {
                connection.rollback();
                if (e.getErrorCode() == 1062 || "23000".equals(e.getSQLState())) {
                    throw new IllegalArgumentException("Esta operación ya fue procesada; no se descontó dos veces.");
                }
                throw e;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    private long beginOperation(Connection connection, long businessId, long panelId, long userId,
                                String token) throws SQLException {
        String sql = "INSERT INTO operation_runs(business_id,panel_module_id,user_id,operation_token,status) VALUES(?,?,?,?,'STARTED')";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1,businessId); statement.setLong(2,panelId); statement.setLong(3,userId); statement.setString(4,token); statement.executeUpdate();
            try (ResultSet keys=statement.getGeneratedKeys()) { if(!keys.next()) throw new SQLException("No se inició la operación."); return keys.getLong(1); }
        }
    }

    private void finishOperation(Connection connection,long operationId,long rowId,String message)throws SQLException{
        try(PreparedStatement statement=connection.prepareStatement("UPDATE operation_runs SET status='COMPLETED',result_row_id=?,result_message=?,completed_at=CURRENT_TIMESTAMP WHERE id=?")){statement.setLong(1,rowId);statement.setString(2,message);statement.setLong(3,operationId);statement.executeUpdate();}
    }

    private void insertMovement(Connection connection,long businessId,CustomModule panel,long rowId,long columnId,
                                long auditId,long userId,String operation,BigDecimal before,BigDecimal change,
                                BigDecimal after,String note)throws SQLException{
        String sql="INSERT INTO inventory_movements(business_id,module_id,row_id,column_id,source_panel_id,audit_event_id,created_by,operation,quantity_before,quantity_change,quantity_after,note) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement statement=connection.prepareStatement(sql)){statement.setLong(1,businessId);statement.setLong(2,panel.linkedModuleId());statement.setLong(3,rowId);statement.setLong(4,columnId);statement.setLong(5,panel.id());statement.setLong(6,auditId);statement.setLong(7,userId);statement.setString(8,operation);statement.setBigDecimal(9,before);statement.setBigDecimal(10,change);statement.setBigDecimal(11,after);if(note==null||note.isBlank())statement.setNull(12,Types.VARCHAR);else statement.setString(12,note.trim());statement.executeUpdate();}
    }

    private BigDecimal decimal(String value){try{return new BigDecimal(value==null||value.isBlank()?"0":value.replace(',','.'));}catch(NumberFormatException e){throw new IllegalArgumentException("La existencia actual no contiene un número válido.");}}
}
