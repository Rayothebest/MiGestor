package com.gestor.model;

import java.time.LocalDateTime;

public record AlertRule(
        long id,
        long businessId,
        long moduleId,
        long columnId,
        String name,
        String ruleType,
        String conditionOperator,
        String comparisonValue,
        String messageTemplate,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public long getId() { return id; }
    public long getBusinessId() { return businessId; }
    public long getModuleId() { return moduleId; }
    public long getColumnId() { return columnId; }
    public String getName() { return name; }
    public String getRuleType() { return ruleType; }
    public String getConditionOperator() { return conditionOperator; }
    public String getComparisonValue() { return comparisonValue; }
    public String getMessageTemplate() { return messageTemplate; }
    public boolean isActive() { return active; }
    public boolean getActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
