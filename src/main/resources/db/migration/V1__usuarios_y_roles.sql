-- =====================================================================
-- V1: Seguridad - roles y usuarios
-- =====================================================================
-- ---------------------------------------------------------------------
-- 1. SEGURIDAD: roles y usuarios
-- ---------------------------------------------------------------------
CREATE TABLE rol (
    id      SMALLINT PRIMARY KEY,
    nombre  VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO rol (id, nombre) VALUES (1, 'ADMIN'), (2, 'VENDEDOR');

CREATE TABLE usuario (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username                  VARCHAR(50)  NOT NULL UNIQUE,
    nombre_completo           VARCHAR(120) NOT NULL,
    rol_id                    SMALLINT     NOT NULL REFERENCES rol(id),
    password_hash             VARCHAR(100) NOT NULL,   -- BCrypt/Argon2, NUNCA texto plano
    codigo_recuperacion_hash  VARCHAR(100),            -- hash del código de recuperación (se muestra una sola vez)
    debe_cambiar_password     BOOLEAN      NOT NULL DEFAULT FALSE,
    intentos_fallidos         SMALLINT     NOT NULL DEFAULT 0,
    bloqueado_hasta           TIMESTAMPTZ,
    activo                    BOOLEAN      NOT NULL DEFAULT TRUE,
    ultimo_acceso             TIMESTAMPTZ,
    creado_en                 TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- El usuario ADMIN inicial lo crea la app en el primer arranque
-- (si no existen usuarios), porque el hash se genera desde Java.
