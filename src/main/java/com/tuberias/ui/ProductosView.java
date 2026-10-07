package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.Formato;
import com.tuberias.model.Producto;
import com.tuberias.service.Sesion;
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

import java.util.List;
import java.util.function.Function;

/** Listado de productos con búsqueda y acciones. */
final class ProductosView {

    private ProductosView() {}

    static Parent crear(AppContext ctx) {
        ObservableList<Producto> items = FXCollections.observableArrayList();
        boolean admin = Sesion.actual().esAdmin();

        // ---- barra de búsqueda
        TextField buscar = new TextField();
        buscar.setPromptText("Buscar por nombre, código, categoría, marca, modelo o material");
        HBox.setHgrow(buscar, Priority.ALWAYS);
        CheckBox inactivos = new CheckBox("Mostrar inactivos");
        Button nuevo = new Button("Nuevo producto");

        HBox superior = new HBox(10, buscar, inactivos, nuevo);
        superior.setAlignment(Pos.CENTER_LEFT);

        // ---- tabla
        TableView<Producto> tabla = new TableView<>(items);
        tabla.setPlaceholder(new Label("No hay productos para mostrar."));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        List<TableColumn<Producto, String>> columnas = List.of(
                col("Código", 80, Producto::codigo),
                col("Nombre", 220, Producto::nombre),
                col("Categoría", 110, Producto::categoriaNombre),
                col("Marca", 90, p -> p.marcaNombre() == null ? "" : p.marcaNombre()),
                col("Medidas", 190, Producto::medidas),
                col("Stock", 90, p -> Formato.numero(p.stockActual()) + " " + p.unidadCodigo()),
                col("Mínimo", 70, p -> Formato.numero(p.stockMinimo())),
                col("Costo prom.", 90, p -> Formato.dinero(p.costoPromedio())),
                col("Precio sug.", 90, p -> Formato.dinero(p.precioSugerido())),
                col("Estado", 70, p -> p.activo() ? "Activo" : "Inactivo"));
        tabla.getColumns().addAll(columnas);

        // Rojo suave = stock bajo; gris = inactivo
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Producto p, boolean vacio) {
                super.updateItem(p, vacio);
                if (vacio || p == null) {
                    setStyle("");
                } else if (!p.activo()) {
                    setStyle("-fx-text-fill: #999999;");
                } else if (p.stockBajo()) {
                    setStyle("-fx-background-color: #fdecea;");
                } else {
                    setStyle("");
                }
            }
        });

        // ---- acciones sobre la fila seleccionada
        Button editar = new Button("Editar");
        Button ajustar = new Button("Ajustar stock");
        Button movimientos = new Button("Ver movimientos");
        Button estado = new Button("Desactivar");

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        editar.disableProperty().bind(seleccion.isNull());
        movimientos.disableProperty().bind(seleccion.isNull());
        estado.disableProperty().bind(seleccion.isNull());
        if (admin) {
            ajustar.disableProperty().bind(seleccion.isNull());
        } else {
            ajustar.setDisable(true);
        }
        seleccion.addListener((obs, anterior, actual) -> {
            if (actual != null) {
                estado.setText(actual.activo() ? "Desactivar" : "Activar");
            }
        });

        Label resumen = new Label();
        Label error = Ui.etiquetaError();
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox inferior = new HBox(10, editar, ajustar, movimientos, estado, espacio, resumen);
        inferior.setAlignment(Pos.CENTER_LEFT);

        // ---- carga de datos
        Runnable recargar = () -> {
            Producto elegido = seleccion.get();
            Ui.manejar(error, () -> {
                items.setAll(ctx.productos().listar(buscar.getText(), inactivos.isSelected()));
                resumen.setText(items.size() + " producto(s)");
            });
            if (elegido != null) {
                items.stream().filter(p -> p.id() == elegido.id()).findFirst()
                        .ifPresent(p -> tabla.getSelectionModel().select(p));
            }
        };

        buscar.textProperty().addListener((obs, anterior, actual) -> recargar.run());
        inactivos.setOnAction(e -> recargar.run());

        nuevo.setOnAction(e -> {
            if (ProductoDialog.mostrar(ctx, null)) {
                recargar.run();
            }
        });
        editar.setOnAction(e -> {
            Producto p = seleccion.get();
            if (p != null && ProductoDialog.mostrar(ctx, p)) {
                recargar.run();
            }
        });
        ajustar.setOnAction(e -> {
            Producto p = seleccion.get();
            if (p != null && AjusteStockDialog.mostrar(ctx, p)) {
                recargar.run();
            }
        });
        movimientos.setOnAction(e -> {
            Producto p = seleccion.get();
            if (p != null) {
                MovimientosDialog.mostrar(ctx, p);
            }
        });
        estado.setOnAction(e -> {
            Producto p = seleccion.get();
            if (p != null) {
                Ui.manejar(error, () -> ctx.productos().cambiarActivo(p.id(), !p.activo()));
                if (error.getText().isEmpty()) {
                    recargar.run();
                }
            }
        });

        recargar.run();

        VBox raiz = new VBox(10, Ui.titulo("Productos"), superior, tabla, inferior, error);
        raiz.setPadding(new Insets(16));
        return raiz;
    }

    private static TableColumn<Producto, String> col(String titulo, double ancho, Function<Producto, String> valor) {
        TableColumn<Producto, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(valor.apply(cd.getValue())));
        return c;
    }
}
