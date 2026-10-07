package com.tuberias.model;

/** Qué originó un movimiento de caja. El nombre es el valor guardado en movimiento_caja.origen. */
public enum OrigenCaja {
    MANUAL("Manual"),
    VENTA("Venta"),
    ANULACION_VENTA("Anulación de venta"),
    COMPRA("Compra"),
    ANULACION_COMPRA("Anulación de compra");

    private final String etiqueta;

    OrigenCaja(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
