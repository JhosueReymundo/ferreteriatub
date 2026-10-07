package com.tuberias.repository;

import com.tuberias.model.Proveedor;

import java.util.List;

public interface ProveedorRepository {

    /** Busca por nombre o RUC (sin distinguir mayúsculas). */
    List<Proveedor> listar(String filtro, boolean incluirInactivos);

    /** Devuelve el id; lanza NegocioException si el RUC ya existe. */
    long crear(String nombre, String ruc);

    void actualizar(long id, String nombre, String ruc);

    void cambiarActivo(long id, boolean activo);
}
