package com.gestor.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public record PanelField(
        long id,
        long businessId,
        long panelModuleId,
        long columnId,
        String name,
        String dataType,
        String optionsText,
        boolean requiredField,
        String defaultValue,
        Long relatedModuleId,
        Long relatedDisplayColumnId,
        boolean visible,
        int position
) {
    public List<String> options() {
        if (optionsText == null || optionsText.isBlank()) return Collections.emptyList();
        return Arrays.stream(optionsText.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public long getId() { return id; }
    public long getBusinessId() { return businessId; }
    public long getPanelModuleId() { return panelModuleId; }
    public long getColumnId() { return columnId; }
    public String getName() { return name; }
    public String getDataType() { return dataType; }
    public String getOptionsText() { return optionsText; }
    public boolean isRequiredField() { return requiredField; }
    public boolean getRequiredField() { return requiredField; }
    public String getDefaultValue() { return defaultValue; }
    public Long getRelatedModuleId() { return relatedModuleId; }
    public Long getRelatedDisplayColumnId() { return relatedDisplayColumnId; }
    public boolean isVisible() { return visible; }
    public boolean getVisible() { return visible; }
    public int getPosition() { return position; }
    public List<String> getOptions() { return options(); }
    public boolean isRelation() { return "RELATION".equals(dataType); }
    public boolean getRelation() { return isRelation(); }
}
