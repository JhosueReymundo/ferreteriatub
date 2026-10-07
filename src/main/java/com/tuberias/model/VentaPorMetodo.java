package com.tuberias.model;

import java.math.BigDecimal;

public record VentaPorMetodo(MetodoPago metodoPago, BigDecimal total) {
}
