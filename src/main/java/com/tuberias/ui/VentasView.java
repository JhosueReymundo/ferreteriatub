package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.VentaResumen;
import com.tuberias.model.Formato;
import com.tuberias.service.Sesion;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/** Listado de ventas: registrar, ver detalle y anular. */
final class VentasView {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private VentasView() {}

    static Parent crear(AppContext ctx) {
        ObservableList<VentaResumen> items = FXCollections.observableArrayList();
        boolean admin = Sesion.actual().esAdmin();

        TextField buscar = new TextField();
        buscar.setPromptText("Buscar por cliente o documento");
        HBox.setHgrow(buscar, Priority.ALWAYS);
        CheckBox anuladas = new CheckBox("Mostrar anuladas");
        Button nueva = new Button("Nueva venta");
        HBox superior = new HBox(10, buscar, anuladas, nueva);
        superior.setAlignment(Pos.CENTER_LEFT);

        TableView<VentaResumen> tabla = new TableView<>(items);
        tabla.setPlaceholder(new Label("No hay ventas para mostrar."));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.getColumns().add(VentasView.<VentaResumen>col("N°", 60, c -> String.valueOf(c.id())));
        tabla.getColumns().add(VentasView.<VentaResumen>col("Fecha", 130, c -> FECHA.format(c.fecha())));
        tabla.getColumns().add(VentasView.<VentaResumen>col("Cliente", 260,
                c -> c.cliente() == null ? "(sin cliente)" : c.cliente()));
        tabla.getColumns().add(VentasView.<VentaResumen>col("Pago", 110, c -> c.metodoPago().toString()));
        tabla.getColumns().add(VentasView.<VentaResumen>col("Total", 110, c -> Formato.dinero(c.total())));
        tabla.getColumns().add(VentasView.<VentaResumen>col("Estado", 90, c -> c.anulada() ? "Anulada" : "Registrada"));
        tabla.getColumns().add(VentasView.<VentaResumen>col("Usuario", 100, VentaResumen::usuario));
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(VentaResumen c, boolean vacio) {
                super.updateItem(c, vacio);
                setStyle(vacio || c == null || !c.anulada() ? "" : "-fx-text-fill: #999999;");
            }
        });

        Button detalle = new Button("Ver detalle");
        Button anular = new Button("Anular venta");
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
            items.setAll(ctx.ventas().listar(buscar.getText(), anuladas.isSelected()));
            resumen.setText(items.size() + " venta(s)");
        });

        buscar.textProperty().addListener((obs, anterior, actual) -> recargar.run());
        anuladas.setOnAction(e -> recargar.run());
        nueva.setOnAction(e -> {
            // Sin caja abierta no se puede vender: se ofrece abrirla en el momento
            boolean[] hayCaja = {false};
            Ui.manejar(error, () -> hayCaja[0] = ctx.caja().cajaAbierta().isPresent());
            if (!hayCaja[0] && error.getText().isEmpty()) {
                Alert aviso = new Alert(Alert.AlertType.CONFIRMATION,
                        "Para registrar ventas hay que abrir la caja primero.\n\n¿Abrirla ahora?",
                        ButtonType.YES, ButtonType.NO);
                aviso.setHeaderText("No hay una caja abierta");
                if (aviso.showAndWait().orElse(ButtonType.NO) == ButtonType.YES && AbrirCajaDialog.mostrar(ctx)) {
                    hayCaja[0] = true;
                }
            }
            if (hayCaja[0] && VentaDialog.mostrar(ctx)) {
                recargar.run();
            }
        });
        detalle.setOnAction(e -> {
            VentaResumen c = seleccion.get();
            if (c != null) {
                Ui.manejar(error, () -> VentaDetalleDialog.mostrar(ctx, c.id()));
            }
        });
        anular.setOnAction(e -> {
            VentaResumen c = seleccion.get();
            if (c == null) {
                return;
            }
            TextInputDialog pedirMotivo = new TextInputDialog();
            pedirMotivo.setTitle("Anular venta");
            pedirMotivo.setHeaderText("Anular la venta #" + c.id() + " por " + Formato.dinero(c.total())
                    + "\nLas unidades vendidas volverán al inventario.");
            pedirMotivo.setContentText("Motivo:");
            pedirMotivo.showAndWait().ifPresent(motivo -> {
                Ui.manejar(error, () -> ctx.ventas().anular(Sesion.actual(), c.id(), motivo));
                if (error.getText().isEmpty()) {
                    recargar.run();
                }
            });
        });

        recargar.run();

        VBox raiz = new VBox(10, Ui.titulo("Ventas"), superior, tabla, inferior, error);
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
