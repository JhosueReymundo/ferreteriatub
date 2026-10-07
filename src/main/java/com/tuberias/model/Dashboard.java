package com.tuberias.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Indicadores del inicio. {@code utilidad}, {@code totalCompras} y {@code valorInventario}
 * son null para quien no es administrador (muestran costos).
 */
public record Dashboard(
        Periodo periodo,
        LocalDate desde,
        LocalDate hasta,
        int nroVentas,
        BigDecimal totalVendido,
        BigDecimal ticketPromedio,
        BigDecimal utilidad,
        BigDecimal totalCompras,
        BigDecimal valorInventario,
        List<VentaDia> ventasPorDia,
        List<ProductoVendido> masVendidos,
        List<VentaPorMetodo> porMetodo) {
}
