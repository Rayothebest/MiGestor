package com.gestor.model;

import java.time.LocalDateTime;

public record ReportDefinition(
        long id,
        long businessId,
        long moduleId,
        Long userId,
        String name,
        String reportType,
        Long groupByColumnId,
        Long valueColumnId,
        Long dateColumnId,
        String aggregateFunction,
        String chartType,
        String filtersText,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public long getId() { return id; }
    public long getBusinessId() { return businessId; }
    public long getModuleId() { return moduleId; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getReportType() { return reportType; }
    public Long getGroupByColumnId() { return groupByColumnId; }
    public Long getValueColumnId() { return valueColumnId; }
    public Long getDateColumnId() { return dateColumnId; }
    public String getAggregateFunction() { return aggregateFunction; }
    public String getChartType() { return chartType; }
    public String getFiltersText() { return filtersText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
