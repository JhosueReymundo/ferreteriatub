package com.tuberias.repository;

import com.tuberias.model.CompraDetalle;
import com.tuberias.model.CompraResumen;
import com.tuberias.model.NuevaCompra;

import java.util.List;
import java.util.Optional;

public interface CompraRepository {

    /**
     * Guarda la compra, sus líneas y los ingresos al kardex en UNA transacción:
     * si algo falla no queda nada a medias. Devuelve el id de la compra.
     */
    long registrar(NuevaCompra compra, long usuarioId);

    /** Más recientes primero. Busca por proveedor, RUC o comprobante. */
    List<CompraResumen> listar(String filtro, boolean incluirAnuladas, int limite);

    Optional<CompraDetalle> buscarDetalle(long id);

    /**
     * Anula la compra y revierte sus ingresos del inventario (en una transacción).
     * Lanza NegocioException si ya estaba anulada o si el stock quedaría negativo.
     */
    void anular(long id, long usuarioId, String motivo);
}
