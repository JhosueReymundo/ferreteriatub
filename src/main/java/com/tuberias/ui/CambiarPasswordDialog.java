package com.tuberias.ui;

import com.tuberias.model.Usuario;
import com.tuberias.service.AuthException;
import com.tuberias.service.AuthService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

final class CambiarPasswordDialog {

    private CambiarPasswordDialog() {}

    /**
     * @param obligatorio si es true no hay botón Cancelar: la persona debe cambiarla para continuar.
     */
    static void mostrar(AuthService auth, Usuario usuario, boolean obligatorio) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Cambiar contraseña");
        dialog.setHeaderText(obligatorio
                ? "Debes cambiar tu contraseña antes de continuar"
                : "Cambiar contraseña");

        PasswordField actual = new PasswordField();
        actual.setPromptText("Contraseña actual");
        PasswordField nueva = new PasswordField();
        nueva.setPromptText("Nueva contraseña (mínimo " + AuthService.MIN_PASSWORD + " caracteres)");
        PasswordField confirmacion = new PasswordField();
        confirmacion.setPromptText("Repite la nueva contraseña");
        Label error = Ui.etiquetaError();

        VBox cuerpo = new VBox(10, actual, nueva, confirmacion, error);
        cuerpo.setPadding(new Insets(10));
        cuerpo.setPrefWidth(340);
        dialog.getDialogPane().setContent(cuerpo);

        ButtonType cambiar = new ButtonType("Cambiar contraseña", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(cambiar);
        if (!obligatorio) {
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        }

        boolean[] exito = {false};
        Button botonCambiar = (Button) dialog.getDialogPane().lookupButton(cambiar);
        // Si hay error se consume el evento y el diálogo no se cierra
        botonCambiar.addEventFilter(ActionEvent.ACTION, ev -> {
            error.setText("");
            try {
                auth.cambiarPassword(usuario.id(), actual.getText(), nueva.getText(), confirmacion.getText());
                exito[0] = true;
            } catch (AuthException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                error.setText("Ocurrió un error inesperado. Revisa la conexión con la base de datos.");
                ev.consume();
            }
        });

        Platform.runLater(actual::requestFocus);
        dialog.showAndWait();

        if (exito[0]) {
            new Alert(Alert.AlertType.INFORMATION, "Tu contraseña fue actualizada.").showAndWait();
        }
    }
}
