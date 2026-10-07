package com.tuberias.service;

import com.tuberias.model.Proveedor;
import com.tuberias.repository.ProveedorRepository;

import java.util.List;
import java.util.regex.Pattern;

import static com.tuberias.service.Validar.texto;

/** Reglas de negocio de proveedores (solo nombre y RUC opcional). */
public class ProveedorService {

    private static final Pattern RUC = Pattern.compile("^[0-9]{11}$");

    private final ProveedorRepository repo;

    public ProveedorService(ProveedorRepository repo) {
        this.repo = repo;
    }

    public List<Proveedor> listar(String filtro, boolean incluirInactivos) {
        return repo.listar(filtro, incluirInactivos);
    }

    /** Proveedores activos para elegir en una compra. */
    public List<Proveedor> activos() {
        return repo.listar("", false);
    }

    public Proveedor crear(String nombre, String ruc) {
        String n = texto(nombre, "El nombre", 150, true);
        String r = validarRuc(ruc);
        long id = repo.crear(n, r);
        return new Proveedor(id, n, r, true);
    }

    public void actualizar(long id, String nombre, String ruc) {
        repo.actualizar(id, texto(nombre, "El nombre", 150, true), validarRuc(ruc));
    }

    public void cambiarActivo(long id, boolean activo) {
        repo.cambiarActivo(id, activo);
    }

    private static String validarRuc(String ruc) {
        String r = ruc == null ? "" : ruc.trim();
        if (r.isEmpty()) {
            return null;
        }
        if (!RUC.matcher(r).matches()) {
            throw new NegocioException("El RUC debe tener exactamente 11 dígitos (solo números).");
        }
        return r;
    }
}
