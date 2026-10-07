package com.tuberias.model;

/** Si el dinero entra o sale de la caja. El nombre es el valor guardado en movimiento_caja.tipo. */
public enum TipoMovimientoCaja {
    INGRESO("Ingreso"),
    EGRESO("Egreso");

    private final String etiqueta;

    TipoMovimientoCaja(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
