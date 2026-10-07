package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.Categoria;
import com.tuberias.model.Forma;
import com.tuberias.model.Formato;
import com.tuberias.model.Marca;
import com.tuberias.model.Producto;
import com.tuberias.model.ProductoDatos;
import com.tuberias.model.StockInicial;
import com.tuberias.model.UnidadMedida;
import com.tuberias.service.NegocioException;
import com.tuberias.service.ProductoService;
import com.tuberias.service.Sesion;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Optional;

/** Formulario para crear o editar un producto. */
final class ProductoDialog {

    private static final Marca SIN_MARCA = new Marca(0, "(sin marca)");

    private ProductoDialog() {}

    /**
     * @param existente producto a editar, o null para crear uno nuevo
     * @return true si se guardó
     */
    static boolean mostrar(AppContext ctx, Producto existente) {
        ProductoService svc = ctx.productos();
        boolean nuevo = existente == null;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(nuevo ? "Nuevo producto" : "Editar producto");
        dialog.setHeaderText(nuevo ? "Registrar un producto" : "Editar: " + existente.nombre());

        Label error = Ui.etiquetaError();

        // ---- campos
        TextField codigo = new TextField();
        codigo.setPromptText(nuevo ? "Automático si lo dejas vacío" : "Código");
        TextField nombre = new TextField();
        nombre.setPromptText("Ej. Tubo PVC desagüe 4\" x 3 m");

        ComboBox<Categoria> categoria = new ComboBox<>(FXCollections.observableArrayList(svc.categorias()));
        categoria.setMaxWidth(Double.MAX_VALUE);
        categoria.setPromptText("Tipo de tubo");
        Button nuevaCategoria = new Button("+");
        nuevaCategoria.setOnAction(e -> pedirNombre("Nueva categoría",
                "Nombre del tipo de tubo (ej. PVC, Acero galvanizado)").ifPresent(n -> {
            try {
                Categoria creada = svc.crearCategoria(n);
                categoria.getItems().setAll(svc.categorias());
                categoria.setValue(creada);
                error.setText("");
            } catch (NegocioException ex) {
                error.setText(ex.getMessage());
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                error.setText("Ocurrió un error inesperado al crear la categoría.");
            }
        }));

        ComboBox<Marca> marca = new ComboBox<>();
        marca.getItems().add(SIN_MARCA);
        marca.getItems().addAll(svc.marcas());
        marca.setValue(SIN_MARCA);
        marca.setMaxWidth(Double.MAX_VALUE);
        Button nuevaMarca = new Button("+");
        nuevaMarca.setOnAction(e -> pedirNombre("Nueva marca", "Nombre de la marca").ifPresent(n -> {
            try {
                Marca creada = svc.crearMarca(n);
                marca.getItems().setAll(SIN_MARCA);
                marca.getItems().addAll(svc.marcas());
                marca.setValue(creada);
                error.setText("");
            } catch (NegocioException ex) {
                error.setText(ex.getMessage());
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                error.setText("Ocurrió un error inesperado al crear la marca.");
            }
        }));

        TextField modelo = new TextField();
        ComboBox<UnidadMedida> unidad = new ComboBox<>(FXCollections.observableArrayList(svc.unidades()));
        unidad.setPromptText("Cómo se cuenta");
        unidad.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Forma> forma = new ComboBox<>(FXCollections.observableArrayList(Forma.values()));
        forma.setValue(Forma.SIN_ESPECIFICAR);
        forma.setMaxWidth(Double.MAX_VALUE);
        TextField material = new TextField();
        material.setPromptText("PVC, acero negro, galvanizado...");
        TextField diametro = new TextField();
        diametro.setPromptText("Solo tubos circulares");
        TextField ancho = new TextField();
        TextField alto = new TextField();
        ancho.setPromptText("Cuadrados / rectangulares");
        alto.setPromptText("Cuadrados / rectangulares");
        TextField espesor = new TextField();
        TextField longitud = new TextField();
        TextField color = new TextField();
        TextField norma = new TextField();
        norma.setPromptText("SAP, SAL, C-10, ASTM...");
        TextArea descripcion = new TextArea();
        descripcion.setPrefRowCount(3);
        descripcion.setWrapText(true);
        descripcion.setPromptText("Otras características");
        TextField precio = new TextField();
        precio.setPromptText("Opcional; solo como sugerencia al vender");
        TextField stockMinimo = new TextField();
        stockMinimo.setPromptText("0 = sin alerta");

        TextField stockCantidad = new TextField();
        stockCantidad.setPromptText("Cuántos tienes ahora");
        TextField stockCosto = new TextField();
        stockCosto.setPromptText("Cuánto te costó cada uno");

        // ---- valores iniciales al editar
        if (!nuevo) {
            codigo.setText(existente.codigo());
            nombre.setText(existente.nombre());
            categoria.getItems().stream().filter(c -> c.id() == existente.categoriaId())
                    .findFirst().ifPresent(categoria::setValue);
            if (existente.marcaId() != null) {
                marca.getItems().stream().filter(m -> m.id() == existente.marcaId())
                        .findFirst().ifPresent(marca::setValue);
            }
            modelo.setText(vacio(existente.modelo()));
            unidad.getItems().stream().filter(u -> u.id() == existente.unidadId())
                    .findFirst().ifPresent(unidad::setValue);
            forma.setValue(existente.forma());
            material.setText(vacio(existente.material()));
            diametro.setText(Formato.numero(existente.diametroMm()));
            ancho.setText(Formato.numero(existente.anchoMm()));
            alto.setText(Formato.numero(existente.altoMm()));
            espesor.setText(Formato.numero(existente.espesorMm()));
            longitud.setText(Formato.numero(existente.longitudM()));
            color.setText(vacio(existente.color()));
            norma.setText(vacio(existente.normaClase()));
            descripcion.setText(vacio(existente.descripcion()));
            precio.setText(Formato.numero(existente.precioSugerido()));
            stockMinimo.setText(Formato.numero(existente.stockMinimo()));
        }

        // ---- distribución
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        ColumnConstraints etiquetas = new ColumnConstraints();
        etiquetas.setMinWidth(130);
        ColumnConstraints campos = new ColumnConstraints();
        campos.setHgrow(Priority.ALWAYS);
        campos.setMinWidth(300);
        grid.getColumnConstraints().addAll(etiquetas, campos);

        int fila = 0;
        fila = agregar(grid, fila, "Código", codigo);
        fila = agregar(grid, fila, "Nombre *", nombre);
        fila = agregar(grid, fila, "Categoría *", conBoton(categoria, nuevaCategoria));
        fila = agregar(grid, fila, "Marca", conBoton(marca, nuevaMarca));
        fila = agregar(grid, fila, "Modelo", modelo);
        fila = agregar(grid, fila, "Unidad *", unidad);
        fila = agregar(grid, fila, "Forma", forma);
        fila = agregar(grid, fila, "Material", material);
        fila = agregar(grid, fila, "Diámetro (mm)", diametro);
        fila = agregar(grid, fila, "Ancho (mm)", ancho);
        fila = agregar(grid, fila, "Alto (mm)", alto);
        fila = agregar(grid, fila, "Espesor (mm)", espesor);
        fila = agregar(grid, fila, "Longitud (m)", longitud);
        fila = agregar(grid, fila, "Color", color);
        fila = agregar(grid, fila, "Norma / clase", norma);
        fila = agregar(grid, fila, "Descripción", descripcion);
        fila = agregar(grid, fila, "Precio sugerido (S/)", precio);
        fila = agregar(grid, fila, "Stock mínimo", stockMinimo);

        if (nuevo) {
            Label titulo = new Label("Stock inicial (opcional)");
            titulo.setStyle("-fx-font-weight: bold;");
            Label nota = Ui.subtitulo("Si ya tienes existencias de este producto, regístralas aquí. "
                    + "Se guardan como stock inicial: no cuentan como compra ni como venta, "
                    + "así que no afectan la caja ni las ganancias.");
            nota.setMaxWidth(420);
            grid.add(titulo, 0, fila++, 2, 1);
            grid.add(nota, 0, fila++, 2, 1);
            fila = agregar(grid, fila, "Cantidad", stockCantidad);
            fila = agregar(grid, fila, "Costo unitario (S/)", stockCosto);
        } else {
            Label nota = Ui.subtitulo("El stock se modifica con compras, ventas o ajustes de inventario.");
            grid.add(nota, 0, fila, 2, 1);
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(600);
        scroll.setPrefViewportHeight(480);

        VBox cuerpo = new VBox(8, scroll, error);
        dialog.getDialogPane().setContent(cuerpo);

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);

        boolean[] guardado = {false};
        Button botonGuardar = (Button) dialog.getDialogPane().lookupButton(guardar);
        // Si hay error se consume el evento y el diálogo no se cierra
        botonGuardar.addEventFilter(ActionEvent.ACTION, ev -> {
            error.setText("");
            try {
                Marca m = marca.getValue();
                ProductoDatos datos = new ProductoDatos(
                        codigo.getText(),
                        nombre.getText(),
                        categoria.getValue() == null ? null : categoria.getValue().id(),
                        (m == null || m.id() == 0) ? null : m.id(),
                        modelo.getText(),
                        unidad.getValue() == null ? null : unidad.getValue().id(),
                        forma.getValue(),
                        material.getText(),
                        Ui.decimal(diametro.getText(), "El diámetro"),
                        Ui.decimal(ancho.getText(), "El ancho"),
                        Ui.decimal(alto.getText(), "El alto"),
                        Ui.decimal(espesor.getText(), "El espesor"),
                        Ui.decimal(longitud.getText(), "La longitud"),
                        color.getText(),
                        norma.getText(),
                        descripcion.getText(),
                        Ui.decimal(precio.getText(), "El precio sugerido"),
                        Ui.decimal(stockMinimo.getText(), "El stock mínimo"));

                if (nuevo) {
                    StockInicial inicial = null;
                    if (!stockCantidad.getText().isBlank() || !stockCosto.getText().isBlank()) {
                        inicial = new StockInicial(
                                Ui.decimal(stockCantidad.getText(), "La cantidad del stock inicial"),
                                Ui.decimal(stockCosto.getText(), "El costo unitario del stock inicial"));
                    }
                    svc.crear(Sesion.actual(), datos, inicial);
                } else {
                    svc.actualizar(existente.id(), datos);
                }
                guardado[0] = true;
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
        return guardado[0];
    }

    // ---------------------------------------------------------------- helpers

    private static int agregar(GridPane grid, int fila, String etiqueta, Node campo) {
        grid.add(new Label(etiqueta), 0, fila);
        grid.add(campo, 1, fila);
        return fila + 1;
    }

    private static HBox conBoton(ComboBox<?> combo, Button boton) {
        HBox.setHgrow(combo, Priority.ALWAYS);
        HBox caja = new HBox(6, combo, boton);
        return caja;
    }

    private static Optional<String> pedirNombre(String titulo, String encabezado) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle(titulo);
        d.setHeaderText(encabezado);
        d.setContentText("Nombre:");
        return d.showAndWait();
    }

    private static String vacio(String texto) {
        return texto == null ? "" : texto;
    }
}
