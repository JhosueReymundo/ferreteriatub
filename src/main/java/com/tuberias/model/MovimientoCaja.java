package com.tuberias.model;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code ventaId} / {@code compraId} solo vienen cuando el movimiento nació de una venta o compra. */
public record MovimientoCaja(
        long id,
        Instant fecha,
        TipoMovimientoCaja tipo,
        OrigenCaja origen,
        String concepto,
        BigDecimal monto,
        MetodoPago metodoPago,
        Long ventaId,
        Long compraId,
        String usuario) {

    /** Monto con signo: positivo si entra, negativo si sale. */
    public BigDecimal montoFirmado() {
        return tipo == TipoMovimientoCaja.INGRESO ? monto : monto.negate();
    }
}
