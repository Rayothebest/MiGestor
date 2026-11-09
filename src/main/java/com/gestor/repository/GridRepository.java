package com.gestor.repository;

import com.gestor.config.Database;
import com.gestor.model.ModuleColumn;
import com.gestor.model.ModuleRow;
import com.gestor.model.ViewFilter;
import com.gestor.service.FormulaService;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class GridRepository {
    private static final Set<String> TYPES = Set.of("TEXT", "NUMBER", "DATE", "BOOLEAN", "SELECT", "RELATION", "LOOKUP", "FORMULA");
    private static final Set<String> FILTER_OPERATORS = Set.of("EQ", "NE", "CONTAINS", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY", "NOT_EMPTY");
    private static final String SYSTEM_ID_MESSAGE = "La columna ID es automática y no se puede crear, editar ni eliminar.";
    private static final String COLUMN_SELECT = """
            SELECT id,name,data_type,options_text,position,required_field,default_value,
                   related_module_id,related_display_column_id,relation_column_id,source_column_id,
                   formula_expression,low_stock_threshold
            FROM module_columns
            """;
    private final FormulaService formulas = new FormulaService();

    public record RowOption(long id, String label) {
        public long getId() { return id; }
        public String getLabel() { return label; }
    }

    public List<ModuleColumn> columns(long moduleId, long businessId) throws SQLException {
        try (Connection connection = Database.getConnection()) { return columns(connection, moduleId, businessId); }
    }

    public List<ModuleColumn> columns(Connection connection, long moduleId, long businessId) throws SQLException {
        List<ModuleColumn> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(COLUMN_SELECT + " WHERE module_id=? AND business_id=? ORDER BY position,id")) {
            statement.setLong(1, moduleId); statement.setLong(2, businessId);
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(mapColumn(rows)); }
        }
        return result;
    }

    public void createColumn(long moduleId, long businessId, String name, String type, String options) throws SQLException {
        createColumn(moduleId, businessId, name, type, options, false, null, null, null, null, null, null, null);
    }

    public void createColumn(long moduleId, long businessId, String name, String type, String options,
                             boolean required, String defaultValue, Long relatedModuleId, Long displayColumnId,
                             Long relationColumnId, Long sourceColumnId, String formula, BigDecimal threshold) throws SQLException {
        rejectReservedName(name); validateType(type);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureTable(connection, moduleId, businessId);
                validateMetadata(connection, 0, moduleId, businessId, type, options, defaultValue,
                        relatedModuleId, displayColumnId, relationColumnId, sourceColumnId, formula, threshold);
                String sql = """
                        INSERT INTO module_columns
                          (module_id,business_id,name,data_type,options_text,required_field,default_value,
                           related_module_id,related_display_column_id,relation_column_id,source_column_id,
                           formula_expression,low_stock_threshold,position)
                        SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,COALESCE(MAX(position),-1)+1
                        FROM module_columns WHERE module_id=? AND business_id=?
                        """;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindMetadata(statement, 1, moduleId, businessId, name, type, options, required, defaultValue,
                            relatedModuleId, displayColumnId, relationColumnId, sourceColumnId, formula, threshold);
                    statement.setLong(14, moduleId); statement.setLong(15, businessId); statement.executeUpdate();
                }
                connection.commit();
            } catch (Exception e) { connection.rollback(); rethrow(e); }
            finally { connection.setAutoCommit(true); }
        }
    }

    public boolean updateColumn(long id, long moduleId, long businessId, String name, String type, String options) throws SQLException {
        return updateColumn(id, moduleId, businessId, name, type, options, false, null, null, null, null, null, null, null);
    }

    public boolean updateColumn(long id, long moduleId, long businessId, String name, String type, String options,
                                boolean required, String defaultValue, Long relatedModuleId, Long displayColumnId,
                                Long relationColumnId, Long sourceColumnId, String formula, BigDecimal threshold) throws SQLException {
        rejectReservedName(name); validateType(type);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ModuleColumn existing = column(connection, id, moduleId, businessId);
                String workflow = workflowDependency(connection, businessId, id);
                if (existing != null && workflow != null &&
                        (!existing.dataType().equals(type) ||
                                ("SELECT".equals(type) && !Objects.equals(existing.optionsText(), options)) ||
                                ("RELATION".equals(type) &&
                                        !Objects.equals(existing.relatedModuleId(), relatedModuleId)))) {
                    throw new IllegalArgumentException("La columna se usa en la regla «" + workflow +
                            "». Edita o elimina primero esa regla.");
                }
                validateMetadata(connection, id, moduleId, businessId, type, options, defaultValue,
                        relatedModuleId, displayColumnId, relationColumnId, sourceColumnId, formula, threshold);
                String sql = """
                        UPDATE module_columns SET name=?,data_type=?,options_text=?,required_field=?,default_value=?,
                          related_module_id=?,related_display_column_id=?,relation_column_id=?,source_column_id=?,
                          formula_expression=?,low_stock_threshold=?
                        WHERE id=? AND module_id=? AND business_id=? AND LOWER(TRIM(name))<>'id'
                        """;
                boolean updated;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, name); statement.setString(2, type);
                    setString(statement, 3, "SELECT".equals(type) ? options : null);
                    statement.setBoolean(4, required && !derived(type));
                    setString(statement, 5, derived(type) ? null : nullable(defaultValue));
                    setLong(statement, 6, "RELATION".equals(type) ? relatedModuleId : null);
                    setLong(statement, 7, "RELATION".equals(type) ? displayColumnId : null);
                    setLong(statement, 8, "LOOKUP".equals(type) ? relationColumnId : null);
                    setLong(statement, 9, "LOOKUP".equals(type) ? sourceColumnId : null);
                    setString(statement, 10, "FORMULA".equals(type) ? formula : null);
                    setDecimal(statement, 11, "NUMBER".equals(type) ? threshold : null);
                    statement.setLong(12, id); statement.setLong(13, moduleId); statement.setLong(14, businessId);
                    updated = statement.executeUpdate() == 1;
                }
                if (!updated) throw new IllegalArgumentException(SYSTEM_ID_MESSAGE);
                if (derived(type)) {
                    try (PreparedStatement delete = connection.prepareStatement(
                            "DELETE cv FROM cell_values cv JOIN module_rows r ON r.id=cv.row_id " +
                                    "WHERE cv.column_id=? AND cv.business_id=? AND r.module_id=?")) {
                        delete.setLong(1, id); delete.setLong(2, businessId); delete.setLong(3, moduleId); delete.executeUpdate();
                    }
                }
                connection.commit(); return true;
            } catch (Exception e) { connection.rollback(); rethrow(e); return false; }
            finally { connection.setAutoCommit(true); }
        }
    }

    public boolean deleteColumn(long id, long moduleId, long businessId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            String workflow = workflowDependency(connection, businessId, id);
            if (workflow != null) throw new IllegalArgumentException(
                    "La columna se usa en la regla «" + workflow + "». Elimina primero esa regla.");
            try (PreparedStatement used = connection.prepareStatement(
                    "SELECT name FROM custom_modules WHERE business_id=? AND linked_module_id=? AND adjustment_column_id=? LIMIT 1")) {
                used.setLong(1, businessId); used.setLong(2, moduleId); used.setLong(3, id);
                try (ResultSet rows = used.executeQuery()) {
                    if (rows.next()) throw new IllegalArgumentException("La columna está configurada como existencia en el Panel «" + rows.getString(1) + "».");
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM module_columns WHERE id=? AND module_id=? AND business_id=? AND LOWER(TRIM(name))<>'id'")) {
                statement.setLong(1, id); statement.setLong(2, moduleId); statement.setLong(3, businessId);
                boolean deleted = statement.executeUpdate() == 1;
                if (!deleted) throw new IllegalArgumentException(SYSTEM_ID_MESSAGE);
                return true;
            }
        }
    }

    public List<ModuleRow> rows(long moduleId, long businessId) throws SQLException {
        try (Connection connection = Database.getConnection()) { return rows(connection, moduleId, businessId); }
    }

    public List<ModuleRow> rows(Connection connection, long moduleId, long businessId) throws SQLException {
        List<ModuleRow> result = rawRows(connection, moduleId, businessId, false);
        List<ModuleColumn> moduleColumns = columns(connection, moduleId, businessId);
        for (ModuleRow row : result) applyDerived(connection, businessId, moduleColumns, row.cells());
        return result;
    }

    public ModuleRow rowForUpdate(Connection connection, long rowId, long moduleId, long businessId) throws SQLException {
        List<ModuleRow> result = rawRows(connection, moduleId, businessId, true, rowId);
        if (result.isEmpty()) throw new IllegalArgumentException("El registro seleccionado ya no existe.");
        return result.get(0);
    }

    public long createRow(long moduleId, long businessId, Map<Long, String> values) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try { long id = saveRow(connection, 0, moduleId, businessId, values); connection.commit(); return id; }
            catch (Exception e) { connection.rollback(); rethrow(e); return -1; }
            finally { connection.setAutoCommit(true); }
        }
    }

    public long updateRow(long rowId, long moduleId, long businessId, Map<Long, String> values) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try { long id = saveRow(connection, rowId, moduleId, businessId, values); connection.commit(); return id; }
            catch (Exception e) { connection.rollback(); rethrow(e); return -1; }
            finally { connection.setAutoCommit(true); }
        }
    }

    public long saveRow(Connection connection, long rowId, long moduleId, long businessId, Map<Long, String> values) throws SQLException {
        boolean creating = rowId == 0;
        if (creating) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO module_rows(module_id,business_id) SELECT id,business_id FROM custom_modules " +
                            "WHERE id=? AND business_id=? AND module_type='TABLE'", Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, moduleId); statement.setLong(2, businessId);
                if (statement.executeUpdate() != 1) throw new IllegalArgumentException("La Tabla no existe.");
                try (ResultSet keys = statement.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("No se obtuvo el ID de fila."); rowId = keys.getLong(1); }
            }
        } else rowForUpdate(connection, rowId, moduleId, businessId);

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE value_text=VALUES(value_text)")) {
            for (ModuleColumn column : columns(connection, moduleId, businessId)) {
                if (column.isSystemId() || column.isReadOnly() || (!creating && !values.containsKey(column.id()))) continue;
                String value = clean(values.get(column.id()));
                if (value.isBlank() && column.defaultValue() != null) value = column.defaultValue();
                validateValue(connection, column, value, businessId);
                statement.setLong(1, rowId); statement.setLong(2, column.id()); statement.setLong(3, businessId); statement.setString(4, value); statement.addBatch();
            }
            statement.executeBatch();
        }
        return rowId;
    }

    public void updateSingleValue(Connection connection, long rowId, long moduleId, long businessId,
                                  long columnId, String value) throws SQLException {
        ModuleColumn column = column(connection, columnId, moduleId, businessId);
        if (column == null || column.isReadOnly()) throw new IllegalArgumentException("Columna de ajuste no válida.");
        rowForUpdate(connection, rowId, moduleId, businessId); validateValue(connection, column, value, businessId);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO cell_values(row_id,column_id,business_id,value_text) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE value_text=VALUES(value_text)")) {
            statement.setLong(1,rowId); statement.setLong(2,columnId); statement.setLong(3,businessId); statement.setString(4,value); statement.executeUpdate();
        }
    }

    public boolean deleteRow(long rowId, long moduleId, long businessId) throws SQLException {
        try (Connection connection = Database.getConnection()) { return deleteRow(connection, rowId, moduleId, businessId); }
    }

    public boolean deleteRow(Connection connection, long rowId, long moduleId, long businessId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM module_rows WHERE id=? AND module_id=? AND business_id=?")) {
            statement.setLong(1,rowId); statement.setLong(2,moduleId); statement.setLong(3,businessId); return statement.executeUpdate()==1;
        }
    }

    public Map<Long, List<RowOption>> relationOptions(List<ModuleColumn> moduleColumns, long businessId) throws SQLException {
        Map<Long,List<RowOption>> result=new HashMap<>();
        for(ModuleColumn column:moduleColumns)if(column.isRelation())result.put(column.id(),relationOptions(column,businessId));
        return result;
    }

    public List<RowOption> relationOptions(ModuleColumn relation,long businessId)throws SQLException{
        if(!relation.isRelation()||relation.relatedModuleId()==null)return List.of();
        List<ModuleColumn> targetColumns=columns(relation.relatedModuleId(),businessId);
        List<ModuleRow> targetRows=rows(relation.relatedModuleId(),businessId);
        return targetRows.stream().map(row->{String label=relation.relatedDisplayColumnId()==null?"":row.value(relation.relatedDisplayColumnId());if(label==null||label.isBlank())label=targetColumns.stream().filter(c->!c.isReadOnly()).map(c->row.value(c.id())).filter(v->v!=null&&!v.isBlank()).findFirst().orElse("Registro #"+row.id());return new RowOption(row.id(),"#"+row.id()+" · "+label);}).toList();
    }

    public List<ModuleRow> filterAndSort(List<ModuleRow> source,List<ModuleColumn> moduleColumns,String search,
                                         List<ViewFilter> filters,Long sortColumnId,String direction){
        String query=clean(search).toLowerCase(Locale.ROOT);
        List<ModuleRow> result=source.stream().filter(row->{if(!query.isBlank()){boolean found=Long.toString(row.id()).contains(query)||row.cells().values().stream().filter(Objects::nonNull).map(v->v.toLowerCase(Locale.ROOT)).anyMatch(v->v.contains(query));if(!found)return false;}for(ViewFilter filter:filters)if(!FILTER_OPERATORS.contains(filter.operator())||!matches(row.value(filter.columnId()),filter))return false;return true;}).collect(Collectors.toCollection(ArrayList::new));
        if(sortColumnId!=null){ModuleColumn column=moduleColumns.stream().filter(c->c.id()==sortColumnId).findFirst().orElse(null);if(column!=null){Comparator<ModuleRow> comparator=(a,b)->compareValues(a.value(sortColumnId),b.value(sortColumnId),column.dataType());if("DESC".equalsIgnoreCase(direction))comparator=comparator.reversed();result.sort(comparator.thenComparingLong(ModuleRow::id));}}
        return result;
    }

    public ModuleColumn column(long columnId,long moduleId,long businessId)throws SQLException{try(Connection connection=Database.getConnection()){return column(connection,columnId,moduleId,businessId);}}
    public ModuleColumn column(Connection connection,long columnId,long moduleId,long businessId)throws SQLException{try(PreparedStatement statement=connection.prepareStatement(COLUMN_SELECT+" WHERE id=? AND module_id=? AND business_id=?")){statement.setLong(1,columnId);statement.setLong(2,moduleId);statement.setLong(3,businessId);try(ResultSet rows=statement.executeQuery()){return rows.next()?mapColumn(rows):null;}}}

    private List<ModuleRow> rawRows(Connection connection,long moduleId,long businessId,boolean lock,long... onlyRow)throws SQLException{
        Map<Long,Map<Long,String>> data=new LinkedHashMap<>();String sql="SELECT r.id,c.column_id,c.value_text FROM module_rows r LEFT JOIN cell_values c ON c.row_id=r.id AND c.business_id=r.business_id WHERE r.module_id=? AND r.business_id=?"+(onlyRow.length==1?" AND r.id=?":"")+" ORDER BY r.id"+(lock?" FOR UPDATE":"");
        try(PreparedStatement statement=connection.prepareStatement(sql)){statement.setLong(1,moduleId);statement.setLong(2,businessId);if(onlyRow.length==1)statement.setLong(3,onlyRow[0]);try(ResultSet rows=statement.executeQuery()){while(rows.next()){long id=rows.getLong(1);data.computeIfAbsent(id,key->new HashMap<>());long col=rows.getLong(2);if(!rows.wasNull())data.get(id).put(col,rows.getString(3));}}}
        return data.entrySet().stream().map(e->new ModuleRow(e.getKey(),e.getValue())).toList();
    }

    private void applyDerived(Connection connection,long businessId,List<ModuleColumn> moduleColumns,Map<Long,String> cells)throws SQLException{
        Map<Long,ModuleColumn> byId=new HashMap<>();moduleColumns.forEach(c->byId.put(c.id(),c));
        for(ModuleColumn column:moduleColumns)if(column.isReadOnly())try{cells.put(column.id(),derivedValue(connection,businessId,column,byId,cells,new HashSet<>()));}catch(RuntimeException e){cells.put(column.id(),"#ERROR");}
    }

    private String derivedValue(Connection connection,long businessId,ModuleColumn column,Map<Long,ModuleColumn> moduleColumns,Map<Long,String> cells,Set<Long> visiting)throws SQLException{
        if(!visiting.add(column.id()))throw new IllegalArgumentException("La fórmula contiene un ciclo.");
        try{
            if(column.isLookup()){ModuleColumn relation=moduleColumns.get(column.relationColumnId());if(relation==null||!relation.isRelation()||column.sourceColumnId()==null)return "";long rowId=parseLong(cells.get(relation.id()));return rowId<=0?"":relatedCell(connection,rowId,relation.relatedModuleId(),column.sourceColumnId(),businessId);}
            if(column.isFormula()){BigDecimal value=formulas.evaluate(column.formulaExpression(),ref->{ModuleColumn dependency=moduleColumns.get(ref);String raw;try{raw=dependency!=null&&dependency.isReadOnly()?derivedValue(connection,businessId,dependency,moduleColumns,cells,visiting):cells.getOrDefault(ref,"");}catch(SQLException e){throw new IllegalArgumentException(e);}return decimal(raw);});return value.stripTrailingZeros().toPlainString();}
            return cells.getOrDefault(column.id(),"");
        }finally{visiting.remove(column.id());}
    }

    private String relatedCell(Connection connection,long rowId,Long moduleId,long columnId,long businessId)throws SQLException{
        if(moduleId==null)return "";String sql="SELECT cv.value_text FROM module_rows r JOIN module_columns mc ON mc.id=? AND mc.module_id=r.module_id AND mc.business_id=r.business_id LEFT JOIN cell_values cv ON cv.row_id=r.id AND cv.column_id=mc.id AND cv.business_id=r.business_id WHERE r.id=? AND r.module_id=? AND r.business_id=?";
        try(PreparedStatement statement=connection.prepareStatement(sql)){statement.setLong(1,columnId);statement.setLong(2,rowId);statement.setLong(3,moduleId);statement.setLong(4,businessId);try(ResultSet rows=statement.executeQuery()){return rows.next()?clean(rows.getString(1)):"";}}
    }

    private void validateMetadata(Connection connection,long columnId,long moduleId,long businessId,String type,String options,String defaultValue,Long relatedModuleId,Long displayColumnId,Long relationColumnId,Long sourceColumnId,String formula,BigDecimal threshold)throws SQLException{
        if("SELECT".equals(type)&&clean(options).isBlank())throw new IllegalArgumentException("La lista necesita al menos una opción.");
        if("RELATION".equals(type)){if(relatedModuleId==null||!isTable(connection,relatedModuleId,businessId))throw new IllegalArgumentException("Selecciona una Tabla relacionada del mismo negocio.");if(displayColumnId!=null&&column(connection,displayColumnId,relatedModuleId,businessId)==null)throw new IllegalArgumentException("La columna para mostrar no pertenece a la Tabla relacionada.");}
        if("LOOKUP".equals(type)){ModuleColumn relation=relationColumnId==null?null:column(connection,relationColumnId,moduleId,businessId);if(relation==null||!relation.isRelation()||relation.relatedModuleId()==null)throw new IllegalArgumentException("Selecciona una columna de relación válida.");if(sourceColumnId==null||column(connection,sourceColumnId,relation.relatedModuleId(),businessId)==null)throw new IllegalArgumentException("Selecciona el dato que se heredará.");}
        if("FORMULA".equals(type)){if(clean(formula).isBlank())throw new IllegalArgumentException("Escribe la fórmula.");Set<Long> refs=formulas.referencedColumns(formula);if(columnId>0&&refs.contains(columnId))throw new IllegalArgumentException("Una fórmula no puede referirse a sí misma.");for(Long ref:refs){ModuleColumn dependency=column(connection,ref,moduleId,businessId);if(dependency==null||!("NUMBER".equals(dependency.dataType())||dependency.isReadOnly()))throw new IllegalArgumentException("La fórmula contiene una columna inexistente o no numérica: {"+ref+"}.");}formulas.evaluate(formula,ignored->BigDecimal.ONE);}
        if(threshold!=null&&!"NUMBER".equals(type))throw new IllegalArgumentException("El umbral de stock solo se usa con números.");
        if(!derived(type)&&defaultValue!=null&&!defaultValue.isBlank()){ModuleColumn temp=new ModuleColumn(0,"valor predeterminado",type,options,0,false,null,relatedModuleId,displayColumnId,relationColumnId,sourceColumnId,formula,threshold);validateValue(connection,temp,defaultValue,businessId);}
    }

    private void validateValue(Connection connection,ModuleColumn column,String value,long businessId)throws SQLException{
        if(value==null||value.isBlank()){if(column.requiredField())throw new IllegalArgumentException("El campo «"+column.name()+"» es obligatorio.");return;}
        try{switch(column.dataType()){case "NUMBER"->new BigDecimal(value.replace(',','.'));case "DATE"->LocalDate.parse(value);case "BOOLEAN"->{if(!Set.of("true","false").contains(value))throw new IllegalArgumentException();}case "SELECT"->{if(!column.options().contains(value))throw new IllegalArgumentException();}case "RELATION"->{long rowId=Long.parseLong(value);if(column.relatedModuleId()==null||!rowExists(connection,rowId,column.relatedModuleId(),businessId))throw new IllegalArgumentException();}default->{}}}catch(Exception e){if(e instanceof SQLException sql)throw sql;throw new IllegalArgumentException("Valor inválido en «"+column.name()+"».");}
    }

    private boolean rowExists(Connection connection,long rowId,long moduleId,long businessId)throws SQLException{try(PreparedStatement statement=connection.prepareStatement("SELECT 1 FROM module_rows WHERE id=? AND module_id=? AND business_id=?")){statement.setLong(1,rowId);statement.setLong(2,moduleId);statement.setLong(3,businessId);try(ResultSet rows=statement.executeQuery()){return rows.next();}}}
    private boolean matches(String raw,ViewFilter filter){String value=clean(raw),expected=clean(filter.valueText());return switch(filter.operator()){case"EMPTY"->value.isBlank();case"NOT_EMPTY"->!value.isBlank();case"EQ"->value.equalsIgnoreCase(expected);case"NE"->!value.equalsIgnoreCase(expected);case"CONTAINS"->value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));case"GT"->compareFlexible(value,expected)>0;case"GTE"->compareFlexible(value,expected)>=0;case"LT"->compareFlexible(value,expected)<0;case"LTE"->compareFlexible(value,expected)<=0;case"BETWEEN"->compareFlexible(value,expected)>=0&&compareFlexible(value,clean(filter.secondValueText()))<=0;default->false;};}
    private int compareValues(String left,String right,String type){if("NUMBER".equals(type)||"FORMULA".equals(type))return decimal(left).compareTo(decimal(right));if("DATE".equals(type))try{return LocalDate.parse(clean(left)).compareTo(LocalDate.parse(clean(right)));}catch(Exception ignored){}return clean(left).compareToIgnoreCase(clean(right));}
    private int compareFlexible(String left,String right){try{return new BigDecimal(left.replace(',','.')).compareTo(new BigDecimal(right.replace(',','.')));}catch(Exception ignored){}try{return LocalDate.parse(left).compareTo(LocalDate.parse(right));}catch(Exception ignored){}return left.compareToIgnoreCase(right);}

    private ModuleColumn mapColumn(ResultSet rows)throws SQLException{return new ModuleColumn(rows.getLong("id"),rows.getString("name"),rows.getString("data_type"),rows.getString("options_text"),rows.getInt("position"),rows.getBoolean("required_field"),rows.getString("default_value"),optionalLong(rows,"related_module_id"),optionalLong(rows,"related_display_column_id"),optionalLong(rows,"relation_column_id"),optionalLong(rows,"source_column_id"),rows.getString("formula_expression"),rows.getBigDecimal("low_stock_threshold"));}
    private void bindMetadata(PreparedStatement s,int start,long moduleId,long businessId,String name,String type,String options,boolean required,String defaultValue,Long relatedModuleId,Long displayColumnId,Long relationColumnId,Long sourceColumnId,String formula,BigDecimal threshold)throws SQLException{int i=start;s.setLong(i++,moduleId);s.setLong(i++,businessId);s.setString(i++,name);s.setString(i++,type);setString(s,i++,"SELECT".equals(type)?options:null);s.setBoolean(i++,required&&!derived(type));setString(s,i++,derived(type)?null:nullable(defaultValue));setLong(s,i++,"RELATION".equals(type)?relatedModuleId:null);setLong(s,i++,"RELATION".equals(type)?displayColumnId:null);setLong(s,i++,"LOOKUP".equals(type)?relationColumnId:null);setLong(s,i++,"LOOKUP".equals(type)?sourceColumnId:null);setString(s,i++,"FORMULA".equals(type)?formula:null);setDecimal(s,i,"NUMBER".equals(type)?threshold:null);}
    private void ensureTable(Connection c,long moduleId,long businessId)throws SQLException{if(!isTable(c,moduleId,businessId))throw new IllegalArgumentException("La Tabla no existe.");}
    private String workflowDependency(Connection connection,long businessId,long columnId)throws SQLException{
        try(PreparedStatement statement=connection.prepareStatement("""
                SELECT name FROM workflow_rules
                WHERE business_id=? AND (condition_column_id=? OR relation_column_id=?
                  OR quantity_column_id=? OR stock_column_id=?) LIMIT 1
                """)){
            statement.setLong(1,businessId);
            for(int index=2;index<=5;index++)statement.setLong(index,columnId);
            try(ResultSet rows=statement.executeQuery()){if(rows.next())return rows.getString(1);}
        }
        try(PreparedStatement statement=connection.prepareStatement("""
                SELECT r.name FROM workflow_mappings m
                JOIN workflow_rules r ON r.id=m.rule_id AND r.business_id=m.business_id
                WHERE m.business_id=? AND (m.target_column_id=? OR m.source_column_id=?) LIMIT 1
                """)){
            statement.setLong(1,businessId);
            statement.setLong(2,columnId);
            statement.setLong(3,columnId);
            try(ResultSet rows=statement.executeQuery()){return rows.next()?rows.getString(1):null;}
        }
    }
    private boolean isTable(Connection c,long moduleId,long businessId)throws SQLException{try(PreparedStatement s=c.prepareStatement("SELECT 1 FROM custom_modules WHERE id=? AND business_id=? AND module_type='TABLE'")){s.setLong(1,moduleId);s.setLong(2,businessId);try(ResultSet r=s.executeQuery()){return r.next();}}}
    private void validateType(String type){if(!TYPES.contains(type))throw new IllegalArgumentException("Tipo de columna inválido.");}
    private void rejectReservedName(String name){if("ID".equalsIgnoreCase(clean(name)))throw new IllegalArgumentException(SYSTEM_ID_MESSAGE);}
    private boolean derived(String type){return"LOOKUP".equals(type)||"FORMULA".equals(type);}
    private String clean(String value){return value==null?"":value.trim();}
    private String nullable(String value){return clean(value).isBlank()?null:clean(value);}
    private BigDecimal decimal(String value){try{return new BigDecimal(clean(value).replace(',','.'));}catch(Exception ignored){return BigDecimal.ZERO;}}
    private long parseLong(String value){try{return Long.parseLong(clean(value));}catch(Exception ignored){return-1;}}
    private Long optionalLong(ResultSet r,String name)throws SQLException{long value=r.getLong(name);return r.wasNull()?null:value;}
    private void setLong(PreparedStatement s,int i,Long value)throws SQLException{if(value==null)s.setNull(i,Types.BIGINT);else s.setLong(i,value);}
    private void setString(PreparedStatement s,int i,String value)throws SQLException{if(value==null)s.setNull(i,Types.VARCHAR);else s.setString(i,value);}
    private void setDecimal(PreparedStatement s,int i,BigDecimal value)throws SQLException{if(value==null)s.setNull(i,Types.DECIMAL);else s.setBigDecimal(i,value);}
    private void rethrow(Exception e)throws SQLException{if(e instanceof SQLException sql)throw sql;if(e instanceof RuntimeException runtime)throw runtime;throw new SQLException(e);}
}
