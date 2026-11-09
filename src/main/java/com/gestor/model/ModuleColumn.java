package com.gestor.model;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public record ModuleColumn(
        long id,
        String name,
        String dataType,
        String optionsText,
        int position,
        boolean requiredField,
        String defaultValue,
        Long relatedModuleId,
        Long relatedDisplayColumnId,
        Long relationColumnId,
        Long sourceColumnId,
        String formulaExpression,
        BigDecimal lowStockThreshold
) {
    public ModuleColumn(long id, String name, String dataType, String optionsText, int position) {
        this(id, name, dataType, optionsText, position, false, null,
                null, null, null, null, null, null);
    }

    public boolean isSystemId() {
        return "ID".equalsIgnoreCase(name == null ? "" : name.trim());
    }

    public List<String> options() {
        if (optionsText == null || optionsText.isBlank()) return Collections.emptyList();
        return Arrays.stream(optionsText.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
    public long getId(){ return id; }
    public String getName(){ return name; }
    public String getDataType(){ return dataType; }
    public String getOptionsText(){ return optionsText; }
    public int getPosition(){ return position; }
    public boolean isRequiredField(){ return requiredField; }
    public boolean getRequiredField(){ return requiredField; }
    public String getDefaultValue(){ return defaultValue; }
    public Long getRelatedModuleId(){ return relatedModuleId; }
    public Long getRelatedDisplayColumnId(){ return relatedDisplayColumnId; }
    public Long getRelationColumnId(){ return relationColumnId; }
    public Long getSourceColumnId(){ return sourceColumnId; }
    public String getFormulaExpression(){ return formulaExpression; }
    public BigDecimal getLowStockThreshold(){ return lowStockThreshold; }
    public List<String> getOptions(){ return options(); }
    public boolean getSystemId(){ return isSystemId(); }
    public boolean isRelation(){ return "RELATION".equals(dataType); }
    public boolean isLookup(){ return "LOOKUP".equals(dataType); }
    public boolean isFormula(){ return "FORMULA".equals(dataType); }
    public boolean isReadOnly(){ return isLookup() || isFormula(); }
    public boolean getRelation(){ return isRelation(); }
    public boolean getLookup(){ return isLookup(); }
    public boolean getFormula(){ return isFormula(); }
    public boolean getReadOnly(){ return isReadOnly(); }
}
