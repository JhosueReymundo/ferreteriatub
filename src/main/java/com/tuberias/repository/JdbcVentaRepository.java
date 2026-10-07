package com.tuberias.repository;

import com.tuberias.model.LineaDetalleVenta;
import com.tuberias.model.LineaVenta;
import com.tuberias.model.MetodoPago;
import com.tuberias.model.NuevaVenta;
import com.tuberias.model.VentaDetalle;
import com.tuberias.model.VentaResumen;
import com.tuberias.service.NegocioException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Ventas con JDBC puro: cada operación que toca varias tablas va en una sola transacción. */
public class JdbcVentaRepository implements VentaRepository {

    private static final String CHECK_VIOLATION = "23514";
    private static final String FK_VIOLATION = "23503";

    private static final String SELECT_RESUMEN = """
            SELECT v.id, v.fecha, c.nombre AS cliente, v.metodo_pago, v.total,
                   v.estado, u.username
              FROM venta v
              LEFT JOIN cliente c ON c.id = v.cliente_id
              JOIN usuario u      ON u.id = v.usuario_id
            """;

    private final DataSource dataSource;

    public JdbcVentaRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public long registrar(NuevaVenta venta, long usuarioId) {
        String insertVenta = """
                INSERT INTO venta (cliente_id, usuario_id, metodo_pago, total, observacion)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """;
        String insertDetalle = """
                INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, costo_unitario, subtotal)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                long ventaId;
                try (PreparedStatement ps = con.prepareStatement(insertVenta)) {
                    ps.setObject(1, venta.clienteId(), Types.BIGINT);
                    ps.setLong(2, usuarioId);
                    ps.setString(3, venta.metodoPago().name());
                    ps.setBigDecimal(4, venta.total());
                    ps.setString(5, venta.observacion());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        ventaId = rs.getLong(1);
                    }
                }
                for (LineaVenta linea : venta.lineas()) {
                    // Costo promedio vigente AHORA; se bloquea la fila para que nadie lo cambie a mitad de la venta
                    BigDecimal costo = costoPromedioBloqueado(con, linea.productoId());
                    try (PreparedStatement ps = con.prepareStatement(insertDetalle)) {
                        ps.setLong(1, ventaId);
                        ps.setLong(2, linea.productoId());
                        ps.setBigDecimal(3, linea.cantidad());
                        ps.setBigDecimal(4, linea.precioUnitario());
                        ps.setBigDecimal(5, costo);
                        ps.setBigDecimal(6, linea.subtotal());
                        ps.executeUpdate();
                    }
                    // El trigger del kardex baja el stock (y rechaza si quedaría negativo)
                    insertarMovimiento(con, linea.productoId(), "VENTA", linea.cantidad().negate(),
                            costo, ventaId, usuarioId, "Venta #" + ventaId);
                }
                // El dinero entra a la caja abierta en la MISMA transacción (sin caja abierta no se vende)
                if (venta.total().signum() > 0) {
                    CajaSql.registrar(con, "No hay una caja abierta. Abre la caja (menú Caja) antes de registrar ventas.",
                            "INGRESO", "VENTA", "Venta #" + ventaId, venta.total(),
                            venta.metodoPago().name(), ventaId, null, usuarioId);
                }
                con.commit();
                return ventaId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (CHECK_VIOLATION.equals(e.getSQLState())) {
                throw new NegocioException("Stock insuficiente: alguno de los productos ya no tiene las "
                        + "unidades pedidas. Vuelve a abrir la venta para ver el stock actual.");
            }
            if (FK_VIOLATION.equals(e.getSQLState())) {
                throw new NegocioException("El cliente o alguno de los productos ya no existe.");
            }
            throw new IllegalStateException("No se pudo registrar la venta", e);
        }
    }

    @Override
    public List<VentaResumen> listar(String filtro, boolean incluirAnuladas, int limite) {
        String sql = SELECT_RESUMEN + """
                 WHERE (v.estado = 'REGISTRADA' OR ?)
                   AND (lower(coalesce(c.nombre, '')) LIKE ? OR coalesce(c.documento, '') LIKE ?)
                 ORDER BY v.fecha DESC, v.id DESC
                 LIMIT ?
                """;
        String patron = "%" + escaparLike(filtro == null ? "" : filtro.trim().toLowerCase(Locale.ROOT)) + "%";
        List<VentaResumen> lista = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, incluirAnuladas);
            ps.setString(2, patron);
            ps.setString(3, patron);
            ps.setInt(4, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearResumen(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo listar las ventas", e);
        }
        return lista;
    }

    @Override
    public Optional<VentaDetalle> buscarDetalle(long id) {
        String sqlCabecera = SELECT_RESUMEN.replace("v.estado, u.username",
                "v.estado, u.username, v.observacion, v.motivo_anulacion") + " WHERE v.id = ?";
        String sqlLineas = """
                SELECT d.producto_id, pr.codigo, pr.nombre, un.codigo AS unidad,
                       d.cantidad, d.precio_unitario, d.costo_unitario, d.subtotal
                  FROM detalle_venta d
                  JOIN producto pr      ON pr.id = d.producto_id
                  JOIN unidad_medida un ON un.id = pr.unidad_id
                 WHERE d.venta_id = ?
                 ORDER BY d.id
                """;
        try (Connection con = dataSource.getConnection()) {
            VentaResumen resumen;
            String observacion;
            String motivo;
            try (PreparedStatement ps = con.prepareStatement(sqlCabecera)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    resumen = mapearResumen(rs);
                    observacion = rs.getString("observacion");
                    motivo = rs.getString("motivo_anulacion");
                }
            }
            List<LineaDetalleVenta> lineas = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(sqlLineas)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lineas.add(new LineaDetalleVenta(
                                rs.getLong("producto_id"),
                                rs.getString("codigo"),
                                rs.getString("nombre"),
                                rs.getString("unidad"),
                                rs.getBigDecimal("cantidad"),
                                rs.getBigDecimal("precio_unitario"),
                                rs.getBigDecimal("costo_unitario"),
                                rs.getBigDecimal("subtotal")));
                    }
                }
            }
            return Optional.of(new VentaDetalle(resumen, observacion, motivo, lineas));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar la venta", e);
        }
    }

    @Override
    public void anular(long id, long usuarioId, String motivo) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                // Bloquea la venta para que no se anule dos veces a la vez
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT estado FROM venta WHERE id = ? FOR UPDATE")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new NegocioException("La venta no existe.");
                        }
                        if ("ANULADA".equals(rs.getString(1))) {
                            throw new NegocioException("La venta ya está anulada.");
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE venta
                           SET estado = 'ANULADA', anulada_en = now(), anulada_por = ?, motivo_anulacion = ?
                         WHERE id = ?
                        """)) {
                    ps.setLong(1, usuarioId);
                    ps.setString(2, motivo);
                    ps.setLong(3, id);
                    ps.executeUpdate();
                }
                // Las unidades vuelven al inventario con el costo con el que salieron
                List<Object[]> lineas = new ArrayList<>();
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT producto_id, cantidad, costo_unitario FROM detalle_venta WHERE venta_id = ? ORDER BY id")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            lineas.add(new Object[]{rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3)});
                        }
                    }
                }
                for (Object[] l : lineas) {
                    insertarMovimiento(con, (Long) l[0], "ANULACION_VENTA", (BigDecimal) l[1],
                            (BigDecimal) l[2], id, usuarioId, "Anulación de la venta #" + id);
                }
                // Si la venta había entrado a caja, el dinero se devuelve desde la caja abierta
                CajaSql.revertirDeVenta(con, id, usuarioId);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo anular la venta", e);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static BigDecimal costoPromedioBloqueado(Connection con, long productoId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT costo_promedio FROM producto WHERE id = ? FOR UPDATE")) {
            ps.setLong(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NegocioException("Un producto de la venta ya no existe.");
                }
                return rs.getBigDecimal(1);
            }
        }
    }

    private static void insertarMovimiento(Connection con, long productoId, String tipo, BigDecimal cantidad,
                                           BigDecimal costoUnitario, long ventaId, long usuarioId,
                                           String observacion) throws SQLException {
        String sql = """
                INSERT INTO movimiento_inventario
                       (producto_id, tipo, cantidad, costo_unitario, venta_id, usuario_id, observacion)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, productoId);
            ps.setString(2, tipo);
            ps.setBigDecimal(3, cantidad);
            ps.setBigDecimal(4, costoUnitario);
            ps.setLong(5, ventaId);
            ps.setLong(6, usuarioId);
            ps.setString(7, observacion);
            ps.executeUpdate();
        }
    }

    private static VentaResumen mapearResumen(ResultSet rs) throws SQLException {
        return new VentaResumen(
                rs.getLong("id"),
                rs.getObject("fecha", OffsetDateTime.class).toInstant(),
                rs.getString("cliente"),
                MetodoPago.valueOf(rs.getString("metodo_pago")),
                rs.getBigDecimal("total"),
                "ANULADA".equals(rs.getString("estado")),
                rs.getString("username"));
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
