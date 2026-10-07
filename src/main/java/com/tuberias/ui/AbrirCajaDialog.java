package com.tuberias.ui;

import com.tuberias.AppContext;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Abre la caja con el efectivo inicial. */
final class AbrirCajaDialog {

    private AbrirCajaDialog() {}

    /** @return true si la caja se abrió */
    static boolean mostrar(AppContext ctx) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Abrir caja");
        dialog.setHeaderText("Abrir la caja del día");

        TextField monto = new TextField("0");
        monto.setPromptText("Efectivo inicial (S/)");
        TextField observacion = new TextField();
        observacion.setPromptText("Observación (opcional)");
        Label error = Ui.etiquetaError();

        VBox cuerpo = new VBox(10,
                new Label("Efectivo con el que abres la caja (S/) *"), monto,
                Ui.subtitulo("Es el dinero que ya hay en el cajón, por ejemplo para dar vuelto. Puede ser 0."),
                new Label("Observación"), observacion, error);
        cuerpo.setPadding(new Insets(10));
        cuerpo.setPrefWidth(380);
        dialog.getDialogPane().setContent(cuerpo);

        ButtonType abrir = new ButtonType("Abrir caja", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(abrir, ButtonType.CANCEL);

        boolean[] abierta = {false};
        Button boton = (Button) dialog.getDialogPane().lookupButton(abrir);
        boton.addEventFilter(ActionEvent.ACTION, ev -> {
            error.setText("");
            try {
                ctx.caja().abrir(Sesion.actual(), Ui.decimal(monto.getText(), "El monto"), observacion.getText());
                abierta[0] = true;
            } catch (NegocioException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                error.setText("Ocurrió un error inesperado. Revisa la conexión con la base de datos.");
                ev.consume();
            }
        });

        Platform.runLater(() -> {
            monto.requestFocus();
            monto.selectAll();
        });
        dialog.showAndWait();
        return abierta[0];
    }
}
