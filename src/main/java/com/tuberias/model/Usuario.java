package com.tuberias.model;

import java.time.Instant;

public record Usuario(
        long id,
        String username,
        String nombreCompleto,
        short rolId,
        String rolNombre,
        String passwordHash,
        String codigoRecuperacionHash,
        boolean debeCambiarPassword,
        int intentosFallidos,
        Instant bloqueadoHasta,
        boolean activo) {

    public boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    public boolean esAdmin() {
        return rolId == Roles.ADMIN;
    }
}
