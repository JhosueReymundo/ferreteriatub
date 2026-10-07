package com.tuberias.service;

import com.tuberias.model.CompraDetalle;
import com.tuberias.model.CompraResumen;
import com.tuberias.model.LineaCompra;
import com.tuberias.model.NuevaCompra;
import com.tuberias.model.Producto;
import com.tuberias.model.Usuario;
import com.tuberias.repository.CompraRepository;
import com.tuberias.repository.ProductoRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tuberias.service.Validar.*;

/** Reglas de negocio de compras. */
public class CompraService {

    private static final int MAX_LINEAS = 100;
    private static final int MAX_LISTADO = 300;

    private final CompraRepository repo;
    private final ProductoRepository productos;
    private final Clock clock;

    public CompraService(CompraRepository repo, ProductoRepository productos, Clock clock) {
        this.repo = repo;
        this.productos = productos;
        this.clock = clock;
    }

    // ------------------------------------------------------------- consultas

    public List<CompraResumen> listar(String filtro, boolean incluirAnuladas) {
        return repo.listar(filtro, incluirAnuladas, MAX_LISTADO);
    }

    public Optional<CompraDetalle> detalle(long id) {
        return repo.buscarDetalle(id);
    }

    // ------------------------------------------------------------- registrar

    /**
     * Registra la compra. Al guardarla, el stock de cada producto sube y su costo
     * promedio se recalcula; el precio de ESTA compra queda en el detalle.
     */
    public long registrar(Usuario usuario, NuevaCompra compra) {
        LocalDate hoy = LocalDate.now(clock);
        if (compra.fecha() == null) {
            throw new NegocioException("Indica la fecha de la compra.");
        }
        if (compra.fecha().isAfter(hoy)) {
            throw new NegocioException("La fecha de la compra no puede ser futura.");
        }
        List<LineaCompra> origen = compra.lineas() == null ? List.of() : compra.lineas();
        if (origen.isEmpty()) {
            throw new NegocioException("Agrega al menos un producto a la compra.");
        }
        if (origen.size() > MAX_LINEAS) {
            throw new NegocioException("Una compra admite como máximo " + MAX_LINEAS + " líneas.");
        }

        Map<Long, Producto> vistos = new HashMap<>();
        List<LineaCompra> lineas = new ArrayList<>();
        for (LineaCompra linea : origen) {
            LineaCompra valida = validarLinea(linea);
            Producto producto = vistos.get(valida.productoId());
            if (producto == null) {
                producto = productos.buscarPorId(valida.productoId())
                        .orElseThrow(() -> new NegocioException("Un producto de la compra ya no existe."));
                vistos.put(valida.productoId(), producto);
            }
            if (!producto.activo()) {
                throw new NegocioException("El producto \"" + producto.nombre() + "\" está inactivo.");
            }
            lineas.add(valida);
        }

        NuevaCompra limpia = new NuevaCompra(
                compra.proveedorId(),
                compra.fecha(),
                texto(compra.comprobante(), "El comprobante", 30, false),
                texto(compra.observacion(), "La observación", 255, false),
                List.copyOf(lineas),
                compra.pagoCaja());
        if (limpia.total().compareTo(LIMITE_12_2) >= 0) {
            throw new NegocioException("El total de la compra es demasiado grande.");
        }
        return repo.registrar(limpia, usuario.id());
    }

    /** Valida una línea (también se usa al agregarla en pantalla, para avisar de inmediato). */
    public LineaCompra validarLinea(LineaCompra linea) {
        BigDecimal cantidad = decimal(linea.cantidad(), "La cantidad", 2, LIMITE_12_2, false);
        if (cantidad == null) {
            throw new NegocioException("La cantidad es obligatoria.");
        }
        BigDecimal costo = decimal(linea.costoUnitario(), "El costo unitario", 4, LIMITE_12_4, true);
        if (costo == null) {
            throw new NegocioException("El costo unitario es obligatorio.");
        }
        LineaCompra valida = new LineaCompra(linea.productoId(), cantidad, costo);
        if (valida.subtotal().compareTo(LIMITE_12_2) >= 0) {
            throw new NegocioException("El subtotal de una línea es demasiado grande.");
        }
        return valida;
    }

    // --------------------------------------------------------------- anular

    /** Anula la compra y devuelve del inventario lo que había ingresado. Solo administrador. */
    public void anular(Usuario usuario, long compraId, String motivo) {
        if (usuario == null || !usuario.esAdmin()) {
            throw new NegocioException("Solo el administrador puede anular compras.");
        }
        repo.anular(compraId, usuario.id(), texto(motivo, "El motivo", 255, true));
    }
}
