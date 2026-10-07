package com.tuberias.repository;

import com.tuberias.model.CajaSesion;
import com.tuberias.model.MetodoPago;
import com.tuberias.model.MovimientoCaja;
import com.tuberias.model.OrigenCaja;
import com.tuberias.model.TipoMovimientoCaja;
import com.tuberias.service.NegocioException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Caja con JDBC puro. Las operaciones que mueven dinero van en una transacción con la fila de caja bloqueada. */
public class JdbcCajaRepository implements CajaRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private static final String SELECT_SESION = """
            SELECT s.id, u.username, s.fecha_apertura, s.monto_apertura, s.estado,
                   s.fecha_cierre, s.monto_esperado, s.monto_cierre_real, s.observacion
              FROM caja_sesion s
              JOIN usuario u ON u.id = s.usuario_id
            """;

    private final DataSource dataSource;

    public JdbcCajaRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<CajaSesion> buscarAbierta() {
        return consultarUna(SELECT_SESION + " WHERE s.estado = 'ABIERTA'", null);
    }

    @Override
    public Optional<CajaSesion> buscarSesion(long id) {
        return consultarUna(SELECT_SESION + " WHERE s.id = ?", id);
    }

    private Optional<CajaSesion> consultarUna(String sql, Long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (id != null) {
                ps.setLong(1, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapearSesion(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar la caja", e);
        }
    }

    @Override
    public List<CajaSesion> listarSesiones(int limite) {
        String sql = SELECT_SESION + " ORDER BY s.fecha_apertura DESC, s.id DESC LIMIT ?";
        List<CajaSesion> lista = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearSesion(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo listar las cajas", e);
        }
        return lista;
    }

    @Override
    public List<MovimientoCaja> listarMovimientos(long sesionId) {
        String sql = """
                SELECT m.id, m.fecha, m.tipo, m.origen, m.concepto, m.monto, m.metodo_pago,
                       m.venta_id, m.compra_id, u.username
                  FROM movimiento_caja m
                  JOIN usuario u ON u.id = m.usuario_id
                 WHERE m.caja_sesion_id = ?
                 ORDER BY m.fecha, m.id
                """;
        List<MovimientoCaja> lista = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, sesionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new MovimientoCaja(
                            rs.getLong("id"),
                            rs.getObject("fecha", OffsetDateTime.class).toInstant(),
                            TipoMovimientoCaja.valueOf(rs.getString("tipo")),
                            OrigenCaja.valueOf(rs.getString("origen")),
                            rs.getString("concepto"),
                            rs.getBigDecimal("monto"),
                            MetodoPago.valueOf(rs.getString("metodo_pago")),
                            (Long) rs.getObject("venta_id"),
                            (Long) rs.getObject("compra_id"),
                            rs.getString("username")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo listar los movimientos de caja", e);
        }
        return lista;
    }

    @Override
    public long abrir(long usuarioId, BigDecimal montoApertura, String observacion) {
        String sql = """
                INSERT INTO caja_sesion (usuario_id, monto_apertura, observacion)
                VALUES (?, ?, ?)
                RETURNING id
                """;
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ps.setBigDecimal(2, montoApertura);
            ps.setString(3, observacion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new NegocioException("Ya hay una caja abierta.");
            }
            throw new IllegalStateException("No se pudo abrir la caja", e);
        }
    }

    @Override
    public CajaSesion cerrar(long usuarioId, BigDecimal montoContado, String observacion) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                Long cajaId = CajaSql.cajaAbiertaBloqueada(con);   // nadie registra nada mientras se cierra
                if (cajaId == null) {
                    throw new NegocioException("No hay una caja abierta.");
                }
                BigDecimal esperado = CajaSql.efectivoEnCaja(con, cajaId);
                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE caja_sesion
                           SET estado = 'CERRADA', fecha_cierre = now(), cerrada_por = ?,
                               monto_esperado = ?, monto_cierre_real = ?,
                               observacion = coalesce(?, observacion)
                         WHERE id = ?
                        """)) {
                    ps.setLong(1, usuarioId);
                    ps.setBigDecimal(2, esperado);
                    ps.setBigDecimal(3, montoContado);
                    ps.setString(4, observacion);
                    ps.setLong(5, cajaId);
                    ps.executeUpdate();
                }
                con.commit();
                return buscarSesion(cajaId).orElseThrow();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo cerrar la caja", e);
        }
    }

    @Override
    public void registrarMovimiento(long usuarioId, TipoMovimientoCaja tipo, String concepto,
                                    BigDecimal monto, MetodoPago metodoPago) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                CajaSql.registrar(con, "No hay una caja abierta. Ábrela para registrar movimientos.",
                        tipo.name(), OrigenCaja.MANUAL.name(), concepto, monto, metodoPago.name(),
                        null, null, usuarioId);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo registrar el movimiento de caja", e);
        }
    }

    private static CajaSesion mapearSesion(ResultSet rs) throws SQLException {
        OffsetDateTime cierre = rs.getObject("fecha_cierre", OffsetDateTime.class);
        return new CajaSesion(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getObject("fecha_apertura", OffsetDateTime.class).toInstant(),
                rs.getBigDecimal("monto_apertura"),
                "ABIERTA".equals(rs.getString("estado")),
                cierre == null ? null : cierre.toInstant(),
                rs.getBigDecimal("monto_esperado"),
                rs.getBigDecimal("monto_cierre_real"),
                rs.getString("observacion"));
    }
}
