module com.sibim {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;

    requires java.sql;
    requires com.zaxxer.hikari;
    requires org.postgresql.jdbc;

    requires bcrypt;

    requires org.apache.poi.ooxml;
    requires kernel;
    requires layout;
    requires io;
    requires org.slf4j;
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
