USE gestor_no_code;

-- Ejecutar una sola vez después de 005_complete_sales.sql.
-- Las reglas pertenecen a un negocio. Una regla se elimina si desaparece un
-- módulo o columna configurada; los eventos de historial permanecen.
CREATE TABLE workflow_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  trigger_type ENUM('ROW_CREATED','SALE_CREATED') NOT NULL,
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

CREATE TABLE workflow_mappings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_id BIGINT NOT NULL,
  business_id BIGINT NOT NULL,
  target_column_id BIGINT NOT NULL,
  source_type ENUM('SOURCE_COLUMN','SALE_ID','SALE_TOTAL','SALE_PAYMENT','LITERAL') NOT NULL,
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

ALTER TABLE inventory_movements
  ADD COLUMN workflow_rule_id BIGINT NULL,
  ADD CONSTRAINT fk_movements_workflow FOREIGN KEY (workflow_rule_id)
    REFERENCES workflow_rules(id) ON DELETE SET NULL,
  ADD INDEX idx_movements_workflow (business_id,workflow_rule_id);
