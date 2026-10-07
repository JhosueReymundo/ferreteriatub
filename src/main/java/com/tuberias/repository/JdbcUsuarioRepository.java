package com.tuberias.repository;

import com.tuberias.model.Usuario;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** Acceso a la tabla usuario con JDBC puro (sin ORM). */
public class JdbcUsuarioRepository implements UsuarioRepository {

    private static final String SELECT_BASE = """
            SELECT u.id, u.username, u.nombre_completo, u.rol_id, r.nombre AS rol_nombre,
                   u.password_hash, u.codigo_recuperacion_hash, u.debe_cambiar_password,
                   u.intentos_fallidos, u.bloqueado_hasta, u.activo
              FROM usuario u
              JOIN rol r ON r.id = u.rol_id
            """;

    private final DataSource dataSource;

    public JdbcUsuarioRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean hayUsuarios() {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT EXISTS (SELECT 1 FROM usuario)");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getBoolean(1);
        } catch (SQLException e) {
            throw error("consultar si existen usuarios", e);
        }
    }

    @Override
    public Optional<Usuario> buscarPorUsername(String username) {
        return buscarUno(SELECT_BASE + " WHERE lower(u.username) = lower(?)", ps -> ps.setString(1, username));
    }

    @Override
    public Optional<Usuario> buscarPorId(long id) {
        return buscarUno(SELECT_BASE + " WHERE u.id = ?", ps -> ps.setLong(1, id));
    }

    @Override
    public long crear(String username, String nombreCompleto, short rolId,
                      String passwordHash, String codigoRecuperacionHash) {
        String sql = """
                INSERT INTO usuario (username, nombre_completo, rol_id, password_hash, codigo_recuperacion_hash)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """;
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, nombreCompleto);
            ps.setShort(3, rolId);
            ps.setString(4, passwordHash);
            ps.setString(5, codigoRecuperacionHash);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw error("crear el usuario", e);
        }
    }

    @Override
    public void actualizarPassword(long id, String passwordHash) {
        String sql = """
                UPDATE usuario
                   SET password_hash = ?, debe_cambiar_password = FALSE,
                       intentos_fallidos = 0, bloqueado_hasta = NULL
                 WHERE id = ?
                """;
        ejecutar(sql, "actualizar la contraseña", ps -> {
            ps.setString(1, passwordHash);
            ps.setLong(2, id);
        });
    }

    @Override
    public void actualizarPasswordYCodigo(long id, String passwordHash, String codigoRecuperacionHash) {
        String sql = """
                UPDATE usuario
                   SET password_hash = ?, codigo_recuperacion_hash = ?, debe_cambiar_password = FALSE,
                       intentos_fallidos = 0, bloqueado_hasta = NULL
                 WHERE id = ?
                """;
        ejecutar(sql, "restablecer la contraseña", ps -> {
            ps.setString(1, passwordHash);
            ps.setString(2, codigoRecuperacionHash);
            ps.setLong(3, id);
        });
    }

    @Override
    public void registrarFallo(long id, int intentosFallidos, Instant bloqueadoHasta) {
        ejecutar("UPDATE usuario SET intentos_fallidos = ?, bloqueado_hasta = ? WHERE id = ?",
                "registrar el intento fallido", ps -> {
                    ps.setInt(1, intentosFallidos);
                    if (bloqueadoHasta == null) {
                        ps.setNull(2, Types.TIMESTAMP_WITH_TIMEZONE);
                    } else {
                        ps.setObject(2, OffsetDateTime.ofInstant(bloqueadoHasta, ZoneOffset.UTC));
                    }
                    ps.setLong(3, id);
                });
    }

    @Override
    public void registrarAcceso(long id) {
        ejecutar("UPDATE usuario SET ultimo_acceso = now(), intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = ?",
                "registrar el acceso", ps -> ps.setLong(1, id));
    }

    // ---------------------------------------------------------------- helpers

    @FunctionalInterface
    private interface Parametros {
        void asignar(PreparedStatement ps) throws SQLException;
    }

    private Optional<Usuario> buscarUno(String sql, Parametros parametros) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            parametros.asignar(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw error("buscar el usuario", e);
        }
    }

    private void ejecutar(String sql, String accion, Parametros parametros) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            parametros.asignar(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw error(accion, e);
        }
    }

    private static Usuario mapear(ResultSet rs) throws SQLException {
        OffsetDateTime bloqueado = rs.getObject("bloqueado_hasta", OffsetDateTime.class);
        return new Usuario(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("nombre_completo"),
                rs.getShort("rol_id"),
                rs.getString("rol_nombre"),
                rs.getString("password_hash"),
                rs.getString("codigo_recuperacion_hash"),
                rs.getBoolean("debe_cambiar_password"),
                rs.getInt("intentos_fallidos"),
                bloqueado == null ? null : bloqueado.toInstant(),
                rs.getBoolean("activo"));
    }

    private static IllegalStateException error(String accion, SQLException e) {
        return new IllegalStateException("No se pudo " + accion, e);
    }
}
