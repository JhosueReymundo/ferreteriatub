package com.tuberias.repository;

import com.tuberias.model.ProductoStockBajo;

/** Consulta si un producto está en o bajo su stock mínimo. */
public interface StockAlertas {

    /** Devuelve el producto si está en alerta, o null si su stock sigue por encima del mínimo. */
    ProductoStockBajo buscarSiEstaEnAlerta(long productoId);
}
