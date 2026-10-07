package com.tuberias.model;

/** Cómo pagó el cliente. El nombre del enum es el valor guardado en venta.metodo_pago. */
public enum MetodoPago {
    EFECTIVO("Efectivo"),
    YAPE_PLIN("Yape / Plin"),
    TRANSFERENCIA("Transferencia"),
    TARJETA("Tarjeta");

    private final String etiqueta;

    MetodoPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
