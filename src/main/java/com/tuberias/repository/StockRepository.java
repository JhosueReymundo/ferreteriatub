package com.tuberias.repository;

import com.tuberias.model.ProductoStockBajo;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Consultas de stock con JDBC puro (sin ORM). */
public class StockRepository implements StockAlertas {

    private final DataSource dataSource;

    public StockRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Productos en o bajo su stock mínimo, los más críticos primero. */
    public List<ProductoStockBajo> listarStockBajo() {
        String sql = """
                SELECT id, codigo, nombre, stock_actual, stock_minimo
                  FROM v_stock_bajo
                 ORDER BY stock_actual - stock_minimo, nombre
                """;
        List<ProductoStockBajo> resultado = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new ProductoStockBajo(
                        rs.getLong("id"),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getBigDecimal("stock_actual"),
                        rs.getBigDecimal("stock_minimo")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar el stock bajo", e);
        }
        return resultado;
    }

    /** Cantidad de productos en alerta (para el indicador del dashboard). */
    public int contarStockBajo() {
        String sql = "SELECT count(*) FROM v_stock_bajo";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo contar el stock bajo", e);
        }
    }

    /** Cambia el stock mínimo de un producto. Devuelve true si el producto existe. */
    public boolean actualizarStockMinimo(long productoId, BigDecimal nuevoMinimo) {
        if (nuevoMinimo == null || nuevoMinimo.signum() < 0) {
            throw new IllegalArgumentException("El stock mínimo no puede ser negativo");
        }
        String sql = "UPDATE producto SET stock_minimo = ? WHERE id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBigDecimal(1, nuevoMinimo);
            ps.setLong(2, productoId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo actualizar el stock mínimo", e);
        }
    }

    /**
     * Para usar justo después de registrar una venta: devuelve el producto si
     * quedó en alerta, o null si su stock sigue por encima del mínimo.
     */
    @Override
    public ProductoStockBajo buscarSiEstaEnAlerta(long productoId) {
        String sql = "SELECT id, codigo, nombre, stock_actual, stock_minimo FROM v_stock_bajo WHERE id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new ProductoStockBajo(
                        rs.getLong("id"),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getBigDecimal("stock_actual"),
                        rs.getBigDecimal("stock_minimo"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo verificar el stock del producto", e);
        }
    }
}
