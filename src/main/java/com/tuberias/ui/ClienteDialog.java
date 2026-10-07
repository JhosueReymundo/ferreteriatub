package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.Cliente;
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

/** Formulario de cliente: nombre obligatorio; documento (DNI o RUC) y teléfono opcionales. */
final class ClienteDialog {

    private ClienteDialog() {}

    /**
     * @param existente cliente a editar, o null para crear uno nuevo
     * @return el cliente guardado, o vacío si se canceló
     */
    static Optional<Cliente> mostrar(AppContext ctx, Cliente existente) {
        boolean nuevo = existente == null;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(nuevo ? "Nuevo cliente" : "Editar cliente");
        dialog.setHeaderText(nuevo ? "Registrar un cliente" : "Editar: " + existente.nombre());

        TextField nombre = new TextField(nuevo ? "" : existente.nombre());
        nombre.setPromptText("Nombre o razón social");
        TextField documento = new TextField(nuevo || existente.documento() == null ? "" : existente.documento());
        documento.setPromptText("DNI (8) o RUC (11), opcional");
        TextField telefono = new TextField(nuevo || existente.telefono() == null ? "" : existente.telefono());
        telefono.setPromptText("Teléfono (opcional)");
        Label error = Ui.etiquetaError();

        VBox cuerpo = new VBox(10, new Label("Nombre *"), nombre, new Label("DNI / RUC"), documento, new Label("Teléfono"), telefono, error);
        cuerpo.setPadding(new Insets(10));
        cuerpo.setPrefWidth(360);
        dialog.getDialogPane().setContent(cuerpo);

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);

        Cliente[] resultado = {null};
        Button botonGuardar = (Button) dialog.getDialogPane().lookupButton(guardar);
        botonGuardar.addEventFilter(ActionEvent.ACTION, ev -> {
            error.setText("");
            try {
                if (nuevo) {
                    resultado[0] = ctx.clientes().crear(nombre.getText(), documento.getText(), telefono.getText());
                } else {
                    ctx.clientes().actualizar(existente.id(), nombre.getText(), documento.getText(), telefono.getText());
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
