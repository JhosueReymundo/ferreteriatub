package com.tuberias.repository;

import com.tuberias.model.CompraDetalle;
import com.tuberias.model.CompraResumen;
import com.tuberias.model.LineaCompra;
import com.tuberias.model.LineaDetalleCompra;
import com.tuberias.model.NuevaCompra;
import com.tuberias.service.NegocioException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Compras con JDBC puro: cada operación que toca varias tablas va en una sola transacción. */
public class JdbcCompraRepository implements CompraRepository {

    private static final String CHECK_VIOLATION = "23514";
    private static final String FK_VIOLATION = "23503";

    private static final String SELECT_RESUMEN = """
            SELECT c.id, c.fecha, p.nombre AS proveedor, c.nro_comprobante, c.total,
                   c.estado, u.username
              FROM compra c
              LEFT JOIN proveedor p ON p.id = c.proveedor_id
              JOIN usuario u        ON u.id = c.usuario_id
            """;

    private final DataSource dataSource;

    public JdbcCompraRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public long registrar(NuevaCompra compra, long usuarioId) {
        String insertCompra = """
                INSERT INTO compra (proveedor_id, usuario_id, fecha, nro_comprobante, total, observacion)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
        String insertDetalle = """
                INSERT INTO detalle_compra (compra_id, producto_id, cantidad, costo_unitario, subtotal)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                long compraId;
                try (PreparedStatement ps = con.prepareStatement(insertCompra)) {
                    ps.setObject(1, compra.proveedorId(), Types.BIGINT);
                    ps.setLong(2, usuarioId);
                    ps.setObject(3, compra.fecha());
                    ps.setString(4, compra.comprobante());
                    ps.setBigDecimal(5, compra.total());
                    ps.setString(6, compra.observacion());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        compraId = rs.getLong(1);
                    }
                }
                for (LineaCompra linea : compra.lineas()) {
                    try (PreparedStatement ps = con.prepareStatement(insertDetalle)) {
                        ps.setLong(1, compraId);
                        ps.setLong(2, linea.productoId());
                        ps.setBigDecimal(3, linea.cantidad());
                        ps.setBigDecimal(4, linea.costoUnitario());
                        ps.setBigDecimal(5, linea.subtotal());
                        ps.executeUpdate();
                    }
                    // El trigger del kardex sube el stock y recalcula el costo promedio
                    insertarMovimiento(con, linea.productoId(), "COMPRA", linea.cantidad(),
                            linea.costoUnitario(), compraId, usuarioId, "Compra #" + compraId);
                }
                // Si el usuario indicó que se pagó desde caja, el egreso se guarda en la misma transacción
                if (compra.pagoCaja() != null && compra.total().signum() > 0) {
                    CajaSql.registrar(con, "No hay una caja abierta. Ábrela o elige \"No registrar en caja\".",
                            "EGRESO", "COMPRA", "Compra #" + compraId, compra.total(),
                            compra.pagoCaja().name(), null, compraId, usuarioId);
                }
                con.commit();
                return compraId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (FK_VIOLATION.equals(e.getSQLState())) {
                throw new NegocioException("El proveedor o alguno de los productos ya no existe.");
            }
            throw new IllegalStateException("No se pudo registrar la compra", e);
        }
    }

    @Override
    public List<CompraResumen> listar(String filtro, boolean incluirAnuladas, int limite) {
        String sql = SELECT_RESUMEN + """
                 WHERE (c.estado = 'REGISTRADA' OR ?)
                   AND (lower(coalesce(p.nombre, '')) LIKE ?
                        OR coalesce(p.ruc, '') LIKE ?
                        OR lower(coalesce(c.nro_comprobante, '')) LIKE ?)
                 ORDER BY c.fecha DESC, c.id DESC
                 LIMIT ?
                """;
        String patron = "%" + escaparLike(filtro == null ? "" : filtro.trim().toLowerCase(Locale.ROOT)) + "%";
        List<CompraResumen> lista = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, incluirAnuladas);
            ps.setString(2, patron);
            ps.setString(3, patron);
            ps.setString(4, patron);
            ps.setInt(5, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearResumen(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo listar las compras", e);
        }
        return lista;
    }

    @Override
    public Optional<CompraDetalle> buscarDetalle(long id) {
        String sqlCabecera = SELECT_RESUMEN.replace("c.estado, u.username",
                "c.estado, u.username, c.observacion, c.motivo_anulacion") + " WHERE c.id = ?";
        String sqlLineas = """
                SELECT d.producto_id, pr.codigo, pr.nombre, un.codigo AS unidad,
                       d.cantidad, d.costo_unitario, d.subtotal
                  FROM detalle_compra d
                  JOIN producto pr      ON pr.id = d.producto_id
                  JOIN unidad_medida un ON un.id = pr.unidad_id
                 WHERE d.compra_id = ?
                 ORDER BY d.id
                """;
        try (Connection con = dataSource.getConnection()) {
            CompraResumen resumen;
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
            List<LineaDetalleCompra> lineas = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(sqlLineas)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lineas.add(new LineaDetalleCompra(
                                rs.getLong("producto_id"),
                                rs.getString("codigo"),
                                rs.getString("nombre"),
                                rs.getString("unidad"),
                                rs.getBigDecimal("cantidad"),
                                rs.getBigDecimal("costo_unitario"),
                                rs.getBigDecimal("subtotal")));
                    }
                }
            }
            return Optional.of(new CompraDetalle(resumen, observacion, motivo, lineas));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar la compra", e);
        }
    }

    @Override
    public void anular(long id, long usuarioId, String motivo) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                // Bloquea la compra para que no se anule dos veces a la vez
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT estado FROM compra WHERE id = ? FOR UPDATE")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new NegocioException("La compra no existe.");
                        }
                        if ("ANULADA".equals(rs.getString(1))) {
                            throw new NegocioException("La compra ya está anulada.");
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE compra
                           SET estado = 'ANULADA', anulada_en = now(), anulada_por = ?, motivo_anulacion = ?
                         WHERE id = ?
                        """)) {
                    ps.setLong(1, usuarioId);
                    ps.setString(2, motivo);
                    ps.setLong(3, id);
                    ps.executeUpdate();
                }
                // Devuelve del inventario lo que entró con esta compra
                List<Object[]> lineas = new ArrayList<>();
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT producto_id, cantidad, costo_unitario FROM detalle_compra WHERE compra_id = ? ORDER BY id")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            lineas.add(new Object[]{rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3)});
                        }
                    }
                }
                for (Object[] l : lineas) {
                    insertarMovimiento(con, (Long) l[0], "ANULACION_COMPRA", ((BigDecimal) l[1]).negate(),
                            (BigDecimal) l[2], id, usuarioId, "Anulación de la compra #" + id);
                }
                // Si la compra había salido de caja, el dinero vuelve a la caja abierta
                CajaSql.revertirDeCompra(con, id, usuarioId);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (CHECK_VIOLATION.equals(e.getSQLState())) {
                throw new NegocioException("No se puede anular: parte de esta mercadería ya salió del "
                        + "inventario (por ventas o ajustes) y el stock quedaría negativo.");
            }
            throw new IllegalStateException("No se pudo anular la compra", e);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void insertarMovimiento(Connection con, long productoId, String tipo, BigDecimal cantidad,
                                           BigDecimal costoUnitario, long compraId, long usuarioId,
                                           String observacion) throws SQLException {
        String sql = """
                INSERT INTO movimiento_inventario
                       (producto_id, tipo, cantidad, costo_unitario, compra_id, usuario_id, observacion)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, productoId);
            ps.setString(2, tipo);
            ps.setBigDecimal(3, cantidad);
            ps.setBigDecimal(4, costoUnitario);
            ps.setLong(5, compraId);
            ps.setLong(6, usuarioId);
            ps.setString(7, observacion);
            ps.executeUpdate();
        }
    }

    private static CompraResumen mapearResumen(ResultSet rs) throws SQLException {
        return new CompraResumen(
                rs.getLong("id"),
                rs.getObject("fecha", LocalDate.class),
                rs.getString("proveedor"),
                rs.getString("nro_comprobante"),
                rs.getBigDecimal("total"),
                "ANULADA".equals(rs.getString("estado")),
                rs.getString("username"));
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
