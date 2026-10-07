package com.tuberias.model;

import java.time.LocalDate;

/** Rango de días que muestra el dashboard (siempre termina hoy). */
public enum Periodo {
    HOY("Hoy"),
    ULTIMOS_7("Últimos 7 días"),
    ULTIMOS_30("Últimos 30 días"),
    ESTE_MES("Este mes");

    private final String etiqueta;

    Periodo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Primer día del período (incluido), dado el día de hoy. */
    public LocalDate desde(LocalDate hoy) {
        return switch (this) {
            case HOY -> hoy;
            case ULTIMOS_7 -> hoy.minusDays(6);
            case ULTIMOS_30 -> hoy.minusDays(29);
            case ESTE_MES -> hoy.withDayOfMonth(1);
        };
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
