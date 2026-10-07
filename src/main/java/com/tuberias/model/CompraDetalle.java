package com.tuberias.model;

import java.util.List;

public record CompraDetalle(
        CompraResumen resumen,
        String observacion,
        String motivoAnulacion,
        List<LineaDetalleCompra> lineas) {
}
