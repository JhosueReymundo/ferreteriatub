package com.tuberias.model;

import java.math.BigDecimal;

public record LineaDetalleCompra(
        long productoId,
        String codigo,
        String nombre,
        String unidad,
        BigDecimal cantidad,
        BigDecimal costoUnitario,
        BigDecimal subtotal) {
}
