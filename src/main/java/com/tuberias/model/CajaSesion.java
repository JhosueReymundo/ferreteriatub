package com.tuberias.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un turno de caja. Mientras está abierta, {@code fechaCierre}, {@code montoEsperado}
 * y {@code montoCierreReal} son null.
 */
public record CajaSesion(
        long id,
        String usuario,
        Instant fechaApertura,
        BigDecimal montoApertura,
        boolean abierta,
        Instant fechaCierre,
        BigDecimal montoEsperado,
        BigDecimal montoCierreReal,
        String observacion) {

    /** Real - esperado: negativo = faltó plata, positivo = sobró. Null si aún está abierta. */
    public BigDecimal diferencia() {
        return abierta ? null : montoCierreReal.subtract(montoEsperado);
    }
}
