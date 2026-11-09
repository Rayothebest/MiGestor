USE gestor_no_code;

-- Ejecutar una sola vez después de 002_module_types.sql.
-- Los Paneles ya existentes conservan el comportamiento de registrar filas.
ALTER TABLE custom_modules
  ADD COLUMN panel_action ENUM('REGISTER','REMOVE') NULL AFTER linked_module_id;

UPDATE custom_modules
SET panel_action='REGISTER'
WHERE module_type='PANEL' AND panel_action IS NULL;
