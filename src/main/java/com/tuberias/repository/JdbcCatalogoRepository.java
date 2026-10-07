package com.tuberias.repository;

import com.tuberias.model.Categoria;
import com.tuberias.model.Marca;
import com.tuberias.model.UnidadMedida;
import com.tuberias.service.NegocioException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Categorías, marcas y unidades de medida con JDBC puro. */
public class JdbcCatalogoRepository implements CatalogoRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final DataSource dataSource;

    public JdbcCatalogoRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Categoria> listarCategorias() {
        List<Categoria> lista = new ArrayList<>();
        consultar("SELECT id, nombre FROM categoria WHERE activo ORDER BY lower(nombre)", "listar las categorías",
                rs -> lista.add(new Categoria(rs.getLong("id"), rs.getString("nombre"))));
        return lista;
    }

    @Override
    public long crearCategoria(String nombre) {
        return insertarNombre("INSERT INTO categoria (nombre) VALUES (?) RETURNING id", nombre,
                "Ya existe una categoría con ese nombre.", "crear la categoría");
    }

    @Override
    public List<Marca> listarMarcas() {
        List<Marca> lista = new ArrayList<>();
        consultar("SELECT id, nombre FROM marca WHERE activo ORDER BY lower(nombre)", "listar las marcas",
                rs -> lista.add(new Marca(rs.getLong("id"), rs.getString("nombre"))));
        return lista;
    }

    @Override
    public long crearMarca(String nombre) {
        return insertarNombre("INSERT INTO marca (nombre) VALUES (?) RETURNING id", nombre,
                "Ya existe una marca con ese nombre.", "crear la marca");
    }

    @Override
    public List<UnidadMedida> listarUnidades() {
        List<UnidadMedida> lista = new ArrayList<>();
        consultar("SELECT id, codigo, nombre FROM unidad_medida ORDER BY id", "listar las unidades",
                rs -> lista.add(new UnidadMedida(rs.getShort("id"), rs.getString("codigo"), rs.getString("nombre"))));
        return lista;
    }

    // ---------------------------------------------------------------- helpers

    @FunctionalInterface
    private interface Fila {
        void leer(ResultSet rs) throws SQLException;
    }

    private void consultar(String sql, String accion, Fila fila) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                fila.leer(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo " + accion, e);
        }
    }

    private long insertarNombre(String sql, String nombre, String mensajeDuplicado, String accion) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new NegocioException(mensajeDuplicado);
            }
            throw new IllegalStateException("No se pudo " + accion, e);
        }
    }
}
