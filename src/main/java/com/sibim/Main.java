package com.sibim;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    // File+console logging with daily rotation is configured declaratively
    // in src/main/resources/logback.xml (logback resolves its ${user.home}
    // placeholder against the System property automatically) — no manual
    // setup needed here.
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Catches anything that escapes every other try/catch in the app —
        // without this, an exception on a background thread (e.g. a JavaFX
        // Task with no setOnFailed handler) fails silently: nothing is
        // logged and the user gets no indication anything went wrong.
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) ->
            log.error("Excepcion no capturada en el hilo '{}'", thread.getName(), ex));

        // DB initialization is now done on a virtual thread inside SplashController
        // so the JavaFX window appears immediately instead of blocking here for up
        // to 8 s (HikariCP connectionTimeout) before the splash is even painted.
        Application.launch(MainApp.class, args);
    }
}
