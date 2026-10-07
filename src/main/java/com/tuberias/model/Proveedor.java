package com.tuberias.model;

public record Proveedor(long id, String nombre, String ruc, boolean activo) {

    /** Texto para listas desplegables. */
    @Override
    public String toString() {
        return ruc == null ? nombre : nombre + " — " + ruc;
    }
}
