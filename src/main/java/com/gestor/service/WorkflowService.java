package com.gestor.service;

import com.gestor.config.Database;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One event runs all matching rules in the caller's transaction. Created rows never recurse. */
public class WorkflowService {
    private static final Set<String> TRIGGERS = Set.of("ROW_CREATED", "SALE_CREATED", "RETURN_CREATED");
    private static final Set<String> ACTIONS = Set.of("ADJUST_STOCK", "CREATE_ROW");
    private static final Set<String> SOURCES = Set.of("SOURCE_COLUMN", "SALE_ID", "SALE_TOTAL",
            "SALE_PAYMENT", "RETURN_ID", "RETURN_TOTAL", "RETURN_METHOD", "LITERAL");
    private final GridRepository grid = new GridRepository();
    private final AuditRepository audit = new AuditRepository();

    public record MappingInput(long targetColumnId, String sourceType, Long sourceColumnId,
                               String literalValue) {}

    public record RuleInput(long id, String name, boolean active, String triggerType,
                            Long sourceModuleId, Long conditionColumnId, String conditionValue,
                            String actionType, long targetModuleId, Long relationColumnId,
                            Long quantityColumnId, Long stockColumnId, String operation,
                            List<MappingInput> mappings) {}

    public record Mapping(long id, long targetColumnId, String sourceType, Long sourceColumnId,
                          String literalValue, String targetColumnName, String sourceColumnName) {
        public long getId() { return id; }
        public long getTargetColumnId() { return targetColumnId; }
        public String getSourceType() { return sourceType; }
        public Long getSourceColumnId() { return sourceColumnId; }
        public String getLiteralValue() { return literalValue; }
        public String getTargetColumnName() { return targetColumnName; }
        public String getSourceColumnName() { return sourceColumnName; }
    }

    public record Rule(long id, long businessId, String name, boolean active, String triggerType,
                       Long sourceModuleId, Long conditionColumnId, String conditionValue,
                       String actionType, long targetModuleId, Long relationColumnId,
                       Long quantityColumnId, Long stockColumnId, String operation,
                       String sourceModuleName, String targetModuleName, List<Mapping> mappings) {
        public long getId() { return id; }
        public long getBusinessId() { return businessId; }
        public String getName() { return name; }
        public boolean isActive() { return active; }
        public boolean getActive() { return active; }
        public String getTriggerType() { return triggerType; }
        public Long getSourceModuleId() { return sourceModuleId; }
        public Long getConditionColumnId() { return conditionColumnId; }
        public String getConditionValue() { return conditionValue; }
        public String getActionType() { return actionType; }
        public long getTargetModuleId() { return targetModuleId; }
        public Long getRelationColumnId() { return relationColumnId; }
        public Long getQuantityColumnId() { return quantityColumnId; }
        public Long getStockColumnId() { return stockColumnId; }
        public String getOperation() { return operation; }
        public String getSourceModuleName() { return sourceModuleName; }
        public String getTargetModuleName() { return targetModuleName; }
        public List<Mapping> getMappings() { return mappings; }
    }

