package com.gestor.service;

import com.gestor.model.CustomModule;
import com.gestor.model.ModuleColumn;
import com.gestor.model.ModuleRow;
import com.gestor.repository.GridRepository;
import com.gestor.repository.ModuleRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Calculates report summaries in memory after loading business-scoped rows and columns. */
public class ReportService {
    private static final MathContext MATH = MathContext.DECIMAL128;
    private static final Set<String> AGGREGATES = Set.of("COUNT", "SUM", "AVG", "MIN", "MAX");
    private static final int MAX_GROUPS = 12;

    private final ModuleRepository moduleRepository;
    private final GridRepository gridRepository;

    public ReportService() {
        this(new ModuleRepository(), new GridRepository());
    }

    public ReportService(ModuleRepository moduleRepository, GridRepository gridRepository) {
        this.moduleRepository = moduleRepository;
        this.gridRepository = gridRepository;
    }

    public record NumericSummary(String name, BigDecimal total, BigDecimal average,
                                 BigDecimal min, BigDecimal max) {
        public String getName() { return name; }
        public BigDecimal getTotal() { return total; }
        public BigDecimal getAverage() { return average; }
        public BigDecimal getMin() { return min; }
        public BigDecimal getMax() { return max; }
    }

    public record GroupSummary(String label, long count, int percent) {
        public String getLabel() { return label; }
        public long getCount() { return count; }
        public int getPercent() { return percent; }
    }

    public record ChartPoint(String label, BigDecimal value) {
        public String getLabel() { return label; }
        public BigDecimal getValue() { return value; }
    }

    public record ChartView(String groupName, String aggregateName, String chartType,
                            String note, List<ChartPoint> points) {
        public String getGroupName() { return groupName; }
        public String getAggregateName() { return aggregateName; }
        public String getChartType() { return chartType; }
        public String getNote() { return note; }
        public List<ChartPoint> getPoints() { return points; }
    }

