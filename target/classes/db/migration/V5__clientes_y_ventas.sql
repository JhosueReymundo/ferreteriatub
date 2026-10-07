-- =====================================================================
-- V5: Clientes y ventas
-- =====================================================================
-- El cliente es opcional (venta a "cliente varios"). Cada línea guarda el
-- precio REALMENTE cobrado (con rebaja si la hubo) y el costo promedio del
-- producto en ese momento, para que la utilidad histórica no cambie después.

CREATE TABLE cliente (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre     VARCHAR(150) NOT NULL,
    documento  VARCHAR(11)  UNIQUE
               CHECK (documento ~ '^([0-9]{8}|[0-9]{11})$'),   -- opcional: DNI (8) o RUC (11)
    telefono   VARCHAR(20),
    activo     BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_cliente_nombre ON cliente (lower(nombre));

CREATE TABLE venta (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id        BIGINT        REFERENCES cliente(id),            -- opcional
    usuario_id        BIGINT        NOT NULL REFERENCES usuario(id),
    fecha             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    metodo_pago       VARCHAR(15)   NOT NULL DEFAULT 'EFECTIVO'
                      CHECK (metodo_pago IN ('EFECTIVO','YAPE_PLIN','TRANSFERENCIA','TARJETA')),
    total             NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    estado            VARCHAR(10)   NOT NULL DEFAULT 'REGISTRADA'
                      CHECK (estado IN ('REGISTRADA','ANULADA')),
    observacion       VARCHAR(255),
    anulada_en        TIMESTAMPTZ,
    anulada_por       BIGINT        REFERENCES usuario(id),
    motivo_anulacion  VARCHAR(255),
    -- Una venta está anulada si y solo si tiene fecha de anulación
    CONSTRAINT ck_venta_anulacion CHECK ((estado = 'ANULADA') = (anulada_en IS NOT NULL))
);

CREATE INDEX ix_venta_fecha   ON venta (fecha DESC);
CREATE INDEX ix_venta_cliente ON venta (cliente_id);

CREATE TABLE detalle_venta (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venta_id         BIGINT        NOT NULL REFERENCES venta(id),
    producto_id      BIGINT        NOT NULL REFERENCES producto(id),
    cantidad         NUMERIC(12,2) NOT NULL CHECK (cantidad > 0),
    precio_unitario  NUMERIC(12,2) NOT NULL CHECK (precio_unitario >= 0),  -- precio realmente cobrado
    costo_unitario   NUMERIC(12,4) NOT NULL CHECK (costo_unitario >= 0),   -- costo promedio al vender
    subtotal         NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0)
);

CREATE INDEX ix_detalle_venta_venta    ON detalle_venta (venta_id);
CREATE INDEX ix_detalle_venta_producto ON detalle_venta (producto_id);

-- El kardex apunta a la venta que lo originó
ALTER TABLE movimiento_inventario ADD COLUMN venta_id BIGINT REFERENCES venta(id);
CREATE INDEX ix_mov_venta ON movimiento_inventario (venta_id);

-- ---------------------------------------------------------------------
-- Trigger del kardex: al anular una venta, las unidades vuelven al inventario
-- con el costo con el que salieron (entra al promedio ponderado como una entrada).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_aplicar_movimiento() RETURNS trigger AS $$
DECLARE
    v_stock  NUMERIC;
    v_costo  NUMERIC;
    v_nuevo  NUMERIC;
BEGIN
    SELECT stock_actual, costo_promedio
      INTO v_stock, v_costo
      FROM producto
     WHERE id = NEW.producto_id
       FOR UPDATE;

    v_nuevo := v_stock + NEW.cantidad;

    IF NEW.costo_unitario IS NOT NULL AND v_nuevo > 0 THEN
        IF NEW.cantidad > 0
           AND NEW.tipo IN ('STOCK_INICIAL','COMPRA','AJUSTE_ENTRADA','ANULACION_VENTA') THEN
            -- Entrada valorizada: promedio ponderado
            v_costo := ((v_stock * v_costo) + (NEW.cantidad * NEW.costo_unitario)) / v_nuevo;
        ELSIF NEW.cantidad < 0 AND NEW.tipo = 'ANULACION_COMPRA' THEN
            -- Se quita del promedio lo que aportó la compra anulada
            v_costo := GREATEST(((v_stock * v_costo) + (NEW.cantidad * NEW.costo_unitario)) / v_nuevo, 0);
        END IF;
    END IF;

    -- Si el stock quedara negativo, el CHECK de producto lanza error y se revierte todo
    UPDATE producto
       SET stock_actual   = v_nuevo,
           costo_promedio = ROUND(v_costo, 4)
     WHERE id = NEW.producto_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
