package com.tuberias.repository;

import com.tuberias.model.Categoria;
import com.tuberias.model.Marca;
import com.tuberias.model.UnidadMedida;

import java.util.List;

public interface CatalogoRepository {

    List<Categoria> listarCategorias();

    /** Devuelve el id; lanza NegocioException si ya existe una con ese nombre. */
    long crearCategoria(String nombre);

    List<Marca> listarMarcas();

    long crearMarca(String nombre);

    List<UnidadMedida> listarUnidades();
}
