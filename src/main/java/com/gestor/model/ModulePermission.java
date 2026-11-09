package com.gestor.model;

public record ModulePermission(
        long id,
        long businessId,
        long userId,
        long moduleId,
        boolean canView,
        boolean canCreate,
        boolean canEdit,
        boolean canDelete,
        boolean canManage
) {
    public long getId() { return id; }
    public long getBusinessId() { return businessId; }
    public long getUserId() { return userId; }
    public long getModuleId() { return moduleId; }
    public boolean isCanView() { return canView; }
    public boolean isCanCreate() { return canCreate; }
    public boolean isCanEdit() { return canEdit; }
    public boolean isCanDelete() { return canDelete; }
    public boolean isCanManage() { return canManage; }
    public boolean getCanView() { return canView; }
    public boolean getCanCreate() { return canCreate; }
    public boolean getCanEdit() { return canEdit; }
    public boolean getCanDelete() { return canDelete; }
    public boolean getCanManage() { return canManage; }
}
