package com.gestor.model;

public record ViewFilter(
        long id,
        long savedViewId,
        long columnId,
        String operator,
        String valueText,
        String secondValueText,
        int position
) {
    public long getId() { return id; }
    public long getSavedViewId() { return savedViewId; }
    public long getColumnId() { return columnId; }
    public String getOperator() { return operator; }
    public String getValueText() { return valueText; }
    public String getSecondValueText() { return secondValueText; }
    public int getPosition() { return position; }
}
