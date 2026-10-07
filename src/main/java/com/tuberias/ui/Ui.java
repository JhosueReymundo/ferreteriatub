package com.tuberias.ui;

import com.tuberias.model.Formato;
import com.tuberias.model.ProductoStockBajo;
import com.tuberias.service.NegocioException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** Pequeños ayudantes para armar las pantallas. */
final class Ui {

    private Ui() {}

    static Label titulo(String texto) {
        Label l = new Label(texto);
        l.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        return l;
    }

    static Label subtitulo(String texto) {
        Label l = new Label(texto);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #666666;");
        return l;
    }

    static Label etiquetaError() {
        Label l = new Label();
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #c0392b;");
        return l;
    }

    /** Formulario centrado con ancho máximo fijo. */
    static Parent tarjeta(Node... hijos) {
        VBox caja = new VBox(10, hijos);
        caja.setPadding(new Insets(30));
        caja.setMaxWidth(380);
        caja.setAlignment(Pos.CENTER_LEFT);
        StackPane raiz = new StackPane(caja);
        raiz.setPadding(new Insets(20));
        return raiz;
    }

    /**
     * Ejecuta una acción mostrando en {@code error} los mensajes de negocio
     * y un texto genérico si ocurre algo inesperado (por ejemplo, la BD caída).
     */
    static void manejar(Label error, Runnable accion) {
        error.setText("");
        try {
            accion.run();
        } catch (NegocioException e) {
            error.setText(e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            error.setText("Ocurrió un error inesperado. Revisa la conexión con la base de datos.");
        }
    }

    /** Convierte el texto de un campo en número (acepta coma o punto). Vacío = null. */
    static BigDecimal decimal(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(texto.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new NegocioException(campo + " no es un número válido.");
        }
    }

    private static final int MAX_EN_AVISO = 10;

    /** Aviso de productos que llegaron a su stock mínimo. No hace nada si la lista está vacía. */
    static void avisarStockBajo(String encabezado, List<ProductoStockBajo> enAlerta) {
        if (enAlerta == null || enAlerta.isEmpty()) {
            return;
        }
        String detalle = enAlerta.stream()
                .limit(MAX_EN_AVISO)
                .map(p -> "• " + p.nombre() + ": quedan " + Formato.numero(p.stockActual())
                        + " (mínimo " + Formato.numero(p.stockMinimo()) + ")"
                        + (p.agotado() ? "  ← AGOTADO" : ""))
                .collect(Collectors.joining("\n"));
        if (enAlerta.size() > MAX_EN_AVISO) {
            detalle += "\n… y " + (enAlerta.size() - MAX_EN_AVISO) + " más";
        }
        Alert alerta = new Alert(Alert.AlertType.WARNING);
        alerta.setTitle("Stock bajo");
        alerta.setHeaderText(encabezado);
        alerta.setContentText(detalle);
        alerta.showAndWait();
    }
}
