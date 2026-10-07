-- =====================================================================
-- V6: Caja
-- =====================================================================
-- Una "sesión" de caja es un turno: se abre con un monto inicial, se registran
-- movimientos y se cierra contando el efectivo.
--  * Solo puede haber UNA caja abierta a la vez.
--  * Las ventas se registran siempre en la caja abierta (ingreso).
--  * Las compras se registran en caja solo si el usuario lo indica (puede
--    haberse pagado por otro medio o a crédito).
--  * El stock inicial NO toca la caja (es mercadería que ya se tenía).
--  * El efectivo esperado = monto de apertura + ingresos en EFECTIVO - egresos en EFECTIVO.
--    Yape/Plin, transferencias y tarjeta se muestran aparte: no están en el cajón.
--  * Los movimientos no se borran ni se editan: una anulación genera el movimiento contrario.

CREATE TABLE caja_sesion (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id         BIGINT        NOT NULL REFERENCES usuario(id),
    fecha_apertura     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    monto_apertura     NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (monto_apertura >= 0),
    estado             VARCHAR(10)   NOT NULL DEFAULT 'ABIERTA'
                       CHECK (estado IN ('ABIERTA','CERRADA')),
    fecha_cierre       TIMESTAMPTZ,
    cerrada_por        BIGINT        REFERENCES usuario(id),
    monto_esperado     NUMERIC(12,2),              -- lo que el sistema calculó al cerrar
    monto_cierre_real  NUMERIC(12,2) CHECK (monto_cierre_real >= 0),  -- lo que se contó
    observacion        VARCHAR(255),
    -- Abierta: sin datos de cierre. Cerrada: con todos los datos de cierre.
    CONSTRAINT ck_caja_cierre CHECK (
        (estado = 'ABIERTA' AND fecha_cierre IS NULL AND cerrada_por IS NULL
                            AND monto_esperado IS NULL AND monto_cierre_real IS NULL)
        OR
        (estado = 'CERRADA' AND fecha_cierre IS NOT NULL AND cerrada_por IS NOT NULL
                            AND monto_esperado IS NOT NULL AND monto_cierre_real IS NOT NULL)
    )
);

-- Solo una caja abierta a la vez
CREATE UNIQUE INDEX ux_una_caja_abierta ON caja_sesion (estado) WHERE estado = 'ABIERTA';
CREATE INDEX ix_caja_apertura ON caja_sesion (fecha_apertura DESC);

CREATE TABLE movimiento_caja (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    caja_sesion_id  BIGINT        NOT NULL REFERENCES caja_sesion(id),
    tipo            VARCHAR(10)   NOT NULL CHECK (tipo IN ('INGRESO','EGRESO')),
    origen          VARCHAR(20)   NOT NULL
                    CHECK (origen IN ('MANUAL','VENTA','ANULACION_VENTA','COMPRA','ANULACION_COMPRA')),
    concepto        VARCHAR(150)  NOT NULL,
    monto           NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    metodo_pago     VARCHAR(15)   NOT NULL DEFAULT 'EFECTIVO'
                    CHECK (metodo_pago IN ('EFECTIVO','YAPE_PLIN','TRANSFERENCIA','TARJETA')),
    venta_id        BIGINT        REFERENCES venta(id),
    compra_id       BIGINT        REFERENCES compra(id),
    usuario_id      BIGINT        NOT NULL REFERENCES usuario(id),
    fecha           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- El origen decide si hay venta/compra asociada y si entra o sale dinero
    CONSTRAINT ck_mov_caja_venta  CHECK ((origen IN ('VENTA','ANULACION_VENTA')) = (venta_id IS NOT NULL)),
    CONSTRAINT ck_mov_caja_compra CHECK ((origen IN ('COMPRA','ANULACION_COMPRA')) = (compra_id IS NOT NULL)),
    CONSTRAINT ck_mov_caja_tipo CHECK (
        origen = 'MANUAL'
        OR (origen = 'VENTA'             AND tipo = 'INGRESO')
        OR (origen = 'ANULACION_VENTA'   AND tipo = 'EGRESO')
        OR (origen = 'COMPRA'            AND tipo = 'EGRESO')
        OR (origen = 'ANULACION_COMPRA'  AND tipo = 'INGRESO')
    )
);

CREATE INDEX ix_mov_caja_sesion ON movimiento_caja (caja_sesion_id, fecha);

-- Una venta/compra solo puede cobrarse/pagarse y anularse UNA vez en caja
CREATE UNIQUE INDEX ux_mov_caja_venta         ON movimiento_caja (venta_id)  WHERE origen = 'VENTA';
CREATE UNIQUE INDEX ux_mov_caja_anul_venta    ON movimiento_caja (venta_id)  WHERE origen = 'ANULACION_VENTA';
CREATE UNIQUE INDEX ux_mov_caja_compra        ON movimiento_caja (compra_id) WHERE origen = 'COMPRA';
CREATE UNIQUE INDEX ux_mov_caja_anul_compra   ON movimiento_caja (compra_id) WHERE origen = 'ANULACION_COMPRA';

-- Defensa extra: no se puede agregar movimientos a una caja ya cerrada
CREATE FUNCTION fn_movimiento_caja_solo_abierta() RETURNS trigger AS $$
BEGIN
    IF (SELECT estado FROM caja_sesion WHERE id = NEW.caja_sesion_id) <> 'ABIERTA' THEN
        RAISE EXCEPTION 'La caja % está cerrada', NEW.caja_sesion_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_movimiento_caja_solo_abierta
    BEFORE INSERT ON movimiento_caja
    FOR EACH ROW EXECUTE FUNCTION fn_movimiento_caja_solo_abierta();
