package com.tuberias;

import com.tuberias.db.Database;
import com.tuberias.repository.JdbcCajaRepository;
import com.tuberias.repository.JdbcCatalogoRepository;
import com.tuberias.repository.JdbcClienteRepository;
import com.tuberias.repository.JdbcCompraRepository;
import com.tuberias.repository.JdbcDashboardRepository;
import com.tuberias.repository.JdbcProductoRepository;
import com.tuberias.repository.JdbcProveedorRepository;
import com.tuberias.repository.JdbcUsuarioRepository;
import com.tuberias.repository.JdbcVentaRepository;
import com.tuberias.repository.StockRepository;
import com.tuberias.service.AuthService;
import com.tuberias.service.CajaService;
import com.tuberias.service.ClienteService;
import com.tuberias.service.DashboardService;
import com.tuberias.service.CompraService;
import com.tuberias.service.PasswordHasher;
import com.tuberias.service.ProductoService;
import com.tuberias.service.ProveedorService;
import com.tuberias.service.VentaService;
import com.tuberias.ui.Navegador;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import javax.sql.DataSource;
import java.time.Clock;

public class MainApp extends Application {

    private Throwable errorInicio;

    @Override
    public void init() {
        // Antes de mostrar la ventana: conecta y aplica las migraciones
        try {
            Database.iniciar();
        } catch (Exception e) {
            errorInicio = e;
        }
    }

    @Override
    public void start(Stage stage) {
        if (errorInicio != null) {
            mostrarErrorDeInicio(errorInicio);
            Platform.exit();
            return;
        }

        DataSource ds = Database.getDataSource();
        JdbcProductoRepository productoRepo = new JdbcProductoRepository(ds);
        StockRepository stockRepo = new StockRepository(ds);
        AppContext ctx = new AppContext(
                new AuthService(new JdbcUsuarioRepository(ds), new PasswordHasher(), Clock.systemUTC()),
                new ProductoService(productoRepo, new JdbcCatalogoRepository(ds)),
                stockRepo,
                new ProveedorService(new JdbcProveedorRepository(ds)),
                new CompraService(new JdbcCompraRepository(ds), productoRepo, Clock.systemDefaultZone()),
                new ClienteService(new JdbcClienteRepository(ds)),
                new VentaService(new JdbcVentaRepository(ds), productoRepo, stockRepo),
                new CajaService(new JdbcCajaRepository(ds)),
                new DashboardService(new JdbcDashboardRepository(ds), Clock.systemDefaultZone()));

        new Navegador(stage, ctx).mostrarInicio();
        stage.show();
    }

    @Override
    public void stop() {
        Database.cerrar();
    }

    private void mostrarErrorDeInicio(Throwable error) {
        error.printStackTrace();
        Throwable causa = error;
        while (causa.getCause() != null) {
            causa = causa.getCause();
        }
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        alerta.setTitle("Sistema de Tubos");
        alerta.setHeaderText("No se pudo iniciar la base de datos");
        alerta.setContentText("Revisa application.properties y que PostgreSQL esté encendido.\n\nDetalle: "
                + causa.getMessage());
        alerta.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
