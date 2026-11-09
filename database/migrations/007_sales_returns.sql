USE gestor_no_code;

-- Ejecutar una sola vez después de 006_workflows.sql.
-- Conserva la columna de existencias original aunque Ventas se reconfigure después.
ALTER TABLE sales
  ADD COLUMN stock_column_id BIGINT NULL AFTER source_module_id,
  ADD CONSTRAINT fk_sales_original_stock FOREIGN KEY (stock_column_id)
    REFERENCES module_columns(id) ON DELETE SET NULL;

UPDATE sales s
JOIN (
  SELECT business_id,sale_id,MIN(column_id) AS stock_column_id
  FROM inventory_movements
  WHERE sale_id IS NOT NULL AND column_id IS NOT NULL AND operation='SUBTRACT'
  GROUP BY business_id,sale_id
  HAVING COUNT(DISTINCT column_id)=1
) movement ON movement.sale_id=s.id AND movement.business_id=s.business_id
SET s.stock_column_id=movement.stock_column_id
WHERE s.stock_column_id IS NULL;

CREATE TABLE sale_returns (
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

CREATE TABLE sale_return_items (
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

ALTER TABLE inventory_movements
  ADD COLUMN sale_return_id BIGINT NULL,
  ADD CONSTRAINT fk_movements_return FOREIGN KEY (sale_return_id) REFERENCES sale_returns(id) ON DELETE SET NULL,
  ADD INDEX idx_movements_return (business_id,sale_return_id);

ALTER TABLE workflow_rules
  MODIFY COLUMN trigger_type ENUM('ROW_CREATED','SALE_CREATED','RETURN_CREATED') NOT NULL;

ALTER TABLE workflow_mappings
  MODIFY COLUMN source_type ENUM('SOURCE_COLUMN','SALE_ID','SALE_TOTAL','SALE_PAYMENT',
    'RETURN_ID','RETURN_TOTAL','RETURN_METHOD','LITERAL') NOT NULL;
