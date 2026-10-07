package com.tuberias.model;

import java.math.BigDecimal;
import java.util.Locale;

/** Formato de números para mostrar en pantalla. */
public final class Formato {

    private Formato() {}

    /** 12.50 -> "12.5", 100.00 -> "100", null -> "". */
    public static String numero(BigDecimal valor) {
        return valor == null ? "" : valor.stripTrailingZeros().toPlainString();
    }

    /** 1234.5 -> "S/ 1,234.50", null -> "". */
    public static String dinero(BigDecimal valor) {
        return valor == null ? "" : String.format(Locale.US, "S/ %,.2f", valor);
    }

    /** Costo unitario con 2 a 4 decimales: 12.5 -> "S/ 12.50", 12.3456 -> "S/ 12.3456". */
    public static String costo(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        int decimales = Math.max(2, Math.min(4, valor.stripTrailingZeros().scale()));
        return String.format(Locale.US, "S/ %,." + decimales + "f", valor);
    }
}
