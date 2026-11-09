USE gestor_no_code;

-- Ejecutar una sola vez después de 008_cash_register.sql.
-- Los códigos y fotos pertenecen a filas de Tablas; al borrar la fila se borran.
CREATE TABLE product_assets (
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
