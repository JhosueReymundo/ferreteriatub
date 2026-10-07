package com.tuberias.service;

/** Error de negocio con un mensaje que se puede mostrar tal cual al usuario. */
public class NegocioException extends RuntimeException {

    public NegocioException(String mensaje) {
        super(mensaje);
    }
}
