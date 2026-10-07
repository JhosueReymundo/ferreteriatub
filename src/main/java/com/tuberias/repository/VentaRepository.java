package com.tuberias.repository;

import com.tuberias.model.NuevaVenta;
import com.tuberias.model.VentaDetalle;
import com.tuberias.model.VentaResumen;

import java.util.List;
import java.util.Optional;

public interface VentaRepository {

    /**
     * Guarda la venta, sus líneas (con el costo promedio de cada producto en ese momento)
     * y las salidas del kardex en UNA transacción. Devuelve el id de la venta.
     * Lanza NegocioException si algún producto no tiene stock suficiente.
     */
    long registrar(NuevaVenta venta, long usuarioId);

    /** Más recientes primero. Busca por cliente o documento. */
    List<VentaResumen> listar(String filtro, boolean incluirAnuladas, int limite);

    Optional<VentaDetalle> buscarDetalle(long id);

    /**
     * Anula la venta y devuelve las unidades al inventario (en una transacción).
     * Lanza NegocioException si ya estaba anulada.
     */
    void anular(long id, long usuarioId, String motivo);
}
