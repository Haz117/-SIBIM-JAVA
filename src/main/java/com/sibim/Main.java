package com.sibim;

import com.sibim.db.DatabaseConfig;
import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Initialize DB pool before JavaFX starts; fall back to demo mode if
        // the connection fails OR the SIBIM schema isn't present (e.g. a fresh
        // Postgres instance with no migrations applied yet).
        try {
            DatabaseConfig.init();
            try (Connection c = DatabaseConfig.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT 1 FROM products LIMIT 1")) {
                ps.execute(); // throws if table doesn't exist
            }
        } catch (Exception e) {
            log.warn("No se pudo conectar a la base de datos o el esquema no existe: {}", e.getMessage());
            log.warn("La aplicación iniciará en modo demo (datos de muestra).");
            DatabaseConfig.close();
            DatabaseConfig.setDemoMode(true);
        }

        // Launch JavaFX
        Application.launch(MainApp.class, args);

        // Shutdown pool on exit
        DatabaseConfig.close();
    }
}
