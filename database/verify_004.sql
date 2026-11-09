USE gestor_no_code;

-- Verificación de solo lectura para ejecutar después de 004_business_tools.sql.
-- Todos los conteos de huérfanos o cruces entre negocios deben devolver cero.

SELECT 'businesses' AS entity_name, COUNT(*) AS row_count FROM businesses
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'custom_modules', COUNT(*) FROM custom_modules
UNION ALL SELECT 'module_columns', COUNT(*) FROM module_columns
UNION ALL SELECT 'module_rows', COUNT(*) FROM module_rows
UNION ALL SELECT 'cell_values', COUNT(*) FROM cell_values
UNION ALL SELECT 'module_permissions', COUNT(*) FROM module_permissions
UNION ALL SELECT 'panel_fields', COUNT(*) FROM panel_fields
UNION ALL SELECT 'audit_events', COUNT(*) FROM audit_events
UNION ALL SELECT 'inventory_movements', COUNT(*) FROM inventory_movements
UNION ALL SELECT 'operation_runs', COUNT(*) FROM operation_runs
UNION ALL SELECT 'saved_views', COUNT(*) FROM saved_views
UNION ALL SELECT 'saved_view_filters', COUNT(*) FROM saved_view_filters
UNION ALL SELECT 'report_definitions', COUNT(*) FROM report_definitions
UNION ALL SELECT 'alert_rules', COUNT(*) FROM alert_rules
ORDER BY entity_name;

SELECT expected.table_name,
       expected.column_name,
       CASE WHEN actual.column_name IS NULL THEN 'MISSING' ELSE 'OK' END AS verification
FROM (
    SELECT 'users' AS table_name, 'role' AS column_name
    UNION ALL SELECT 'users', 'active'
    UNION ALL SELECT 'custom_modules', 'adjustment_column_id'
    UNION ALL SELECT 'custom_modules', 'adjustment_operation'
    UNION ALL SELECT 'custom_modules', 'allow_negative'
    UNION ALL SELECT 'module_columns', 'required_field'
    UNION ALL SELECT 'module_columns', 'default_value'
    UNION ALL SELECT 'module_columns', 'related_module_id'
    UNION ALL SELECT 'module_columns', 'related_display_column_id'
    UNION ALL SELECT 'module_columns', 'relation_column_id'
    UNION ALL SELECT 'module_columns', 'source_column_id'
    UNION ALL SELECT 'module_columns', 'formula_expression'
    UNION ALL SELECT 'module_columns', 'low_stock_threshold'
) expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = expected.table_name
 AND actual.column_name = expected.column_name
ORDER BY expected.table_name, expected.column_name;

SELECT expected.table_name,
       CASE WHEN actual.table_name IS NULL THEN 'MISSING' ELSE 'OK' END AS verification
FROM (
    SELECT 'module_permissions' AS table_name
    UNION ALL SELECT 'panel_fields'
    UNION ALL SELECT 'audit_events'
    UNION ALL SELECT 'inventory_movements'
    UNION ALL SELECT 'operation_runs'
    UNION ALL SELECT 'saved_views'
    UNION ALL SELECT 'saved_view_filters'
    UNION ALL SELECT 'report_definitions'
    UNION ALL SELECT 'alert_rules'
) expected
LEFT JOIN information_schema.tables actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = expected.table_name
ORDER BY expected.table_name;

SELECT 'users_business' AS check_name, COUNT(*) AS invalid_rows
FROM users child
LEFT JOIN businesses parent ON parent.id = child.business_id
WHERE parent.id IS NULL
UNION ALL
SELECT 'modules_business', COUNT(*)
FROM custom_modules child
LEFT JOIN businesses parent ON parent.id = child.business_id
WHERE parent.id IS NULL
UNION ALL
SELECT 'linked_modules_cross_business_or_invalid', COUNT(*)
FROM custom_modules child
LEFT JOIN custom_modules parent ON parent.id = child.linked_module_id
WHERE child.linked_module_id IS NOT NULL
  AND (parent.id IS NULL OR parent.business_id <> child.business_id OR parent.module_type <> 'TABLE')
