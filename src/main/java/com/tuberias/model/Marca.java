package com.tuberias.model;

public record Marca(long id, String nombre) {

    @Override
    public String toString() {
        return nombre;
    }
}
