package com.tuberias.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

/**
 * Muestra el código de recuperación una sola vez. No se puede cerrar
 * hasta que la persona confirme que lo guardó.
 */
final class CodigoRecuperacionDialog {

    private CodigoRecuperacionDialog() {}

    static void mostrar(String codigo, String encabezado) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Código de recuperación");
        dialog.setHeaderText(encabezado);

        Label explicacion = new Label(
                "Es la única forma de recuperar tu contraseña si la olvidas. "
                + "Se muestra una sola vez: anótalo y guárdalo en un lugar seguro.");
        explicacion.setWrapText(true);
        explicacion.setMaxWidth(380);

        Label textoCodigo = new Label(codigo);
        textoCodigo.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 24px; -fx-font-weight: bold;");

        Button copiar = new Button("Copiar código");
        copiar.setOnAction(e -> {
            ClipboardContent contenido = new ClipboardContent();
            contenido.putString(codigo);
            Clipboard.getSystemClipboard().setContent(contenido);
            copiar.setText("¡Copiado!");
        });

        CheckBox guardado = new CheckBox("Ya guardé este código en un lugar seguro");

        VBox cuerpo = new VBox(12, explicacion, textoCodigo, copiar, guardado);
        cuerpo.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(cuerpo);

        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.getDialogPane().lookupButton(ButtonType.OK)
                .disableProperty().bind(guardado.selectedProperty().not());

        dialog.showAndWait();
    }
}
