package com.tuberias.ui;

import com.tuberias.service.AuthService;
import com.tuberias.service.Sesion;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

final class LoginView {

    private LoginView() {}

    static Parent crear(Navegador nav, AuthService auth) {
        TextField usuario = new TextField();
        usuario.setPromptText("Usuario");
        PasswordField password = new PasswordField();
        password.setPromptText("Contraseña");
        Label error = Ui.etiquetaError();

        Button entrar = new Button("Ingresar");
        entrar.setDefaultButton(true);
        entrar.setMaxWidth(Double.MAX_VALUE);
        entrar.setOnAction(e -> {
            Ui.manejar(error, () -> {
                Sesion.iniciar(auth.login(usuario.getText(), password.getText()));
                nav.mostrarPrincipal();
            });
            if (!error.getText().isEmpty()) {
                password.clear();
            }
        });

        Hyperlink olvide = new Hyperlink("¿Olvidaste tu contraseña?");
        olvide.setOnAction(e -> nav.mostrarRecuperar());

        return Ui.tarjeta(
                Ui.titulo("Sistema de Tubos"),
                Ui.subtitulo("Inicia sesión para continuar"),
                usuario, password, entrar, error, olvide);
    }
}
