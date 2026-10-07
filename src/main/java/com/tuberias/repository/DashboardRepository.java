package com.tuberias.repository;

import com.tuberias.model.ProductoVendido;
import com.tuberias.model.ResumenVentas;
import com.tuberias.model.VentaDia;
import com.tuberias.model.VentaPorMetodo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Consultas de solo lectura para los indicadores. Los rangos de ventas son [desde, hasta)
 * en instantes; así el "día" lo decide la zona del negocio y no la de la base de datos.
 */
public interface DashboardRepository {

    ResumenVentas resumenVentas(Instant desde, Instant hasta);

    /** Solo los días con ventas, en orden. */
    List<VentaDia> ventasPorDia(Instant desde, Instant hasta, ZoneId zona);

    List<ProductoVendido> masVendidos(Instant desde, Instant hasta, int limite);

    List<VentaPorMetodo> ventasPorMetodo(Instant desde, Instant hasta);

    /** Compras no anuladas con fecha entre ambos días, inclusive. */
    BigDecimal totalCompras(LocalDate desde, LocalDate hasta);

    /** Stock actual x costo promedio de los productos activos. */
    BigDecimal valorInventario();
}
