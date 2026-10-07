package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.Formato;
import com.tuberias.model.LineaCompra;
import com.tuberias.model.MetodoPago;
import com.tuberias.model.NuevaCompra;
import com.tuberias.model.Producto;
import com.tuberias.model.Proveedor;
import com.tuberias.service.NegocioException;
import com.tuberias.service.Sesion;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.scene.control.ScrollPane;
import javafx.stage.Screen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Function;

/** Formulario para registrar una compra: datos generales + detalle de productos. */
final class CompraDialog {

    private static final String NO_REGISTRAR_EN_CAJA = "No registrar en caja";
    private static final Proveedor SIN_PROVEEDOR = new Proveedor(0, "(sin proveedor)", null, true);

    /** Una línea del detalle tal como se ve en pantalla. */
    private record Linea(Producto producto, BigDecimal cantidad, BigDecimal costo) {
        BigDecimal subtotal() {
            return new LineaCompra(producto.id(), cantidad, costo).subtotal();
        }
    }

    private CompraDialog() {}

    /** @return true si la compra se registró */
    static boolean mostrar(AppContext ctx) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Nueva compra");
        dialog.setHeaderText("Registrar una compra de mercadería");

        Label error = Ui.etiquetaError();

        // ---- datos generales
        DatePicker fecha = new DatePicker(LocalDate.now());
        fecha.setEditable(false);

        ComboBox<Proveedor> proveedor = new ComboBox<>();
        proveedor.setMaxWidth(Double.MAX_VALUE);
        cargarProveedores(ctx, proveedor);
        proveedor.setValue(SIN_PROVEEDOR);
        Button nuevoProveedor = new Button("+");
        nuevoProveedor.setOnAction(e -> ProveedorDialog.mostrar(ctx, null).ifPresent(creado -> {
            cargarProveedores(ctx, proveedor);
            proveedor.setValue(creado);
        }));
        HBox.setHgrow(proveedor, Priority.ALWAYS);

        TextField comprobante = new TextField();
        comprobante.setPromptText("Factura o boleta del proveedor (opcional)");
        TextField observacion = new TextField();
        observacion.setPromptText("Observación (opcional)");

        // Con qué se pagó desde la caja. Por defecto: efectivo si hay caja abierta; si no, no registrar.
        ComboBox<Object> pagoCaja = new ComboBox<>();
        pagoCaja.getItems().add(NO_REGISTRAR_EN_CAJA);
        pagoCaja.getItems().addAll((Object[]) MetodoPago.values());
        pagoCaja.setValue(ctx.caja().cajaAbierta().isPresent() ? MetodoPago.EFECTIVO : NO_REGISTRAR_EN_CAJA);
        Label ayudaPago = Ui.subtitulo("Elige \"" + NO_REGISTRAR_EN_CAJA + "\" si se pagó por otro medio o a crédito.");

        GridPane general = new GridPane();
        general.setHgap(10);
        general.setVgap(8);
        general.addRow(0, new Label("Fecha de compra"), fecha);
        general.addRow(1, new Label("Proveedor"), new HBox(6, proveedor, nuevoProveedor));
        general.addRow(2, new Label("Comprobante"), comprobante);
        general.addRow(3, new Label("Observación"), observacion);
        general.addRow(4, new Label("Pagado desde caja"), pagoCaja);
        general.add(ayudaPago, 1, 5);
        GridPane.setHgrow(comprobante, Priority.ALWAYS);
        GridPane.setHgrow(observacion, Priority.ALWAYS);
        general.getColumnConstraints().add(new javafx.scene.layout.ColumnConstraints(110));

        // ---- agregar productos
        ProductoSelector selector = new ProductoSelector(ctx.productos());
        Label infoProducto = Ui.subtitulo("Elige un producto de la lista.");
        TextField cantidad = new TextField();
        cantidad.setPromptText("Cantidad");
        cantidad.setPrefWidth(110);
        TextField costo = new TextField();
        costo.setPromptText("Costo unitario (S/)");
        costo.setPrefWidth(150);
        Button agregar = new Button("Agregar al detalle");
        HBox entrada = new HBox(8, cantidad, costo, agregar);
        entrada.setAlignment(Pos.CENTER_LEFT);

        // Al elegir un producto se sugiere su costo promedio actual (se puede cambiar)
        selector.seleccionado().addListener((obs, anterior, p) -> {
            if (p == null) {
                infoProducto.setText("Elige un producto de la lista.");
                return;
            }
            infoProducto.setText("Stock actual: " + Formato.numero(p.stockActual()) + " " + p.unidadCodigo()
                    + "  ·  Costo promedio: " + Formato.costo(p.costoPromedio()));
            costo.setText(p.costoPromedio().signum() > 0 ? Formato.numero(p.costoPromedio()) : "");
        });

