package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.Formato;
import com.tuberias.model.MovimientoInventario;
import com.tuberias.model.Producto;
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
import java.util.List;
import java.util.function.Function;

/** Historial (kardex) de entradas y salidas de un producto. */
final class MovimientosDialog {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private MovimientosDialog() {}

    static void mostrar(AppContext ctx, Producto producto) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Movimientos de inventario");
        dialog.setHeaderText(producto.nombre() + "\nStock actual: "
                + Formato.numero(producto.stockActual()) + " " + producto.unidadCodigo());

        TableView<MovimientoInventario> tabla = new TableView<>();
        tabla.getColumns().add(columna("Fecha", 130, m -> FECHA.format(m.fecha())));
        tabla.getColumns().add(columna("Tipo", 140, MovimientoInventario::tipoEtiqueta));
        tabla.getColumns().add(columna("Cantidad", 80, m -> signo(m)));
        tabla.getColumns().add(columna("Costo unit.", 90, m -> Formato.dinero(m.costoUnitario())));
        tabla.getColumns().add(columna("Usuario", 90, MovimientoInventario::usuario));
        tabla.getColumns().add(columna("Observación", 200,
                m -> m.observacion() == null ? "" : m.observacion()));
        tabla.setPrefSize(760, 360);

        Label aviso = Ui.etiquetaError();
        try {
            List<MovimientoInventario> lista = ctx.productos().movimientos(producto.id());
            tabla.setItems(FXCollections.observableArrayList(lista));
            tabla.setPlaceholder(new Label("Este producto aún no tiene movimientos."));
        } catch (RuntimeException e) {
            e.printStackTrace();
            aviso.setText("No se pudieron cargar los movimientos. Revisa la conexión con la base de datos.");
        }

        dialog.getDialogPane().setContent(new VBox(8, tabla, aviso));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private static String signo(MovimientoInventario m) {
        String texto = Formato.numero(m.cantidad());
        return m.cantidad().signum() > 0 ? "+" + texto : texto;
    }

    private static TableColumn<MovimientoInventario, String> columna(
            String titulo, double ancho, Function<MovimientoInventario, String> valor) {
        TableColumn<MovimientoInventario, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(valor.apply(cd.getValue())));
        return c;
    }
}
