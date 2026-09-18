module com.sibim {
    requires transitive javafx.controls;
    requires javafx.fxml;

    // Not JavaFX-Swing interop (no SwingNode/JFXPanel usage) — this is what
    // exposes java.awt.* (Graphics2D, BufferedImage, ...) that ImageUtils
    // uses directly for product-photo resizing.
    requires java.desktop;

    requires java.sql;
    requires java.net.http;
    // transitive: DialogUtil's public API (persistTableSort, captureColumnReset,
    // persistColumnWidths) takes java.util.prefs.Preferences as a parameter type.
    requires transitive java.prefs;
    requires com.zaxxer.hikari;
    requires org.postgresql.jdbc;
    requires org.xerial.sqlitejdbc;

    requires bcrypt;

    requires org.apache.poi.ooxml;
    requires jakarta.mail;
    requires kernel;
    requires layout;
    requires io;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    // transitive: DialogUtil.loadAsync's public API takes org.slf4j.Logger as a parameter.
    requires transitive org.slf4j;
    requires ch.qos.logback.classic;
    requires io.github.cdimascio.dotenv.java;
    requires org.flywaydb.core;
    requires atlantafx.base;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    requires com.google.zxing;

    opens com.sibim to javafx.fxml;
    opens com.sibim.controller to javafx.fxml;
    opens com.sibim.model to javafx.base;
    opens com.sibim.model.enums to javafx.base;
    // Flyway (named module) needs to read SQL files in db/migration at runtime.
    // Without this, the module system blocks getResourceAsStream on the package.
    opens db.migration to org.flywaydb.core;

    exports com.sibim;
    exports com.sibim.controller;
    exports com.sibim.model;
    exports com.sibim.model.enums;
    exports com.sibim.service;
    // Controllers already construct repositories directly across the module
    // (e.g. `new ProductoRepository()`), and EmailService's public constructor
    // takes ConfiguracionRepository — without this export, that's a "type not
    // exported" warning even though everything involved lives in this one module.
    exports com.sibim.repository;
    exports com.sibim.session;
    exports com.sibim.config;
    exports com.sibim.util;
}
