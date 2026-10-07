package com.tuberias.model;

import java.math.BigDecimal;
import java.util.List;

/** Datos para registrar una venta. El cliente es opcional (null = cliente varios). */
public record NuevaVenta(
        Long clienteId,
        MetodoPago metodoPago,
        String observacion,
        List<LineaVenta> lineas) {

    public BigDecimal total() {
        return lineas.stream().map(LineaVenta::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
