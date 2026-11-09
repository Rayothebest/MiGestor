USE gestor_no_code;

-- Ejecutar una sola vez después de 007_sales_returns.sql.
-- Cada negocio tiene un bloqueo propio para serializar aperturas, cierres y movimientos.
CREATE TABLE cash_registers (
  business_id BIGINT PRIMARY KEY,
  current_session_id BIGINT NULL,
  CONSTRAINT fk_cash_register_business FOREIGN KEY (business_id)
    REFERENCES businesses(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE cash_sessions (
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

CREATE TABLE cash_movements (
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

-- Conserva las ventas y devoluciones previas al módulo de Caja como operaciones
-- fuera de turno. No se incluyen en el efectivo esperado de turnos futuros.
INSERT INTO cash_movements
  (business_id,created_by,kind,category,payment_method,amount,note,sale_id,created_at)
SELECT business_id,created_by,'SALE','Venta',payment_method,total_amount,
       'Venta anterior a Caja',id,created_at
FROM sales;

INSERT INTO cash_movements
  (business_id,created_by,kind,category,payment_method,amount,note,sale_return_id,created_at)
SELECT business_id,created_by,'RETURN','Devolución',refund_method,total_amount,
       'Devolución anterior a Caja',id,created_at
FROM sale_returns;
