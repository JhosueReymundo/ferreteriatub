package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.Cliente;
import com.tuberias.model.Formato;
import com.tuberias.model.LineaVenta;
import com.tuberias.model.MetodoPago;
import com.tuberias.model.NuevaVenta;
import com.tuberias.model.Producto;
import com.tuberias.model.VentaRegistrada;
import com.tuberias.service.NegocioException;
import com.tuberias.service.Sesion;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.function.Function;

/** Formulario para registrar una venta: cliente y pago (opcionales/obligatorio) + detalle de productos. */
final class VentaDialog {

    private static final Cliente SIN_CLIENTE = new Cliente(0, "(sin cliente)", null, null, true);

    /** Una línea del detalle tal como se ve en pantalla. */
    private record Linea(Producto producto, BigDecimal cantidad, BigDecimal precio) {
        BigDecimal subtotal() {
            return new LineaVenta(producto.id(), cantidad, precio).subtotal();
        }
    }

    private VentaDialog() {}

    /** @return true si la venta se registró */
    static boolean mostrar(AppContext ctx) {
        boolean admin = Sesion.actual().esAdmin();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Nueva venta");
        dialog.setHeaderText("Registrar una venta");

        Label error = Ui.etiquetaError();

        // ---- datos generales
        ComboBox<Cliente> cliente = new ComboBox<>();
        cliente.setMaxWidth(Double.MAX_VALUE);
        cargarClientes(ctx, cliente);
        cliente.setValue(SIN_CLIENTE);
        Button nuevoCliente = new Button("+");
        nuevoCliente.setOnAction(e -> ClienteDialog.mostrar(ctx, null).ifPresent(creado -> {
            cargarClientes(ctx, cliente);
            cliente.setValue(creado);
        }));
        HBox.setHgrow(cliente, Priority.ALWAYS);

        ComboBox<MetodoPago> pago = new ComboBox<>(FXCollections.observableArrayList(MetodoPago.values()));
        pago.setValue(MetodoPago.EFECTIVO);
        TextField observacion = new TextField();
        observacion.setPromptText("Observación (opcional)");

        GridPane general = new GridPane();
        general.setHgap(10);
        general.setVgap(8);
        general.addRow(0, new Label("Cliente"), new HBox(6, cliente, nuevoCliente));
        general.addRow(1, new Label("Método de pago"), pago);
        general.addRow(2, new Label("Observación"), observacion);
        GridPane.setHgrow(observacion, Priority.ALWAYS);
        general.getColumnConstraints().add(new ColumnConstraints(110));

        // ---- agregar productos (solo los que tienen stock)
        ProductoSelector selector = new ProductoSelector(ctx.productos(), true);
        Label infoProducto = Ui.subtitulo("Elige un producto de la lista.");
        TextField cantidad = new TextField();
        cantidad.setPromptText("Cantidad");
        cantidad.setPrefWidth(110);
        TextField precio = new TextField();
        precio.setPromptText("Precio unitario (S/)");
        precio.setPrefWidth(150);
        Button agregar = new Button("Agregar al detalle");
        HBox entrada = new HBox(8, cantidad, precio, agregar);
        entrada.setAlignment(Pos.CENTER_LEFT);

        // El precio sugerido es solo un punto de partida: se puede cambiar (rebaja, descuento, etc.)
        selector.seleccionado().addListener((obs, anterior, p) -> {
            if (p == null) {
                infoProducto.setText("Elige un producto de la lista.");
                return;
            }
            String info = "Stock disponible: " + Formato.numero(p.stockActual()) + " " + p.unidadCodigo();
            if (p.precioSugerido() != null) {
                info += "  ·  Precio sugerido: " + Formato.dinero(p.precioSugerido());
            }
            if (admin) {
                info += "  ·  Costo promedio: " + Formato.costo(p.costoPromedio());
            }
            infoProducto.setText(info);
            precio.setText(p.precioSugerido() != null ? Formato.numero(p.precioSugerido()) : "");
        });

        // ---- detalle
        ObservableList<Linea> lineas = FXCollections.observableArrayList();
        TableView<Linea> tabla = new TableView<>(lineas);
        tabla.setPlaceholder(new Label("Aún no agregaste productos."));
        tabla.setPrefHeight(160);
        tabla.getColumns().add(VentaDialog.<Linea>col("Producto", 330,
                l -> l.producto().codigo() + " · " + l.producto().nombre()));
        tabla.getColumns().add(VentaDialog.<Linea>col("Cantidad", 90,
                l -> Formato.numero(l.cantidad()) + " " + l.producto().unidadCodigo()));
        tabla.getColumns().add(VentaDialog.<Linea>col("Precio unit.", 100, l -> Formato.dinero(l.precio())));
        tabla.getColumns().add(VentaDialog.<Linea>col("Subtotal", 100, l -> Formato.dinero(l.subtotal())));

        Button quitar = new Button("Quitar seleccionada");
        quitar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        quitar.setOnAction(e -> lineas.remove(tabla.getSelectionModel().getSelectedItem()));

        Label total = new Label();
        total.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Runnable actualizarTotal = () -> total.setText("Total: " + Formato.dinero(
                lineas.stream().map(Linea::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)));
        lineas.addListener((ListChangeListener<Linea>) c -> actualizarTotal.run());
        actualizarTotal.run();

        Region espacio = new Region();
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
                BigDecimal prec = Ui.decimal(precio.getText(), "El precio");
                LineaVenta valida = ctx.ventas().validarLinea(new LineaVenta(p.id(), cant, prec));

                // Aviso inmediato si, sumando lo ya agregado, se pasa del stock
                BigDecimal yaAgregado = lineas.stream()
                        .filter(l -> l.producto().id() == p.id())
                        .map(Linea::cantidad)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (yaAgregado.add(valida.cantidad()).compareTo(p.stockActual()) > 0) {
                    throw new NegocioException("Stock insuficiente de \"" + p.nombre() + "\": hay "
                            + Formato.numero(p.stockActual()) + " " + p.unidadCodigo()
                            + (yaAgregado.signum() > 0 ? " y ya agregaste " + Formato.numero(yaAgregado) : "")
                            + ".");
                }
                lineas.add(new Linea(p, valida.cantidad(), valida.precioUnitario()));
                selector.limpiar();
                cantidad.clear();
                precio.clear();
            } catch (NegocioException ex) {
                error.setText(ex.getMessage());
            }
        });

        VBox cuerpo = new VBox(10,
                general,
                new Label("Agregar productos"),
                selector, infoProducto, entrada,
                new Label("Detalle de la venta"),
                tabla, pie, error);
        cuerpo.setPadding(new Insets(10));
        cuerpo.setPrefWidth(720);
        dialog.getDialogPane().setContent(cuerpo);

        ButtonType registrar = new ButtonType("Registrar venta", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registrar, ButtonType.CANCEL);

        VentaRegistrada[] resultado = {null};
        Button botonRegistrar = (Button) dialog.getDialogPane().lookupButton(registrar);
        botonRegistrar.addEventFilter(ActionEvent.ACTION, ev -> {
            error.setText("");
            try {
                if (!cantidad.getText().isBlank()) {
                    throw new NegocioException("Tienes una línea sin agregar. Pulsa \"Agregar al detalle\" "
                            + "o borra la cantidad.");
                }
                Cliente elegido = cliente.getValue();
                NuevaVenta nueva = new NuevaVenta(
                        (elegido == null || elegido.id() == 0) ? null : elegido.id(),
                        pago.getValue(),
                        observacion.getText(),
                        lineas.stream()
                                .map(l -> new LineaVenta(l.producto().id(), l.cantidad(), l.precio()))
                                .toList());

                // Vender bajo el costo se permite (liquidaciones), pero se pide confirmar
                var bajoCosto = ctx.ventas().productosBajoCosto(nueva);
                if (!bajoCosto.isEmpty()) {
                    Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                            "Estos productos se venden por debajo de su costo:\n• "
                                    + String.join("\n• ", bajoCosto) + "\n\n¿Registrar la venta de todos modos?",
                            ButtonType.YES, ButtonType.NO);
                    confirmar.setHeaderText("Precio menor al costo");
                    if (confirmar.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                        ev.consume();
                        return;
                    }
                }
                resultado[0] = ctx.ventas().registrar(Sesion.actual(), nueva);
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

        if (resultado[0] == null) {
            return false;
        }
        // La venta ya está guardada; ahora se avisa de lo que quedó con poco stock
        Ui.avisarStockBajo("Venta registrada. Estos productos están en su stock mínimo", resultado[0].stockBajo());
        return true;
    }

    private static void cargarClientes(AppContext ctx, ComboBox<Cliente> combo) {
        combo.getItems().setAll(SIN_CLIENTE);
        combo.getItems().addAll(ctx.clientes().activos());
    }

    private static <T> TableColumn<T, String> col(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(valor.apply(cd.getValue())));
        return c;
    }
}
