-- =====================================================================
-- V3: Proveedores (simples: nombre y RUC opcional)
-- =====================================================================
CREATE TABLE proveedor (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre     VARCHAR(150) NOT NULL,
    ruc        VARCHAR(11)  UNIQUE
               CHECK (ruc ~ '^[0-9]{11}$'),          -- opcional; si existe, 11 dígitos
    activo     BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_proveedor_nombre ON proveedor (lower(nombre));
