CREATE DATABASE IF NOT EXISTS gestor_no_code CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE gestor_no_code;

CREATE TABLE IF NOT EXISTS businesses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(190) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role ENUM('OWNER','MANAGER','EMPLOYEE') NOT NULL DEFAULT 'OWNER',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_users_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  INDEX idx_users_business (business_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS custom_modules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  module_type ENUM('TABLE','PANEL') NOT NULL DEFAULT 'TABLE',
  linked_module_id BIGINT NULL,
  panel_action ENUM('REGISTER','REMOVE','ADJUST') NULL,
  adjustment_column_id BIGINT NULL,
  adjustment_operation ENUM('ADD','SUBTRACT','SET') NULL,
  allow_negative BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_modules_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_modules_linked FOREIGN KEY (linked_module_id) REFERENCES custom_modules(id) ON DELETE SET NULL,
  UNIQUE KEY uq_module_name_business (business_id, name),
  INDEX idx_modules_business (business_id),
  INDEX idx_modules_linked (business_id, linked_module_id),
  INDEX idx_modules_adjustment_column (adjustment_column_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS module_columns (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_id BIGINT NOT NULL,
  business_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  data_type ENUM('TEXT','NUMBER','DATE','BOOLEAN','SELECT','RELATION','LOOKUP','FORMULA') NOT NULL,
  options_text TEXT,
  required_field BOOLEAN NOT NULL DEFAULT FALSE,
  default_value TEXT,
  related_module_id BIGINT NULL,
  related_display_column_id BIGINT NULL,
  relation_column_id BIGINT NULL,
  source_column_id BIGINT NULL,
  formula_expression VARCHAR(1000) NULL,
  low_stock_threshold DECIMAL(19,4) NULL,
  position INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_columns_module FOREIGN KEY (module_id) REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_columns_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_columns_related_module FOREIGN KEY (related_module_id) REFERENCES custom_modules(id) ON DELETE SET NULL,
  CONSTRAINT fk_columns_related_display FOREIGN KEY (related_display_column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  CONSTRAINT fk_columns_relation_source FOREIGN KEY (relation_column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  CONSTRAINT fk_columns_lookup_source FOREIGN KEY (source_column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  UNIQUE KEY uq_column_name_module (module_id, name),
  INDEX idx_columns_scope (business_id, module_id),
  INDEX idx_columns_related_module (related_module_id),
  INDEX idx_columns_related_display (related_display_column_id),
  INDEX idx_columns_relation_source (relation_column_id),
  INDEX idx_columns_lookup_source (source_column_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS module_rows (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_id BIGINT NOT NULL,
  business_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_rows_module FOREIGN KEY (module_id) REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_rows_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  INDEX idx_rows_scope (business_id, module_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cell_values (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  row_id BIGINT NOT NULL,
  column_id BIGINT NOT NULL,
  business_id BIGINT NOT NULL,
  value_text TEXT,
  CONSTRAINT fk_cells_row FOREIGN KEY (row_id) REFERENCES module_rows(id) ON DELETE CASCADE,
  CONSTRAINT fk_cells_column FOREIGN KEY (column_id) REFERENCES module_columns(id) ON DELETE CASCADE,
  CONSTRAINT fk_cells_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  UNIQUE KEY uq_cell (row_id, column_id),
  INDEX idx_cells_scope (business_id, row_id)
) ENGINE=InnoDB;

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

CREATE TABLE IF NOT EXISTS sale_settings (
  business_id BIGINT PRIMARY KEY,
  module_id BIGINT NOT NULL,
  display_column_id BIGINT NOT NULL,
  stock_column_id BIGINT NOT NULL,
  price_column_id BIGINT NOT NULL,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_sale_settings_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_sale_settings_module FOREIGN KEY (module_id) REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_sale_settings_display FOREIGN KEY (display_column_id) REFERENCES module_columns(id) ON DELETE CASCADE,
  CONSTRAINT fk_sale_settings_stock FOREIGN KEY (stock_column_id) REFERENCES module_columns(id) ON DELETE CASCADE,
  CONSTRAINT fk_sale_settings_price FOREIGN KEY (price_column_id) REFERENCES module_columns(id) ON DELETE CASCADE,
  CONSTRAINT fk_sale_settings_user FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product_assets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  module_id BIGINT NOT NULL,
  row_id BIGINT NOT NULL,
  barcode VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  image_mime VARCHAR(32) NULL,
  image_data MEDIUMBLOB NULL,
  image_updated_at TIMESTAMP NULL,
  updated_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_product_assets_business FOREIGN KEY (business_id)
    REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_product_assets_module FOREIGN KEY (module_id)
    REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_product_assets_row FOREIGN KEY (row_id)
    REFERENCES module_rows(id) ON DELETE CASCADE,
  CONSTRAINT fk_product_assets_editor FOREIGN KEY (updated_by)
    REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_product_asset_row (row_id),
  UNIQUE KEY uq_product_asset_barcode (business_id,module_id,barcode),
  INDEX idx_product_assets_scope (business_id,module_id,row_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sales (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  source_module_id BIGINT NULL,
  stock_column_id BIGINT NULL,
  source_module_name VARCHAR(100) NOT NULL,
  created_by BIGINT NULL,
  operation_token VARCHAR(64) NOT NULL,
  payment_method ENUM('CASH','CARD','TRANSFER','OTHER') NOT NULL,
  total_amount DECIMAL(19,2) NOT NULL,
  note VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sales_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_sales_module FOREIGN KEY (source_module_id) REFERENCES custom_modules(id) ON DELETE SET NULL,
  CONSTRAINT fk_sales_original_stock FOREIGN KEY (stock_column_id) REFERENCES module_columns(id) ON DELETE SET NULL,
  CONSTRAINT fk_sales_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_sales_token (business_id, operation_token),
  INDEX idx_sales_scope_time (business_id, created_at, id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sale_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sale_id BIGINT NOT NULL,
  business_id BIGINT NOT NULL,
  product_row_id BIGINT NULL,
  product_label VARCHAR(255) NOT NULL,
  quantity DECIMAL(19,4) NOT NULL,
  unit_price DECIMAL(19,4) NOT NULL,
  line_total DECIMAL(19,2) NOT NULL,
  stock_before DECIMAL(19,4) NOT NULL,
  stock_after DECIMAL(19,4) NOT NULL,
  CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
  CONSTRAINT fk_sale_items_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_sale_items_product FOREIGN KEY (product_row_id) REFERENCES module_rows(id) ON DELETE SET NULL,
  INDEX idx_sale_items_scope (business_id, sale_id),
  INDEX idx_sale_items_product (business_id, product_row_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sale_returns (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  sale_id BIGINT NOT NULL,
  created_by BIGINT NULL,
  operation_token VARCHAR(64) NOT NULL,
  refund_method ENUM('CASH','CARD','TRANSFER','OTHER') NOT NULL,
  total_amount DECIMAL(19,2) NOT NULL,
  note VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_returns_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_returns_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
  CONSTRAINT fk_returns_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_returns_token (business_id,operation_token),
  INDEX idx_returns_sale (business_id,sale_id,created_at,id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sale_return_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  return_id BIGINT NOT NULL,
  business_id BIGINT NOT NULL,
  sale_item_id BIGINT NOT NULL,
  product_row_id BIGINT NOT NULL,
  stock_column_id BIGINT NOT NULL,
  product_label VARCHAR(255) NOT NULL,
  quantity DECIMAL(19,4) NOT NULL,
  unit_price DECIMAL(19,4) NOT NULL,
  line_total DECIMAL(19,2) NOT NULL,
  stock_before DECIMAL(19,4) NOT NULL,
  stock_after DECIMAL(19,4) NOT NULL,
  CONSTRAINT fk_return_items_return FOREIGN KEY (return_id) REFERENCES sale_returns(id) ON DELETE CASCADE,
  CONSTRAINT fk_return_items_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_return_items_sale_item FOREIGN KEY (sale_item_id) REFERENCES sale_items(id) ON DELETE CASCADE,
  UNIQUE KEY uq_return_item (return_id,sale_item_id),
  INDEX idx_return_items_scope (business_id,sale_item_id,return_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cash_registers (
  business_id BIGINT PRIMARY KEY,
  current_session_id BIGINT NULL,
  CONSTRAINT fk_cash_register_business FOREIGN KEY (business_id)
    REFERENCES businesses(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cash_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  opened_by BIGINT NULL,
  closed_by BIGINT NULL,
  open_token VARCHAR(64) NOT NULL,
  close_token VARCHAR(64) NULL,
  opening_cash DECIMAL(19,2) NOT NULL,
  expected_cash DECIMAL(19,2) NULL,
  counted_cash DECIMAL(19,2) NULL,
  difference_cash DECIMAL(19,2) NULL,
  open_note VARCHAR(255) NULL,
  close_note VARCHAR(255) NULL,
  opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at TIMESTAMP NULL,
  CONSTRAINT fk_cash_session_business FOREIGN KEY (business_id)
    REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_cash_session_opener FOREIGN KEY (opened_by)
    REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_cash_session_closer FOREIGN KEY (closed_by)
    REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_cash_open_token (business_id,open_token),
  UNIQUE KEY uq_cash_close_token (business_id,close_token),
  INDEX idx_cash_sessions_recent (business_id,opened_at,id),
  INDEX idx_cash_sessions_open (business_id,closed_at,id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cash_movements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  session_id BIGINT NULL,
  created_by BIGINT NULL,
  kind ENUM('SALE','RETURN','INCOME','EXPENSE') NOT NULL,
  category VARCHAR(80) NOT NULL,
  payment_method ENUM('CASH','CARD','TRANSFER','OTHER') NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  note VARCHAR(255) NULL,
  operation_token VARCHAR(64) NULL,
  sale_id BIGINT NULL,
  sale_return_id BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cash_movement_business FOREIGN KEY (business_id)
    REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_cash_movement_session FOREIGN KEY (session_id)
    REFERENCES cash_sessions(id) ON DELETE SET NULL,
  CONSTRAINT fk_cash_movement_user FOREIGN KEY (created_by)
    REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_cash_movement_sale FOREIGN KEY (sale_id)
    REFERENCES sales(id) ON DELETE SET NULL,
  CONSTRAINT fk_cash_movement_return FOREIGN KEY (sale_return_id)
    REFERENCES sale_returns(id) ON DELETE SET NULL,
  UNIQUE KEY uq_cash_manual_token (business_id,operation_token),
  UNIQUE KEY uq_cash_sale (business_id,sale_id),
  UNIQUE KEY uq_cash_return (business_id,sale_return_id),
  INDEX idx_cash_movement_session (business_id,session_id,id),
  INDEX idx_cash_movement_recent (business_id,created_at,id),
  INDEX idx_cash_movement_category (business_id,category)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS workflow_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  trigger_type ENUM('ROW_CREATED','SALE_CREATED','RETURN_CREATED') NOT NULL,
  source_module_id BIGINT NULL,
  condition_column_id BIGINT NULL,
  condition_value TEXT NULL,
  action_type ENUM('ADJUST_STOCK','CREATE_ROW') NOT NULL,
  target_module_id BIGINT NOT NULL,
  relation_column_id BIGINT NULL,
  quantity_column_id BIGINT NULL,
  stock_column_id BIGINT NULL,
  operation ENUM('ADD','SUBTRACT') NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_workflow_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_workflow_source FOREIGN KEY (source_module_id) REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_workflow_target FOREIGN KEY (target_module_id) REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_workflow_condition FOREIGN KEY (condition_column_id) REFERENCES module_columns(id) ON DELETE CASCADE,
  CONSTRAINT fk_workflow_relation FOREIGN KEY (relation_column_id) REFERENCES module_columns(id) ON DELETE CASCADE,
  CONSTRAINT fk_workflow_quantity FOREIGN KEY (quantity_column_id) REFERENCES module_columns(id) ON DELETE CASCADE,
  CONSTRAINT fk_workflow_stock FOREIGN KEY (stock_column_id) REFERENCES module_columns(id) ON DELETE CASCADE,
  CONSTRAINT fk_workflow_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_workflow_editor FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_workflow_business_id (id,business_id),
  INDEX idx_workflow_trigger (business_id,active,trigger_type,source_module_id,id),
  INDEX idx_workflow_target (business_id,target_module_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS workflow_mappings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_id BIGINT NOT NULL,
  business_id BIGINT NOT NULL,
  target_column_id BIGINT NOT NULL,
  source_type ENUM('SOURCE_COLUMN','SALE_ID','SALE_TOTAL','SALE_PAYMENT',
    'RETURN_ID','RETURN_TOTAL','RETURN_METHOD','LITERAL') NOT NULL,
  source_column_id BIGINT NULL,
  literal_value TEXT NULL,
  position INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_workflow_mapping_rule FOREIGN KEY (rule_id,business_id)
    REFERENCES workflow_rules(id,business_id) ON DELETE CASCADE,
  CONSTRAINT fk_workflow_mapping_target FOREIGN KEY (target_column_id)
    REFERENCES module_columns(id) ON DELETE CASCADE,
  CONSTRAINT fk_workflow_mapping_source FOREIGN KEY (source_column_id)
    REFERENCES module_columns(id) ON DELETE CASCADE,
  UNIQUE KEY uq_workflow_mapping_target (rule_id,target_column_id),
  INDEX idx_workflow_mapping_scope (business_id,rule_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inventory_movements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  module_id BIGINT NULL,
  row_id BIGINT NULL,
  column_id BIGINT NULL,
  source_panel_id BIGINT NULL,
  sale_id BIGINT NULL,
  sale_return_id BIGINT NULL,
  workflow_rule_id BIGINT NULL,
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
  CONSTRAINT fk_movements_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE SET NULL,
  CONSTRAINT fk_movements_return FOREIGN KEY (sale_return_id) REFERENCES sale_returns(id) ON DELETE SET NULL,
  CONSTRAINT fk_movements_workflow FOREIGN KEY (workflow_rule_id) REFERENCES workflow_rules(id) ON DELETE SET NULL,
  CONSTRAINT fk_movements_audit FOREIGN KEY (audit_event_id) REFERENCES audit_events(id) ON DELETE SET NULL,
  CONSTRAINT fk_movements_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_movements_scope_time (business_id, created_at),
  INDEX idx_movements_row (business_id, module_id, row_id),
  INDEX idx_movements_sale (business_id, sale_id),
  INDEX idx_movements_return (business_id, sale_return_id),
  INDEX idx_movements_workflow (business_id, workflow_rule_id)
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

CREATE TABLE IF NOT EXISTS alert_schedules (
  rule_id BIGINT PRIMARY KEY,
  business_id BIGINT NOT NULL,
  interval_minutes SMALLINT NOT NULL,
  last_run_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_alert_schedule_rule FOREIGN KEY (rule_id) REFERENCES alert_rules(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_schedule_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  INDEX idx_alert_schedule_due (business_id,last_run_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS alert_notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  rule_id BIGINT NOT NULL,
  module_id BIGINT NOT NULL,
  row_id BIGINT NOT NULL,
  message VARCHAR(500) NOT NULL,
  current_value VARCHAR(255) NULL,
  scan_token CHAR(36) NOT NULL,
  first_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged_at DATETIME NULL,
  acknowledged_by BIGINT NULL,
  resolved_at DATETIME NULL,
  CONSTRAINT fk_alert_notification_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_notification_rule FOREIGN KEY (rule_id) REFERENCES alert_rules(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_notification_module FOREIGN KEY (module_id) REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_notification_row FOREIGN KEY (row_id) REFERENCES module_rows(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_notification_acknowledged_by FOREIGN KEY (acknowledged_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_alert_notification_match (business_id,rule_id,row_id),
  INDEX idx_alert_notification_inbox (business_id,resolved_at,acknowledged_at,last_seen_at)
) ENGINE=InnoDB;
