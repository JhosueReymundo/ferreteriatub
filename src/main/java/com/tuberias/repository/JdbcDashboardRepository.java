package com.tuberias.repository;

import com.tuberias.model.MetodoPago;
import com.tuberias.model.ProductoVendido;
import com.tuberias.model.ResumenVentas;
import com.tuberias.model.VentaDia;
import com.tuberias.model.VentaPorMetodo;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Indicadores con JDBC puro (solo lectura). */
public class JdbcDashboardRepository implements DashboardRepository {

    private final DataSource dataSource;

    public JdbcDashboardRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ResumenVentas resumenVentas(Instant desde, Instant hasta) {
        String sqlTotales = """
                SELECT count(*), coalesce(sum(total), 0)
                  FROM venta
                 WHERE estado = 'REGISTRADA' AND fecha >= ? AND fecha < ?
                """;
        String sqlUtilidad = """
                SELECT coalesce(sum(d.subtotal - d.cantidad * d.costo_unitario), 0)
                  FROM detalle_venta d
                  JOIN venta v ON v.id = d.venta_id
                 WHERE v.estado = 'REGISTRADA' AND v.fecha >= ? AND v.fecha < ?
                """;
        try (Connection con = dataSource.getConnection()) {
            int nro;
            BigDecimal total;
            try (PreparedStatement ps = con.prepareStatement(sqlTotales)) {
                rango(ps, desde, hasta);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    nro = rs.getInt(1);
                    total = rs.getBigDecimal(2);
                }
            }
            BigDecimal utilidad;
            try (PreparedStatement ps = con.prepareStatement(sqlUtilidad)) {
                rango(ps, desde, hasta);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    utilidad = rs.getBigDecimal(1);
                }
            }
            return new ResumenVentas(nro, total, utilidad);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo calcular el resumen de ventas", e);
        }
    }

    @Override
    public List<VentaDia> ventasPorDia(Instant desde, Instant hasta, ZoneId zona) {
        String sql = """
                SELECT (fecha AT TIME ZONE ?)::date AS dia, sum(total) AS total
                  FROM venta
                 WHERE estado = 'REGISTRADA' AND fecha >= ? AND fecha < ?
                 GROUP BY 1
                 ORDER BY 1
                """;
        List<VentaDia> lista = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, zona.getId());
            ps.setTimestamp(2, Timestamp.from(desde));
            ps.setTimestamp(3, Timestamp.from(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new VentaDia(rs.getObject("dia", LocalDate.class), rs.getBigDecimal("total")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo calcular las ventas por día", e);
        }
        return lista;
    }

    @Override
    public List<ProductoVendido> masVendidos(Instant desde, Instant hasta, int limite) {
        String sql = """
                SELECT p.nombre, u.codigo AS unidad, sum(d.cantidad) AS cantidad, sum(d.subtotal) AS total
                  FROM detalle_venta d
                  JOIN venta v          ON v.id = d.venta_id
                  JOIN producto p       ON p.id = d.producto_id
                  JOIN unidad_medida u  ON u.id = p.unidad_id
                 WHERE v.estado = 'REGISTRADA' AND v.fecha >= ? AND v.fecha < ?
                 GROUP BY p.id, p.nombre, u.codigo
                 ORDER BY sum(d.subtotal) DESC, p.nombre
                 LIMIT ?
                """;
        List<ProductoVendido> lista = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            rango(ps, desde, hasta);
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new ProductoVendido(rs.getString("nombre"), rs.getString("unidad"),
                            rs.getBigDecimal("cantidad"), rs.getBigDecimal("total")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo calcular los productos más vendidos", e);
        }
        return lista;
    }

    @Override
    public List<VentaPorMetodo> ventasPorMetodo(Instant desde, Instant hasta) {
        String sql = """
                SELECT metodo_pago, sum(total) AS total
                  FROM venta
                 WHERE estado = 'REGISTRADA' AND fecha >= ? AND fecha < ?
                 GROUP BY metodo_pago
                 ORDER BY sum(total) DESC
                """;
        List<VentaPorMetodo> lista = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            rango(ps, desde, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new VentaPorMetodo(MetodoPago.valueOf(rs.getString("metodo_pago")),
                            rs.getBigDecimal("total")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo calcular las ventas por método de pago", e);
        }
        return lista;
    }

    @Override
    public BigDecimal totalCompras(LocalDate desde, LocalDate hasta) {
        String sql = """
                SELECT coalesce(sum(total), 0)
                  FROM compra
                 WHERE estado = 'REGISTRADA' AND fecha BETWEEN ? AND ?
                """;
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, desde);
            ps.setObject(2, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo calcular el total de compras", e);
        }
    }

    @Override
    public BigDecimal valorInventario() {
        String sql = "SELECT coalesce(sum(stock_actual * costo_promedio), 0) FROM producto WHERE activo";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo calcular el valor del inventario", e);
        }
    }

    private static void rango(PreparedStatement ps, Instant desde, Instant hasta) throws SQLException {
        ps.setTimestamp(1, Timestamp.from(desde));
        ps.setTimestamp(2, Timestamp.from(hasta));
    }
}
