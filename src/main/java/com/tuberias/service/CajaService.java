package com.tuberias.service;

import com.tuberias.model.CajaSesion;
import com.tuberias.model.MetodoPago;
import com.tuberias.model.MovimientoCaja;
import com.tuberias.model.ResumenCaja;
import com.tuberias.model.TipoMovimientoCaja;
import com.tuberias.model.Usuario;
import com.tuberias.repository.CajaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.tuberias.service.Validar.*;

/**
 * Reglas de la caja. Abrir y cerrar pueden hacerlo todos los usuarios (el vendedor abre el negocio);
 * los ingresos y egresos manuales (gastos, aportes) son solo del administrador.
 */
public class CajaService {

    private static final int MAX_SESIONES = 60;

    private final CajaRepository repo;

    public CajaService(CajaRepository repo) {
        this.repo = repo;
    }

    // ------------------------------------------------------------- consultas

    public Optional<CajaSesion> cajaAbierta() {
        return repo.buscarAbierta();
    }

    /** Últimas sesiones, la más reciente primero. */
    public List<CajaSesion> sesiones() {
        return repo.listarSesiones(MAX_SESIONES);
    }

    public Optional<ResumenCaja> resumen(long sesionId) {
        return repo.buscarSesion(sesionId)
                .map(s -> ResumenCaja.de(s, repo.listarMovimientos(sesionId)));
    }

    public List<MovimientoCaja> movimientos(long sesionId) {
        return repo.listarMovimientos(sesionId);
    }

    // ------------------------------------------------------- abrir y cerrar

    /** Abre la caja con el efectivo inicial (puede ser 0). */
    public long abrir(Usuario usuario, BigDecimal montoApertura, String observacion) {
        if (montoApertura == null) {
            throw new NegocioException("Indica con cuánto efectivo abres la caja (puede ser 0).");
        }
        BigDecimal monto = decimal(montoApertura, "El monto de apertura", 2, LIMITE_12_2, true);
        return repo.abrir(usuario.id(), monto, texto(observacion, "La observación", 255, false));
    }

    /**
     * Cierra la caja con el efectivo realmente contado. El sistema calcula lo esperado y devuelve
     * la sesión cerrada, cuya {@code diferencia()} dice si faltó o sobró dinero.
     */
    public CajaSesion cerrar(Usuario usuario, BigDecimal montoContado, String observacion) {
        if (montoContado == null) {
            throw new NegocioException("Indica cuánto efectivo contaste en la caja.");
        }
        BigDecimal contado = decimal(montoContado, "El efectivo contado", 2, LIMITE_12_2, true);
        return repo.cerrar(usuario.id(), contado, texto(observacion, "La observación", 255, false));
    }

    // ---------------------------------------------------- movimientos manuales

    public void registrarMovimiento(Usuario usuario, TipoMovimientoCaja tipo, String concepto,
                                    BigDecimal monto, MetodoPago metodoPago) {
        if (usuario == null || !usuario.esAdmin()) {
            throw new NegocioException("Solo el administrador puede registrar ingresos o egresos manuales.");
        }
        if (tipo == null) {
            throw new NegocioException("Indica si es un ingreso o un egreso.");
        }
        if (metodoPago == null) {
            throw new NegocioException("Selecciona el método de pago.");
        }
        String c = texto(concepto, "El concepto", 150, true);
        if (monto == null) {
            throw new NegocioException("El monto es obligatorio.");
        }
        BigDecimal m = decimal(monto, "El monto", 2, LIMITE_12_2, false);
        repo.registrarMovimiento(usuario.id(), tipo, c, m, metodoPago);
    }
}
