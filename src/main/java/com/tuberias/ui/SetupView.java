package com.tuberias.ui;

import com.tuberias.service.AuthService;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/** Primer arranque: crea la cuenta del administrador. */
final class SetupView {

    private SetupView() {}

    static Parent crear(Navegador nav, AuthService auth) {
        TextField nombre = new TextField();
        nombre.setPromptText("Nombre completo");
        TextField usuario = new TextField();
        usuario.setPromptText("Usuario (ej. admin)");
        PasswordField password = new PasswordField();
        password.setPromptText("Contraseña (mínimo " + AuthService.MIN_PASSWORD + " caracteres)");
        PasswordField confirmacion = new PasswordField();
        confirmacion.setPromptText("Repite la contraseña");
        Label error = Ui.etiquetaError();

        Button botonCrear = new Button("Crear cuenta");
        botonCrear.setDefaultButton(true);
        botonCrear.setMaxWidth(Double.MAX_VALUE);
        botonCrear.setOnAction(e -> Ui.manejar(error, () -> {
            String codigo = auth.configuracionInicial(
                    usuario.getText(), nombre.getText(), password.getText(), confirmacion.getText());
            CodigoRecuperacionDialog.mostrar(codigo, "Cuenta creada. Guarda tu código de recuperación");
            nav.mostrarLogin();
        }));

        return Ui.tarjeta(
                Ui.titulo("Bienvenido"),
                Ui.subtitulo("Es la primera vez que se abre el sistema. Crea la cuenta del administrador."),
                nombre, usuario, password, confirmacion, botonCrear, error);
    }
}
