package com.tuberias.repository;

import com.tuberias.model.MovimientoInventario;
import com.tuberias.model.Producto;
import com.tuberias.model.ProductoDatos;
import com.tuberias.model.StockInicial;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductoRepository {

    /** Busca por nombre, código, categoría, marca, modelo o material (sin distinguir mayúsculas). */
    List<Producto> listar(String filtro, boolean incluirInactivos);

    Optional<Producto> buscarPorId(long id);

    /** Siguiente código automático (P00001, P00002...). */
    String siguienteCodigo();

    /**
     * Crea el producto y, si se indica, su stock inicial, todo en una sola transacción.
     * Lanza NegocioException si el código ya existe.
     */
    long crear(ProductoDatos datos, StockInicial stockInicial, long usuarioId);

    void actualizar(long id, ProductoDatos datos);

    void cambiarActivo(long id, boolean activo);

    /**
     * Inserta un movimiento en el kardex (el trigger actualiza stock y costo promedio).
     * La cantidad lleva signo. Lanza NegocioException si el stock quedaría negativo.
     */
    void registrarMovimiento(long productoId, String tipo, BigDecimal cantidad,
                             BigDecimal costoUnitario, long usuarioId, String observacion);

    List<MovimientoInventario> listarMovimientos(long productoId, int limite);
}
