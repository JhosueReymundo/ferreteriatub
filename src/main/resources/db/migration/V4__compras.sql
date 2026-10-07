-- =====================================================================
-- V4: Compras
-- =====================================================================
-- El proveedor es opcional. Cada línea guarda el costo pagado ESA vez;
-- el stock y el costo promedio los mueve el kardex (movimiento_inventario).

CREATE TABLE compra (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    proveedor_id      BIGINT        REFERENCES proveedor(id),          -- opcional
    usuario_id        BIGINT        NOT NULL REFERENCES usuario(id),
    fecha             DATE          NOT NULL DEFAULT CURRENT_DATE,     -- fecha de la compra (puede ser pasada)
    nro_comprobante   VARCHAR(30),                                     -- factura/boleta del proveedor, opcional
    total             NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    estado            VARCHAR(10)   NOT NULL DEFAULT 'REGISTRADA'
                      CHECK (estado IN ('REGISTRADA','ANULADA')),
    observacion       VARCHAR(255),
    creado_en         TIMESTAMPTZ   NOT NULL DEFAULT now(),            -- cuándo se registró en el sistema
    anulada_en        TIMESTAMPTZ,
    anulada_por       BIGINT        REFERENCES usuario(id),
    motivo_anulacion  VARCHAR(255),
    -- Una compra está anulada si y solo si tiene fecha de anulación
    CONSTRAINT ck_compra_anulacion CHECK ((estado = 'ANULADA') = (anulada_en IS NOT NULL))
);

CREATE INDEX ix_compra_fecha     ON compra (fecha DESC);
CREATE INDEX ix_compra_proveedor ON compra (proveedor_id);

CREATE TABLE detalle_compra (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    compra_id       BIGINT        NOT NULL REFERENCES compra(id),
    producto_id     BIGINT        NOT NULL REFERENCES producto(id),
    cantidad        NUMERIC(12,2) NOT NULL CHECK (cantidad > 0),
    costo_unitario  NUMERIC(12,4) NOT NULL CHECK (costo_unitario >= 0),  -- lo que pagaste ESA vez
    subtotal        NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0)
);

CREATE INDEX ix_detalle_compra_compra   ON detalle_compra (compra_id);
CREATE INDEX ix_detalle_compra_producto ON detalle_compra (producto_id);

-- El kardex apunta a la compra que lo originó
ALTER TABLE movimiento_inventario ADD COLUMN compra_id BIGINT REFERENCES compra(id);
CREATE INDEX ix_mov_compra ON movimiento_inventario (compra_id);

-- ---------------------------------------------------------------------
-- Trigger del kardex: ahora también "deshace" el aporte de una compra
-- anulada al costo promedio (antes solo movía el stock).
-- Ejemplo: stock 20 a S/6 (valor 120); se anula una compra de 10 a S/7:
--          (20*6 - 10*7) / 10 = S/5 de costo promedio.
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
        IF NEW.cantidad > 0 AND NEW.tipo IN ('STOCK_INICIAL','COMPRA','AJUSTE_ENTRADA') THEN
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
