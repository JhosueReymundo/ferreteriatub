package com.tuberias.model;

/** Tipo de tubo (PVC, acero, galvanizado...). */
public record Categoria(long id, String nombre) {

    @Override
    public String toString() {
        return nombre;
    }
}