        // ---- detalle
        ObservableList<Linea> lineas = FXCollections.observableArrayList();
        TableView<Linea> tabla = new TableView<>(lineas);
        tabla.setPlaceholder(new Label("Aún no agregaste productos."));
        tabla.setPrefHeight(170);
        tabla.getColumns().add(CompraDialog.<Linea>col("Producto", 330,
                l -> l.producto().codigo() + " · " + l.producto().nombre()));
        tabla.getColumns().add(CompraDialog.<Linea>col("Cantidad", 90,
                l -> Formato.numero(l.cantidad()) + " " + l.producto().unidadCodigo()));
        tabla.getColumns().add(CompraDialog.<Linea>col("Costo unit.", 100, l -> Formato.costo(l.costo())));
        tabla.getColumns().add(CompraDialog.<Linea>col("Subtotal", 100, l -> Formato.dinero(l.subtotal())));

        Button quitar = new Button("Quitar seleccionada");
        quitar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        quitar.setOnAction(e -> lineas.remove(tabla.getSelectionModel().getSelectedItem()));

        Label total = new Label();
        total.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Runnable actualizarTotal = () -> total.setText("Total: " + Formato.dinero(
                lineas.stream().map(Linea::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)));
        lineas.addListener((javafx.collections.ListChangeListener<Linea>) c -> actualizarTotal.run());
        actualizarTotal.run();

        javafx.scene.layout.Region espacio = new javafx.scene.layout.Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(10, quitar, espacio, total);
        pie.setAlignment(Pos.CENTER_LEFT);

        agregar.setOnAction(e -> {
            error.setText("");
            try {
                Producto p = selector.seleccionado().get();
                if (p == null) {
                    throw new NegocioException("Selecciona un producto de la lista.");
                }
                BigDecimal cant = Ui.decimal(cantidad.getText(), "La cantidad");
                BigDecimal cost = Ui.decimal(costo.getText(), "El costo unitario");
                LineaCompra valida = ctx.compras().validarLinea(new LineaCompra(p.id(), cant, cost));
                lineas.add(new Linea(p, valida.cantidad(), valida.costoUnitario()));
                selector.limpiar();
                cantidad.clear();
                costo.clear();
            } catch (NegocioException ex) {
                error.setText(ex.getMessage());
            }
        });

        VBox cuerpo = new VBox(10,
                general,
                new Label("Agregar productos"),
                selector, infoProducto, entrada,
                new Label("Detalle de la compra"),
                tabla, pie, error);
//        cuerpo.setPadding(new Insets(10));
//        cuerpo.setPrefWidth(720);
//        dialog.getDialogPane().setContent(cuerpo);
        cuerpo.setPadding(new Insets(10));

        ScrollPane scroll = new ScrollPane(cuerpo);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(740);

// Alto máximo según la pantalla, dejando espacio para barra de título, encabezado y botones
        double altoPantalla = Screen.getPrimary().getVisualBounds().getHeight();
        scroll.setPrefViewportHeight(Math.min(620, altoPantalla - 220));

        dialog.getDialogPane().setContent(scroll);
        dialog.setResizable(true);

        ButtonType registrar = new ButtonType("Registrar compra", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registrar, ButtonType.CANCEL);

        boolean[] registrada = {false};
        Button botonRegistrar = (Button) dialog.getDialogPane().lookupButton(registrar);
        botonRegistrar.addEventFilter(ActionEvent.ACTION, ev -> {
            error.setText("");
            try {
                if (!cantidad.getText().isBlank()) {
                    throw new NegocioException("Tienes una línea sin agregar. Pulsa \"Agregar al detalle\" "
                            + "o borra la cantidad.");
                }
                Proveedor elegido = proveedor.getValue();
                NuevaCompra nueva = new NuevaCompra(
                        (elegido == null || elegido.id() == 0) ? null : elegido.id(),
                        fecha.getValue(),
                        comprobante.getText(),
                        observacion.getText(),
                        lineas.stream()
                                .map(l -> new LineaCompra(l.producto().id(), l.cantidad(), l.costo()))
                                .toList(),
                        pagoCaja.getValue() instanceof MetodoPago m ? m : null);
                ctx.compras().registrar(Sesion.actual(), nueva);
                registrada[0] = true;
            } catch (NegocioException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                error.setText("Ocurrió un error inesperado. Revisa la conexión con la base de datos.");
                ev.consume();
            }
        });

        Platform.runLater(selector::enfocar);
        dialog.showAndWait();
        return registrada[0];
    }

    private static void cargarProveedores(AppContext ctx, ComboBox<Proveedor> combo) {
        combo.getItems().setAll(SIN_PROVEEDOR);
        combo.getItems().addAll(ctx.proveedores().activos());
    }

    private static <T> TableColumn<T, String> col(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(valor.apply(cd.getValue())));
        return c;
    }
}
