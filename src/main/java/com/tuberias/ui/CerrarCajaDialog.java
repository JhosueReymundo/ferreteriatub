package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.CajaSesion;
import com.tuberias.model.Formato;
import com.tuberias.service.NegocioException;
import com.tuberias.service.Sesion;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

/**
 * Cierra la caja. Primero se cuenta el efectivo y recién después el sistema muestra lo esperado,
 * para que el conteo sea honesto.
 */
final class CerrarCajaDialog {

    private CerrarCajaDialog() {}

    /** @return true si la caja se cerró */
    static boolean mostrar(AppContext ctx) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Cerrar caja");
        dialog.setHeaderText("Cerrar la caja");

        TextField contado = new TextField();
        contado.setPromptText("Efectivo contado (S/)");
        TextField observacion = new TextField();
        observacion.setPromptText("Observación (opcional)");
        Label error = Ui.etiquetaError();

        VBox cuerpo = new VBox(10,
                new Label("Efectivo que hay ahora en el cajón (S/) *"), contado,
                Ui.subtitulo("Cuenta el dinero en efectivo. No incluyas Yape/Plin, transferencias ni tarjeta. "
                        + "El sistema comparará con lo que esperaba."),
                new Label("Observación"), observacion, error);
        cuerpo.setPadding(new Insets(10));
        cuerpo.setPrefWidth(400);
        dialog.getDialogPane().setContent(cuerpo);

        ButtonType cerrar = new ButtonType("Cerrar caja", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(cerrar, ButtonType.CANCEL);

        CajaSesion[] cerrada = {null};
        Button boton = (Button) dialog.getDialogPane().lookupButton(cerrar);
        boton.addEventFilter(ActionEvent.ACTION, ev -> {
            error.setText("");
            try {
                BigDecimal monto = Ui.decimal(contado.getText(), "El efectivo contado");
                cerrada[0] = ctx.caja().cerrar(Sesion.actual(), monto, observacion.getText());
            } catch (NegocioException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                error.setText("Ocurrió un error inesperado. Revisa la conexión con la base de datos.");
                ev.consume();
            }
        });

        Platform.runLater(contado::requestFocus);
        dialog.showAndWait();

        if (cerrada[0] == null) {
            return false;
        }
        CajaSesion c = cerrada[0];
        BigDecimal dif = c.diferencia();
        String resultado = dif.signum() == 0 ? "La caja cuadra exactamente."
                : dif.signum() < 0 ? "FALTAN " + Formato.dinero(dif.negate()) + "."
                : "SOBRAN " + Formato.dinero(dif) + ".";
        Alert info = new Alert(dif.signum() == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        info.setTitle("Caja cerrada");
        info.setHeaderText("Caja #" + c.id() + " cerrada");
        info.setContentText("Efectivo esperado: " + Formato.dinero(c.montoEsperado())
                + "\nEfectivo contado: " + Formato.dinero(c.montoCierreReal())
                + "\n\n" + resultado);
        info.showAndWait();
        return true;
    }
}
