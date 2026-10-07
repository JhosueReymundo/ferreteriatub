package com.tuberias.model;

import java.math.BigDecimal;

public record ProductoVendido(String nombre, String unidad, BigDecimal cantidad, BigDecimal total) {
}
