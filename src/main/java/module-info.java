module com.sibim {
    requires javafx.controls;
    requires javafx.fxml;

    // Not JavaFX-Swing interop (no SwingNode/JFXPanel usage) — this is what
    // exposes java.awt.* (Graphics2D, BufferedImage, ...) that ImageUtils
    // uses directly for product-photo resizing.
    requires java.desktop;

    requires java.sql;
    requires java.net.http;
    requires com.zaxxer.hikari;
    requires org.postgresql.jdbc;
    requires org.xerial.sqlitejdbc;

    requires bcrypt;

    requires org.apache.poi.ooxml;
    requires kernel;
    requires layout;
    requires io;
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires io.github.cdimascio.dotenv.java;
    requires atlantafx.base;

    opens com.sibim to javafx.fxml;
    opens com.sibim.controller to javafx.fxml;
    opens com.sibim.model to javafx.base;
    opens com.sibim.model.enums to javafx.base;

    exports com.sibim;
    exports com.sibim.controller;
    exports com.sibim.model;
    exports com.sibim.model.enums;
    exports com.sibim.service;
    exports com.sibim.session;
    exports com.sibim.config;
    exports com.sibim.util;
}
