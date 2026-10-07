package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.ProductoStockBajo;
import com.tuberias.model.Usuario;
import com.tuberias.service.Sesion;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.List;

/** Cambia de pantalla dentro de la única ventana de la aplicación. */
public class Navegador {

    private final Stage stage;
    private final AppContext ctx;

    public Navegador(Stage stage, AppContext ctx) {
        this.stage = stage;
        this.ctx = ctx;
    }

    /** Primera vez: configuración inicial. Después: login. */
    public void mostrarInicio() {
        if (ctx.auth().necesitaConfiguracionInicial()) {
            mostrarSetup();
        } else {
            mostrarLogin();
        }
    }

    public void mostrarSetup() {
        mostrar(SetupView.crear(this, ctx.auth()), "Configuración inicial", 440, 540);
    }

    public void mostrarLogin() {
        mostrar(LoginView.crear(this, ctx.auth()), "Iniciar sesión", 440, 420);
    }

    public void mostrarRecuperar() {
        mostrar(RecuperarView.crear(this, ctx.auth()), "Recuperar contraseña", 440, 540);
    }

    public void mostrarPrincipal() {
        Usuario usuario = Sesion.actual();
        mostrar(PrincipalView.crear(this, ctx), "Principal", 1100, 680);
        Platform.runLater(() -> {
            if (usuario.debeCambiarPassword()) {
                CambiarPasswordDialog.mostrar(ctx.auth(), usuario, true);
            }
            avisarStockBajo();
        });
    }

    public void cerrarSesion() {
        Sesion.cerrar();
        mostrarLogin();
    }

    private void mostrar(Parent raiz, String titulo, double ancho, double alto) {
        stage.setScene(new Scene(raiz, ancho, alto));
        stage.setTitle("Sistema de Tubos - " + titulo);
        stage.sizeToScene();
        stage.centerOnScreen();
    }

    /** Al iniciar sesión avisa de los productos que llegaron a su stock mínimo. */
    private void avisarStockBajo() {
        List<ProductoStockBajo> enAlerta;
        try {
            enAlerta = ctx.stock().listarStockBajo();
        } catch (RuntimeException e) {
            e.printStackTrace();
            return; // el aviso es un extra; no debe impedir usar el sistema
        }
        Ui.avisarStockBajo(enAlerta.size() + " producto(s) llegaron a su stock mínimo", enAlerta);
    }
}
