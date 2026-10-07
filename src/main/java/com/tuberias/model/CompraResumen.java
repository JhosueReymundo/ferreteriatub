package com.tuberias.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fila del listado de compras. {@code proveedor} es null si no se indicó. */
public record CompraResumen(
        long id,
        LocalDate fecha,
        String proveedor,
        String comprobante,
        BigDecimal total,
        boolean anulada,
        String usuario) {
}
