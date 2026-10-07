package com.tuberias.repository;

import com.tuberias.model.Proveedor;
import com.tuberias.service.NegocioException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Proveedores con JDBC puro. */
public class JdbcProveedorRepository implements ProveedorRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final DataSource dataSource;

    public JdbcProveedorRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Proveedor> listar(String filtro, boolean incluirInactivos) {
        String sql = """
                SELECT id, nombre, ruc, activo
                  FROM proveedor
                 WHERE (activo OR ?)
                   AND (lower(nombre) LIKE ? OR coalesce(ruc, '') LIKE ?)
                 ORDER BY lower(nombre), id
                """;
        String patron = "%" + escaparLike(filtro == null ? "" : filtro.trim().toLowerCase(Locale.ROOT)) + "%";
        List<Proveedor> lista = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, incluirInactivos);
            ps.setString(2, patron);
            ps.setString(3, patron);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Proveedor(rs.getLong("id"), rs.getString("nombre"),
                            rs.getString("ruc"), rs.getBoolean("activo")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo listar los proveedores", e);
        }
        return lista;
    }

    @Override
    public long crear(String nombre, String ruc) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO proveedor (nombre, ruc) VALUES (?, ?) RETURNING id")) {
            ps.setString(1, nombre);
            ps.setString(2, ruc);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw traducir("crear el proveedor", e);
        }
    }

    @Override
    public void actualizar(long id, String nombre, String ruc) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE proveedor SET nombre = ?, ruc = ? WHERE id = ?")) {
            ps.setString(1, nombre);
            ps.setString(2, ruc);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw traducir("actualizar el proveedor", e);
        }
    }

    @Override
    public void cambiarActivo(long id, boolean activo) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE proveedor SET activo = ? WHERE id = ?")) {
            ps.setBoolean(1, activo);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw traducir("cambiar el estado del proveedor", e);
        }
    }

    private static RuntimeException traducir(String accion, SQLException e) {
        if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
            return new NegocioException("Ya existe un proveedor con ese RUC.");
        }
        return new IllegalStateException("No se pudo " + accion, e);
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
