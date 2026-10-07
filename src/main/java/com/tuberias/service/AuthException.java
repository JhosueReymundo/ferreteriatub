package com.tuberias.service;

/** Error de seguridad (login, contraseñas) con un mensaje para mostrar al usuario. */
public class AuthException extends NegocioException {

    public AuthException(String mensaje) {
        super(mensaje);
    }
}
