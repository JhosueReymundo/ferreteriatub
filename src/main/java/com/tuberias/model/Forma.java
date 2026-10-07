package com.tuberias.model;

/** Forma de la sección del tubo. SIN_ESPECIFICAR se guarda como NULL en la base de datos. */
public enum Forma {
    SIN_ESPECIFICAR("Sin especificar"),
    CIRCULAR("Circular"),
    CUADRADO("Cuadrado"),
    RECTANGULAR("Rectangular"),
    OVALADO("Ovalado"),
    OTRO("Otro");

    private final String etiqueta;

    Forma(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Valor para la columna producto.forma (null si no se especificó). */
    public String aBd() {
        return this == SIN_ESPECIFICAR ? null : name();
    }

    public static Forma deBd(String valor) {
        return valor == null ? SIN_ESPECIFICAR : valueOf(valor);
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
