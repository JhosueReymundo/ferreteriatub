package com.tuberias.service;

import java.math.BigDecimal;

/** Validaciones de campos compartidas por los servicios. */
final class Validar {

    // Límites según las columnas NUMERIC de la base de datos
    static final BigDecimal LIMITE_8_2 = new BigDecimal("1000000");
    static final BigDecimal LIMITE_6_2 = new BigDecimal("10000");
    static final BigDecimal LIMITE_12_2 = new BigDecimal("10000000000");
    static final BigDecimal LIMITE_12_4 = new BigDecimal("100000000");

    private Validar() {}

    /** Recorta el texto; vacío = null (o error si es obligatorio); valida el largo máximo. */
    static String texto(String valor, String campo, int max, boolean obligatorio) {
        String t = valor == null ? "" : valor.trim();
        if (t.isEmpty()) {
            if (obligatorio) {
                throw new NegocioException(campo + " es obligatorio.");
            }
            return null;
        }
        if (t.length() > max) {
            throw new NegocioException(campo + " es demasiado largo (máximo " + max + " caracteres).");
        }
        return t;
    }

    /** Valida un decimal opcional (null pasa): signo, cantidad de decimales y tamaño. */
    static BigDecimal decimal(BigDecimal valor, String campo, int decimales,
                              BigDecimal maxExclusivo, boolean permiteCero) {
        if (valor == null) {
            return null;
        }
        if (valor.signum() < 0) {
            throw new NegocioException(campo + " no puede ser negativo.");
        }
        if (!permiteCero && valor.signum() == 0) {
            throw new NegocioException(campo + " debe ser mayor que 0.");
        }
        if (valor.stripTrailingZeros().scale() > decimales) {
            throw new NegocioException(campo + " admite como máximo " + decimales + " decimales.");
        }
        if (valor.compareTo(maxExclusivo) >= 0) {
            throw new NegocioException(campo + " es demasiado grande.");
        }
        return valor;
    }
}
