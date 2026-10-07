package com.tuberias.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Un producto dentro de una venta, al precio realmente cobrado (puede llevar rebaja). */
public record LineaVenta(long productoId, BigDecimal cantidad, BigDecimal precioUnitario) {

    /** cantidad × precio, redondeado a 2 decimales (igual que la columna subtotal). */
    public BigDecimal subtotal() {
        return cantidad.multiply(precioUnitario).setScale(2, RoundingMode.HALF_UP);
    }
}
