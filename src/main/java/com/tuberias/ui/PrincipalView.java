package com.tuberias.ui;

import com.tuberias.AppContext;
import com.tuberias.model.Usuario;
import com.tuberias.service.Sesion;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/** Ventana principal: barra superior con el usuario y menú lateral. */
final class PrincipalView {

    private static final String COLOR_ICONO_MENU = "#4a5568";
    private static final String COLOR_AVATAR = "#3b82f6";

    private PrincipalView() {}

    static Parent crear(Navegador nav, AppContext ctx) {
        Usuario usuario = Sesion.actual();
        BorderPane raiz = new BorderPane();

        // ---- barra superior
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        Button cambiar = botonBarra("Cambiar contraseña", FontAwesomeSolid.KEY);
        cambiar.setOnAction(e -> CambiarPasswordDialog.mostrar(ctx.auth(), usuario, false));
        Button salir = botonBarra("Cerrar sesión", FontAwesomeSolid.SIGN_OUT_ALT);
        salir.setOnAction(e -> nav.cerrarSesion());

        HBox barra = new HBox(10, crearUsuario(usuario), espacio, cambiar, salir);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(10, 16, 10, 16));
        barra.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");
        raiz.setTop(barra);

        // ---- menú lateral
        VBox menu = new VBox(4);
        menu.setPadding(new Insets(12));
        menu.setPrefWidth(190);
        menu.setStyle("-fx-background-color: #fafbfc; -fx-border-color: #e2e8f0; -fx-border-width: 0 1 0 0;");

        Button inicio = botonMenu("Inicio", FontAwesomeSolid.HOME);
        inicio.setOnAction(e -> irInicio(raiz, ctx));
        Button productos = botonMenu("Productos", FontAwesomeSolid.BOXES);
        productos.setOnAction(e -> irProductos(raiz, ctx));
        Button ventas = botonMenu("Ventas", FontAwesomeSolid.CASH_REGISTER);
        ventas.setOnAction(e -> raiz.setCenter(VentasView.crear(ctx)));
        Button clientes = botonMenu("Clientes", FontAwesomeSolid.USERS);
        clientes.setOnAction(e -> raiz.setCenter(ClientesView.crear(ctx)));
        Button caja = botonMenu("Caja", FontAwesomeSolid.WALLET);
        caja.setOnAction(e -> raiz.setCenter(CajaView.crear(ctx)));
        menu.getChildren().addAll(inicio, productos, ventas, clientes, caja);

        // Compras y proveedores muestran costos: solo para el administrador
        if (usuario.esAdmin()) {
            Button compras = botonMenu("Compras", FontAwesomeSolid.SHOPPING_CART);
            compras.setOnAction(e -> raiz.setCenter(ComprasView.crear(ctx)));
            Button proveedores = botonMenu("Proveedores", FontAwesomeSolid.TRUCK);
            proveedores.setOnAction(e -> raiz.setCenter(ProveedoresView.crear(ctx)));
            menu.getChildren().addAll(compras, proveedores);
        }
        raiz.setLeft(menu);

        irInicio(raiz, ctx);
        return raiz;
    }

    /** Avatar circular con la inicial + nombre y rol en dos líneas. */
    private static HBox crearUsuario(Usuario usuario) {
        String nombre = usuario.nombreCompleto();
        String inicial = nombre.isBlank() ? "?" : nombre.substring(0, 1).toUpperCase();

        Label letra = new Label(inicial);
        letra.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        StackPane avatar = new StackPane(letra);
        avatar.setMinSize(36, 36);
        avatar.setMaxSize(36, 36);
        avatar.setStyle("-fx-background-color: " + COLOR_AVATAR + "; -fx-background-radius: 18;");

        Label lblNombre = new Label(nombre);
        lblNombre.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a202c;");
        Label lblRol = new Label(usuario.rolNombre());
        lblRol.setStyle("-fx-font-size: 11px; -fx-text-fill: #718096;");
        VBox textos = new VBox(1, lblNombre, lblRol);
        textos.setAlignment(Pos.CENTER_LEFT);

        HBox caja = new HBox(10, avatar, textos);
        caja.setAlignment(Pos.CENTER_LEFT);
        return caja;
    }

    private static void irInicio(BorderPane raiz, AppContext ctx) {
        raiz.setCenter(InicioView.crear(ctx, () -> irProductos(raiz, ctx)));
    }

    private static void irProductos(BorderPane raiz, AppContext ctx) {
        raiz.setCenter(ProductosView.crear(ctx));
    }

    private static Button botonMenu(String texto, Ikon icono) {
        FontIcon fi = new FontIcon(icono);
        fi.setIconSize(16);
        fi.setIconColor(Color.web(COLOR_ICONO_MENU));

        Button b = new Button(texto, fi);
        b.setGraphicTextGap(12);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setStyle("-fx-background-color: transparent; -fx-padding: 9 12; -fx-font-size: 13px;"
                + "-fx-text-fill: #2d3748; -fx-cursor: hand; -fx-background-radius: 6;");
        b.setOnMouseEntered(e -> b.setStyle(b.getStyle().replace("transparent", "#e8edf3")));
        b.setOnMouseExited(e -> b.setStyle(b.getStyle().replace("#e8edf3", "transparent")));
        return b;
    }

    private static Button botonBarra(String texto, Ikon icono) {
        FontIcon fi = new FontIcon(icono);
        fi.setIconSize(13);
        Button b = new Button(texto, fi);
        b.setGraphicTextGap(8);
        return b;
    }
}


