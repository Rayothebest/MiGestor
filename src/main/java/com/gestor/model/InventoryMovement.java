package com.gestor.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryMovement(
        long id,
        long businessId,
        Long moduleId,
        Long rowId,
        Long columnId,
        Long sourcePanelId,
        Long auditEventId,
        Long createdBy,
        String operation,
        BigDecimal quantityBefore,
        BigDecimal quantityChange,
        BigDecimal quantityAfter,
        String note,
        LocalDateTime createdAt
) {
    public long getId() { return id; }
    public long getBusinessId() { return businessId; }
    public Long getModuleId() { return moduleId; }
    public Long getRowId() { return rowId; }
    public Long getColumnId() { return columnId; }
    public Long getSourcePanelId() { return sourcePanelId; }
    public Long getAuditEventId() { return auditEventId; }
    public Long getCreatedBy() { return createdBy; }
    public String getOperation() { return operation; }
    public BigDecimal getQuantityBefore() { return quantityBefore; }
    public BigDecimal getQuantityChange() { return quantityChange; }
    public BigDecimal getQuantityAfter() { return quantityAfter; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
