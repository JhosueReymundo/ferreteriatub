package com.tuberias.service;

import com.tuberias.model.LineaVenta;
import com.tuberias.model.NuevaVenta;
import com.tuberias.model.Producto;
import com.tuberias.model.ProductoStockBajo;
import com.tuberias.model.Usuario;
import com.tuberias.model.VentaDetalle;
import com.tuberias.model.VentaRegistrada;
import com.tuberias.model.VentaResumen;
import com.tuberias.repository.ProductoRepository;
import com.tuberias.repository.StockAlertas;
import com.tuberias.repository.VentaRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.tuberias.service.Validar.*;

/** Reglas de negocio de ventas. */
public class VentaService {

    private static final int MAX_LINEAS = 100;
    private static final int MAX_LISTADO = 300;

    private final VentaRepository repo;
    private final ProductoRepository productos;
    private final StockAlertas alertas;

    public VentaService(VentaRepository repo, ProductoRepository productos, StockAlertas alertas) {
        this.repo = repo;
        this.productos = productos;
        this.alertas = alertas;
    }

    // ------------------------------------------------------------- consultas

    public List<VentaResumen> listar(String filtro, boolean incluirAnuladas) {
        return repo.listar(filtro, incluirAnuladas, MAX_LISTADO);
    }

    public Optional<VentaDetalle> detalle(long id) {
        return repo.buscarDetalle(id);
    }

    // ------------------------------------------------------------- registrar

    /**
     * Registra la venta: baja el stock, guarda el precio realmente cobrado de cada línea y el
     * costo del producto en ese momento. Devuelve también los productos que quedaron en alerta
     * de stock mínimo para avisar de inmediato.
     */
    public VentaRegistrada registrar(Usuario usuario, NuevaVenta venta) {
        NuevaVenta limpia = validar(venta);

        long id = repo.registrar(limpia, usuario.id());

        // El aviso es un extra: si falla la consulta, la venta ya está guardada y no se pierde nada
        List<ProductoStockBajo> enAlerta = new ArrayList<>();
        Set<Long> ids = new LinkedHashSet<>();
        limpia.lineas().forEach(l -> ids.add(l.productoId()));
        for (long productoId : ids) {
            try {
                ProductoStockBajo p = alertas.buscarSiEstaEnAlerta(productoId);
                if (p != null) {
                    enAlerta.add(p);
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        return new VentaRegistrada(id, limpia.total(), enAlerta);
    }

    /**
     * Nombres de los productos que se venderían a un precio menor a su costo promedio.
     * No bloquea la venta (a veces se liquida con pérdida), pero la pantalla pide confirmación.
     */
    public List<String> productosBajoCosto(NuevaVenta venta) {
        Set<String> nombres = new LinkedHashSet<>();
        for (LineaVenta linea : venta.lineas() == null ? List.<LineaVenta>of() : venta.lineas()) {
            if (linea.precioUnitario() == null) {
                continue;
            }
            productos.buscarPorId(linea.productoId()).ifPresent(p -> {
                if (linea.precioUnitario().compareTo(p.costoPromedio()) < 0) {
                    nombres.add(p.nombre());
                }
            });
        }
        return new ArrayList<>(nombres);
    }

    /** Valida una línea (también se usa al agregarla en pantalla, para avisar de inmediato). */
    public LineaVenta validarLinea(LineaVenta linea) {
        BigDecimal cantidad = decimal(linea.cantidad(), "La cantidad", 2, LIMITE_12_2, false);
        if (cantidad == null) {
            throw new NegocioException("La cantidad es obligatoria.");
        }
        BigDecimal precio = decimal(linea.precioUnitario(), "El precio", 2, LIMITE_12_2, true);
        if (precio == null) {
            throw new NegocioException("El precio es obligatorio.");
        }
        LineaVenta valida = new LineaVenta(linea.productoId(), cantidad, precio);
        if (valida.subtotal().compareTo(LIMITE_12_2) >= 0) {
            throw new NegocioException("El subtotal de una línea es demasiado grande.");
        }
        return valida;
    }

    private NuevaVenta validar(NuevaVenta venta) {
        if (venta.metodoPago() == null) {
            throw new NegocioException("Selecciona el método de pago.");
        }
        List<LineaVenta> origen = venta.lineas() == null ? List.of() : venta.lineas();
        if (origen.isEmpty()) {
            throw new NegocioException("Agrega al menos un producto a la venta.");
        }
        if (origen.size() > MAX_LINEAS) {
            throw new NegocioException("Una venta admite como máximo " + MAX_LINEAS + " líneas.");
        }

        List<LineaVenta> lineas = new ArrayList<>();
        Map<Long, BigDecimal> pedido = new LinkedHashMap<>();   // total pedido por producto
        for (LineaVenta linea : origen) {
            LineaVenta valida = validarLinea(linea);
            lineas.add(valida);
            pedido.merge(valida.productoId(), valida.cantidad(), BigDecimal::add);
        }

        // Stock actual (leído ahora) frente a lo pedido, sumando las líneas del mismo producto
        for (Map.Entry<Long, BigDecimal> e : pedido.entrySet()) {
            Producto p = productos.buscarPorId(e.getKey())
                    .orElseThrow(() -> new NegocioException("Un producto de la venta ya no existe."));
            if (!p.activo()) {
                throw new NegocioException("El producto \"" + p.nombre() + "\" está inactivo.");
            }
            if (e.getValue().compareTo(p.stockActual()) > 0) {
                throw new NegocioException("Stock insuficiente de \"" + p.nombre() + "\": hay "
                        + p.stockActual().stripTrailingZeros().toPlainString() + " " + p.unidadCodigo()
                        + " y pides " + e.getValue().stripTrailingZeros().toPlainString() + ".");
            }
        }

        NuevaVenta limpia = new NuevaVenta(
                venta.clienteId(),
                venta.metodoPago(),
                texto(venta.observacion(), "La observación", 255, false),
                List.copyOf(lineas));
        if (limpia.total().compareTo(LIMITE_12_2) >= 0) {
            throw new NegocioException("El total de la venta es demasiado grande.");
        }
        return limpia;
    }

    // --------------------------------------------------------------- anular

    /** Anula la venta y devuelve las unidades al inventario. Solo administrador. */
    public void anular(Usuario usuario, long ventaId, String motivo) {
        if (usuario == null || !usuario.esAdmin()) {
            throw new NegocioException("Solo el administrador puede anular ventas.");
        }
        repo.anular(ventaId, usuario.id(), texto(motivo, "El motivo", 255, true));
    }
}
