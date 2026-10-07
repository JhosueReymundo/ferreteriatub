package com.tuberias.service;

import com.tuberias.model.Usuario;

/** Usuario que tiene la sesión abierta (la app de escritorio solo tiene una a la vez). */
public final class Sesion {

    private static Usuario actual;

    private Sesion() {}

    public static void iniciar(Usuario usuario) {
        actual = usuario;
    }

    public static Usuario actual() {
        return actual;
    }

    public static void cerrar() {
        actual = null;
    }
}
