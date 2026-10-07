package com.tuberias.service;

import com.tuberias.model.Categoria;
import com.tuberias.model.Forma;
import com.tuberias.model.Marca;
import com.tuberias.model.MovimientoInventario;
import com.tuberias.model.Producto;
import com.tuberias.model.ProductoDatos;
import com.tuberias.model.StockInicial;
import com.tuberias.model.UnidadMedida;
import com.tuberias.model.Usuario;
import com.tuberias.repository.CatalogoRepository;
import com.tuberias.repository.ProductoRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.tuberias.service.Validar.*;

/** Reglas de negocio de productos, catálogos y ajustes de inventario. */
public class ProductoService {

    private static final int MAX_MOVIMIENTOS = 200;

    private final ProductoRepository repo;
    private final CatalogoRepository catalogo;

    public ProductoService(ProductoRepository repo, CatalogoRepository catalogo) {
        this.repo = repo;
        this.catalogo = catalogo;
    }

    // ------------------------------------------------------------- consultas

    public List<Producto> listar(String filtro, boolean incluirInactivos) {
        return repo.listar(filtro, incluirInactivos);
    }

    public Optional<Producto> buscar(long id) {
        return repo.buscarPorId(id);
    }

    public List<MovimientoInventario> movimientos(long productoId) {
        return repo.listarMovimientos(productoId, MAX_MOVIMIENTOS);
    }

    // -------------------------------------------------------------- catálogos

    public List<Categoria> categorias() {
        return catalogo.listarCategorias();
    }

    public List<Marca> marcas() {
        return catalogo.listarMarcas();
    }

    public List<UnidadMedida> unidades() {
        return catalogo.listarUnidades();
    }

    public Categoria crearCategoria(String nombre) {
        String limpio = texto(nombre, "El nombre de la categoría", 80, true);
        return new Categoria(catalogo.crearCategoria(limpio), limpio);
    }

    public Marca crearMarca(String nombre) {
        String limpio = texto(nombre, "El nombre de la marca", 80, true);
        return new Marca(catalogo.crearMarca(limpio), limpio);
    }

    // -------------------------------------------------------------- productos

    /**
     * Crea un producto. Si ya tiene existencias, se pasan como {@code stockInicial}:
     * se registran como STOCK_INICIAL en el kardex, sin afectar ventas, compras ni caja.
     */
    public long crear(Usuario usuario, ProductoDatos datos, StockInicial stockInicial) {
        ProductoDatos limpio = validar(datos);
        StockInicial inicial = validarStockInicial(stockInicial);
        if (limpio.codigo() == null) {
            limpio = conCodigo(limpio, repo.siguienteCodigo());
        }
        return repo.crear(limpio, inicial, usuario.id());
    }

    public void actualizar(long id, ProductoDatos datos) {
        ProductoDatos limpio = validar(datos);
        if (limpio.codigo() == null) {
            // Al editar, el código no puede quedar vacío: se conserva el actual
            limpio = conCodigo(limpio, repo.buscarPorId(id)
                    .orElseThrow(() -> new NegocioException("El producto ya no existe."))
                    .codigo());
        }
        repo.actualizar(id, limpio);
    }

    public void cambiarActivo(long id, boolean activo) {
        repo.cambiarActivo(id, activo);
    }

    // ------------------------------------------------------ ajustes de stock

