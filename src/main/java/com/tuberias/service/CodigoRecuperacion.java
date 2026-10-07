package com.tuberias.service;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Código de recuperación de 12 caracteres (formato XXXX-XXXX-XXXX).
 * Usa un alfabeto sin caracteres ambiguos (sin 0/O, 1/I/L) para que sea fácil de anotar.
 */
public final class CodigoRecuperacion {

    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LONGITUD = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodigoRecuperacion() {}

    /** Genera un código nuevo ya formateado para mostrarlo al usuario. */
    public static String generar() {
        StringBuilder sb = new StringBuilder(LONGITUD + 2);
        for (int i = 0; i < LONGITUD; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /** Quita espacios y guiones y pasa a mayúsculas; es lo que se hashea y se compara. */
    public static String normalizar(String ingresado) {
        if (ingresado == null) return "";
        return ingresado.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
