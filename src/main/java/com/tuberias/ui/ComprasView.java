package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.CompraResumen;
import com.tuberias.model.Formato;
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
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/** Listado de compras: registrar, ver detalle y anular. */
final class ComprasView {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ComprasView() {}

    static Parent crear(AppContext ctx) {
        ObservableList<CompraResumen> items = FXCollections.observableArrayList();
        boolean admin = Sesion.actual().esAdmin();

        TextField buscar = new TextField();
        buscar.setPromptText("Buscar por proveedor, RUC o comprobante");
        HBox.setHgrow(buscar, Priority.ALWAYS);
        CheckBox anuladas = new CheckBox("Mostrar anuladas");
        Button nueva = new Button("Nueva compra");
        HBox superior = new HBox(10, buscar, anuladas, nueva);
        superior.setAlignment(Pos.CENTER_LEFT);

        TableView<CompraResumen> tabla = new TableView<>(items);
        tabla.setPlaceholder(new Label("No hay compras para mostrar."));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.getColumns().add(ComprasView.<CompraResumen>col("N°", 60, c -> String.valueOf(c.id())));
        tabla.getColumns().add(ComprasView.<CompraResumen>col("Fecha", 100, c -> FECHA.format(c.fecha())));
        tabla.getColumns().add(ComprasView.<CompraResumen>col("Proveedor", 250,
                c -> c.proveedor() == null ? "(sin proveedor)" : c.proveedor()));
        tabla.getColumns().add(ComprasView.<CompraResumen>col("Comprobante", 130,
                c -> c.comprobante() == null ? "" : c.comprobante()));
        tabla.getColumns().add(ComprasView.<CompraResumen>col("Total", 110, c -> Formato.dinero(c.total())));
        tabla.getColumns().add(ComprasView.<CompraResumen>col("Estado", 90, c -> c.anulada() ? "Anulada" : "Registrada"));
        tabla.getColumns().add(ComprasView.<CompraResumen>col("Usuario", 100, CompraResumen::usuario));
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(CompraResumen c, boolean vacio) {
                super.updateItem(c, vacio);
                setStyle(vacio || c == null || !c.anulada() ? "" : "-fx-text-fill: #999999;");
            }
        });

        Button detalle = new Button("Ver detalle");
        Button anular = new Button("Anular compra");
        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        detalle.disableProperty().bind(seleccion.isNull());
        if (admin) {
            anular.disableProperty().bind(seleccion.isNull());
        } else {
            anular.setDisable(true);
        }

        Label resumen = new Label();
        Label error = Ui.etiquetaError();
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox inferior = new HBox(10, detalle, anular, espacio, resumen);
        inferior.setAlignment(Pos.CENTER_LEFT);

        Runnable recargar = () -> Ui.manejar(error, () -> {
            items.setAll(ctx.compras().listar(buscar.getText(), anuladas.isSelected()));
            resumen.setText(items.size() + " compra(s)");
        });

        buscar.textProperty().addListener((obs, anterior, actual) -> recargar.run());
        anuladas.setOnAction(e -> recargar.run());
        nueva.setOnAction(e -> {
            if (CompraDialog.mostrar(ctx)) {
                recargar.run();
            }
        });
        detalle.setOnAction(e -> {
            CompraResumen c = seleccion.get();
            if (c != null) {
                Ui.manejar(error, () -> CompraDetalleDialog.mostrar(ctx, c.id()));
            }
        });
        anular.setOnAction(e -> {
            CompraResumen c = seleccion.get();
            if (c == null) {
                return;
            }
            TextInputDialog pedirMotivo = new TextInputDialog();
            pedirMotivo.setTitle("Anular compra");
            pedirMotivo.setHeaderText("Anular la compra #" + c.id() + " por " + Formato.dinero(c.total())
                    + "\nSe descontará del stock lo que ingresó con esta compra.");
            pedirMotivo.setContentText("Motivo:");
            pedirMotivo.showAndWait().ifPresent(motivo -> {
                Ui.manejar(error, () -> ctx.compras().anular(Sesion.actual(), c.id(), motivo));
                if (error.getText().isEmpty()) {
                    recargar.run();
                }
            });
        });

        recargar.run();

        VBox raiz = new VBox(10, Ui.titulo("Compras"), superior, tabla, inferior, error);
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
