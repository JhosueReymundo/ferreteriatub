package com.tuberias.model;

import java.math.BigDecimal;

/** Producto cuyo stock actual está en o por debajo de su stock mínimo. */
public record ProductoStockBajo(
        long id,
        String codigo,
        String nombre,
        BigDecimal stockActual,
        BigDecimal stockMinimo) {

    /** Cantidad que falta para llegar al mínimo (0 si ya lo alcanzó). */
    public BigDecimal faltante() {
        BigDecimal dif = stockMinimo.subtract(stockActual);
        return dif.signum() > 0 ? dif : BigDecimal.ZERO;
    }

    public boolean agotado() {
        return stockActual.signum() <= 0;
    }
}
