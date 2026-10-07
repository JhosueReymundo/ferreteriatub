package com.tuberias.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Una fila del kardex de un producto. La cantidad es positiva si entra y negativa si sale. */
public record MovimientoInventario(
        long id,
        String tipo,
        BigDecimal cantidad,
        BigDecimal costoUnitario,
        String usuario,
        Instant fecha,
        String observacion) {

    public String tipoEtiqueta() {
        return switch (tipo) {
            case "STOCK_INICIAL" -> "Stock inicial";
            case "COMPRA" -> "Compra";
            case "VENTA" -> "Venta";
            case "AJUSTE_ENTRADA" -> "Ajuste (+)";
            case "AJUSTE_SALIDA" -> "Ajuste (−)";
            case "ANULACION_COMPRA" -> "Anulación de compra";
            case "ANULACION_VENTA" -> "Anulación de venta";
            default -> tipo;
        };
    }
}
