package com.tuberias.service;

import org.mindrot.jbcrypt.BCrypt;

/** Hash de contraseñas y códigos con BCrypt. Nunca se guarda texto plano. */
public class PasswordHasher {

    private static final int COSTO = 12;

    public String hash(String plano) {
        return BCrypt.hashpw(plano, BCrypt.gensalt(COSTO));
    }

    public boolean verificar(String plano, String hash) {
        if (plano == null || hash == null) return false;
        try {
            return BCrypt.checkpw(plano, hash);
        } catch (IllegalArgumentException e) {
            return false; // hash con formato inválido
        }
    }
}
