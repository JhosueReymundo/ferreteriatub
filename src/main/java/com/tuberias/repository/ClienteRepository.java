package com.tuberias.repository;

import com.tuberias.model.Cliente;

import java.util.List;

public interface ClienteRepository {

    /** Busca por nombre, documento o teléfono (sin distinguir mayúsculas). */
    List<Cliente> listar(String filtro, boolean incluirInactivos);

    /** Devuelve el id; lanza NegocioException si el documento ya existe. */
    long crear(String nombre, String documento, String telefono);

    void actualizar(long id, String nombre, String documento, String telefono);

    void cambiarActivo(long id, boolean activo);
}
