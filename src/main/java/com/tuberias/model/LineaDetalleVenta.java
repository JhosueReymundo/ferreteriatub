package com.tuberias.model;

import java.math.BigDecimal;

public record LineaDetalleVenta(
        long productoId,
        String codigo,
        String nombre,
        String unidad,
        BigDecimal cantidad,
        BigDecimal precioUnitario,
        BigDecimal costoUnitario,
        BigDecimal subtotal) {

    /** Ganancia de la línea: lo cobrado menos el costo de lo que salió. */
    public BigDecimal utilidad() {
        return subtotal.subtract(cantidad.multiply(costoUnitario));
    }
}
