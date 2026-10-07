package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.Formato;
import com.tuberias.model.LineaDetalleVenta;
import com.tuberias.model.VentaDetalle;
import com.tuberias.model.VentaResumen;
import com.tuberias.service.NegocioException;
import com.tuberias.service.Sesion;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/** Muestra el detalle de una venta ya registrada (solo lectura). Costo y utilidad: solo administrador. */
final class VentaDetalleDialog {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private VentaDetalleDialog() {}

    static void mostrar(AppContext ctx, long ventaId) {
        VentaDetalle detalle = ctx.ventas().detalle(ventaId)
                .orElseThrow(() -> new NegocioException("La venta ya no existe."));
        VentaResumen v = detalle.resumen();
        boolean admin = Sesion.actual().esAdmin();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Detalle de la venta");
        dialog.setHeaderText("Venta #" + v.id() + (v.anulada() ? "  (ANULADA)" : ""));

        StringBuilder datos = new StringBuilder();
        datos.append("Fecha: ").append(FECHA.format(v.fecha())).append('\n');
        datos.append("Cliente: ").append(v.cliente() == null ? "(sin cliente)" : v.cliente()).append('\n');
        datos.append("Método de pago: ").append(v.metodoPago()).append('\n');
        datos.append("Registrada por: ").append(v.usuario());
        if (detalle.observacion() != null) {
            datos.append("\nObservación: ").append(detalle.observacion());
        }
        Label etiquetaDatos = new Label(datos.toString());

        TableView<LineaDetalleVenta> tabla = new TableView<>(
                FXCollections.observableArrayList(detalle.lineas()));
        tabla.getColumns().add(VentaDetalleDialog.<LineaDetalleVenta>col("Producto", 300,
                l -> l.codigo() + " · " + l.nombre()));
        tabla.getColumns().add(VentaDetalleDialog.<LineaDetalleVenta>col("Cantidad", 90,
                l -> Formato.numero(l.cantidad()) + " " + l.unidad()));
        tabla.getColumns().add(VentaDetalleDialog.<LineaDetalleVenta>col("Precio unit.", 100,
                l -> Formato.dinero(l.precioUnitario())));
        if (admin) {
            tabla.getColumns().add(VentaDetalleDialog.<LineaDetalleVenta>col("Costo unit.", 100,
                    l -> Formato.costo(l.costoUnitario())));
        }
        tabla.getColumns().add(VentaDetalleDialog.<LineaDetalleVenta>col("Subtotal", 100,
                l -> Formato.dinero(l.subtotal())));
        tabla.setPrefHeight(220);

        Label total = new Label("Total: " + Formato.dinero(v.total()));
        total.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        VBox cuerpo = new VBox(10, etiquetaDatos, tabla, total);
        if (admin) {
            cuerpo.getChildren().add(Ui.subtitulo("Utilidad de esta venta (precio cobrado - costo promedio de ese momento): "
                    + Formato.dinero(detalle.utilidadTotal())));
        }
        if (v.anulada()) {
            Label anulada = new Label("Anulada. Motivo: "
                    + (detalle.motivoAnulacion() == null ? "(no indicado)" : detalle.motivoAnulacion()));
            anulada.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
            anulada.setWrapText(true);
            cuerpo.getChildren().add(anulada);
        }
        cuerpo.setPrefWidth(700);
        dialog.getDialogPane().setContent(cuerpo);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private static <T> TableColumn<T, String> col(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(valor.apply(cd.getValue())));
        return c;
    }
}
