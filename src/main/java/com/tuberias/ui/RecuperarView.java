package com.tuberias.ui;

import com.tuberias.service.AuthService;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/** Restablece la contraseña usando el código de recuperación. */
final class RecuperarView {

    private RecuperarView() {}

    static Parent crear(Navegador nav, AuthService auth) {
        TextField usuario = new TextField();
        usuario.setPromptText("Usuario");
        TextField codigo = new TextField();
        codigo.setPromptText("Código de recuperación (XXXX-XXXX-XXXX)");
        PasswordField nueva = new PasswordField();
        nueva.setPromptText("Nueva contraseña (mínimo " + AuthService.MIN_PASSWORD + " caracteres)");
        PasswordField confirmacion = new PasswordField();
        confirmacion.setPromptText("Repite la nueva contraseña");
        Label error = Ui.etiquetaError();

        Button restablecer = new Button("Restablecer contraseña");
        restablecer.setDefaultButton(true);
        restablecer.setMaxWidth(Double.MAX_VALUE);
        restablecer.setOnAction(e -> Ui.manejar(error, () -> {
            String nuevoCodigo = auth.recuperarPassword(
                    usuario.getText(), codigo.getText(), nueva.getText(), confirmacion.getText());
            CodigoRecuperacionDialog.mostrar(nuevoCodigo,
                    "Contraseña restablecida. Guarda tu NUEVO código (el anterior ya no sirve)");
            nav.mostrarLogin();
        }));

        Hyperlink volver = new Hyperlink("Volver al inicio de sesión");
        volver.setOnAction(e -> nav.mostrarLogin());

        return Ui.tarjeta(
                Ui.titulo("Recuperar contraseña"),
                Ui.subtitulo("Ingresa tu usuario y el código de recuperación que se te mostró al crear la cuenta."),
                usuario, codigo, nueva, confirmacion, restablecer, error, volver);
    }
}