    /** Uses the saved report columns and calculation rather than the generic table distribution. */
    public ChartView chart(long moduleId, long businessId, Long groupByColumnId,
                           Long valueColumnId, String aggregateFunction, String chartType) throws SQLException {
        requireTable(moduleId, businessId);
        String aggregate = normalizeAggregate(aggregateFunction);
        List<ModuleColumn> columns = gridRepository.columns(moduleId, businessId);
        List<ModuleRow> rows = gridRepository.rows(moduleId, businessId);
        ModuleColumn group = groupByColumnId == null ? chooseGroupColumn(columns, rows) : columns.stream()
                .filter(column -> column.id() == groupByColumnId && !column.isSystemId()).findFirst().orElse(null);
        if (group == null) return new ChartView("", aggregate, "BAR", "Selecciona una columna para agrupar.", List.of());
        ModuleColumn value = valueColumnId == null ? null : columns.stream()
                .filter(column -> column.id() == valueColumnId && Set.of("NUMBER", "FORMULA").contains(column.dataType()))
                .findFirst().orElse(null);
        if (!"COUNT".equals(aggregate) && value == null) {
            return new ChartView(group.name(), aggregate, "BAR", "La columna numérica ya no está disponible.", List.of());
        }

        Map<String, ChartAccumulator> groups = new LinkedHashMap<>();
        for (ModuleRow row : rows) {
            BigDecimal number = "COUNT".equals(aggregate) ? null : parseNumber(row.value(value.id()));
            if (!"COUNT".equals(aggregate) && number == null) continue;
            String raw = row.value(group.id());
            String label = raw == null || raw.isBlank() ? "Sin valor" : raw.trim();
            groups.computeIfAbsent(label, ignored -> new ChartAccumulator()).add(number);
        }
        String type = chartType == null ? "BAR" : chartType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("BAR", "PIE", "LINE").contains(type)) type = "BAR";
        boolean dateLine = "LINE".equals(type) && "DATE".equals(group.dataType());
        if ("LINE".equals(type) && !dateLine) type = "BAR";
        List<Map.Entry<String, ChartAccumulator>> ordered = new ArrayList<>(groups.entrySet());
        if (dateLine) ordered.sort(Map.Entry.comparingByKey());
        else ordered.sort((left, right) -> {
            int byValue = right.getValue().value(aggregate).compareTo(left.getValue().value(aggregate));
            return byValue != 0 ? byValue : String.CASE_INSENSITIVE_ORDER.compare(left.getKey(), right.getKey());
        });
        List<ChartPoint> points = new ArrayList<>();
        if (dateLine) {
            int start = Math.max(0, ordered.size() - MAX_GROUPS);
            for (int i = start; i < ordered.size(); i++) {
                var entry = ordered.get(i);
                points.add(new ChartPoint(shortLabel(entry.getKey()), display(entry.getValue().value(aggregate))));
            }
        } else {
            int visible = Math.min(MAX_GROUPS, ordered.size());
            if (ordered.size() > MAX_GROUPS) visible = MAX_GROUPS - 1;
            for (int i = 0; i < visible; i++) {
                var entry = ordered.get(i);
                points.add(new ChartPoint(shortLabel(entry.getKey()), display(entry.getValue().value(aggregate))));
            }
            if (visible < ordered.size()) {
                ChartAccumulator other = new ChartAccumulator();
                for (int i = visible; i < ordered.size(); i++) other.merge(ordered.get(i).getValue());
                points.add(new ChartPoint("Otros", display(other.value(aggregate))));
            }
        }
        String note = "";
        if ("PIE".equals(type) && points.stream().anyMatch(point -> point.value().signum() < 0)) {
            type = "BAR";
            note = "La gráfica circular se muestra como barras porque hay valores negativos.";
        }
        if ("PIE".equals(type) && points.stream().allMatch(point -> point.value().signum() == 0)) {
            type = "BAR";
        }
        return new ChartView(group.name(), aggregate, type, note, List.copyOf(points));
    }

    private String shortLabel(String label) {
        return label.length() <= 80 ? label : label.substring(0, 79) + "…";
    }

    private static final class ChartAccumulator {
        private long count;
        private BigDecimal sum = BigDecimal.ZERO;
        private BigDecimal min;
        private BigDecimal max;

        void add(BigDecimal number) {
            count++;
            if (number == null) return;
            sum = sum.add(number, MATH);
            min = min == null ? number : min.min(number);
            max = max == null ? number : max.max(number);
        }

        void merge(ChartAccumulator other) {
            count += other.count;
            sum = sum.add(other.sum, MATH);
            if (other.min != null) min = min == null ? other.min : min.min(other.min);
            if (other.max != null) max = max == null ? other.max : max.max(other.max);
        }

        BigDecimal value(String aggregate) {
            return switch (aggregate) {
                case "COUNT" -> BigDecimal.valueOf(count);
                case "SUM" -> sum;
                case "AVG" -> count == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(count), MATH);
                case "MIN" -> min == null ? BigDecimal.ZERO : min;
                case "MAX" -> max == null ? BigDecimal.ZERO : max;
                default -> BigDecimal.ZERO;
            };
        }
    }

    public int rowCount(long moduleId, long businessId) throws SQLException {
        requireTable(moduleId, businessId);
        return gridRepository.rows(moduleId, businessId).size();
    }

    public List<NumericSummary> numericSummaries(long moduleId, long businessId) throws SQLException {
        requireTable(moduleId, businessId);
        List<ModuleColumn> columns = gridRepository.columns(moduleId, businessId);
        List<ModuleRow> rows = gridRepository.rows(moduleId, businessId);
        return numericSummaries(columns, rows);
    }

    public List<NumericSummary> summarizeNumericColumns(long moduleId, long businessId) throws SQLException {
        return numericSummaries(moduleId, businessId);
    }

    public List<NumericSummary> numericSummaries(List<ModuleColumn> columns, List<ModuleRow> rows) {
        List<NumericSummary> summaries = new ArrayList<>();
        for (ModuleColumn column : safe(columns)) {
            if (column.isSystemId() || !Set.of("NUMBER", "FORMULA").contains(column.dataType())) continue;
            List<BigDecimal> values = numericValues(column.id(), rows);
            if (values.isEmpty()) continue;
            BigDecimal total = values.stream().reduce(BigDecimal.ZERO, (left, right) -> left.add(right, MATH));
            BigDecimal average = total.divide(BigDecimal.valueOf(values.size()), MATH);
            BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            summaries.add(new NumericSummary(column.name(), display(total), display(average),
                    display(min), display(max)));
        }
        return summaries;
    }

    public List<GroupSummary> groupSummaries(long moduleId, long businessId) throws SQLException {
        requireTable(moduleId, businessId);
        List<ModuleColumn> columns = gridRepository.columns(moduleId, businessId);
        List<ModuleRow> rows = gridRepository.rows(moduleId, businessId);
        ModuleColumn column = chooseGroupColumn(columns, rows);
        return column == null ? List.of() : groupSummaries(columns, rows, column.id());
    }

    public List<GroupSummary> summarizeGroups(long moduleId, long businessId) throws SQLException {
        return groupSummaries(moduleId, businessId);
    }

    public List<GroupSummary> groupSummaries(long moduleId, long businessId, long columnId) throws SQLException {
        requireTable(moduleId, businessId);
        List<ModuleColumn> columns = gridRepository.columns(moduleId, businessId);
        List<ModuleRow> rows = gridRepository.rows(moduleId, businessId);
        return groupSummaries(columns, rows, columnId);
    }

    public List<GroupSummary> groupSummaries(List<ModuleColumn> columns, List<ModuleRow> rows, Long columnId) {
        if (columnId == null || rows == null || rows.isEmpty()) return List.of();
        ModuleColumn selected = safe(columns).stream()
                .filter(column -> column.id() == columnId && !column.isSystemId())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("La columna de agrupación no pertenece a la tabla."));

        Map<String, Long> counts = new LinkedHashMap<>();
        for (ModuleRow row : rows) {
            String raw = row.value(selected.id());
            String label = raw == null ? "" : raw.trim();
            counts.merge(label, 1L, Long::sum);
        }
        List<Map.Entry<String, Long>> ordered = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .toList();
        long total = rows.size();
        List<GroupSummary> result = new ArrayList<>();
        int visible = Math.min(MAX_GROUPS, ordered.size());
        if (ordered.size() > MAX_GROUPS) visible = MAX_GROUPS - 1;
        for (int index = 0; index < visible; index++) {
            Map.Entry<String, Long> entry = ordered.get(index);
            result.add(new GroupSummary(entry.getKey(), entry.getValue(), percent(entry.getValue(), total)));
        }
        if (visible < ordered.size()) {
            long otherCount = ordered.subList(visible, ordered.size()).stream()
                    .mapToLong(Map.Entry::getValue).sum();
            result.add(new GroupSummary("Otros", otherCount, percent(otherCount, total)));
        }
        return result;
    }

    public BigDecimal aggregate(long moduleId, long businessId, Long columnId,
                                String aggregateFunction) throws SQLException {
        requireTable(moduleId, businessId);
        String aggregate = normalizeAggregate(aggregateFunction);
        List<ModuleRow> rows = gridRepository.rows(moduleId, businessId);
        if ("COUNT".equals(aggregate)) return BigDecimal.valueOf(rows.size());
        if (columnId == null) throw new IllegalArgumentException("Selecciona una columna numérica para calcular el reporte.");
        ModuleColumn column = gridRepository.columns(moduleId, businessId).stream()
                .filter(candidate -> candidate.id() == columnId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("La columna no pertenece a la tabla."));
        if (!Set.of("NUMBER", "FORMULA").contains(column.dataType())) {
            throw new IllegalArgumentException("El cálculo requiere una columna numérica.");
        }
        List<BigDecimal> values = numericValues(columnId, rows);
        if (values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal result = switch (aggregate) {
            case "SUM" -> values.stream().reduce(BigDecimal.ZERO, (left, right) -> left.add(right, MATH));
            case "AVG" -> values.stream().reduce(BigDecimal.ZERO, (left, right) -> left.add(right, MATH))
                    .divide(BigDecimal.valueOf(values.size()), MATH);
            case "MIN" -> values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            case "MAX" -> values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            default -> throw new IllegalArgumentException("La operación del reporte no es válida.");
        };
        return display(result);
    }

    private CustomModule requireTable(long moduleId, long businessId) throws SQLException {
        CustomModule module = moduleRepository.find(moduleId, businessId);
        if (module == null || !module.isTable()) {
            throw new IllegalArgumentException("La tabla seleccionada no existe en este negocio.");
        }
        return module;
    }

    private ModuleColumn chooseGroupColumn(List<ModuleColumn> columns, List<ModuleRow> rows) {
        if (rows == null || rows.isEmpty()) return null;
        return safe(columns).stream()
                .filter(column -> !column.isSystemId())
                .filter(column -> hasAtLeastOneValue(column.id(), rows))
                .min(Comparator.comparingInt(this::groupPriority)
                        .thenComparingInt(ModuleColumn::position)
                        .thenComparingLong(ModuleColumn::id))
                .orElse(null);
    }

    private int groupPriority(ModuleColumn column) {
        return switch (column.dataType()) {
            case "SELECT", "BOOLEAN" -> 0;
            case "RELATION", "LOOKUP" -> 1;
            case "DATE" -> 2;
            case "TEXT" -> 3;
            default -> 4;
        };
    }

    private boolean hasAtLeastOneValue(long columnId, List<ModuleRow> rows) {
        return rows.stream().map(row -> row.value(columnId)).anyMatch(value -> value != null && !value.isBlank());
    }

    private List<BigDecimal> numericValues(long columnId, List<ModuleRow> rows) {
        List<BigDecimal> values = new ArrayList<>();
        if (rows == null) return values;
        for (ModuleRow row : rows) {
            BigDecimal parsed = parseNumber(row.value(columnId));
            if (parsed != null) values.add(parsed);
        }
        return values;
    }

    private BigDecimal parseNumber(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replace(" ", "");
        if (normalized.contains(",") && normalized.contains(".")) normalized = normalized.replace(",", "");
        else normalized = normalized.replace(',', '.');
        try {
            return new BigDecimal(normalized, MATH);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeAggregate(String value) {
        String aggregate = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!AGGREGATES.contains(aggregate)) throw new IllegalArgumentException("La operación del reporte no es válida.");
        return aggregate;
    }

    private int percent(long count, long total) {
        if (total <= 0) return 0;
        return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP).intValue();
    }

    private BigDecimal display(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        BigDecimal rounded = value.scale() > 4 ? value.setScale(4, RoundingMode.HALF_UP) : value;
        return rounded.stripTrailingZeros();
    }

    private List<ModuleColumn> safe(List<ModuleColumn> columns) {
        return columns == null ? List.of() : columns;
    }
}
