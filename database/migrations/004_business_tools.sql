USE gestor_no_code;

-- Ejecutar una sola vez después de 003_panel_actions.sql.
-- Conserva los datos actuales. Los usuarios existentes pasan a ser propietarios
-- activos y los módulos/columnas conservan su comportamiento anterior.

ALTER TABLE users
  ADD COLUMN role ENUM('OWNER','MANAGER','EMPLOYEE') NOT NULL DEFAULT 'OWNER' AFTER password_hash,
  ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE AFTER role;

ALTER TABLE custom_modules
  MODIFY COLUMN panel_action ENUM('REGISTER','REMOVE','ADJUST') NULL,
  ADD COLUMN adjustment_column_id BIGINT NULL AFTER panel_action,
  ADD COLUMN adjustment_operation ENUM('ADD','SUBTRACT','SET') NULL AFTER adjustment_column_id,
  ADD COLUMN allow_negative BOOLEAN NOT NULL DEFAULT FALSE AFTER adjustment_operation,
  ADD INDEX idx_modules_adjustment_column (adjustment_column_id);

ALTER TABLE module_columns
  MODIFY COLUMN data_type ENUM('TEXT','NUMBER','DATE','BOOLEAN','SELECT','RELATION','LOOKUP','FORMULA') NOT NULL,
  ADD COLUMN required_field BOOLEAN NOT NULL DEFAULT FALSE AFTER options_text,
  ADD COLUMN default_value TEXT NULL AFTER required_field,
  ADD COLUMN related_module_id BIGINT NULL AFTER default_value,
  ADD COLUMN related_display_column_id BIGINT NULL AFTER related_module_id,
  ADD COLUMN relation_column_id BIGINT NULL AFTER related_display_column_id,
  ADD COLUMN source_column_id BIGINT NULL AFTER relation_column_id,
  ADD COLUMN formula_expression VARCHAR(1000) NULL AFTER source_column_id,
  ADD COLUMN low_stock_threshold DECIMAL(19,4) NULL AFTER formula_expression;

