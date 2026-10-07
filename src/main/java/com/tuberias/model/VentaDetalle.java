package com.tuberias.model;

import java.math.BigDecimal;
import java.util.List;

public record VentaDetalle(
        VentaResumen resumen,
        String observacion,
        String motivoAnulacion,
        List<LineaDetalleVenta> lineas) {

    public BigDecimal utilidadTotal() {
        return lineas.stream().map(LineaDetalleVenta::utilidad).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
