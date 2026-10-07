package com.tuberias.service;

import com.tuberias.model.Cliente;
import com.tuberias.repository.ClienteRepository;

import java.util.List;
import java.util.regex.Pattern;

import static com.tuberias.service.Validar.texto;

/** Reglas de negocio de clientes (todo opcional salvo el nombre). */
public class ClienteService {

    private static final Pattern DOCUMENTO = Pattern.compile("^([0-9]{8}|[0-9]{11})$");
    private static final Pattern TELEFONO = Pattern.compile("^[0-9+()\\- ]{6,20}$");

    private final ClienteRepository repo;

    public ClienteService(ClienteRepository repo) {
        this.repo = repo;
    }

    public List<Cliente> listar(String filtro, boolean incluirInactivos) {
        return repo.listar(filtro, incluirInactivos);
    }

    /** Clientes activos para elegir en una venta. */
    public List<Cliente> activos() {
        return repo.listar("", false);
    }

    public Cliente crear(String nombre, String documento, String telefono) {
        String n = texto(nombre, "El nombre", 150, true);
        String d = validarDocumento(documento);
        String t = validarTelefono(telefono);
        long id = repo.crear(n, d, t);
        return new Cliente(id, n, d, t, true);
    }

    public void actualizar(long id, String nombre, String documento, String telefono) {
        repo.actualizar(id, texto(nombre, "El nombre", 150, true),
                validarDocumento(documento), validarTelefono(telefono));
    }

    public void cambiarActivo(long id, boolean activo) {
        repo.cambiarActivo(id, activo);
    }

    private static String validarDocumento(String documento) {
        String d = documento == null ? "" : documento.trim();
        if (d.isEmpty()) {
            return null;
        }
        if (!DOCUMENTO.matcher(d).matches()) {
            throw new NegocioException("El documento debe ser un DNI (8 dígitos) o un RUC (11 dígitos), solo números.");
        }
        return d;
    }

    private static String validarTelefono(String telefono) {
        String t = telefono == null ? "" : telefono.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (!TELEFONO.matcher(t).matches()) {
            throw new NegocioException("El teléfono solo admite números, espacios y + - ( ), de 6 a 20 caracteres.");
        }
        return t;
    }
}
