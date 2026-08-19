package com.sibim;

import com.sibim.db.DatabaseConfig;
import javafx.application.Application;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class Main {
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
            System.err.println("Advertencia: No se pudo conectar a la base de datos o el esquema no existe: " + e.getMessage());
            System.err.println("La aplicacion iniciara en modo demo (datos de muestra).");
            DatabaseConfig.close(); // release any pool that may have been created
            DatabaseConfig.setDemoMode(true);
        }

        // Launch JavaFX
        Application.launch(MainApp.class, args);

        // Shutdown pool on exit
        DatabaseConfig.close();
    }
}
