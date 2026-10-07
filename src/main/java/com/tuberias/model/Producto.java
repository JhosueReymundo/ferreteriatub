package com.tuberias.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record Producto(
        long id,
        String codigo,
        String nombre,
        long categoriaId,
        String categoriaNombre,
        Long marcaId,
        String marcaNombre,
        String modelo,
        short unidadId,
        String unidadCodigo,
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
        BigDecimal stockActual,
        BigDecimal costoPromedio,
        BigDecimal stockMinimo,
        boolean activo) {

    /** Resumen de medidas para la tabla, p. ej. "Ø 50 mm · esp. 3.2 mm · 6 m". */
    public String medidas() {
        List<String> partes = new ArrayList<>();
        if (diametroMm != null) {
            partes.add("Ø " + Formato.numero(diametroMm) + " mm");
        }
        if (anchoMm != null && altoMm != null) {
            partes.add(Formato.numero(anchoMm) + "×" + Formato.numero(altoMm) + " mm");
        } else if (anchoMm != null) {
            partes.add("ancho " + Formato.numero(anchoMm) + " mm");
        } else if (altoMm != null) {
            partes.add("alto " + Formato.numero(altoMm) + " mm");
        }
        if (espesorMm != null) {
            partes.add("esp. " + Formato.numero(espesorMm) + " mm");
        }
        if (longitudM != null) {
            partes.add(Formato.numero(longitudM) + " m");
        }
        return String.join(" · ", partes);
    }

    /** Alerta: tiene stock mínimo configurado y el stock llegó a ese nivel o menos. */
    public boolean stockBajo() {
        return stockMinimo.signum() > 0 && stockActual.compareTo(stockMinimo) <= 0;
    }

    /** Para precargar el formulario de edición. */
    public ProductoDatos toDatos() {
        return new ProductoDatos(codigo, nombre, categoriaId, marcaId, modelo, unidadId, forma,
                material, diametroMm, anchoMm, altoMm, espesorMm, longitudM, color, normaClase,
                descripcion, precioSugerido, stockMinimo);
    }
}
