package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.Proveedor;
import com.tuberias.service.NegocioException;
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

import java.util.Optional;

/** Formulario de proveedor: solo nombre y RUC (opcional). */
final class ProveedorDialog {

    private ProveedorDialog() {}

    /**
     * @param existente proveedor a editar, o null para crear uno nuevo
     * @return el proveedor guardado, o vacío si se canceló
     */
    static Optional<Proveedor> mostrar(AppContext ctx, Proveedor existente) {
        boolean nuevo = existente == null;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(nuevo ? "Nuevo proveedor" : "Editar proveedor");
        dialog.setHeaderText(nuevo ? "Registrar un proveedor" : "Editar: " + existente.nombre());

        TextField nombre = new TextField(nuevo ? "" : existente.nombre());
        nombre.setPromptText("Nombre o razón social");
        TextField ruc = new TextField(nuevo || existente.ruc() == null ? "" : existente.ruc());
        ruc.setPromptText("RUC (opcional, 11 dígitos)");
        Label error = Ui.etiquetaError();

        VBox cuerpo = new VBox(10, new Label("Nombre *"), nombre, new Label("RUC"), ruc, error);
        cuerpo.setPadding(new Insets(10));
        cuerpo.setPrefWidth(360);
        dialog.getDialogPane().setContent(cuerpo);

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);

        Proveedor[] resultado = {null};
        Button botonGuardar = (Button) dialog.getDialogPane().lookupButton(guardar);
        botonGuardar.addEventFilter(ActionEvent.ACTION, ev -> {
            error.setText("");
            try {
                if (nuevo) {
                    resultado[0] = ctx.proveedores().crear(nombre.getText(), ruc.getText());
                } else {
                    ctx.proveedores().actualizar(existente.id(), nombre.getText(), ruc.getText());
                    resultado[0] = existente;
                }
            } catch (NegocioException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                error.setText("Ocurrió un error inesperado. Revisa la conexión con la base de datos.");
                ev.consume();
            }
        });

        Platform.runLater(nombre::requestFocus);
        dialog.showAndWait();
        return Optional.ofNullable(resultado[0]);
    }
}
