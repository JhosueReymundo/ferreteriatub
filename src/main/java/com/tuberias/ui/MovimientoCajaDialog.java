package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.MetodoPago;
import com.tuberias.model.TipoMovimientoCaja;
import com.tuberias.service.NegocioException;
import com.tuberias.service.Sesion;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Ingreso o egreso manual (gastos, aportes, retiros). Solo administrador. */
final class MovimientoCajaDialog {

    private MovimientoCajaDialog() {}

    /** @return true si el movimiento se registró */
    static boolean mostrar(AppContext ctx, TipoMovimientoCaja tipo) {
        boolean ingreso = tipo == TipoMovimientoCaja.INGRESO;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(ingreso ? "Registrar ingreso" : "Registrar egreso");
        dialog.setHeaderText(ingreso ? "Dinero que entra a la caja (aporte, cobro, etc.)"
                : "Dinero que sale de la caja (gasto, retiro, etc.)");

        TextField concepto = new TextField();
        concepto.setPromptText(ingreso ? "Ej. Aporte del dueño" : "Ej. Pago de luz, pasajes");
        TextField monto = new TextField();
        monto.setPromptText("Monto (S/)");
        ComboBox<MetodoPago> metodo = new ComboBox<>(FXCollections.observableArrayList(MetodoPago.values()));
        metodo.setValue(MetodoPago.EFECTIVO);
        Label error = Ui.etiquetaError();

        VBox cuerpo = new VBox(10,
                new Label("Concepto *"), concepto,
                new Label("Monto (S/) *"), monto,
                new Label("Método"), metodo, error);
        cuerpo.setPadding(new Insets(10));
        cuerpo.setPrefWidth(380);
        dialog.getDialogPane().setContent(cuerpo);

        ButtonType guardar = new ButtonType("Registrar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);

        boolean[] registrado = {false};
        Button boton = (Button) dialog.getDialogPane().lookupButton(guardar);
        boton.addEventFilter(ActionEvent.ACTION, ev -> {
            error.setText("");
            try {
                ctx.caja().registrarMovimiento(Sesion.actual(), tipo, concepto.getText(),
                        Ui.decimal(monto.getText(), "El monto"), metodo.getValue());
                registrado[0] = true;
            } catch (NegocioException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                error.setText("Ocurrió un error inesperado. Revisa la conexión con la base de datos.");
                ev.consume();
            }
        });

        Platform.runLater(concepto::requestFocus);
        dialog.showAndWait();
        return registrado[0];
    }
}
