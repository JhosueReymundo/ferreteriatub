package com.tuberias.model;

import java.math.BigDecimal;

/**
 * Datos que se capturan al crear o editar un producto (sin id ni stock).
 * Los campos categoriaId y unidadId son obligatorios pero van como objetos
 * para poder decir "no se eligió nada"; el servicio los valida.
 */
public record ProductoDatos(
        String codigo,
        String nombre,
        Long categoriaId,
        Long marcaId,
        String modelo,
        Short unidadId,
        Forma forma,
        String material,
        BigDecimal diametroMm,
        BigDecimal anchoMm,
        BigDecimal altoMm,
        BigDecimal espesorMm,
        BigDecimal longitudM,
        String color,
        String normaClase,
        String descripcion,
        BigDecimal precioSugerido,
        BigDecimal stockMinimo) {
}
