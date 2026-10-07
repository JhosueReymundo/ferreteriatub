package com.tuberias.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Un producto dentro de una compra: cuánto entra y a qué costo unitario se pagó ESA vez. */
public record LineaCompra(long productoId, BigDecimal cantidad, BigDecimal costoUnitario) {

    /** cantidad × costo, redondeado a 2 decimales (igual que la columna subtotal). */
    public BigDecimal subtotal() {
        return cantidad.multiply(costoUnitario).setScale(2, RoundingMode.HALF_UP);
    }
}
