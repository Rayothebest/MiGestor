package com.gestor.service;

import com.gestor.model.CustomModule;
import com.gestor.model.ModuleColumn;
import com.gestor.model.ModuleRow;
import com.gestor.repository.AlertRepository;
import com.gestor.repository.AlertRepository.AlertRuleView;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Evaluates active alerts on demand. This service never sends e-mail or external notifications. */
public class AlertService {
    private final AlertRepository alertRepository;
    private final ModuleRepository moduleRepository;
    private final GridRepository gridRepository;

    public AlertService() {
        this(new AlertRepository(), new ModuleRepository(), new GridRepository());
    }

    public AlertService(AlertRepository alertRepository, ModuleRepository moduleRepository,
                        GridRepository gridRepository) {
        this.alertRepository = alertRepository;
        this.moduleRepository = moduleRepository;
        this.gridRepository = gridRepository;
    }

    public record AlertMatch(String ruleName, long moduleId, String moduleName, long rowId,
                             String message, String currentValue) {
        public String getRuleName() { return ruleName; }
        public long getModuleId() { return moduleId; }
        public String getModuleName() { return moduleName; }
        public long getRowId() { return rowId; }
        public String getMessage() { return message; }
        public String getCurrentValue() { return currentValue; }
    }

    public List<AlertMatch> evaluateAll(long businessId) throws SQLException {
        return evaluate(businessId, alertRepository.listActive(businessId));
    }

    public List<AlertMatch> evaluate(long businessId) throws SQLException {
        return evaluateAll(businessId);
    }

    public List<AlertMatch> matches(long businessId) throws SQLException {
        return evaluateAll(businessId);
    }

    public List<AlertMatch> evaluate(long businessId, List<AlertRuleView> rules) throws SQLException {
        List<AlertMatch> matches = new ArrayList<>();
        Map<Long, ModuleData> modules = new HashMap<>();
        if (rules == null) return matches;
        for (AlertRuleView rule : rules) {
            if (rule == null || !rule.active()) continue;
            ModuleData data = modules.get(rule.moduleId());
            if (data == null) {
                data = loadModule(rule.moduleId(), businessId);
                if (data == null) continue;
                modules.put(rule.moduleId(), data);
            }
            ModuleColumn column = data.columns().stream()
                    .filter(candidate -> candidate.id() == rule.columnId())
                    .findFirst().orElse(null);
            if (column == null || !AlertRepository.ruleTypeFor(column.dataType()).equals(rule.ruleType())) continue;
            if (!validRule(rule)) continue;
            for (ModuleRow row : data.rows()) {
                String current = row.value(column.id());
                if (current == null || current.isBlank()) continue;
                if (matchesRule(rule.ruleType(), rule.conditionOperator(), current, rule.comparisonValue())) {
                    matches.add(new AlertMatch(rule.name(), rule.moduleId(), data.module().name(), row.id(),
                            renderMessage(rule, data.module(), column, row, current), current));
                }
            }
        }
        return matches;
    }

    public List<AlertMatch> evaluate(long businessId, AlertRuleView rule) throws SQLException {
        return evaluate(businessId, rule == null ? List.of() : List.of(rule));
    }

    public List<AlertRuleView> rulesWithMatchCounts(long businessId) throws SQLException {
        List<AlertRuleView> rules = alertRepository.list(businessId);
        List<AlertRuleView> result = new ArrayList<>(rules.size());
        for (AlertRuleView rule : rules) {
            long count = rule.active() ? evaluate(businessId, rule).size() : 0;
            result.add(rule.withMatchCount(count));
        }
        return result;
    }

    private ModuleData loadModule(long moduleId, long businessId) throws SQLException {
        CustomModule module = moduleRepository.find(moduleId, businessId);
        if (module == null || !module.isTable()) return null;
        return new ModuleData(module, gridRepository.columns(moduleId, businessId),
                gridRepository.rows(moduleId, businessId));
    }

    private boolean validRule(AlertRuleView rule) {
        try {
            AlertRepository.validateCondition(rule.ruleType(), rule.conditionOperator(), rule.comparisonValue());
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean matchesRule(String ruleType, String operator, String current, String comparison) {
        String type = normalize(ruleType);
        String condition = normalize(operator);
        try {
            return switch (type) {
                case "NUMBER" -> compareNumber(condition, number(current), number(comparison));
                case "DATE" -> compareDate(condition, LocalDate.parse(current.trim()), LocalDate.parse(comparison.trim()));
                case "VALUE" -> compareValue(condition, current.trim(), comparison.trim());
                default -> false;
            };
        } catch (NumberFormatException | DateTimeParseException ignored) {
            return false;
        }
    }

    private boolean compareNumber(String operator, BigDecimal current, BigDecimal expected) {
        int comparison = current.compareTo(expected);
        return switch (operator) {
            case "EQ" -> comparison == 0;
            case "NE" -> comparison != 0;
            case "GT" -> comparison > 0;
            case "GTE" -> comparison >= 0;
            case "LT" -> comparison < 0;
            case "LTE" -> comparison <= 0;
            default -> false;
        };
    }

    private boolean compareDate(String operator, LocalDate current, LocalDate expected) {
        return switch (operator) {
            case "EQ" -> current.isEqual(expected);
            case "NE" -> !current.isEqual(expected);
            case "BEFORE" -> current.isBefore(expected);
            case "AFTER" -> current.isAfter(expected);
            default -> false;
        };
    }

    private boolean compareValue(String operator, String current, String expected) {
        return switch (operator) {
            case "EQ" -> current.equalsIgnoreCase(expected);
            case "NE" -> !current.equalsIgnoreCase(expected);
            case "CONTAINS" -> current.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            default -> false;
        };
    }

    private BigDecimal number(String value) {
        String normalized = value.trim().replace(" ", "");
        if (normalized.contains(",") && normalized.contains(".")) normalized = normalized.replace(",", "");
        else normalized = normalized.replace(',', '.');
        return new BigDecimal(normalized);
    }

    private String renderMessage(AlertRuleView rule, CustomModule module, ModuleColumn column,
                                 ModuleRow row, String value) {
        String template = rule.messageTemplate();
        if (template == null || template.isBlank()) {
            template = "La fila #{rowId} de {module} cumple la alerta {rule}: {column} = {value}.";
        }
        return template.replace("{rule}", rule.name())
                .replace("{module}", module.name())
                .replace("{column}", column.name())
                .replace("{rowId}", Long.toString(row.id()))
                .replace("{value}", value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record ModuleData(CustomModule module, List<ModuleColumn> columns, List<ModuleRow> rows) {}
}
