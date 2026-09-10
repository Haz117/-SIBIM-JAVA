package com.sibim.util;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class SkeletonUtil {
    public static VBox skeletonRows(int count) {
        VBox box = new VBox(6);
        box.getStyleClass().add("skeleton-container");
        for (int i = 0; i < count; i++) {
            HBox row = new HBox(12);
            row.getStyleClass().add("skeleton-row");
            Region r1 = skelRect(80);
            Region r2 = skelRect(-1);
            HBox.setHgrow(r2, Priority.ALWAYS);
            Region r3 = skelRect(60);
            Region r4 = skelRect(60);
            row.getChildren().addAll(r1, r2, r3, r4);
            box.getChildren().add(row);
        }
        return box;
    }
    private static Region skelRect(double width) {
        Region r = new Region();
        r.getStyleClass().add("skeleton-block");
        r.setPrefHeight(18);
        if (width > 0) r.setPrefWidth(width);
        return r;
    }
}
