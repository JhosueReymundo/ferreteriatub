package com.tuberias.ui;

import com.tuberias.model.Formato;
import com.tuberias.model.Producto;
import com.tuberias.service.ProductoService;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Buscador de productos activos: una caja de texto que filtra la lista mientras se escribe
 * (por código, nombre, categoría, marca, modelo, material o medidas). También se usa en ventas (solo con stock).
 */
final class ProductoSelector extends VBox {

    private final TextField buscar = new TextField();
    private final ListView<Producto> lista = new ListView<>();

    ProductoSelector(ProductoService productos) {
        this(productos, false);
    }

    /** @param soloConStock true para ventas: oculta los productos sin unidades disponibles */
    ProductoSelector(ProductoService productos, boolean soloConStock) {
        super(6);

        var disponibles = productos.listar("", false).stream()
                .filter(p -> !soloConStock || p.stockActual().signum() > 0)
                .toList();
        FilteredList<Producto> filtrada = new FilteredList<>(
                FXCollections.observableArrayList(disponibles), p -> true);

        buscar.setPromptText("Buscar producto: nombre, código, marca, medidas...");
        buscar.textProperty().addListener((obs, anterior, texto) -> {
            String[] palabras = (texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT)).split("\\s+");
            filtrada.setPredicate(p -> {
                String pajar = pajar(p);
                for (String palabra : palabras) {
                    if (!palabra.isEmpty() && !pajar.contains(palabra)) {
                        return false;
                    }
                }
                return true;
            });
        });

        lista.setItems(filtrada);
        lista.setPrefHeight(150);
        lista.setPlaceholder(new javafx.scene.control.Label("Ningún producto coincide."));
        lista.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Producto p, boolean vacio) {
                super.updateItem(p, vacio);
                setText(vacio || p == null ? null : etiqueta(p));
            }
        });

        getChildren().addAll(buscar, lista);
    }

    ReadOnlyObjectProperty<Producto> seleccionado() {
        return lista.getSelectionModel().selectedItemProperty();
    }

    void enfocar() {
        buscar.requestFocus();
    }

    void limpiar() {
        lista.getSelectionModel().clearSelection();
        buscar.clear();
        buscar.requestFocus();
    }

    private static String etiqueta(Producto p) {
        String medidas = p.medidas();
        return p.codigo() + " · " + p.nombre()
                + (medidas.isEmpty() ? "" : " · " + medidas)
                + " · stock " + Formato.numero(p.stockActual()) + " " + p.unidadCodigo();
    }

    private static String pajar(Producto p) {
        return String.join(" ",
                p.codigo(), p.nombre(), p.categoriaNombre(),
                p.marcaNombre() == null ? "" : p.marcaNombre(),
                p.modelo() == null ? "" : p.modelo(),
                p.material() == null ? "" : p.material(),
                p.medidas()).toLowerCase(Locale.ROOT);
    }
}
