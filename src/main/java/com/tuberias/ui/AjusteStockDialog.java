package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.Formato;
import com.tuberias.model.Producto;
import com.tuberias.service.NegocioException;
import com.tuberias.service.Sesion;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Corrección manual de stock (merma, conteo físico). Solo para el administrador. */
final class AjusteStockDialog {

    private AjusteStockDialog() {}

    /** @return true si se registró el ajuste */
    static boolean mostrar(AppContext ctx, Producto producto) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Ajustar stock");
        dialog.setHeaderText(producto.nombre() + "\nStock actual: "
                + Formato.numero(producto.stockActual()) + " " + producto.unidadCodigo());

        Label explicacion = Ui.subtitulo("Úsalo solo para corregir diferencias (merma, conteo físico). "
                + "Las compras y ventas actualizan el stock automáticamente.");
        explicacion.setMaxWidth(380);

        ToggleGroup tipo = new ToggleGroup();
        RadioButton entrada = new RadioButton("Entrada (+)");
        RadioButton salida = new RadioButton("Salida (−)");
        entrada.setToggleGroup(tipo);
        salida.setToggleGroup(tipo);
        entrada.setSelected(true);

        TextField cantidad = new TextField();
        cantidad.setPromptText("Cantidad");
        TextField costo = new TextField(Formato.numero(producto.costoPromedio()));
        costo.setPromptText("Costo unitario (S/)");
        costo.disableProperty().bind(salida.selectedProperty());
        TextField motivo = new TextField();
        motivo.setPromptText("Motivo (ej. conteo físico, merma)");
        Label error = Ui.etiquetaError();

        VBox cuerpo = new VBox(10,
                explicacion,
                new HBox(16, entrada, salida),
                cantidad,
                new Label("Costo unitario (solo en entradas):"),
                costo,
                motivo,
                error);
        cuerpo.setPadding(new Insets(10));
        cuerpo.setPrefWidth(400);
        dialog.getDialogPane().setContent(cuerpo);

        ButtonType registrar = new ButtonType("Registrar ajuste", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registrar, ButtonType.CANCEL);

        boolean[] registrado = {false};
        Button boton = (Button) dialog.getDialogPane().lookupButton(registrar);
        boton.addEventFilter(ActionEvent.ACTION, ev -> {
            error.setText("");
            try {
                boolean esEntrada = entrada.isSelected();
                ctx.productos().ajustarStock(
                        Sesion.actual(),
                        producto.id(),
                        esEntrada,
                        Ui.decimal(cantidad.getText(), "La cantidad"),
                        esEntrada ? Ui.decimal(costo.getText(), "El costo unitario") : null,
                        motivo.getText());
                registrado[0] = true;
            } catch (NegocioException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                error.setText("Ocurrió un error inesperado. Revisa la conexión con la base de datos.");
                ev.consume();
            }
        });

        Platform.runLater(cantidad::requestFocus);
        dialog.showAndWait();
        return registrado[0];
    }
}
