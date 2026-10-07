package com.tuberias.model;

public record UnidadMedida(short id, String codigo, String nombre) {

    @Override
    public String toString() {
        return nombre + " (" + codigo + ")";
    }
}
