package com.tuberias.model;

import java.math.BigDecimal;

/** Existencias con las que ya cuenta el negocio al registrar un producto. */
public record StockInicial(BigDecimal cantidad, BigDecimal costoUnitario) {
}
