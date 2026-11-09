package com.gestor.model;

import java.time.LocalDateTime;

public record AuditEvent(
        long id,
        long businessId,
        Long userId,
        Long moduleId,
        Long rowId,
        String eventType,
        String entityType,
        Long entityId,
        String summary,
        String beforeValues,
        String afterValues,
        LocalDateTime createdAt
) {
    public long getId() { return id; }
    public long getBusinessId() { return businessId; }
    public Long getUserId() { return userId; }
    public Long getModuleId() { return moduleId; }
    public Long getRowId() { return rowId; }
    public String getEventType() { return eventType; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getSummary() { return summary; }
    public String getBeforeValues() { return beforeValues; }
    public String getAfterValues() { return afterValues; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
