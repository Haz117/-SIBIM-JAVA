package com.sibim;

import com.sibim.db.DatabaseConfig;
import javafx.application.Application;

public class Main {
    public static void main(String[] args) {
        // Initialize DB pool before JavaFX starts
        try {
            DatabaseConfig.init();
        } catch (Exception e) {
            System.err.println("Advertencia: No se pudo conectar a la base de datos: " + e.getMessage());
            System.err.println("La aplicacion iniciara en modo demo (datos de muestra).");
            DatabaseConfig.setDemoMode(true);
        }

        // Launch JavaFX
        Application.launch(MainApp.class, args);

        // Shutdown pool on exit
        DatabaseConfig.close();
    }
}
