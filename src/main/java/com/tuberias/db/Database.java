package com.tuberias.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Crea el pool de conexiones y aplica las migraciones de Flyway
 * (src/main/resources/db/migration) al iniciar la aplicación.
 */
public final class Database {

    private static HikariDataSource dataSource;

    private Database() {}

    public static synchronized void iniciar() {
        if (dataSource != null) return;

        Properties props = cargarPropiedades();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getProperty("db.url"));
        config.setUsername(props.getProperty("db.user"));
        config.setPassword(props.getProperty("db.password"));
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    public static DataSource getDataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("La base de datos no ha sido iniciada");
        }
        return dataSource;
    }

    public static synchronized void cerrar() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private static Properties cargarPropiedades() {
        Properties props = new Properties();
        try (InputStream in = Database.class.getResourceAsStream("/application.properties")) {
            if (in == null) {
                throw new IllegalStateException("No se encontró application.properties");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer application.properties", e);
        }
        return props;
    }
}