UNION ALL
SELECT 'columns_cross_business_or_module', COUNT(*)
FROM module_columns child
LEFT JOIN custom_modules parent ON parent.id = child.module_id
WHERE parent.id IS NULL OR parent.business_id <> child.business_id
UNION ALL
SELECT 'column_links_cross_business_or_module', COUNT(*)
FROM module_columns c
LEFT JOIN custom_modules related_module ON related_module.id = c.related_module_id
LEFT JOIN module_columns display_column ON display_column.id = c.related_display_column_id
LEFT JOIN module_columns relation_column ON relation_column.id = c.relation_column_id
LEFT JOIN module_columns source_column ON source_column.id = c.source_column_id
WHERE (c.related_module_id IS NOT NULL
       AND (related_module.id IS NULL OR related_module.business_id <> c.business_id))
   OR (c.related_display_column_id IS NOT NULL
       AND (display_column.id IS NULL
            OR display_column.business_id <> c.business_id
            OR display_column.module_id <> c.related_module_id))
   OR (c.relation_column_id IS NOT NULL
       AND (relation_column.id IS NULL
            OR relation_column.business_id <> c.business_id
            OR relation_column.module_id <> c.module_id))
   OR (c.source_column_id IS NOT NULL
       AND (source_column.id IS NULL OR source_column.business_id <> c.business_id))
UNION ALL
SELECT 'module_adjustments_cross_business_or_module', COUNT(*)
FROM custom_modules m
LEFT JOIN module_columns adjustment_column ON adjustment_column.id = m.adjustment_column_id
WHERE m.adjustment_column_id IS NOT NULL
  AND (adjustment_column.id IS NULL
       OR adjustment_column.business_id <> m.business_id
       OR adjustment_column.module_id <> m.linked_module_id)
UNION ALL
SELECT 'rows_cross_business_or_module', COUNT(*)
FROM module_rows child
LEFT JOIN custom_modules parent ON parent.id = child.module_id
WHERE parent.id IS NULL OR parent.business_id <> child.business_id
UNION ALL
SELECT 'cells_cross_business_or_row_column', COUNT(*)
FROM cell_values cell
LEFT JOIN module_rows module_row ON module_row.id = cell.row_id
LEFT JOIN module_columns module_column ON module_column.id = cell.column_id
WHERE module_row.id IS NULL
   OR module_column.id IS NULL
   OR module_row.business_id <> cell.business_id
   OR module_column.business_id <> cell.business_id
   OR module_row.module_id <> module_column.module_id
UNION ALL
SELECT 'permissions_cross_business', COUNT(*)
FROM module_permissions p
LEFT JOIN users app_user ON app_user.id = p.user_id
LEFT JOIN custom_modules m ON m.id = p.module_id
WHERE app_user.id IS NULL
   OR m.id IS NULL
   OR app_user.business_id <> p.business_id
   OR m.business_id <> p.business_id
UNION ALL
SELECT 'panel_fields_cross_business', COUNT(*)
FROM panel_fields field
LEFT JOIN custom_modules panel ON panel.id = field.panel_module_id
LEFT JOIN custom_modules related ON related.id = field.related_module_id
LEFT JOIN module_columns display_column ON display_column.id = field.related_display_column_id
WHERE panel.id IS NULL
   OR panel.business_id <> field.business_id
   OR panel.module_type <> 'PANEL'
   OR (field.related_module_id IS NOT NULL
       AND (related.id IS NULL OR related.business_id <> field.business_id))
   OR (field.related_display_column_id IS NOT NULL
       AND (display_column.id IS NULL
            OR display_column.business_id <> field.business_id
            OR display_column.module_id <> field.related_module_id))
UNION ALL
SELECT 'audit_events_cross_business', COUNT(*)
FROM audit_events ae
LEFT JOIN users app_user ON app_user.id = ae.user_id
LEFT JOIN custom_modules m ON m.id = ae.module_id
LEFT JOIN module_rows module_row ON module_row.id = ae.row_id
WHERE (ae.user_id IS NOT NULL
       AND (app_user.id IS NULL OR app_user.business_id <> ae.business_id))
   OR (ae.module_id IS NOT NULL
       AND (m.id IS NULL OR m.business_id <> ae.business_id))
   OR (ae.row_id IS NOT NULL
       AND (module_row.id IS NULL OR module_row.business_id <> ae.business_id))
