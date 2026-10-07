package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.CajaSesion;
import com.tuberias.model.Formato;
import com.tuberias.model.MetodoPago;
import com.tuberias.model.MovimientoCaja;
import com.tuberias.model.ResumenCaja;
import com.tuberias.model.TipoMovimientoCaja;
import com.tuberias.service.Sesion;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/** Caja: estado del turno, totales y movimientos. Permite revisar turnos anteriores. */
final class CajaView {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private CajaView() {}

    static Parent crear(AppContext ctx) {
        boolean admin = Sesion.actual().esAdmin();
        ObservableList<MovimientoCaja> movimientos = FXCollections.observableArrayList();

        // ---- barra de acciones
        ComboBox<CajaSesion> sesiones = new ComboBox<>();
        sesiones.setConverter(new StringConverter<>() {
            @Override
            public String toString(CajaSesion s) {
                return s == null ? "" : "Caja #" + s.id() + " · " + FECHA.format(s.fechaApertura())
                        + (s.abierta() ? " · ABIERTA" : " · cerrada");
            }

            @Override
            public CajaSesion fromString(String texto) {
                return null;
            }
        });
        sesiones.setPrefWidth(300);

        Button abrir = new Button("Abrir caja");
        Button cerrar = new Button("Cerrar caja");
        Button ingreso = new Button("Registrar ingreso");
        Button egreso = new Button("Registrar egreso");
        Region espacioSuperior = new Region();
        HBox.setHgrow(espacioSuperior, Priority.ALWAYS);
        HBox acciones = new HBox(10, new Label("Turno:"), sesiones, espacioSuperior, abrir, cerrar);
        if (admin) {
            acciones.getChildren().addAll(ingreso, egreso);
        }
        acciones.setAlignment(Pos.CENTER_LEFT);

        // ---- resumen
        Label estado = new Label();
        estado.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label detalleResumen = new Label();
        detalleResumen.setWrapText(true);
        VBox panel = new VBox(6, estado, detalleResumen);
        panel.setPadding(new Insets(12));
        panel.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 6;");

        // ---- movimientos
        TableView<MovimientoCaja> tabla = new TableView<>(movimientos);
        tabla.setPlaceholder(new Label("Este turno aún no tiene movimientos."));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.getColumns().add(CajaView.<MovimientoCaja>col("Hora", 120, m -> FECHA.format(m.fecha())));
        tabla.getColumns().add(CajaView.<MovimientoCaja>col("Origen", 140, m -> m.origen().toString()));
        tabla.getColumns().add(CajaView.<MovimientoCaja>col("Concepto", 270, MovimientoCaja::concepto));
        tabla.getColumns().add(CajaView.<MovimientoCaja>col("Método", 110, m -> m.metodoPago().toString()));
        tabla.getColumns().add(CajaView.<MovimientoCaja>col("Ingreso", 100,
                m -> m.tipo() == TipoMovimientoCaja.INGRESO ? Formato.dinero(m.monto()) : ""));
        tabla.getColumns().add(CajaView.<MovimientoCaja>col("Egreso", 100,
                m -> m.tipo() == TipoMovimientoCaja.EGRESO ? Formato.dinero(m.monto()) : ""));
        tabla.getColumns().add(CajaView.<MovimientoCaja>col("Usuario", 90, MovimientoCaja::usuario));
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(MovimientoCaja m, boolean vacio) {
                super.updateItem(m, vacio);
                setStyle(vacio || m == null || m.tipo() == TipoMovimientoCaja.INGRESO ? ""
                        : "-fx-text-fill: #a03020;");
            }
        });

        Label error = Ui.etiquetaError();

        // Muestra la sesión elegida (resumen + movimientos)
        Runnable mostrarSeleccion = () -> Ui.manejar(error, () -> {
            CajaSesion elegida = sesiones.getValue();
            boolean hayAbierta = ctx.caja().cajaAbierta().isPresent();
            abrir.setDisable(hayAbierta);
            cerrar.setDisable(!hayAbierta);
            ingreso.setDisable(!hayAbierta);
            egreso.setDisable(!hayAbierta);
            if (elegida == null) {
                estado.setText("Todavía no se abrió ninguna caja.");
                detalleResumen.setText("Pulsa \"Abrir caja\" para empezar a registrar ventas.");
                movimientos.clear();
                return;
            }
            ResumenCaja r = ctx.caja().resumen(elegida.id()).orElseThrow();
            movimientos.setAll(ctx.caja().movimientos(elegida.id()));
            estado.setText(r.sesion().abierta() ? "Caja ABIERTA desde " + FECHA.format(r.sesion().fechaApertura())
                    : "Caja cerrada el " + FECHA.format(r.sesion().fechaCierre()));
            StringBuilder t = new StringBuilder();
            t.append("Apertura: ").append(Formato.dinero(r.sesion().montoApertura()))
                    .append("   ·   Ingresos: ").append(Formato.dinero(r.totalIngresos()))
                    .append("   ·   Egresos: ").append(Formato.dinero(r.totalEgresos())).append('\n');
            t.append(r.sesion().abierta() ? "Efectivo en caja ahora: " : "Efectivo esperado: ")
                    .append(Formato.dinero(r.efectivoEsperado()));
            if (!r.sesion().abierta()) {
                t.append("   ·   Contado: ").append(Formato.dinero(r.sesion().montoCierreReal()));
                var dif = r.sesion().diferencia();
                t.append("   ·   ").append(dif.signum() == 0 ? "Cuadra"
                        : dif.signum() < 0 ? "Faltaron " + Formato.dinero(dif.negate())
                        : "Sobraron " + Formato.dinero(dif));
            }
            t.append("\nCobrado por medios digitales (no está en el cajón): ").append(Formato.dinero(r.netoDigital()));
            List<MetodoPago> digitales = List.of(MetodoPago.YAPE_PLIN, MetodoPago.TRANSFERENCIA, MetodoPago.TARJETA);
            for (MetodoPago m : digitales) {
                if (r.netoPorMetodo().get(m).signum() != 0) {
                    t.append("\n   · ").append(m).append(": ").append(Formato.dinero(r.netoPorMetodo().get(m)));
                }
            }
            detalleResumen.setText(t.toString());
        });

        // Recarga la lista de turnos y deja seleccionado el abierto (o el más reciente)
        Runnable recargar = () -> Ui.manejar(error, () -> {
            List<CajaSesion> lista = ctx.caja().sesiones();
            sesiones.setOnAction(null);
            sesiones.getItems().setAll(lista);
            CajaSesion abierta = lista.stream().filter(CajaSesion::abierta).findFirst()
                    .orElse(lista.isEmpty() ? null : lista.get(0));
            sesiones.setValue(abierta);
            sesiones.setOnAction(e -> mostrarSeleccion.run());
            mostrarSeleccion.run();
        });

        abrir.setOnAction(e -> {
            if (AbrirCajaDialog.mostrar(ctx)) {
                recargar.run();
            }
        });
        cerrar.setOnAction(e -> {
            if (CerrarCajaDialog.mostrar(ctx)) {
                recargar.run();
            }
        });
        ingreso.setOnAction(e -> {
            if (MovimientoCajaDialog.mostrar(ctx, TipoMovimientoCaja.INGRESO)) {
                recargar.run();
            }
        });
        egreso.setOnAction(e -> {
            if (MovimientoCajaDialog.mostrar(ctx, TipoMovimientoCaja.EGRESO)) {
                recargar.run();
            }
        });

        recargar.run();

        VBox raiz = new VBox(10, Ui.titulo("Caja"), acciones, panel, tabla, error);
        raiz.setPadding(new Insets(16));
        return raiz;
    }

    private static <T> TableColumn<T, String> col(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(valor.apply(cd.getValue())));
        return c;
    }
}
