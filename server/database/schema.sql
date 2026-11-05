-- Carrousel App: base propia (carrousel_app), independiente de CarrouselCumple.
-- Una cuenta es un correo verificado con código; sus festejados (el titular o sus hijos) reciben el cupón de cumpleaños.
CREATE TABLE IF NOT EXISTS schema_versions (version INT PRIMARY KEY, installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS parks (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY, name VARCHAR(120) NOT NULL UNIQUE, address VARCHAR(250) NOT NULL DEFAULT '',
 hours VARCHAR(250) NOT NULL DEFAULT '', phone VARCHAR(30) NOT NULL DEFAULT '', latitude DECIMAL(9,6) NULL, longitude DECIMAL(9,6) NULL,
 active TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Personal: admin administra todo; cajero canjea en su parque. El parque del canje sale de la cuenta, nunca del formulario.
CREATE TABLE IF NOT EXISTS staff (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY, name VARCHAR(140) NOT NULL, email VARCHAR(190) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL, role VARCHAR(10) NOT NULL, park_id INT UNSIGNED NULL, active TINYINT NOT NULL DEFAULT 1,
 auth_version INT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL,
 FOREIGN KEY(park_id) REFERENCES parks(id), CONSTRAINT staff_role CHECK(role IN ('admin','cajero'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS accounts (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, email VARCHAR(190) NOT NULL UNIQUE, name VARCHAR(140) NOT NULL DEFAULT '',
 phone VARCHAR(30) NOT NULL DEFAULT '', active TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Festejados de la cuenta. La fecha de nacimiento no se edita desde la app: el cupón emitido depende de ella.
CREATE TABLE IF NOT EXISTS members (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, account_id BIGINT UNSIGNED NOT NULL, name VARCHAR(140) NOT NULL, birth_date DATE NOT NULL,
 created_at DATETIME NOT NULL, UNIQUE(account_id,name,birth_date), FOREIGN KEY(account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Códigos de acceso (solo su hash) y sesiones de la app (solo el HMAC del token).
CREATE TABLE IF NOT EXISTS otp_codes (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, challenge CHAR(48) NOT NULL UNIQUE, email VARCHAR(190) NOT NULL, code_hash VARCHAR(255) NOT NULL,
 attempts INT NOT NULL DEFAULT 0, expires_at DATETIME NOT NULL, consumed_at DATETIME NULL, created_at DATETIME NOT NULL, INDEX(email,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS app_tokens (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, token_hash CHAR(64) NOT NULL UNIQUE, account_id BIGINT UNSIGNED NOT NULL, device VARCHAR(80) NULL,
 created_at DATETIME NOT NULL, last_used_at DATETIME NOT NULL, expires_at DATETIME NOT NULL, revoked_at DATETIME NULL,
 FOREIGN KEY(account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Regalo de cumpleaños y ventana de canje. Sin descripción del regalo no se emiten cupones de cumpleaños.
CREATE TABLE IF NOT EXISTS settings (setting_key VARCHAR(60) PRIMARY KEY, setting_value TEXT NOT NULL, updated_at DATETIME NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO settings(setting_key,setting_value,updated_at) VALUES
 ('birthday_label','',NOW()),('birthday_terms','',NOW()),('birthday_days_before','15',NOW()),('birthday_days_after','14',NOW());
-- Ofertas: con coupon_enabled cada cuenta puede obtener un cupón de la oferta (uno por cuenta) mientras esté vigente.
CREATE TABLE IF NOT EXISTS offers (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY, title VARCHAR(140) NOT NULL, description TEXT NOT NULL, park_id INT UNSIGNED NULL,
 starts_on DATE NOT NULL, ends_on DATE NOT NULL, coupon_enabled TINYINT NOT NULL DEFAULT 0, coupon_label VARCHAR(250) NULL, coupon_terms TEXT NULL,
 active TINYINT NOT NULL DEFAULT 1, created_by INT UNSIGNED NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 FOREIGN KEY(park_id) REFERENCES parks(id), FOREIGN KEY(created_by) REFERENCES staff(id), INDEX(active,starts_on,ends_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS events (
 id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY, title VARCHAR(140) NOT NULL, description TEXT NOT NULL, park_id INT UNSIGNED NULL,
 starts_at DATETIME NOT NULL, ends_at DATETIME NULL, active TINYINT NOT NULL DEFAULT 1, created_by INT UNSIGNED NOT NULL,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 FOREIGN KEY(park_id) REFERENCES parks(id), FOREIGN KEY(created_by) REFERENCES staff(id), INDEX(active,starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Cupón: código aleatorio de 6 caracteres A-Z/0-9 único; copia el regalo y la vigencia al emitirse; se canjea una sola vez.
-- Cumpleaños: uno por festejado y año. Oferta: uno por cuenta y oferta.
CREATE TABLE IF NOT EXISTS coupons (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, kind VARCHAR(10) NOT NULL, account_id BIGINT UNSIGNED NOT NULL, member_id BIGINT UNSIGNED NULL,
 birthday_year SMALLINT NULL, offer_id INT UNSIGNED NULL, code CHAR(6) NOT NULL UNIQUE, label VARCHAR(250) NOT NULL, terms TEXT NOT NULL,
 valid_from DATE NOT NULL, valid_until DATE NOT NULL, created_at DATETIME NOT NULL,
 redeemed_at DATETIME NULL, redeemed_by INT UNSIGNED NULL, redeemed_park_id INT UNSIGNED NULL,
 voided_at DATETIME NULL, voided_by INT UNSIGNED NULL, void_reason VARCHAR(250) NULL,
 UNIQUE KEY birthday_coupon(member_id,birthday_year), UNIQUE KEY offer_coupon(offer_id,account_id),
 FOREIGN KEY(account_id) REFERENCES accounts(id), FOREIGN KEY(member_id) REFERENCES members(id), FOREIGN KEY(offer_id) REFERENCES offers(id),
 FOREIGN KEY(redeemed_by) REFERENCES staff(id), FOREIGN KEY(redeemed_park_id) REFERENCES parks(id), FOREIGN KEY(voided_by) REFERENCES staff(id),
 CONSTRAINT coupon_kind CHECK((kind='birthday' AND member_id IS NOT NULL AND birthday_year IS NOT NULL AND offer_id IS NULL)
   OR (kind='offer' AND offer_id IS NOT NULL AND member_id IS NULL AND birthday_year IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS request_limits (limit_key CHAR(64) NOT NULL, bucket BIGINT NOT NULL, hits INT NOT NULL, PRIMARY KEY(limit_key,bucket)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS audit (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, actor_type VARCHAR(10) NOT NULL, actor_id BIGINT UNSIGNED NULL, event VARCHAR(60) NOT NULL,
 entity VARCHAR(40) NOT NULL, entity_id VARCHAR(40) NULL, details TEXT NOT NULL, created_at DATETIME NOT NULL, INDEX(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO schema_versions(version) VALUES(1);
