package com.tuberias.repository;

import com.tuberias.model.Cliente;
import com.tuberias.service.NegocioException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Clientes con JDBC puro. */
public class JdbcClienteRepository implements ClienteRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final DataSource dataSource;

    public JdbcClienteRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Cliente> listar(String filtro, boolean incluirInactivos) {
        String sql = """
                SELECT id, nombre, documento, telefono, activo
                  FROM cliente
                 WHERE (activo OR ?)
                   AND (lower(nombre) LIKE ? OR coalesce(documento, '') LIKE ? OR coalesce(telefono, '') LIKE ?)
                 ORDER BY lower(nombre), id
                """;
        String patron = "%" + escaparLike(filtro == null ? "" : filtro.trim().toLowerCase(Locale.ROOT)) + "%";
        List<Cliente> lista = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, incluirInactivos);
            ps.setString(2, patron);
            ps.setString(3, patron);
            ps.setString(4, patron);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Cliente(rs.getLong("id"), rs.getString("nombre"), rs.getString("documento"),
                            rs.getString("telefono"), rs.getBoolean("activo")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo listar los clientes", e);
        }
        return lista;
    }

    @Override
    public long crear(String nombre, String documento, String telefono) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO cliente (nombre, documento, telefono) VALUES (?, ?, ?) RETURNING id")) {
            ps.setString(1, nombre);
            ps.setString(2, documento);
            ps.setString(3, telefono);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw traducir("crear el cliente", e);
        }
    }

    @Override
    public void actualizar(long id, String nombre, String documento, String telefono) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE cliente SET nombre = ?, documento = ?, telefono = ? WHERE id = ?")) {
            ps.setString(1, nombre);
            ps.setString(2, documento);
            ps.setString(3, telefono);
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw traducir("actualizar el cliente", e);
        }
    }

    @Override
    public void cambiarActivo(long id, boolean activo) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE cliente SET activo = ? WHERE id = ?")) {
            ps.setBoolean(1, activo);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw traducir("cambiar el estado del cliente", e);
        }
    }

    private static RuntimeException traducir(String accion, SQLException e) {
        if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
            return new NegocioException("Ya existe un cliente con ese documento.");
        }
        return new IllegalStateException("No se pudo " + accion, e);
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
