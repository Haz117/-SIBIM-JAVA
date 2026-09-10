package com.sibim.util;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

public class EmptyStateUtil {
    public static VBox build(String iconCode, String title, String subtitle) {
        FontIcon icon = new FontIcon(iconCode);
        icon.getStyleClass().add("empty-state-icon");
        Label lTitle = new Label(title);
        lTitle.getStyleClass().add("empty-state-title");
        Label lSub = new Label(subtitle);
        lSub.getStyleClass().add("empty-state-subtitle");
        lSub.setWrapText(true);
        lSub.setMaxWidth(320);
        VBox box = new VBox(10, icon, lTitle, lSub);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);
        return box;
    }
}
