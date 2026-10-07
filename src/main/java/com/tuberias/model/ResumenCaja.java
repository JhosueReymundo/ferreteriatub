package com.tuberias.model;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Totales de una sesión de caja, calculados a partir de sus movimientos. */
public record ResumenCaja(
        CajaSesion sesion,
        BigDecimal totalIngresos,
        BigDecimal totalEgresos,
        Map<MetodoPago, BigDecimal> netoPorMetodo) {

    public static ResumenCaja de(CajaSesion sesion, List<MovimientoCaja> movimientos) {
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal egresos = BigDecimal.ZERO;
        Map<MetodoPago, BigDecimal> neto = new EnumMap<>(MetodoPago.class);
        for (MetodoPago m : MetodoPago.values()) {
            neto.put(m, BigDecimal.ZERO);
        }
        for (MovimientoCaja mov : movimientos) {
            if (mov.tipo() == TipoMovimientoCaja.INGRESO) {
                ingresos = ingresos.add(mov.monto());
            } else {
                egresos = egresos.add(mov.monto());
            }
            neto.merge(mov.metodoPago(), mov.montoFirmado(), BigDecimal::add);
        }
        return new ResumenCaja(sesion, ingresos, egresos, neto);
    }

    /** Lo que debería haber en el cajón: apertura + efectivo que entró - efectivo que salió. */
    public BigDecimal efectivoEsperado() {
        return sesion.montoApertura().add(netoPorMetodo.get(MetodoPago.EFECTIVO));
    }

    /** Dinero recibido por medios digitales (Yape/Plin, transferencia, tarjeta): no está en el cajón. */
    public BigDecimal netoDigital() {
        return netoPorMetodo.get(MetodoPago.YAPE_PLIN)
                .add(netoPorMetodo.get(MetodoPago.TRANSFERENCIA))
                .add(netoPorMetodo.get(MetodoPago.TARJETA));
    }
}
