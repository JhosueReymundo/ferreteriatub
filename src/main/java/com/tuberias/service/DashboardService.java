package com.tuberias.service;

import com.tuberias.model.Dashboard;
import com.tuberias.model.Periodo;
import com.tuberias.model.ResumenVentas;
import com.tuberias.model.Usuario;
import com.tuberias.model.VentaDia;
import com.tuberias.repository.DashboardRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Arma los indicadores del inicio. Costos, utilidad e inventario valorizado: solo administrador. */
public class DashboardService {

    private static final int TOP_PRODUCTOS = 8;

    private final DashboardRepository repo;
    private final Clock clock;

    public DashboardService(DashboardRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public Dashboard cargar(Usuario usuario, Periodo periodo) {
        if (usuario == null) {
            throw new NegocioException("Inicia sesión para ver los indicadores.");
        }
        boolean admin = usuario.esAdmin();
        ZoneId zona = clock.getZone();
        LocalDate hoy = LocalDate.now(clock);
        LocalDate desde = periodo.desde(hoy);

        // El día lo define la zona del negocio: [00:00 del primer día, 00:00 de mañana)
        Instant inicio = desde.atStartOfDay(zona).toInstant();
        Instant fin = hoy.plusDays(1).atStartOfDay(zona).toInstant();

        ResumenVentas resumen = repo.resumenVentas(inicio, fin);
        BigDecimal ticket = resumen.nroVentas() == 0 ? BigDecimal.ZERO
                : resumen.total().divide(BigDecimal.valueOf(resumen.nroVentas()), 2, RoundingMode.HALF_UP);

        return new Dashboard(
                periodo, desde, hoy,
                resumen.nroVentas(),
                resumen.total(),
                ticket,
                admin ? resumen.utilidad() : null,
                admin ? repo.totalCompras(desde, hoy) : null,
                admin ? repo.valorInventario() : null,
                completarDias(repo.ventasPorDia(inicio, fin, zona), desde, hoy),
                repo.masVendidos(inicio, fin, TOP_PRODUCTOS),
                repo.ventasPorMetodo(inicio, fin));
    }

    /** Un punto por cada día del rango; los días sin ventas valen 0. */
    static List<VentaDia> completarDias(List<VentaDia> conVentas, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, BigDecimal> porDia = new HashMap<>();
        for (VentaDia v : conVentas) {
            porDia.merge(v.dia(), v.total(), BigDecimal::add);
        }
        List<VentaDia> todos = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            todos.add(new VentaDia(d, porDia.getOrDefault(d, BigDecimal.ZERO)));
        }
        return todos;
    }
}
