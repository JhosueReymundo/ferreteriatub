package com.tuberias.model;

public record Cliente(long id, String nombre, String documento, String telefono, boolean activo) {

    /** Texto para listas desplegables. */
    @Override
    public String toString() {
        return documento == null ? nombre : nombre + " — " + documento;
    }
}
