package com.tuberias.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Fila del listado de ventas. {@code cliente} es null si no se indicó. */
public record VentaResumen(
        long id,
        Instant fecha,
        String cliente,
        MetodoPago metodoPago,
        BigDecimal total,
        boolean anulada,
        String usuario) {
}
