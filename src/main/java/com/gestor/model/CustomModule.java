package com.gestor.model;

public record CustomModule(
        long id,
        String name,
        String description,
        String moduleType,
        Long linkedModuleId,
        String linkedModuleName,
        String panelAction,
        Long adjustmentColumnId,
        String adjustmentColumnName,
        String adjustmentOperation,
        boolean allowNegative
) {
    public CustomModule(long id, String name, String description, String moduleType,
                        Long linkedModuleId, String linkedModuleName, String panelAction) {
        this(id, name, description, moduleType, linkedModuleId, linkedModuleName, panelAction,
                null, null, null, false);
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getModuleType() { return moduleType; }
    public Long getLinkedModuleId() { return linkedModuleId; }
    public String getLinkedModuleName() { return linkedModuleName; }
    public String getPanelAction() { return panelAction; }
    public Long getAdjustmentColumnId() { return adjustmentColumnId; }
    public String getAdjustmentColumnName() { return adjustmentColumnName; }
    public String getAdjustmentOperation() { return adjustmentOperation; }
    public boolean isAllowNegative() { return allowNegative; }
    public boolean getAllowNegative() { return allowNegative; }
    public boolean isTable() { return "TABLE".equals(moduleType); }
    public boolean isPanel() { return "PANEL".equals(moduleType); }
    public boolean getTable() { return isTable(); }
    public boolean getPanel() { return isPanel(); }
    public boolean isRegisterPanel() { return isPanel() && "REGISTER".equals(panelAction); }
    public boolean isRemovePanel() { return isPanel() && "REMOVE".equals(panelAction); }
    public boolean isAdjustPanel() { return isPanel() && "ADJUST".equals(panelAction); }
    public boolean getRegisterPanel() { return isRegisterPanel(); }
    public boolean getRemovePanel() { return isRemovePanel(); }
    public boolean getAdjustPanel() { return isAdjustPanel(); }
}
