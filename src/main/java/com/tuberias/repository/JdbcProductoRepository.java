package com.tuberias.repository;

import com.tuberias.model.Forma;
import com.tuberias.model.MovimientoInventario;
import com.tuberias.model.Producto;
import com.tuberias.model.ProductoDatos;
import com.tuberias.model.StockInicial;
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

/** Productos y kardex con JDBC puro (sin ORM). */
public class JdbcProductoRepository implements ProductoRepository {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";

    private static final String SELECT_BASE = """
            SELECT p.id, p.codigo, p.nombre,
                   p.categoria_id, c.nombre AS categoria_nombre,
                   p.marca_id, m.nombre AS marca_nombre,
                   p.modelo, p.unidad_id, u.codigo AS unidad_codigo,
                   p.forma, p.material, p.diametro_mm, p.ancho_mm, p.alto_mm,
                   p.espesor_mm, p.longitud_m, p.color, p.norma_clase, p.descripcion,
                   p.precio_sugerido, p.stock_actual, p.costo_promedio, p.stock_minimo, p.activo
              FROM producto p
              JOIN categoria c    ON c.id = p.categoria_id
              LEFT JOIN marca m   ON m.id = p.marca_id
              JOIN unidad_medida u ON u.id = p.unidad_id
            """;

    private static final String INSERT_PRODUCTO = """
            INSERT INTO producto (codigo, nombre, categoria_id, marca_id, modelo, unidad_id, forma,
                                  material, diametro_mm, ancho_mm, alto_mm, espesor_mm, longitud_m,
                                  color, norma_clase, descripcion, precio_sugerido, stock_minimo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String UPDATE_PRODUCTO = """
            UPDATE producto
               SET codigo = ?, nombre = ?, categoria_id = ?, marca_id = ?, modelo = ?, unidad_id = ?, forma = ?,
                   material = ?, diametro_mm = ?, ancho_mm = ?, alto_mm = ?, espesor_mm = ?, longitud_m = ?,
                   color = ?, norma_clase = ?, descripcion = ?, precio_sugerido = ?, stock_minimo = ?
             WHERE id = ?
            """;

    private final DataSource dataSource;

    public JdbcProductoRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Producto> listar(String filtro, boolean incluirInactivos) {
        StringBuilder sql = new StringBuilder(SELECT_BASE).append(" WHERE 1 = 1");
        String patron = null;
        if (!incluirInactivos) {
            sql.append(" AND p.activo");
        }
        if (filtro != null && !filtro.isBlank()) {
            patron = "%" + escaparLike(filtro.trim().toLowerCase(Locale.ROOT)) + "%";
            sql.append(" AND (lower(p.nombre) LIKE ? OR lower(p.codigo) LIKE ? OR lower(c.nombre) LIKE ?")
               .append(" OR lower(coalesce(m.nombre, '')) LIKE ? OR lower(coalesce(p.modelo, '')) LIKE ?")
               .append(" OR lower(coalesce(p.material, '')) LIKE ?)");
        }
        sql.append(" ORDER BY lower(p.nombre), p.id");

        List<Producto> lista = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            if (patron != null) {
                for (int i = 1; i <= 6; i++) {
                    ps.setString(i, patron);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo listar los productos", e);
        }
        return lista;
    }

    @Override
    public Optional<Producto> buscarPorId(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BASE + " WHERE p.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo buscar el producto", e);
        }
    }

    @Override
    public String siguienteCodigo() {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT nextval('seq_codigo_producto')");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return String.format("P%05d", rs.getLong(1));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo generar el código del producto", e);
        }
    }

    @Override
    public long crear(ProductoDatos datos, StockInicial stockInicial, long usuarioId) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = con.prepareStatement(INSERT_PRODUCTO)) {
                    asignarDatos(ps, datos);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = rs.getLong(1);
                    }
                }
                if (stockInicial != null) {
                    insertarMovimiento(con, id, "STOCK_INICIAL", stockInicial.cantidad(),
                            stockInicial.costoUnitario(), usuarioId, "Stock inicial");
                }
                con.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw traducir("crear el producto", e);
        }
    }

    @Override
    public void actualizar(long id, ProductoDatos datos) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_PRODUCTO)) {
            asignarDatos(ps, datos);
            ps.setLong(19, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw traducir("actualizar el producto", e);
        }
    }

    @Override
    public void cambiarActivo(long id, boolean activo) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE producto SET activo = ? WHERE id = ?")) {
            ps.setBoolean(1, activo);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw traducir("cambiar el estado del producto", e);
        }
    }

    @Override
    public void registrarMovimiento(long productoId, String tipo, BigDecimal cantidad,
                                    BigDecimal costoUnitario, long usuarioId, String observacion) {
        try (Connection con = dataSource.getConnection()) {
            insertarMovimiento(con, productoId, tipo, cantidad, costoUnitario, usuarioId, observacion);
        } catch (SQLException e) {
            if (CHECK_VIOLATION.equals(e.getSQLState())) {
                throw new NegocioException("Stock insuficiente: la operación dejaría el stock en negativo.");
            }
            throw new IllegalStateException("No se pudo registrar el movimiento de inventario", e);
        }
    }

    @Override
    public List<MovimientoInventario> listarMovimientos(long productoId, int limite) {
        String sql = """
                SELECT mi.id, mi.tipo, mi.cantidad, mi.costo_unitario, u.username, mi.fecha, mi.observacion
                  FROM movimiento_inventario mi
                  JOIN usuario u ON u.id = mi.usuario_id
                 WHERE mi.producto_id = ?
                 ORDER BY mi.fecha DESC, mi.id DESC
                 LIMIT ?
                """;
        List<MovimientoInventario> lista = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, productoId);
            ps.setInt(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new MovimientoInventario(
                            rs.getLong("id"),
                            rs.getString("tipo"),
                            rs.getBigDecimal("cantidad"),
                            rs.getBigDecimal("costo_unitario"),
                            rs.getString("username"),
                            rs.getObject("fecha", OffsetDateTime.class).toInstant(),
                            rs.getString("observacion")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar los movimientos", e);
        }
        return lista;
    }

    // ---------------------------------------------------------------- helpers

    /** Asigna los 18 campos editables, en el mismo orden para INSERT y UPDATE. */
    private static void asignarDatos(PreparedStatement ps, ProductoDatos d) throws SQLException {
        ps.setString(1, d.codigo());
        ps.setString(2, d.nombre());
        ps.setLong(3, d.categoriaId());
        ps.setObject(4, d.marcaId(), Types.BIGINT);
        ps.setString(5, d.modelo());
        ps.setShort(6, d.unidadId());
        ps.setString(7, d.forma().aBd());
        ps.setString(8, d.material());
        ps.setBigDecimal(9, d.diametroMm());
        ps.setBigDecimal(10, d.anchoMm());
        ps.setBigDecimal(11, d.altoMm());
        ps.setBigDecimal(12, d.espesorMm());
        ps.setBigDecimal(13, d.longitudM());
        ps.setString(14, d.color());
        ps.setString(15, d.normaClase());
        ps.setString(16, d.descripcion());
        ps.setBigDecimal(17, d.precioSugerido());
        ps.setBigDecimal(18, d.stockMinimo());
    }

    private static void insertarMovimiento(Connection con, long productoId, String tipo, BigDecimal cantidad,
                                           BigDecimal costoUnitario, long usuarioId, String observacion)
            throws SQLException {
        String sql = """
                INSERT INTO movimiento_inventario (producto_id, tipo, cantidad, costo_unitario, usuario_id, observacion)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, productoId);
            ps.setString(2, tipo);
            ps.setBigDecimal(3, cantidad);
            ps.setBigDecimal(4, costoUnitario);
            ps.setLong(5, usuarioId);
            ps.setString(6, observacion);
            ps.executeUpdate();
        }
    }

    private static Producto mapear(ResultSet rs) throws SQLException {
        long marca = rs.getLong("marca_id");
        Long marcaId = rs.wasNull() ? null : marca;
        return new Producto(
                rs.getLong("id"),
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getLong("categoria_id"),
                rs.getString("categoria_nombre"),
                marcaId,
                rs.getString("marca_nombre"),
                rs.getString("modelo"),
                rs.getShort("unidad_id"),
                rs.getString("unidad_codigo"),
                Forma.deBd(rs.getString("forma")),
                rs.getString("material"),
                rs.getBigDecimal("diametro_mm"),
                rs.getBigDecimal("ancho_mm"),
                rs.getBigDecimal("alto_mm"),
                rs.getBigDecimal("espesor_mm"),
                rs.getBigDecimal("longitud_m"),
                rs.getString("color"),
                rs.getString("norma_clase"),
                rs.getString("descripcion"),
                rs.getBigDecimal("precio_sugerido"),
                rs.getBigDecimal("stock_actual"),
                rs.getBigDecimal("costo_promedio"),
                rs.getBigDecimal("stock_minimo"),
                rs.getBoolean("activo"));
    }

    private static RuntimeException traducir(String accion, SQLException e) {
        if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
            return new NegocioException("Ya existe un producto con ese código.");
        }
        return new IllegalStateException("No se pudo " + accion, e);
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
