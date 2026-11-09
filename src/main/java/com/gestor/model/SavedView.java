package com.gestor.model;

import java.time.LocalDateTime;

public record SavedView(
        long id,
        long businessId,
        long moduleId,
        Long userId,
        String name,
        Long sortColumnId,
        String sortDirection,
        boolean defaultView,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public long getId() { return id; }
    public long getBusinessId() { return businessId; }
    public long getModuleId() { return moduleId; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public Long getSortColumnId() { return sortColumnId; }
    public String getSortDirection() { return sortDirection; }
    public boolean isDefaultView() { return defaultView; }
    public boolean getDefaultView() { return defaultView; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
