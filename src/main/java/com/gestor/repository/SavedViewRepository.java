package com.gestor.repository;

import com.gestor.config.Database;
import com.gestor.model.SavedView;
import com.gestor.model.ViewFilter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SavedViewRepository {
    private static final Set<String> DIRECTIONS = Set.of("ASC", "DESC");
    private static final Set<String> OPERATORS = Set.of("EQ", "NE", "CONTAINS", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY", "NOT_EMPTY");

    public List<SavedView> list(long moduleId, long businessId, long userId) throws SQLException {
        List<SavedView> views = new ArrayList<>();
        String sql = "SELECT id,business_id,module_id,user_id,name,sort_column_id,sort_direction,default_view,created_at,updated_at " +
                "FROM saved_views WHERE module_id=? AND business_id=? AND (user_id=? OR user_id IS NULL) ORDER BY default_view DESC,name";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, moduleId); statement.setLong(2, businessId); statement.setLong(3, userId);
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) views.add(map(rows)); }
        }
        return views;
    }

    public SavedView find(long id, long moduleId, long businessId, long userId) throws SQLException {
        String sql = "SELECT id,business_id,module_id,user_id,name,sort_column_id,sort_direction,default_view,created_at,updated_at " +
                "FROM saved_views WHERE id=? AND module_id=? AND business_id=? AND (user_id=? OR user_id IS NULL)";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1,id); statement.setLong(2,moduleId); statement.setLong(3,businessId); statement.setLong(4,userId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? map(rows) : null; }
        }
    }

    public List<ViewFilter> filters(long viewId) throws SQLException {
        List<ViewFilter> filters = new ArrayList<>();
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT id,saved_view_id,column_id,filter_operator,value_text,second_value_text,position FROM saved_view_filters WHERE saved_view_id=? ORDER BY position,id")) {
            statement.setLong(1,viewId);
            try (ResultSet rows=statement.executeQuery()) { while(rows.next()) filters.add(new ViewFilter(rows.getLong(1),rows.getLong(2),rows.getLong(3),rows.getString(4),rows.getString(5),rows.getString(6),rows.getInt(7))); }
        }
        return filters;
    }

    public long create(long businessId,long moduleId,long userId,String name,Long sortColumnId,String direction,
                       boolean defaultView,List<ViewFilter> filters)throws SQLException{
        if(!DIRECTIONS.contains(direction))throw new IllegalArgumentException("Orden no válido.");
        for(ViewFilter filter:filters)if(!OPERATORS.contains(filter.operator()))throw new IllegalArgumentException("Filtro no válido.");
        try(Connection connection=Database.getConnection()){connection.setAutoCommit(false);try{
            if(defaultView){try(PreparedStatement clear=connection.prepareStatement("UPDATE saved_views SET default_view=FALSE WHERE business_id=? AND module_id=? AND user_id=?")){clear.setLong(1,businessId);clear.setLong(2,moduleId);clear.setLong(3,userId);clear.executeUpdate();}}
            if (sortColumnId != null) {
                try (PreparedStatement validateSort = connection.prepareStatement(
                        "SELECT 1 FROM module_columns WHERE id=? AND module_id=? AND business_id=?")) {
                    validateSort.setLong(1, sortColumnId);
                    validateSort.setLong(2, moduleId);
                    validateSort.setLong(3, businessId);
                    try (ResultSet row = validateSort.executeQuery()) {
                        if (!row.next()) throw new IllegalArgumentException("La columna de orden no pertenece a esta Tabla.");
                    }
                }
            }
            long id;
            String sql="INSERT INTO saved_views(business_id,module_id,user_id,name,sort_column_id,sort_direction,default_view) " +
                    "SELECT m.business_id,m.id,u.id,?,?,?,? FROM custom_modules m JOIN users u ON u.id=? AND u.business_id=m.business_id " +
                    "WHERE m.id=? AND m.business_id=? AND m.module_type='TABLE'";
            try(PreparedStatement insert=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
                insert.setString(1,name);
                if(sortColumnId==null)insert.setNull(2,Types.BIGINT);else insert.setLong(2,sortColumnId);
                insert.setString(3,direction);
                insert.setBoolean(4,defaultView);
                insert.setLong(5,userId);
                insert.setLong(6,moduleId);
                insert.setLong(7,businessId);
                if(insert.executeUpdate()!=1)throw new IllegalArgumentException("Módulo, usuario o columna de orden no válidos.");
                try(ResultSet keys=insert.getGeneratedKeys()){if(!keys.next())throw new SQLException("No se obtuvo el ID de la vista.");id=keys.getLong(1);}
            }
            try(PreparedStatement insertFilter=connection.prepareStatement("INSERT INTO saved_view_filters(saved_view_id,column_id,filter_operator,value_text,second_value_text,position) SELECT ?,c.id,?,?,?,? FROM module_columns c WHERE c.id=? AND c.module_id=? AND c.business_id=?")){int position=0;for(ViewFilter filter:filters){insertFilter.setLong(1,id);insertFilter.setString(2,filter.operator());insertFilter.setString(3,filter.valueText());insertFilter.setString(4,filter.secondValueText());insertFilter.setInt(5,position++);insertFilter.setLong(6,filter.columnId());insertFilter.setLong(7,moduleId);insertFilter.setLong(8,businessId);if(insertFilter.executeUpdate()!=1)throw new IllegalArgumentException("Columna de filtro no válida.");}}
            connection.commit();return id;
        }catch(Exception e){connection.rollback();if(e instanceof SQLException sql)throw sql;if(e instanceof RuntimeException runtime)throw runtime;throw new SQLException(e);}finally{connection.setAutoCommit(true);}}
    }

    public boolean delete(long id,long businessId,long userId)throws SQLException{try(Connection connection=Database.getConnection();PreparedStatement statement=connection.prepareStatement("DELETE FROM saved_views WHERE id=? AND business_id=? AND user_id=?")){statement.setLong(1,id);statement.setLong(2,businessId);statement.setLong(3,userId);return statement.executeUpdate()==1;}}

    private SavedView map(ResultSet rows)throws SQLException{long user=rows.getLong("user_id");Long userId=rows.wasNull()?null:user;long sort=rows.getLong("sort_column_id");Long sortId=rows.wasNull()?null:sort;return new SavedView(rows.getLong("id"),rows.getLong("business_id"),rows.getLong("module_id"),userId,rows.getString("name"),sortId,rows.getString("sort_direction"),rows.getBoolean("default_view"),rows.getTimestamp("created_at").toLocalDateTime(),rows.getTimestamp("updated_at").toLocalDateTime());}
}
