USE gestor_no_code;

-- Ejecutar una sola vez después de 009_product_assets.sql.
-- Las reglas actuales permanecen sin horario hasta que un gerente lo configure.
CREATE TABLE alert_schedules (
  rule_id BIGINT PRIMARY KEY,
  business_id BIGINT NOT NULL,
  interval_minutes SMALLINT NOT NULL,
  last_run_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_alert_schedule_rule FOREIGN KEY (rule_id)
    REFERENCES alert_rules(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_schedule_business FOREIGN KEY (business_id)
    REFERENCES businesses(id) ON DELETE CASCADE,
  INDEX idx_alert_schedule_due (business_id,last_run_at)
) ENGINE=InnoDB;

CREATE TABLE alert_notifications (
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
  CONSTRAINT fk_alert_notification_business FOREIGN KEY (business_id)
    REFERENCES businesses(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_notification_rule FOREIGN KEY (rule_id)
    REFERENCES alert_rules(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_notification_module FOREIGN KEY (module_id)
    REFERENCES custom_modules(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_notification_row FOREIGN KEY (row_id)
    REFERENCES module_rows(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_notification_acknowledged_by FOREIGN KEY (acknowledged_by)
    REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_alert_notification_match (business_id,rule_id,row_id),
  INDEX idx_alert_notification_inbox (business_id,resolved_at,acknowledged_at,last_seen_at)
) ENGINE=InnoDB;
