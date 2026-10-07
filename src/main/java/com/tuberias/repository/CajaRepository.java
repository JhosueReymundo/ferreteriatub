package com.tuberias.repository;

import com.tuberias.model.CajaSesion;
import com.tuberias.model.MetodoPago;
import com.tuberias.model.MovimientoCaja;
import com.tuberias.model.TipoMovimientoCaja;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CajaRepository {

    Optional<CajaSesion> buscarAbierta();

    Optional<CajaSesion> buscarSesion(long id);

    /** Más recientes primero. */
    List<CajaSesion> listarSesiones(int limite);

    /** Movimientos de una sesión, del más antiguo al más reciente. */
    List<MovimientoCaja> listarMovimientos(long sesionId);

    /** Abre una caja. Lanza NegocioException si ya hay una abierta. Devuelve el id. */
    long abrir(long usuarioId, BigDecimal montoApertura, String observacion);

    /**
     * Cierra la caja abierta: calcula el efectivo esperado dentro de la misma transacción y guarda
     * junto al monto contado. Lanza NegocioException si no hay caja abierta.
     */
    CajaSesion cerrar(long usuarioId, BigDecimal montoContado, String observacion);

    /**
     * Ingreso o egreso manual en la caja abierta. Lanza NegocioException si no hay caja abierta
     * o si un egreso en efectivo supera el efectivo disponible.
     */
    void registrarMovimiento(long usuarioId, TipoMovimientoCaja tipo, String concepto,
                             BigDecimal monto, MetodoPago metodoPago);
}
