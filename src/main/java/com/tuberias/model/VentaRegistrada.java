package com.tuberias.model;

import java.math.BigDecimal;
import java.util.List;

/** Resultado de registrar una venta, incluidos los productos que quedaron en alerta de stock mínimo. */
public record VentaRegistrada(long id, BigDecimal total, List<ProductoStockBajo> stockBajo) {
}
