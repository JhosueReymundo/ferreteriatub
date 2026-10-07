package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.Proveedor;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Function;

/** Listado de proveedores con búsqueda. */
final class ProveedoresView {

    private ProveedoresView() {}

    static Parent crear(AppContext ctx) {
        ObservableList<Proveedor> items = FXCollections.observableArrayList();

        TextField buscar = new TextField();
        buscar.setPromptText("Buscar por nombre o RUC");
        HBox.setHgrow(buscar, Priority.ALWAYS);
        CheckBox inactivos = new CheckBox("Mostrar inactivos");
        Button nuevo = new Button("Nuevo proveedor");
        HBox superior = new HBox(10, buscar, inactivos, nuevo);
        superior.setAlignment(Pos.CENTER_LEFT);

        TableView<Proveedor> tabla = new TableView<>(items);
        tabla.setPlaceholder(new Label("No hay proveedores para mostrar."));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.getColumns().add(ProveedoresView.<Proveedor>col("Nombre", 320, Proveedor::nombre));
        tabla.getColumns().add(ProveedoresView.<Proveedor>col("RUC", 130, p -> p.ruc() == null ? "" : p.ruc()));
        tabla.getColumns().add(ProveedoresView.<Proveedor>col("Estado", 90, p -> p.activo() ? "Activo" : "Inactivo"));
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Proveedor p, boolean vacio) {
                super.updateItem(p, vacio);
                setStyle(vacio || p == null || p.activo() ? "" : "-fx-text-fill: #999999;");
            }
        });

        Button editar = new Button("Editar");
        Button estado = new Button("Desactivar");
        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        editar.disableProperty().bind(seleccion.isNull());
        estado.disableProperty().bind(seleccion.isNull());
        seleccion.addListener((obs, anterior, actual) -> {
            if (actual != null) {
                estado.setText(actual.activo() ? "Desactivar" : "Activar");
            }
        });

        Label resumen = new Label();
        Label error = Ui.etiquetaError();
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox inferior = new HBox(10, editar, estado, espacio, resumen);
        inferior.setAlignment(Pos.CENTER_LEFT);

        Runnable recargar = () -> Ui.manejar(error, () -> {
            items.setAll(ctx.proveedores().listar(buscar.getText(), inactivos.isSelected()));
            resumen.setText(items.size() + " proveedor(es)");
        });

        buscar.textProperty().addListener((obs, anterior, actual) -> recargar.run());
        inactivos.setOnAction(e -> recargar.run());
        nuevo.setOnAction(e -> {
            if (ProveedorDialog.mostrar(ctx, null).isPresent()) {
                recargar.run();
            }
        });
        editar.setOnAction(e -> {
            Proveedor p = seleccion.get();
            if (p != null && ProveedorDialog.mostrar(ctx, p).isPresent()) {
                recargar.run();
            }
        });
        estado.setOnAction(e -> {
            Proveedor p = seleccion.get();
            if (p != null) {
                Ui.manejar(error, () -> ctx.proveedores().cambiarActivo(p.id(), !p.activo()));
                if (error.getText().isEmpty()) {
                    recargar.run();
                }
            }
        });

        recargar.run();

        VBox raiz = new VBox(10, Ui.titulo("Proveedores"), superior, tabla, inferior, error);
        raiz.setPadding(new Insets(16));
        return raiz;
    }

    private static <T> TableColumn<T, String> col(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(valor.apply(cd.getValue())));
        return c;
    }
}
