package com.tuberias.model;

import java.math.BigDecimal;

/** Ventas no anuladas de un rango. {@code utilidad} = lo cobrado menos el costo de lo vendido. */
public record ResumenVentas(int nroVentas, BigDecimal total, BigDecimal utilidad) {
}