//package com.tuberias.ui;
//
//import com.tuberias.AppContext;
//import com.tuberias.model.Usuario;
//import com.tuberias.service.Sesion;
//import javafx.geometry.Insets;
//import javafx.geometry.Pos;
//import javafx.scene.Parent;
//import javafx.scene.control.Button;
//import javafx.scene.control.Label;
//import javafx.scene.layout.BorderPane;
//import javafx.scene.layout.HBox;
//import javafx.scene.layout.Priority;
//import javafx.scene.layout.Region;
//import javafx.scene.layout.VBox;
//
//final class PrincipalView {
//
//    private PrincipalView() {}
//
//    static Parent crear(Navegador nav, AppContext ctx) {
//        Usuario usuario = Sesion.actual();
//        BorderPane raiz = new BorderPane();
//
//        Label quien = new Label(usuario.nombreCompleto() + "  (" + usuario.rolNombre() + ")");
//        quien.setStyle("-fx-font-weight: bold;");
//        Button cambiar = new Button("Cambiar contraseña");
//        cambiar.setOnAction(e -> CambiarPasswordDialog.mostrar(ctx.auth(), usuario, false));
//        Button salir = new Button("Cerrar sesión");
//        salir.setOnAction(e -> nav.cerrarSesion());
//
//        Region espacio = new Region();
//        HBox.setHgrow(espacio, Priority.ALWAYS);
//        HBox barra = new HBox(10, quien, espacio, cambiar, salir);
//        barra.setAlignment(Pos.CENTER_LEFT);
//        barra.setPadding(new Insets(10, 16, 10, 16));
//        barra.setStyle("-fx-background-color: #f0f2f5; -fx-border-color: #d0d4d9; -fx-border-width: 0 0 1 0;");
//        raiz.setTop(barra);
//
//        VBox menu = new VBox(6);
//        menu.setPadding(new Insets(12));
//        menu.setPrefWidth(170);
//        menu.setStyle("-fx-background-color: #fafbfc; -fx-border-color: #d0d4d9; -fx-border-width: 0 1 0 0;");
//
//        Button inicio = botonMenu("Inicio");
//        inicio.setOnAction(e -> irInicio(raiz, ctx));
//        Button productos = botonMenu("Productos");
//        productos.setOnAction(e -> irProductos(raiz, ctx));
//        Button ventas = botonMenu("Ventas");
//        ventas.setOnAction(e -> raiz.setCenter(VentasView.crear(ctx)));
//        Button clientes = botonMenu("Clientes");
//        clientes.setOnAction(e -> raiz.setCenter(ClientesView.crear(ctx)));
//        Button caja = botonMenu("Caja");
//        caja.setOnAction(e -> raiz.setCenter(CajaView.crear(ctx)));
//        menu.getChildren().addAll(inicio, productos, ventas, clientes, caja);
//
//        if (usuario.esAdmin()) {
//            Button compras = botonMenu("Compras");
//            compras.setOnAction(e -> raiz.setCenter(ComprasView.crear(ctx)));
//            Button proveedores = botonMenu("Proveedores");
//            proveedores.setOnAction(e -> raiz.setCenter(ProveedoresView.crear(ctx)));
//            menu.getChildren().addAll(compras, proveedores);
//        }
//        raiz.setLeft(menu);
//
//        irInicio(raiz, ctx);
//        return raiz;
//    }
//
//    private static void irInicio(BorderPane raiz, AppContext ctx) {
//        raiz.setCenter(InicioView.crear(ctx, () -> irProductos(raiz, ctx)));
//    }
//
//    private static void irProductos(BorderPane raiz, AppContext ctx) {
//        raiz.setCenter(ProductosView.crear(ctx));
//    }
//
//    private static Button botonMenu(String texto) {
//        Button b = new Button(texto);
//        b.setMaxWidth(Double.MAX_VALUE);
//        b.setAlignment(Pos.CENTER_LEFT);
//        return b;
//    }
//}
