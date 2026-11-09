USE gestor_no_code;

-- Ejecutar una sola vez sobre bases creadas antes de incorporar Tablas y Paneles.
-- Los módulos existentes se conservan y pasan a ser Tablas de datos.
ALTER TABLE custom_modules
  ADD COLUMN module_type ENUM('TABLE','PANEL') NOT NULL DEFAULT 'TABLE' AFTER description,
  ADD COLUMN linked_module_id BIGINT NULL AFTER module_type;

ALTER TABLE custom_modules
  ADD INDEX idx_modules_linked (business_id, linked_module_id),
  ADD CONSTRAINT fk_modules_linked
    FOREIGN KEY (linked_module_id) REFERENCES custom_modules(id)
    ON DELETE SET NULL;
