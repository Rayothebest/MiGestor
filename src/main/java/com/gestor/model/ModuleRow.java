package com.gestor.model;

import java.util.Map;

public record ModuleRow(long id, Map<Long, String> cells) {
    public String value(long columnId) { return cells.getOrDefault(columnId, ""); }
    public long getId(){ return id; }
    public Map<Long,String> getCells(){ return cells; }
}