UNION ALL
SELECT 'movements_cross_business', COUNT(*)
FROM inventory_movements movement
LEFT JOIN custom_modules m ON m.id = movement.module_id
LEFT JOIN module_rows module_row ON module_row.id = movement.row_id
LEFT JOIN module_columns module_column ON module_column.id = movement.column_id
LEFT JOIN custom_modules source_panel ON source_panel.id = movement.source_panel_id
LEFT JOIN audit_events ae ON ae.id = movement.audit_event_id
LEFT JOIN users app_user ON app_user.id = movement.created_by
WHERE (movement.module_id IS NOT NULL
       AND (m.id IS NULL OR m.business_id <> movement.business_id))
   OR (movement.row_id IS NOT NULL
       AND (module_row.id IS NULL
            OR module_row.business_id <> movement.business_id
            OR module_row.module_id <> movement.module_id))
   OR (movement.column_id IS NOT NULL
       AND (module_column.id IS NULL
            OR module_column.business_id <> movement.business_id
            OR module_column.module_id <> movement.module_id))
   OR (movement.source_panel_id IS NOT NULL
       AND (source_panel.id IS NULL OR source_panel.business_id <> movement.business_id))
   OR (movement.audit_event_id IS NOT NULL
       AND (ae.id IS NULL OR ae.business_id <> movement.business_id))
   OR (movement.created_by IS NOT NULL
       AND (app_user.id IS NULL OR app_user.business_id <> movement.business_id))
UNION ALL
SELECT 'operation_runs_cross_business', COUNT(*)
FROM operation_runs run_record
LEFT JOIN custom_modules panel ON panel.id = run_record.panel_module_id
LEFT JOIN users app_user ON app_user.id = run_record.user_id
LEFT JOIN module_rows module_row ON module_row.id = run_record.result_row_id
WHERE (run_record.panel_module_id IS NOT NULL
       AND (panel.id IS NULL
            OR panel.business_id <> run_record.business_id
            OR panel.module_type <> 'PANEL'))
   OR (run_record.user_id IS NOT NULL
       AND (app_user.id IS NULL OR app_user.business_id <> run_record.business_id))
   OR (run_record.result_row_id IS NOT NULL
       AND (module_row.id IS NULL OR module_row.business_id <> run_record.business_id))
UNION ALL
SELECT 'saved_views_cross_business', COUNT(*)
FROM saved_views view_definition
LEFT JOIN custom_modules m ON m.id = view_definition.module_id
LEFT JOIN users app_user ON app_user.id = view_definition.user_id
LEFT JOIN module_columns sort_column ON sort_column.id = view_definition.sort_column_id
WHERE m.id IS NULL
   OR m.business_id <> view_definition.business_id
   OR (view_definition.user_id IS NOT NULL
       AND (app_user.id IS NULL OR app_user.business_id <> view_definition.business_id))
   OR (view_definition.sort_column_id IS NOT NULL
       AND (sort_column.id IS NULL
            OR sort_column.business_id <> view_definition.business_id
            OR sort_column.module_id <> view_definition.module_id))
UNION ALL
SELECT 'saved_view_filters_cross_module', COUNT(*)
FROM saved_view_filters filter_definition
LEFT JOIN saved_views view_definition ON view_definition.id = filter_definition.saved_view_id
LEFT JOIN module_columns module_column ON module_column.id = filter_definition.column_id
WHERE view_definition.id IS NULL
   OR module_column.id IS NULL
   OR module_column.business_id <> view_definition.business_id
   OR module_column.module_id <> view_definition.module_id
UNION ALL
SELECT 'reports_cross_business', COUNT(*)
FROM report_definitions report
LEFT JOIN custom_modules m ON m.id = report.module_id
LEFT JOIN users app_user ON app_user.id = report.user_id
LEFT JOIN module_columns group_column ON group_column.id = report.group_by_column_id
LEFT JOIN module_columns value_column ON value_column.id = report.value_column_id
LEFT JOIN module_columns date_column ON date_column.id = report.date_column_id
WHERE m.id IS NULL
   OR m.business_id <> report.business_id
   OR (report.user_id IS NOT NULL
       AND (app_user.id IS NULL OR app_user.business_id <> report.business_id))
   OR (report.group_by_column_id IS NOT NULL
       AND (group_column.id IS NULL
            OR group_column.business_id <> report.business_id
            OR group_column.module_id <> report.module_id))
   OR (report.value_column_id IS NOT NULL
       AND (value_column.id IS NULL
            OR value_column.business_id <> report.business_id
            OR value_column.module_id <> report.module_id))
   OR (report.date_column_id IS NOT NULL
       AND (date_column.id IS NULL
            OR date_column.business_id <> report.business_id
            OR date_column.module_id <> report.module_id))
UNION ALL
SELECT 'alerts_cross_business', COUNT(*)
FROM alert_rules rule_definition
LEFT JOIN custom_modules m ON m.id = rule_definition.module_id
LEFT JOIN module_columns module_column ON module_column.id = rule_definition.column_id
WHERE m.id IS NULL
   OR module_column.id IS NULL
   OR m.business_id <> rule_definition.business_id
   OR module_column.business_id <> rule_definition.business_id
   OR module_column.module_id <> rule_definition.module_id
ORDER BY check_name;
