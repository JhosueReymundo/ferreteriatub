package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.CompraDetalle;
import com.tuberias.model.CompraResumen;
import com.tuberias.model.Formato;
import com.tuberias.model.LineaDetalleCompra;
import com.tuberias.service.NegocioException;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/** Muestra el detalle de una compra ya registrada (solo lectura). */
final class CompraDetalleDialog {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CompraDetalleDialog() {}

    static void mostrar(AppContext ctx, long compraId) {
        CompraDetalle detalle = ctx.compras().detalle(compraId)
                .orElseThrow(() -> new NegocioException("La compra ya no existe."));
        CompraResumen c = detalle.resumen();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Detalle de la compra");
        dialog.setHeaderText("Compra #" + c.id() + (c.anulada() ? "  (ANULADA)" : ""));

        StringBuilder datos = new StringBuilder();
        datos.append("Fecha: ").append(FECHA.format(c.fecha())).append('\n');
        datos.append("Proveedor: ").append(c.proveedor() == null ? "(sin proveedor)" : c.proveedor()).append('\n');
        if (c.comprobante() != null) {
            datos.append("Comprobante: ").append(c.comprobante()).append('\n');
        }
        datos.append("Registrada por: ").append(c.usuario());
        if (detalle.observacion() != null) {
            datos.append("\nObservación: ").append(detalle.observacion());
        }
        Label etiquetaDatos = new Label(datos.toString());

        TableView<LineaDetalleCompra> tabla = new TableView<>(
                FXCollections.observableArrayList(detalle.lineas()));
        tabla.getColumns().add(CompraDetalleDialog.<LineaDetalleCompra>col("Producto", 320,
                l -> l.codigo() + " · " + l.nombre()));
        tabla.getColumns().add(CompraDetalleDialog.<LineaDetalleCompra>col("Cantidad", 90,
                l -> Formato.numero(l.cantidad()) + " " + l.unidad()));
        tabla.getColumns().add(CompraDetalleDialog.<LineaDetalleCompra>col("Costo unit.", 100,
                l -> Formato.costo(l.costoUnitario())));
        tabla.getColumns().add(CompraDetalleDialog.<LineaDetalleCompra>col("Subtotal", 100,
                l -> Formato.dinero(l.subtotal())));
        tabla.setPrefHeight(220);

        Label total = new Label("Total: " + Formato.dinero(c.total()));
        total.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        VBox cuerpo = new VBox(10, etiquetaDatos, tabla, total);
        if (c.anulada()) {
            Label anulada = new Label("Anulada. Motivo: "
                    + (detalle.motivoAnulacion() == null ? "(no indicado)" : detalle.motivoAnulacion()));
            anulada.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
            anulada.setWrapText(true);
            cuerpo.getChildren().add(anulada);
        }
        cuerpo.setPrefWidth(640);
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