ALTER TABLE module_columns
  ADD INDEX idx_columns_related_module (related_module_id),
  ADD INDEX idx_columns_related_display (related_display_column_id),
  ADD INDEX idx_columns_relation_source (relation_column_id),
  ADD INDEX idx_columns_lookup_source (source_column_id),
  ADD CONSTRAINT fk_columns_related_module
    FOREIGN KEY (related_module_id) REFERENCES custom_modules(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_columns_related_display
    FOREIGN KEY (related_display_column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_columns_relation_source
    FOREIGN KEY (relation_column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_columns_lookup_source
    FOREIGN KEY (source_column_id) REFERENCES module_columns(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS module_permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  module_id BIGINT NOT NULL,
  can_view BOOLEAN NOT NULL DEFAULT FALSE,
  can_create BOOLEAN NOT NULL DEFAULT FALSE,
  can_edit BOOLEAN NOT NULL DEFAULT FALSE,
  can_delete BOOLEAN NOT NULL DEFAULT FALSE,
  can_manage BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_permissions_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_permissions_module FOREIGN KEY (module_id) REFERENCES custom_modules(id) ON DELETE CASCADE,
  UNIQUE KEY uq_permission_user_module (user_id, module_id),
  INDEX idx_permissions_scope (business_id, module_id, user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS panel_fields (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  panel_module_id BIGINT NOT NULL,
  column_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  data_type ENUM('TEXT','NUMBER','DATE','BOOLEAN','SELECT','RELATION') NOT NULL,
  options_text TEXT,
  required_field BOOLEAN NOT NULL DEFAULT FALSE,
  default_value TEXT,
  related_module_id BIGINT NULL,
  related_display_column_id BIGINT NULL,
  visible BOOLEAN NOT NULL DEFAULT TRUE,
  position INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_panel_fields_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_panel_fields_panel FOREIGN KEY (panel_module_id) REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_panel_fields_column FOREIGN KEY (column_id) REFERENCES module_columns(id) ON DELETE CASCADE,
  CONSTRAINT fk_panel_fields_related_module FOREIGN KEY (related_module_id) REFERENCES custom_modules(id) ON DELETE SET NULL,
  CONSTRAINT fk_panel_fields_display_column FOREIGN KEY (related_display_column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  UNIQUE KEY uq_panel_field_column (panel_module_id, column_id),
  INDEX idx_panel_fields_scope (business_id, panel_module_id),
  INDEX idx_panel_fields_related_module (related_module_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS audit_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  module_id BIGINT NULL,
  row_id BIGINT NULL,
  event_type VARCHAR(40) NOT NULL,
  entity_type VARCHAR(40) NOT NULL,
  entity_id BIGINT NULL,
  summary VARCHAR(255) NULL,
  before_values LONGTEXT NULL,
  after_values LONGTEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_audit_module FOREIGN KEY (module_id) REFERENCES custom_modules(id) ON DELETE SET NULL,
  CONSTRAINT fk_audit_row FOREIGN KEY (row_id) REFERENCES module_rows(id) ON DELETE SET NULL,
  INDEX idx_audit_scope_time (business_id, created_at),
  INDEX idx_audit_module_row (business_id, module_id, row_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inventory_movements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  module_id BIGINT NULL,
  row_id BIGINT NULL,
  column_id BIGINT NULL,
  source_panel_id BIGINT NULL,
  audit_event_id BIGINT NULL,
  created_by BIGINT NULL,
  operation ENUM('ADD','SUBTRACT','SET') NOT NULL,
  quantity_before DECIMAL(19,4) NOT NULL,
  quantity_change DECIMAL(19,4) NOT NULL,
  quantity_after DECIMAL(19,4) NOT NULL,
  note VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_movements_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_movements_module FOREIGN KEY (module_id) REFERENCES custom_modules(id) ON DELETE SET NULL,
  CONSTRAINT fk_movements_row FOREIGN KEY (row_id) REFERENCES module_rows(id) ON DELETE SET NULL,
  CONSTRAINT fk_movements_column FOREIGN KEY (column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  CONSTRAINT fk_movements_panel FOREIGN KEY (source_panel_id) REFERENCES custom_modules(id) ON DELETE SET NULL,
  CONSTRAINT fk_movements_audit FOREIGN KEY (audit_event_id) REFERENCES audit_events(id) ON DELETE SET NULL,
  CONSTRAINT fk_movements_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_movements_scope_time (business_id, created_at),
  INDEX idx_movements_row (business_id, module_id, row_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS operation_runs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  panel_module_id BIGINT NULL,
  user_id BIGINT NULL,
  operation_token VARCHAR(64) NOT NULL,
  status ENUM('STARTED','COMPLETED','FAILED') NOT NULL DEFAULT 'STARTED',
  result_row_id BIGINT NULL,
  result_message VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  CONSTRAINT fk_operation_runs_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_operation_runs_panel FOREIGN KEY (panel_module_id) REFERENCES custom_modules(id) ON DELETE SET NULL,
  CONSTRAINT fk_operation_runs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_operation_runs_row FOREIGN KEY (result_row_id) REFERENCES module_rows(id) ON DELETE SET NULL,
  UNIQUE KEY uq_operation_token (business_id, operation_token),
  INDEX idx_operation_runs_scope (business_id, panel_module_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS saved_views (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  module_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  name VARCHAR(100) NOT NULL,
  sort_column_id BIGINT NULL,
  sort_direction ENUM('ASC','DESC') NOT NULL DEFAULT 'ASC',
  default_view BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_saved_views_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_saved_views_module FOREIGN KEY (module_id) REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_saved_views_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_saved_views_sort_column FOREIGN KEY (sort_column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  UNIQUE KEY uq_saved_view_name (module_id, user_id, name),
  INDEX idx_saved_views_scope (business_id, module_id, user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS saved_view_filters (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  saved_view_id BIGINT NOT NULL,
  column_id BIGINT NOT NULL,
  filter_operator ENUM('EQ','NE','CONTAINS','GT','GTE','LT','LTE','BETWEEN','EMPTY','NOT_EMPTY') NOT NULL,
  value_text TEXT NULL,
  second_value_text TEXT NULL,
  position INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_view_filters_view FOREIGN KEY (saved_view_id) REFERENCES saved_views(id) ON DELETE CASCADE,
  CONSTRAINT fk_view_filters_column FOREIGN KEY (column_id) REFERENCES module_columns(id) ON DELETE CASCADE,
  INDEX idx_view_filters_order (saved_view_id, position)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS report_definitions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  module_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  name VARCHAR(120) NOT NULL,
  report_type ENUM('TABLE','SUMMARY','CHART') NOT NULL DEFAULT 'TABLE',
  group_by_column_id BIGINT NULL,
  value_column_id BIGINT NULL,
  date_column_id BIGINT NULL,
  aggregate_function ENUM('COUNT','SUM','AVG','MIN','MAX') NOT NULL DEFAULT 'COUNT',
  chart_type ENUM('BAR','LINE','PIE') NULL,
  filters_text LONGTEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_reports_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_reports_module FOREIGN KEY (module_id) REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_reports_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_reports_group_column FOREIGN KEY (group_by_column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  CONSTRAINT fk_reports_value_column FOREIGN KEY (value_column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  CONSTRAINT fk_reports_date_column FOREIGN KEY (date_column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  UNIQUE KEY uq_report_name (business_id, name),
  INDEX idx_reports_scope (business_id, module_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS alert_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  module_id BIGINT NOT NULL,
  column_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  rule_type ENUM('NUMBER','DATE','VALUE') NOT NULL DEFAULT 'NUMBER',
  condition_operator ENUM('EQ','NE','GT','GTE','LT','LTE','BEFORE','AFTER','CONTAINS') NOT NULL,
  comparison_value VARCHAR(255) NOT NULL,
  message_template VARCHAR(255) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_alert_rules_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_rules_module FOREIGN KEY (module_id) REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_rules_column FOREIGN KEY (column_id) REFERENCES module_columns(id) ON DELETE CASCADE,
  UNIQUE KEY uq_alert_rule_name (business_id, name),
  INDEX idx_alert_rules_scope (business_id, module_id, active)
) ENGINE=InnoDB;