    public boolean canManage(long businessId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection()) { return isManager(connection, businessId, userId); }
    }

    public List<Rule> list(long businessId, long userId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            requireManager(connection, businessId, userId);
            return rules(connection, businessId, null, null, false);
        }
    }

    public Rule find(long businessId, long userId, long ruleId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            requireManager(connection, businessId, userId);
            List<Rule> found = rules(connection, businessId, ruleId, null, false);
            return found.isEmpty() ? null : found.get(0);
        }
    }

    public long save(long businessId, long userId, RuleInput input) throws SQLException {
        if (input == null) throw new IllegalArgumentException("Completa la regla.");
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireManager(connection, businessId, userId);
                RuleInput normalized = validate(connection, businessId, input);
                if (normalized.id() > 0) requireRule(connection, businessId, normalized.id());
                long id = normalized.id() == 0 ? insertRule(connection, businessId, userId, normalized)
                        : updateRule(connection, businessId, userId, normalized);
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM workflow_mappings WHERE rule_id=? AND business_id=?")) {
                    delete.setLong(1, id);
                    delete.setLong(2, businessId);
                    delete.executeUpdate();
                }
                insertMappings(connection, id, businessId, normalized.mappings());
                audit.log(connection, businessId, userId, normalized.targetModuleId(), null,
                        normalized.id() == 0 ? "WORKFLOW_CREATED" : "WORKFLOW_UPDATED",
                        "WORKFLOW", id, "Regla «" + normalized.name() + "» guardada.", null,
                        normalized.triggerType() + " -> " + normalized.actionType());
                connection.commit();
                return id;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public void setActive(long businessId, long userId, long ruleId, boolean active) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireManager(connection, businessId, userId);
                Rule rule = requireRule(connection, businessId, ruleId);
                if (active) validate(connection, businessId, asInput(rule));
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE workflow_rules SET active=?,updated_by=? WHERE id=? AND business_id=?")) {
                    statement.setBoolean(1, active);
                    statement.setLong(2, userId);
                    statement.setLong(3, ruleId);
                    statement.setLong(4, businessId);
                    statement.executeUpdate();
                }
                audit.log(connection, businessId, userId, rule.targetModuleId(), null,
                        active ? "WORKFLOW_ENABLED" : "WORKFLOW_DISABLED", "WORKFLOW", ruleId,
                        "Regla «" + rule.name() + "» " + (active ? "activada." : "desactivada."), null, null);
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public void delete(long businessId, long userId, long ruleId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireManager(connection, businessId, userId);
                Rule rule = requireRule(connection, businessId, ruleId);
                audit.log(connection, businessId, userId, rule.targetModuleId(), null,
                        "WORKFLOW_DELETED", "WORKFLOW", ruleId,
                        "Regla «" + rule.name() + "» eliminada.", null, null);
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM workflow_rules WHERE id=? AND business_id=?")) {
                    statement.setLong(1, ruleId);
                    statement.setLong(2, businessId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        }
    }

    /** Called only after a real table/panel REGISTER row is persisted, before its commit. */
    public void onRowCreated(Connection connection, long businessId, long userId,
                             long moduleId, long rowId) throws SQLException {
        ModuleRow source = grid.rowForUpdate(connection, rowId, moduleId, businessId);
        for (Rule rule : rules(connection, businessId, null, "ROW_CREATED", true)) {
            if (!Objects.equals(rule.sourceModuleId(), moduleId)) continue;
            if (rule.conditionColumnId() != null && !clean(source.value(rule.conditionColumnId()))
                    .equals(clean(rule.conditionValue()))) continue;
            validate(connection, businessId, asInput(rule));
            execute(connection, rule, userId, source, rowId, null, null, null,
                    null, null, null);
        }
    }

    /** Called once per committed sale, including a sale with multiple items. */
    public void onSaleCreated(Connection connection, long businessId, long userId,
                              long saleId, BigDecimal total, String paymentMethod) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sales WHERE id=? AND business_id=? AND total_amount=? AND payment_method=?")) {
            statement.setLong(1, saleId);
            statement.setLong(2, businessId);
            statement.setBigDecimal(3, total);
            statement.setString(4, paymentMethod);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("La venta de origen no existe en este negocio.");
            }
        }
        for (Rule rule : rules(connection, businessId, null, "SALE_CREATED", true)) {
            validate(connection, businessId, asInput(rule));
            execute(connection, rule, userId, null, null, saleId, total, paymentMethod,
                    null, null, null);
        }
    }

    /** Called inside the same transaction that creates the return and restores stock. */
    public void onReturnCreated(Connection connection, long businessId, long userId,
                                long returnId, long saleId, BigDecimal total,
                                String refundMethod) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM sale_returns
                WHERE id=? AND sale_id=? AND business_id=? AND total_amount=? AND refund_method=?
                """)) {
            statement.setLong(1, returnId);
            statement.setLong(2, saleId);
            statement.setLong(3, businessId);
            statement.setBigDecimal(4, total);
            statement.setString(5, refundMethod);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("La devolución de origen no existe en este negocio.");
            }
        }
        for (Rule rule : rules(connection, businessId, null, "RETURN_CREATED", true)) {
            validate(connection, businessId, asInput(rule));
            execute(connection, rule, userId, null, null, saleId, null, null,
                    returnId, total, refundMethod);
        }
    }

    private RuleInput validate(Connection connection, long businessId, RuleInput input) throws SQLException {
        String name = clean(input.name());
        if (input.id() < 0 || name.isEmpty() || name.length() > 100)
            throw new IllegalArgumentException("El nombre de la regla debe tener entre 1 y 100 caracteres.");
        if (input.triggerType() == null || input.actionType() == null ||
                !TRIGGERS.contains(input.triggerType()) || !ACTIONS.contains(input.actionType()))
            throw new IllegalArgumentException("Selecciona un evento y una acción válidos.");
        boolean rowTrigger = "ROW_CREATED".equals(input.triggerType());
        if (rowTrigger && (input.sourceModuleId() == null || input.sourceModuleId() <= 0 ||
                !tableExists(connection, businessId, input.sourceModuleId())))
            throw new IllegalArgumentException("Selecciona una Tabla de origen de este negocio.");
        if (!rowTrigger && (input.sourceModuleId() != null || input.conditionColumnId() != null ||
                !clean(input.conditionValue()).isEmpty()))
            throw new IllegalArgumentException("Las ventas y devoluciones no tienen Tabla ni condición de fila.");
        if (input.targetModuleId() <= 0 || !tableExists(connection, businessId, input.targetModuleId()))
            throw new IllegalArgumentException("Selecciona una Tabla de destino de este negocio.");

        Long conditionColumn = null;
        String conditionValue = null;
        if (rowTrigger && (input.conditionColumnId() != null || !clean(input.conditionValue()).isEmpty())) {
            if (input.conditionColumnId() == null || clean(input.conditionValue()).isEmpty() ||
                    clean(input.conditionValue()).length() > 255)
                throw new IllegalArgumentException("La condición requiere columna y valor (máximo 255 caracteres).");
            ModuleColumn condition = editableColumn(connection, input.conditionColumnId(),
                    input.sourceModuleId(), businessId, "condición");
            conditionColumn = condition.id();
            conditionValue = clean(input.conditionValue());
            if ("SELECT".equals(condition.dataType()) && !condition.options().contains(conditionValue))
                throw new IllegalArgumentException("El valor de la condición debe existir en la lista «" + condition.name() + "».");
        }

        if ("ADJUST_STOCK".equals(input.actionType())) {
            if (!rowTrigger) throw new IllegalArgumentException("El ajuste de existencias requiere una fila de origen.");
            if (input.relationColumnId() == null || input.quantityColumnId() == null ||
                    input.stockColumnId() == null || input.operation() == null ||
                    !Set.of("ADD", "SUBTRACT").contains(input.operation()))
                throw new IllegalArgumentException("Elige producto, cantidad, existencias y operación.");
            ModuleColumn relation = editableColumn(connection, input.relationColumnId(),
                    input.sourceModuleId(), businessId, "producto");
            ModuleColumn quantity = editableColumn(connection, input.quantityColumnId(),
                    input.sourceModuleId(), businessId, "cantidad");
            ModuleColumn stock = editableColumn(connection, input.stockColumnId(),
                    input.targetModuleId(), businessId, "existencias");
            if (!"RELATION".equals(relation.dataType()) ||
                    !Objects.equals(relation.relatedModuleId(), input.targetModuleId()) ||
                    !"NUMBER".equals(quantity.dataType()) || !"NUMBER".equals(stock.dataType()))
                throw new IllegalArgumentException("El producto debe relacionar la Tabla destino; cantidad y existencias deben ser números.");
            return new RuleInput(input.id(), name, input.active(), input.triggerType(),
                    input.sourceModuleId(), conditionColumn, conditionValue, input.actionType(),
                    input.targetModuleId(), relation.id(), quantity.id(), stock.id(), input.operation(), List.of());
        }

        List<MappingInput> mappings = input.mappings() == null ? List.of() : input.mappings();
        if (mappings.isEmpty() || mappings.size() > 40)
            throw new IllegalArgumentException("Agrega entre 1 y 40 campos para crear la fila.");
        Set<Long> seenTargets = new HashSet<>();
        List<MappingInput> normalizedMappings = new ArrayList<>();
        for (MappingInput mapping : mappings) {
            if (mapping == null || mapping.targetColumnId() <= 0 ||
                    !seenTargets.add(mapping.targetColumnId()) || mapping.sourceType() == null ||
                    !SOURCES.contains(mapping.sourceType()))
                throw new IllegalArgumentException("Cada campo de destino debe aparecer una sola vez.");
            ModuleColumn target = editableColumn(connection, mapping.targetColumnId(),
                    input.targetModuleId(), businessId, "destino");
            Long sourceColumnId = null;
            String literalValue = null;
            if ("SOURCE_COLUMN".equals(mapping.sourceType())) {
                if (!rowTrigger || mapping.sourceColumnId() == null)
                    throw new IllegalArgumentException("Selecciona una columna de la fila de origen.");
                sourceColumnId = editableColumn(connection, mapping.sourceColumnId(),
                        input.sourceModuleId(), businessId, "origen").id();
            } else if ("LITERAL".equals(mapping.sourceType())) {
                literalValue = mapping.literalValue() == null ? "" : mapping.literalValue().trim();
                if (literalValue.length() > 1000)
                    throw new IllegalArgumentException("El valor fijo no puede superar 1000 caracteres.");
            } else {
                boolean saleTrigger = "SALE_CREATED".equals(input.triggerType());
                boolean returnTrigger = "RETURN_CREATED".equals(input.triggerType());
                boolean allowed = switch (mapping.sourceType()) {
                    case "SALE_ID" -> saleTrigger || returnTrigger;
                    case "SALE_TOTAL", "SALE_PAYMENT" -> saleTrigger;
                    case "RETURN_ID", "RETURN_TOTAL", "RETURN_METHOD" -> returnTrigger;
                    default -> false;
                };
                if (!allowed) throw new IllegalArgumentException(
                        "El dato seleccionado no corresponde al evento de la regla.");
            }
            if (("SALE_PAYMENT".equals(mapping.sourceType()) ||
                    "RETURN_METHOD".equals(mapping.sourceType())) && "SELECT".equals(target.dataType()) &&
                    !target.options().containsAll(Set.of("CASH", "CARD", "TRANSFER", "OTHER")))
                throw new IllegalArgumentException("La lista de pago debe contener CASH, CARD, TRANSFER y OTHER.");
            normalizedMappings.add(new MappingInput(target.id(), mapping.sourceType(), sourceColumnId, literalValue));
        }
        return new RuleInput(input.id(), name, input.active(), input.triggerType(),
                rowTrigger ? input.sourceModuleId() : null, conditionColumn, conditionValue,
                input.actionType(), input.targetModuleId(), null, null, null, null,
                List.copyOf(normalizedMappings));
    }

    private ModuleColumn editableColumn(Connection connection, long columnId, long moduleId,
                                        long businessId, String label) throws SQLException {
        ModuleColumn column = grid.column(connection, columnId, moduleId, businessId);
        if (column == null || column.isSystemId() || column.isReadOnly())
            throw new IllegalArgumentException("La columna de " + label + " no existe o no se puede usar.");
        return column;
    }

    private boolean tableExists(Connection connection, long businessId, long moduleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM custom_modules WHERE id=? AND business_id=? AND module_type='TABLE'")) {
            statement.setLong(1, moduleId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private boolean isManager(Connection connection, long businessId, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM users WHERE id=? AND business_id=? AND active=TRUE AND role IN ('OWNER','MANAGER')")) {
            statement.setLong(1, userId);
            statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private void requireManager(Connection connection, long businessId, long userId) throws SQLException {
        if (!isManager(connection, businessId, userId))
            throw new SecurityException("Solo gerencia puede configurar las reglas automáticas.");
    }

    private RuleInput asInput(Rule rule) {
        return new RuleInput(rule.id(), rule.name(), rule.active(), rule.triggerType(),
                rule.sourceModuleId(), rule.conditionColumnId(), rule.conditionValue(),
                rule.actionType(), rule.targetModuleId(), rule.relationColumnId(),
                rule.quantityColumnId(), rule.stockColumnId(), rule.operation(),
                rule.mappings().stream().map(m -> new MappingInput(m.targetColumnId(),
                        m.sourceType(), m.sourceColumnId(), m.literalValue())).toList());
    }

    private List<Rule> rules(Connection connection, long businessId, Long ruleId,
                             String triggerType, boolean activeOnly) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id,r.business_id,r.name,r.active,r.trigger_type,r.source_module_id,
                       r.condition_column_id,r.condition_value,r.action_type,r.target_module_id,
                       r.relation_column_id,r.quantity_column_id,r.stock_column_id,r.operation,
                       source.name AS source_name,target.name AS target_name
                FROM workflow_rules r
                LEFT JOIN custom_modules source ON source.id=r.source_module_id
                  AND source.business_id=r.business_id AND source.module_type='TABLE'
                JOIN custom_modules target ON target.id=r.target_module_id
                  AND target.business_id=r.business_id AND target.module_type='TABLE'
                WHERE r.business_id=?
                """);
        if (ruleId != null) sql.append(" AND r.id=?");
        if (triggerType != null) sql.append(" AND r.trigger_type=?");
        if (activeOnly) sql.append(" AND r.active=TRUE");
        sql.append(" ORDER BY r.id");
        List<Rule> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setLong(1, businessId);
            int next = 2;
            if (ruleId != null) statement.setLong(next++, ruleId);
            if (triggerType != null) statement.setString(next, triggerType);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long id = rows.getLong("id");
                    result.add(new Rule(id, rows.getLong("business_id"), rows.getString("name"),
                            rows.getBoolean("active"), rows.getString("trigger_type"),
                            optionalLong(rows, "source_module_id"),
                            optionalLong(rows, "condition_column_id"), rows.getString("condition_value"),
                            rows.getString("action_type"), rows.getLong("target_module_id"),
                            optionalLong(rows, "relation_column_id"),
                            optionalLong(rows, "quantity_column_id"),
                            optionalLong(rows, "stock_column_id"), rows.getString("operation"),
                            rows.getString("source_name"), rows.getString("target_name"),
                            mappings(connection, businessId, id)));
                }
            }
        }
        return result;
    }

    private List<Mapping> mappings(Connection connection, long businessId, long ruleId) throws SQLException {
        List<Mapping> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT m.id,m.target_column_id,m.source_type,m.source_column_id,m.literal_value,
                       target.name AS target_name,source.name AS source_name
                FROM workflow_mappings m
                LEFT JOIN module_columns target ON target.id=m.target_column_id
                  AND target.business_id=m.business_id
                LEFT JOIN module_columns source ON source.id=m.source_column_id
                  AND source.business_id=m.business_id
                WHERE m.business_id=? AND m.rule_id=? ORDER BY m.position,m.id
                """)) {
            statement.setLong(1, businessId);
            statement.setLong(2, ruleId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new Mapping(rows.getLong("id"),
                        rows.getLong("target_column_id"), rows.getString("source_type"),
                        optionalLong(rows, "source_column_id"), rows.getString("literal_value"),
                        rows.getString("target_name"), rows.getString("source_name")));
            }
        }
        return List.copyOf(result);
    }

    private Rule requireRule(Connection connection, long businessId, long ruleId) throws SQLException {
        List<Rule> found = rules(connection, businessId, ruleId, null, false);
        if (found.isEmpty()) throw new IllegalArgumentException("La regla no existe en este negocio.");
        return found.get(0);
    }

    private long insertRule(Connection connection, long businessId, long userId,
                            RuleInput input) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workflow_rules
                  (business_id,name,active,trigger_type,source_module_id,condition_column_id,
                   condition_value,action_type,target_module_id,relation_column_id,
                   quantity_column_id,stock_column_id,operation,created_by,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, businessId);
            statement.setString(2, input.name());
            statement.setBoolean(3, input.active());
            statement.setString(4, input.triggerType());
            setLong(statement, 5, input.sourceModuleId());
            setLong(statement, 6, input.conditionColumnId());
            statement.setString(7, input.conditionValue());
            statement.setString(8, input.actionType());
            statement.setLong(9, input.targetModuleId());
            setLong(statement, 10, input.relationColumnId());
            setLong(statement, 11, input.quantityColumnId());
            setLong(statement, 12, input.stockColumnId());
            statement.setString(13, input.operation());
            statement.setLong(14, userId);
            statement.setLong(15, userId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No se obtuvo el ID de la regla.");
                return keys.getLong(1);
            }
        }
    }

    private long updateRule(Connection connection, long businessId, long userId,
                            RuleInput input) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE workflow_rules SET name=?,active=?,trigger_type=?,source_module_id=?,
                  condition_column_id=?,condition_value=?,action_type=?,target_module_id=?,
                  relation_column_id=?,quantity_column_id=?,stock_column_id=?,operation=?,updated_by=?
                WHERE id=? AND business_id=?
                """)) {
            statement.setString(1, input.name());
            statement.setBoolean(2, input.active());
            statement.setString(3, input.triggerType());
            setLong(statement, 4, input.sourceModuleId());
            setLong(statement, 5, input.conditionColumnId());
            statement.setString(6, input.conditionValue());
            statement.setString(7, input.actionType());
            statement.setLong(8, input.targetModuleId());
            setLong(statement, 9, input.relationColumnId());
            setLong(statement, 10, input.quantityColumnId());
            setLong(statement, 11, input.stockColumnId());
            statement.setString(12, input.operation());
            statement.setLong(13, userId);
            statement.setLong(14, input.id());
            statement.setLong(15, businessId);
            statement.executeUpdate();
            return input.id();
        }
    }

    private void insertMappings(Connection connection, long ruleId, long businessId,
                                List<MappingInput> mappings) throws SQLException {
        if (mappings.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workflow_mappings
                  (rule_id,business_id,target_column_id,source_type,source_column_id,literal_value,position)
                VALUES(?,?,?,?,?,?,?)
                """)) {
            for (int index = 0; index < mappings.size(); index++) {
                MappingInput mapping = mappings.get(index);
                statement.setLong(1, ruleId);
                statement.setLong(2, businessId);
                statement.setLong(3, mapping.targetColumnId());
                statement.setString(4, mapping.sourceType());
                setLong(statement, 5, mapping.sourceColumnId());
                statement.setString(6, mapping.literalValue());
                statement.setInt(7, index);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Long optionalLong(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }

    private void execute(Connection connection, Rule rule, long userId, ModuleRow source,
                         Long sourceRowId, Long saleId, BigDecimal saleTotal,
                         String paymentMethod, Long returnId, BigDecimal returnTotal,
                         String refundMethod) throws SQLException {
        if ("ADJUST_STOCK".equals(rule.actionType())) {
            adjustStock(connection, rule, userId, source, sourceRowId);
        } else {
            createRow(connection, rule, userId, source, sourceRowId, saleId, saleTotal,
                    paymentMethod, returnId, returnTotal, refundMethod);
        }
        audit.log(connection, rule.businessId(), userId, rule.targetModuleId(), null,
                "WORKFLOW_EXECUTED", "WORKFLOW", rule.id(),
                "Regla «" + rule.name() + "» ejecutada desde " +
                        (returnId != null ? "devolución #" + returnId :
                                saleId == null ? "fila #" + sourceRowId : "venta #" + saleId) + ".",
                null, null);
    }

    private void adjustStock(Connection connection, Rule rule, long userId,
                             ModuleRow source, Long sourceRowId) throws SQLException {
        long productRowId;
        try { productRowId = Long.parseLong(clean(source.value(rule.relationColumnId()))); }
        catch (NumberFormatException error) {
            throw new IllegalArgumentException("La regla «" + rule.name() + "» necesita un producto vinculado.");
        }
        BigDecimal quantity = decimal(source.value(rule.quantityColumnId()), "Cantidad", true);
        ModuleRow product = grid.rowForUpdate(connection, productRowId, rule.targetModuleId(), rule.businessId());
        BigDecimal before = decimal(product.value(rule.stockColumnId()), "Existencias", false);
        BigDecimal change = "SUBTRACT".equals(rule.operation()) ? quantity.negate() : quantity;
        BigDecimal after = before.add(change);
        requireStoredDecimal(after, "Existencias finales", false);
        grid.updateSingleValue(connection, productRowId, rule.targetModuleId(), rule.businessId(),
                rule.stockColumnId(), plain(after));
        long auditId = audit.log(connection, rule.businessId(), userId, rule.targetModuleId(), productRowId,
                "STOCK_ADJUSTED", "ROW", productRowId,
                "Regla «" + rule.name() + "»: existencias " + plain(before) + " → " + plain(after) + ".",
                rule.stockColumnId() + "=" + plain(before), rule.stockColumnId() + "=" + plain(after));
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO inventory_movements
                  (business_id,module_id,row_id,column_id,source_panel_id,sale_id,workflow_rule_id,
                   audit_event_id,created_by,operation,quantity_before,quantity_change,quantity_after,note)
                VALUES(?,?,?,?,NULL,NULL,?,?,?,?,?,?,?,?)
                """)) {
            statement.setLong(1, rule.businessId());
            statement.setLong(2, rule.targetModuleId());
            statement.setLong(3, productRowId);
            statement.setLong(4, rule.stockColumnId());
            statement.setLong(5, rule.id());
            statement.setLong(6, auditId);
            statement.setLong(7, userId);
            statement.setString(8, rule.operation());
            statement.setBigDecimal(9, before);
            statement.setBigDecimal(10, change);
            statement.setBigDecimal(11, after);
            statement.setString(12, "Regla #" + rule.id() + ", fila origen #" + sourceRowId);
            statement.executeUpdate();
        }
    }

    private void createRow(Connection connection, Rule rule, long userId, ModuleRow source,
                           Long sourceRowId, Long saleId, BigDecimal saleTotal,
                           String paymentMethod, Long returnId, BigDecimal returnTotal,
                           String refundMethod) throws SQLException {
        Map<Long, String> values = new HashMap<>();
        for (Mapping mapping : rule.mappings()) {
            String value = switch (mapping.sourceType()) {
                case "SOURCE_COLUMN" -> source.value(mapping.sourceColumnId());
                case "SALE_ID" -> Long.toString(saleId);
                case "SALE_TOTAL" -> saleTotal.stripTrailingZeros().toPlainString();
                case "SALE_PAYMENT" -> paymentMethod;
                case "RETURN_ID" -> Long.toString(returnId);
                case "RETURN_TOTAL" -> returnTotal.stripTrailingZeros().toPlainString();
                case "RETURN_METHOD" -> refundMethod;
                case "LITERAL" -> mapping.literalValue();
                default -> throw new IllegalArgumentException("Origen de campo no válido.");
            };
            values.put(mapping.targetColumnId(), value);
        }
        // Direct repository call is intentional: a workflow-created row cannot trigger another rule.
        long createdId = grid.saveRow(connection, 0, rule.targetModuleId(), rule.businessId(), values);
        audit.log(connection, rule.businessId(), userId, rule.targetModuleId(), createdId,
                "ROW_CREATED", "ROW", createdId,
                "Fila creada por la regla «" + rule.name() + "» desde " +
                        (returnId != null ? "devolución #" + returnId :
                                saleId == null ? "fila #" + sourceRowId : "venta #" + saleId) + ".",
                null, RowService.snapshot(values));
    }

    private BigDecimal decimal(String raw, String label, boolean positive) {
        BigDecimal value;
        try { value = raw == null || raw.isBlank() ? BigDecimal.ZERO : new BigDecimal(raw.trim().replace(',', '.')); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(label + " no es un número válido."); }
        requireStoredDecimal(value, label, positive);
        return value;
    }

    private void requireStoredDecimal(BigDecimal value, String label, boolean positive) {
        if ((positive && value.signum() <= 0) || (!positive && value.signum() < 0))
            throw new IllegalArgumentException(label + (positive ? " debe ser mayor que cero." : " no puede ser negativa."));
        BigDecimal normalized = value.stripTrailingZeros();
        if (Math.max(0, normalized.scale()) > 4 ||
                Math.max(0, normalized.precision() - normalized.scale()) > 15)
            throw new IllegalArgumentException(label + " excede 15 enteros y 4 decimales.");
    }

    private String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
}
