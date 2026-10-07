package com.tuberias.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Total vendido en un día (ventas no anuladas). */
public record VentaDia(LocalDate dia, BigDecimal total) {
}