    /**
     * Corrige el stock por mermas, conteo físico, etc. Solo para el administrador.
     * Las compras y ventas ajustan el stock por su cuenta; esto es solo para diferencias.
     */
    public void ajustarStock(Usuario usuario, long productoId, boolean entrada,
                             BigDecimal cantidad, BigDecimal costoUnitario, String motivo) {
        if (usuario == null || !usuario.esAdmin()) {
            throw new NegocioException("Solo el administrador puede ajustar el stock.");
        }
        BigDecimal cant = decimal(cantidad, "La cantidad", 2, LIMITE_12_2, false);
        if (cant == null) {
            throw new NegocioException("La cantidad es obligatoria.");
        }
        String razon = texto(motivo, "El motivo", 255, true);

        Producto producto = repo.buscarPorId(productoId)
                .orElseThrow(() -> new NegocioException("El producto ya no existe."));
        if (!producto.activo()) {
            throw new NegocioException("El producto está inactivo. Actívalo para ajustar su stock.");
        }

        if (entrada) {
            BigDecimal costo = decimal(costoUnitario, "El costo unitario", 4, LIMITE_12_4, true);
            if (costo == null) {
                throw new NegocioException("El costo unitario es obligatorio en una entrada.");
            }
            repo.registrarMovimiento(productoId, "AJUSTE_ENTRADA", cant, costo, usuario.id(), razon);
        } else {
            repo.registrarMovimiento(productoId, "AJUSTE_SALIDA", cant.negate(), null, usuario.id(), razon);
        }
    }

    // ------------------------------------------------------------ validación

    private ProductoDatos validar(ProductoDatos d) {
        String codigo = texto(d.codigo(), "El código", 30, false);
        if (codigo != null) {
            codigo = codigo.toUpperCase(Locale.ROOT);
        }
        String nombre = texto(d.nombre(), "El nombre", 150, true);
        if (d.categoriaId() == null) {
            throw new NegocioException("Selecciona una categoría.");
        }
        if (d.unidadId() == null) {
            throw new NegocioException("Selecciona una unidad de medida.");
        }
        BigDecimal stockMinimo = decimal(d.stockMinimo(), "El stock mínimo", 2, LIMITE_12_2, true);

        return new ProductoDatos(
                codigo,
                nombre,
                d.categoriaId(),
                d.marcaId(),
                texto(d.modelo(), "El modelo", 80, false),
                d.unidadId(),
                d.forma() == null ? Forma.SIN_ESPECIFICAR : d.forma(),
                texto(d.material(), "El material", 60, false),
                decimal(d.diametroMm(), "El diámetro", 2, LIMITE_8_2, false),
                decimal(d.anchoMm(), "El ancho", 2, LIMITE_8_2, false),
                decimal(d.altoMm(), "El alto", 2, LIMITE_8_2, false),
                decimal(d.espesorMm(), "El espesor", 2, LIMITE_6_2, false),
                decimal(d.longitudM(), "La longitud", 2, LIMITE_8_2, false),
                texto(d.color(), "El color", 30, false),
                texto(d.normaClase(), "La norma/clase", 60, false),
                texto(d.descripcion(), "La descripción", 500, false),
                decimal(d.precioSugerido(), "El precio sugerido", 2, LIMITE_12_2, true),
                stockMinimo == null ? BigDecimal.ZERO : stockMinimo);
    }

    private StockInicial validarStockInicial(StockInicial s) {
        if (s == null) {
            return null;
        }
        BigDecimal cantidad = decimal(s.cantidad(), "La cantidad del stock inicial", 2, LIMITE_12_2, false);
        if (cantidad == null) {
            throw new NegocioException("Indica la cantidad del stock inicial.");
        }
        BigDecimal costo = decimal(s.costoUnitario(), "El costo unitario del stock inicial", 4, LIMITE_12_4, true);
        if (costo == null) {
            throw new NegocioException("Indica el costo unitario del stock inicial.");
        }
        return new StockInicial(cantidad, costo);
    }

    private static ProductoDatos conCodigo(ProductoDatos d, String codigo) {
        return new ProductoDatos(codigo, d.nombre(), d.categoriaId(), d.marcaId(), d.modelo(), d.unidadId(),
                d.forma(), d.material(), d.diametroMm(), d.anchoMm(), d.altoMm(), d.espesorMm(), d.longitudM(),
                d.color(), d.normaClase(), d.descripcion(), d.precioSugerido(), d.stockMinimo());
    }
}
