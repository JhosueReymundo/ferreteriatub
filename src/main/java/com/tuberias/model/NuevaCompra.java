package com.tuberias.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos para registrar una compra. El proveedor es opcional (null = sin proveedor).
 * {@code pagoCaja}: con qué se pagó desde la caja abierta; null = no registrar en caja
 * (pagada por otro medio, a crédito, o mercadería que ya se tenía).
 */
public record NuevaCompra(
        Long proveedorId,
        LocalDate fecha,
        String comprobante,
        String observacion,
        List<LineaCompra> lineas,
        MetodoPago pagoCaja) {

    public BigDecimal total() {
        return lineas.stream().map(LineaCompra::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
