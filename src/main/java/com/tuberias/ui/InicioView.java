package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.CajaSesion;
import com.tuberias.model.Dashboard;
import com.tuberias.model.Formato;
import com.tuberias.model.Periodo;
import com.tuberias.model.ProductoStockBajo;
import com.tuberias.model.ProductoVendido;
import com.tuberias.model.ResumenCaja;
import com.tuberias.model.VentaDia;
import com.tuberias.model.VentaPorMetodo;
import com.tuberias.service.Sesion;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** Inicio: indicadores del negocio. Los costos y la utilidad solo los ve el administrador. */
final class InicioView {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private InicioView() {}

    static Parent crear(AppContext ctx, Runnable irAProductos) {
        ComboBox<Periodo> periodo = new ComboBox<>(FXCollections.observableArrayList(Periodo.values()));
        periodo.setValue(Periodo.ULTIMOS_7);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox encabezado = new HBox(10, Ui.titulo("Inicio"), espacio, new Label("Período:"), periodo);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        VBox contenido = new VBox(14);
        Label error = Ui.etiquetaError();

        Runnable recargar = () -> {
            contenido.getChildren().clear();
            Ui.manejar(error, () -> contenido.getChildren().addAll(armar(ctx, periodo.getValue(), irAProductos)));
        };
        periodo.setOnAction(e -> recargar.run());
        recargar.run();

        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox raiz = new VBox(12, encabezado, error, scroll);
        raiz.setPadding(new Insets(16));
        return raiz;
    }

    private static List<Node> armar(AppContext ctx, Periodo periodo, Runnable irAProductos) {
        boolean admin = Sesion.actual().esAdmin();
        Dashboard d = ctx.dashboard().cargar(Sesion.actual(), periodo);

        // ---- tarjetas
        FlowPane tarjetas = new FlowPane(12, 12);
        tarjetas.getChildren().add(tarjeta("Vendido", Formato.dinero(d.totalVendido()), d.nroVentas() + " venta(s)", null));
        tarjetas.getChildren().add(tarjeta("Ticket promedio", Formato.dinero(d.ticketPromedio()), "por venta", null));
        if (admin) {
            tarjetas.getChildren().add(tarjeta("Utilidad", Formato.dinero(d.utilidad()),
                    "cobrado menos costo", d.utilidad().signum() < 0 ? "#c0392b" : "#1e8449"));
            tarjetas.getChildren().add(tarjeta("Compras", Formato.dinero(d.totalCompras()), "en el período", null));
            tarjetas.getChildren().add(tarjeta("Inventario", Formato.dinero(d.valorInventario()), "valor a costo", null));
        }
        tarjetas.getChildren().add(tarjetaCaja(ctx));
        tarjetas.getChildren().add(tarjetaStock(ctx, irAProductos));

        // ---- gráfico de ventas por día
        CategoryAxis ejeX = new CategoryAxis();
        NumberAxis ejeY = new NumberAxis();
        ejeY.setLabel("S/");
        BarChart<String, Number> grafico = new BarChart<>(ejeX, ejeY);
        grafico.setLegendVisible(false);
        grafico.setAnimated(false);
        grafico.setTitle("Ventas por día");
        grafico.setPrefHeight(260);
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (VentaDia v : d.ventasPorDia()) {
            serie.getData().add(new XYChart.Data<>(DIA.format(v.dia()), v.total()));
        }
        grafico.getData().add(serie);

        // ---- más vendidos y métodos de pago
        ListView<String> masVendidos = new ListView<>(FXCollections.observableArrayList(
                d.masVendidos().stream().map(InicioView::lineaProducto).toList()));
        masVendidos.setPlaceholder(new Label("Sin ventas en este período."));
        masVendidos.setPrefHeight(200);
        VBox cajaProductos = new VBox(6, subtituloSeccion("Productos más vendidos"), masVendidos);
        HBox.setHgrow(cajaProductos, Priority.ALWAYS);

        ListView<String> metodos = new ListView<>(FXCollections.observableArrayList(
                d.porMetodo().stream().map(InicioView::lineaMetodo).toList()));
        metodos.setPlaceholder(new Label("Sin ventas en este período."));
        metodos.setPrefHeight(200);
        VBox cajaMetodos = new VBox(6, subtituloSeccion("Cobrado por método de pago"), metodos);
        cajaMetodos.setPrefWidth(280);

        HBox abajo = new HBox(14, cajaProductos, cajaMetodos);

        return List.of(tarjetas, grafico, abajo);
    }

    private static Node tarjetaCaja(AppContext ctx) {
        Optional<CajaSesion> abierta = ctx.caja().cajaAbierta();
        if (abierta.isEmpty()) {
            return tarjeta("Caja", "Cerrada", "ábrela para vender", "#b9770e");
        }
        BigDecimal efectivo = ctx.caja().resumen(abierta.get().id())
                .map(ResumenCaja::efectivoEsperado).orElse(abierta.get().montoApertura());
        return tarjeta("Caja abierta", Formato.dinero(efectivo), "efectivo en el cajón", "#1e8449");
    }

    private static Node tarjetaStock(AppContext ctx, Runnable irAProductos) {
        List<ProductoStockBajo> enAlerta = ctx.stock().listarStockBajo();
        Node t = tarjeta("Stock bajo", String.valueOf(enAlerta.size()),
                enAlerta.isEmpty() ? "todo sobre el mínimo" : "producto(s) en alerta",
                enAlerta.isEmpty() ? "#1e8449" : "#c0392b");
        if (!enAlerta.isEmpty()) {
            Button ver = new Button("Ver productos");
            ver.setOnAction(e -> irAProductos.run());
            ((VBox) t).getChildren().add(ver);
        }
        return t;
    }

    private static VBox tarjeta(String titulo, String valor, String nota, String colorValor) {
        Label t = new Label(titulo);
        t.setStyle("-fx-text-fill: #666666;");
        Label v = new Label(valor);
        v.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;" + (colorValor == null ? "" : "-fx-text-fill: " + colorValor + ";"));
        Label n = new Label(nota);
        n.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        VBox caja = new VBox(2, t, v, n);
        caja.setPadding(new Insets(10, 14, 10, 14));
        caja.setPrefWidth(190);
        caja.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 6;");
        return caja;
    }

    private static Label subtituloSeccion(String texto) {
        Label l = new Label(texto);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    private static String lineaProducto(ProductoVendido p) {
        return p.nombre() + "  —  " + Formato.numero(p.cantidad()) + " " + p.unidad()
                + "  ·  " + Formato.dinero(p.total());
    }

    private static String lineaMetodo(VentaPorMetodo m) {
        return m.metodoPago() + "  —  " + Formato.dinero(m.total());
    }
}
