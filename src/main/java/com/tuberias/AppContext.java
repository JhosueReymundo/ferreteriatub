package com.tuberias;

import com.tuberias.repository.StockRepository;
import com.tuberias.service.AuthService;
import com.tuberias.service.CajaService;
import com.tuberias.service.ClienteService;
import com.tuberias.service.DashboardService;
import com.tuberias.service.CompraService;
import com.tuberias.service.ProductoService;
import com.tuberias.service.ProveedorService;
import com.tuberias.service.VentaService;

/** Agrupa los servicios de la aplicación para pasarlos a las pantallas. */
public record AppContext(
        AuthService auth,
        ProductoService productos,
        StockRepository stock,
        ProveedorService proveedores,
        CompraService compras,
        ClienteService clientes,
        VentaService ventas,
        CajaService caja,
        DashboardService dashboard) {
}
