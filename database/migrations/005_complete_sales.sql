USE gestor_no_code;

-- Ejecutar una sola vez después de 004_business_tools.sql.
-- La configuración desaparece si se elimina su tabla o alguna columna elegida.
-- El historial de ventas conserva sus valores aunque se borren productos.
CREATE TABLE sale_settings (
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

CREATE TABLE sales (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  source_module_id BIGINT NULL,
  source_module_name VARCHAR(100) NOT NULL,
  created_by BIGINT NULL,
  operation_token VARCHAR(64) NOT NULL,
  payment_method ENUM('CASH','CARD','TRANSFER','OTHER') NOT NULL,
  total_amount DECIMAL(19,2) NOT NULL,
  note VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sales_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_sales_module FOREIGN KEY (source_module_id) REFERENCES custom_modules(id) ON DELETE SET NULL,
  CONSTRAINT fk_sales_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_sales_token (business_id, operation_token),
  INDEX idx_sales_scope_time (business_id, created_at, id)
) ENGINE=InnoDB;

CREATE TABLE sale_items (
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

ALTER TABLE inventory_movements
  ADD COLUMN sale_id BIGINT NULL,
  ADD CONSTRAINT fk_movements_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE SET NULL,
  ADD INDEX idx_movements_sale (business_id, sale_id);
