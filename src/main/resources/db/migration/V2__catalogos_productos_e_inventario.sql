-- =====================================================================
-- V2: Catálogos, productos e inventario (kardex)
-- =====================================================================

-- ---------------------------------------------------------------------
-- Catálogos
-- ---------------------------------------------------------------------
CREATE TABLE categoria (                 -- tipo de tubo: PVC, acero, galvanizado, etc.
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  VARCHAR(80) NOT NULL,
    activo  BOOLEAN     NOT NULL DEFAULT TRUE
);
-- Evita duplicados que solo cambian en mayúsculas ("PVC" y "pvc")
CREATE UNIQUE INDEX ux_categoria_nombre ON categoria (lower(nombre));

CREATE TABLE marca (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  VARCHAR(80) NOT NULL,
    activo  BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_marca_nombre ON marca (lower(nombre));

CREATE TABLE unidad_medida (
    id      SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo  VARCHAR(10) NOT NULL UNIQUE,   -- UND, VARA, M
    nombre  VARCHAR(40) NOT NULL
);

INSERT INTO unidad_medida (codigo, nombre) VALUES
    ('UND',  'Unidad'),
    ('VARA', 'Vara'),
    ('M',    'Metro');

-- ---------------------------------------------------------------------
-- Producto (SIN precios de compra/venta fijos)
-- ---------------------------------------------------------------------
CREATE SEQUENCE seq_codigo_producto START 1;   -- para generar P00001, P00002...

CREATE TABLE producto (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo           VARCHAR(30)  NOT NULL UNIQUE,
    nombre           VARCHAR(150) NOT NULL,
    categoria_id     BIGINT       NOT NULL REFERENCES categoria(id),
    marca_id         BIGINT       REFERENCES marca(id),
    modelo           VARCHAR(80),
    unidad_id        SMALLINT     NOT NULL REFERENCES unidad_medida(id),

    -- Características físicas (se llenan las que apliquen a cada tubo)
    forma            VARCHAR(15)
                     CHECK (forma IN ('CIRCULAR','CUADRADO','RECTANGULAR','OVALADO','OTRO')),
    material         VARCHAR(60),                -- PVC, acero negro, galvanizado, etc.
    diametro_mm      NUMERIC(8,2) CHECK (diametro_mm > 0),   -- circulares
    ancho_mm         NUMERIC(8,2) CHECK (ancho_mm > 0),      -- cuadrados / rectangulares
    alto_mm          NUMERIC(8,2) CHECK (alto_mm > 0),
    espesor_mm       NUMERIC(6,2) CHECK (espesor_mm > 0),
    longitud_m       NUMERIC(8,2) CHECK (longitud_m > 0),
    color            VARCHAR(30),
    norma_clase      VARCHAR(60),                -- ej. SAP, SAL, C-10, ASTM
    descripcion      VARCHAR(500),               -- otras características en texto libre

    -- Solo una sugerencia al vender; el precio real queda en detalle_venta
    precio_sugerido  NUMERIC(12,2) CHECK (precio_sugerido >= 0),

    -- Los mantiene el trigger a partir de movimiento_inventario; no se editan a mano
    stock_actual     NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (stock_actual >= 0),
    costo_promedio   NUMERIC(12,4) NOT NULL DEFAULT 0 CHECK (costo_promedio >= 0),
    stock_minimo     NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (stock_minimo >= 0),

    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_producto_categoria ON producto(categoria_id);
CREATE INDEX ix_producto_nombre    ON producto(lower(nombre));

-- ---------------------------------------------------------------------
-- Kardex: movimientos de inventario (fuente de verdad del stock)
-- compra_id y venta_id se agregan en las migraciones de compras y ventas.
-- ---------------------------------------------------------------------
CREATE TABLE movimiento_inventario (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id     BIGINT        NOT NULL REFERENCES producto(id),
    tipo            VARCHAR(20)   NOT NULL
                    CHECK (tipo IN ('STOCK_INICIAL','COMPRA','VENTA',
                                    'AJUSTE_ENTRADA','AJUSTE_SALIDA',
                                    'ANULACION_COMPRA','ANULACION_VENTA')),
    cantidad        NUMERIC(12,2) NOT NULL CHECK (cantidad <> 0),   -- + entra, - sale
    costo_unitario  NUMERIC(12,4) CHECK (costo_unitario >= 0),
    usuario_id      BIGINT        NOT NULL REFERENCES usuario(id),
    fecha           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    observacion     VARCHAR(255),
    -- Las entradas valorizadas necesitan costo
    CONSTRAINT ck_costo_requerido
        CHECK (tipo NOT IN ('STOCK_INICIAL','COMPRA') OR costo_unitario IS NOT NULL),
    -- El signo de la cantidad debe ser coherente con el tipo
    CONSTRAINT ck_signo
        CHECK (
            (tipo IN ('STOCK_INICIAL','COMPRA','AJUSTE_ENTRADA','ANULACION_VENTA') AND cantidad > 0) OR
            (tipo IN ('VENTA','AJUSTE_SALIDA','ANULACION_COMPRA') AND cantidad < 0)
        )
);

CREATE INDEX ix_mov_producto_fecha ON movimiento_inventario(producto_id, fecha);

-- Trigger: actualiza stock y costo promedio ponderado en producto
CREATE OR REPLACE FUNCTION fn_aplicar_movimiento() RETURNS trigger AS $$
DECLARE
    v_stock  NUMERIC;
    v_costo  NUMERIC;
BEGIN
    SELECT stock_actual, costo_promedio
      INTO v_stock, v_costo
      FROM producto
     WHERE id = NEW.producto_id
       FOR UPDATE;

    -- Solo las entradas con costo recalculan el promedio ponderado
    IF NEW.cantidad > 0
       AND NEW.costo_unitario IS NOT NULL
       AND NEW.tipo IN ('STOCK_INICIAL','COMPRA','AJUSTE_ENTRADA')
       AND (v_stock + NEW.cantidad) > 0 THEN
        v_costo := ((v_stock * v_costo) + (NEW.cantidad * NEW.costo_unitario))
                   / (v_stock + NEW.cantidad);
    END IF;

    -- Si el stock quedara negativo, el CHECK de producto lanza error y se revierte todo
    UPDATE producto
       SET stock_actual   = v_stock + NEW.cantidad,
           costo_promedio = ROUND(v_costo, 4)
     WHERE id = NEW.producto_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_aplicar_movimiento
    AFTER INSERT ON movimiento_inventario
    FOR EACH ROW EXECUTE FUNCTION fn_aplicar_movimiento();

-- ---------------------------------------------------------------------
-- Vista para la alerta de stock mínimo
-- Solo alerta si el producto tiene un stock mínimo configurado (> 0)
-- ---------------------------------------------------------------------
CREATE VIEW v_stock_bajo AS
SELECT p.id, p.codigo, p.nombre, p.stock_actual, p.stock_minimo
  FROM producto p
 WHERE p.activo
   AND p.stock_minimo > 0
   AND p.stock_actual <= p.stock_minimo;
