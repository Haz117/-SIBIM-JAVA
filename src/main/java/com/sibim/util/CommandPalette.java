package com.sibim.util;

import com.sibim.service.CategoriaService;
import com.sibim.service.ProductoService;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class CommandPalette {

    public record NavEntry(String icon, String color, String label, String shortcut, Runnable action) {}
    private record Item(String icon, String color, String label, String subtitle, Runnable action) {}

    private CommandPalette() {}

    public static void show(StackPane outerStack, List<NavEntry> navEntries,
                            ProductoService productoSvc, CategoriaService categoriaSvc) {
        // Guard: don't open a second palette
        if (outerStack.getChildren().stream()
                .anyMatch(n -> n.getStyleClass().contains("cmd-backdrop"))) return;
        Platform.runLater(() -> buildAndShow(outerStack, navEntries, productoSvc, categoriaSvc));
    }

    private static void buildAndShow(StackPane outerStack, List<NavEntry> navEntries,
                                     ProductoService productoSvc, CategoriaService categoriaSvc) {

        // Pre-resolve module navigation actions from navEntries
        Runnable goProductos = navEntries.stream()
            .filter(ne -> ne.label().toLowerCase().contains("bien"))
            .map(NavEntry::action).findFirst().orElse(() -> {});
        Runnable goCategorias = navEntries.stream()
            .filter(ne -> ne.label().toLowerCase().contains("categor"))
            .map(NavEntry::action).findFirst().orElse(() -> {});

        // ── Search field ──────────────────────────────────────────────────────
        FontIcon searchIcon = new FontIcon("mdi2m-magnify");
        searchIcon.setIconSize(20);
        searchIcon.setIconColor(Color.web("#94A3B8"));

        TextField tf = new TextField();
        tf.getStyleClass().add("cmd-search-field");
        tf.setPromptText("Buscar o navegar…");
        HBox.setHgrow(tf, Priority.ALWAYS);

        Label kbdEsc = new Label("esc");
        kbdEsc.getStyleClass().add("cmd-kbd");

        HBox inputRow = new HBox(12, searchIcon, tf, kbdEsc);
        inputRow.getStyleClass().add("cmd-input-row");
        inputRow.setAlignment(Pos.CENTER_LEFT);

        // ── Results ───────────────────────────────────────────────────────────
        VBox resultsBox = new VBox(2);
        resultsBox.getStyleClass().add("cmd-results-box");

        ScrollPane scroll = new ScrollPane(resultsBox);
        scroll.getStyleClass().add("cmd-scroll");
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(368);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // Transparent bg handled by .cmd-scroll in CSS

        // ── Panel ─────────────────────────────────────────────────────────────
        VBox panel = new VBox(0, inputRow, scroll);
        panel.getStyleClass().add("cmd-panel");
        panel.setMaxWidth(620);
        panel.setMinWidth(400);

        // ── Backdrop ──────────────────────────────────────────────────────────
        StackPane backdrop = new StackPane(panel);
        backdrop.getStyleClass().add("cmd-backdrop");
        StackPane.setAlignment(panel, Pos.TOP_CENTER);
        StackPane.setMargin(panel, new Insets(72, 0, 0, 0));

        // ── Item tracking ─────────────────────────────────────────────────────
        List<HBox> itemNodes = new ArrayList<>();
        int[] selIdx = {-1};

        // ── Dismiss ───────────────────────────────────────────────────────────
        boolean[] dismissed = {false};
        Runnable dismiss = () -> {
            if (dismissed[0]) return;
            dismissed[0] = true;
            FadeTransition ft = new FadeTransition(Duration.millis(160), backdrop);
            ft.setToValue(0);
            ScaleTransition sc = new ScaleTransition(Duration.millis(160), panel);
            sc.setToY(0.96); sc.setInterpolator(Interpolator.EASE_IN);
            TranslateTransition tr = new TranslateTransition(Duration.millis(160), panel);
            tr.setToY(-8); tr.setInterpolator(Interpolator.EASE_IN);
            ParallelTransition exit = new ParallelTransition(ft, sc, tr);
            exit.setOnFinished(e -> outerStack.getChildren().remove(backdrop));
            exit.play();
        };

        // ── Build result row ──────────────────────────────────────────────────
        Function<Item, HBox> makeRow = item -> {
            FontIcon ico = new FontIcon(item.icon());
            ico.setIconSize(16);
            ico.setIconColor(Color.web(item.color()));

            StackPane iconWrap = new StackPane(ico);
            iconWrap.getStyleClass().add("cmd-item-icon");
            iconWrap.setPrefSize(32, 32);
            iconWrap.setMaxSize(32, 32);
            iconWrap.setStyle("-fx-background-color: " + hexToRgba(item.color(), 0.10)
                + "; -fx-background-radius: 7;");

            Label lbl = new Label(item.label());
            lbl.getStyleClass().add("cmd-item-label");
            HBox.setHgrow(lbl, Priority.ALWAYS);

            HBox row;
            if (item.subtitle() != null && !item.subtitle().isBlank()) {
                Label sub = new Label(item.subtitle());
                sub.getStyleClass().add("cmd-item-sub");
                row = new HBox(11, iconWrap, lbl, sub);
            } else {
                row = new HBox(11, iconWrap, lbl);
            }
            row.getStyleClass().add("cmd-item");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setCursor(Cursor.HAND);
            row.setUserData(item.action());

            row.setOnMouseEntered(e -> {
                itemNodes.forEach(n -> n.getStyleClass().remove("cmd-item-selected"));
                selIdx[0] = itemNodes.indexOf(row);
                row.getStyleClass().add("cmd-item-selected");
            });
            row.setOnMouseExited(e -> row.getStyleClass().remove("cmd-item-selected"));
            row.setOnMouseClicked(e -> { dismiss.run(); item.action().run(); });
            return row;
        };

        Function<String, Label> makeGroup = title -> {
            Label g = new Label(title);
            g.getStyleClass().add("cmd-group-label");
            g.setMaxWidth(Double.MAX_VALUE);
            return g;
        };

        // ── Populate ──────────────────────────────────────────────────────────
        Runnable[] renderRef = {null};
        Runnable populateNav = () -> {
            itemNodes.clear();
            selIdx[0] = -1;
            resultsBox.getChildren().clear();
            resultsBox.getChildren().add(makeGroup.apply("NAVEGAR A"));
            for (NavEntry ne : navEntries) {
                HBox row = makeRow.apply(
                    new Item(ne.icon(), ne.color(), ne.label(), ne.shortcut(), ne.action()));
                itemNodes.add(row);
                resultsBox.getChildren().add(row);
            }
        };
        renderRef[0] = populateNav;
        populateNav.run();

        // ── Debounced search ──────────────────────────────────────────────────
        PauseTransition debounce = new PauseTransition(Duration.millis(190));
        debounce.setOnFinished(ev -> {
            String q = tf.getText().trim().toLowerCase();
            if (q.isEmpty()) { populateNav.run(); return; }

            List<Item> matchedNav = navEntries.stream()
                .filter(ne -> ne.label().toLowerCase().contains(q))
                .map(ne -> new Item(ne.icon(), ne.color(), ne.label(), ne.shortcut(), ne.action()))
                .toList();

            AppExecutor.submit(() -> {
                List<Item> bienes = new ArrayList<>();
                List<Item> cats   = new ArrayList<>();
                try {
                    productoSvc.getAll().stream()
                        .filter(p -> p.getNombre().toLowerCase().contains(q)
                            || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(q))
                            || (p.getArea()   != null && p.getArea().toLowerCase().contains(q)))
                        .limit(5)
                        .forEach(p -> {
                            String sub = (p.getArea() != null ? p.getArea() : "")
                                + (p.getCodigo() != null && !p.getCodigo().isBlank()
                                    ? "  ·  " + p.getCodigo() : "");
                            bienes.add(new Item("mdi2p-package-variant", "#6366F1",
                                p.getNombre(), sub.isBlank() ? null : sub.strip(), goProductos));
                        });
                } catch (Exception ignored) {}
                try {
                    categoriaSvc.findAll().stream()
                        .filter(c -> c.getNombre().toLowerCase().contains(q))
                        .limit(4)
                        .forEach(c -> cats.add(new Item("mdi2t-tag-outline",
                            c.getColor() != null && !c.getColor().isBlank() ? c.getColor() : "#7C3AED",
                            c.getNombre(), null, goCategorias)));
                } catch (Exception ignored) {}

                Platform.runLater(() -> {
                    itemNodes.clear();
                    selIdx[0] = -1;
                    resultsBox.getChildren().clear();

                    boolean any = false;
                    if (!matchedNav.isEmpty()) {
                        any = true;
                        resultsBox.getChildren().add(makeGroup.apply("NAVEGAR A"));
                        for (Item it : matchedNav) {
                            HBox row = makeRow.apply(it); itemNodes.add(row);
                            resultsBox.getChildren().add(row);
                        }
                    }
                    if (!bienes.isEmpty()) {
                        any = true;
                        resultsBox.getChildren().add(makeGroup.apply("BIENES"));
                        for (Item it : bienes) {
                            HBox row = makeRow.apply(it); itemNodes.add(row);
                            resultsBox.getChildren().add(row);
                        }
                    }
                    if (!cats.isEmpty()) {
                        any = true;
                        resultsBox.getChildren().add(makeGroup.apply("CATEGORÍAS"));
                        for (Item it : cats) {
                            HBox row = makeRow.apply(it); itemNodes.add(row);
                            resultsBox.getChildren().add(row);
                        }
                    }
                    if (!any) {
                        Label empty = new Label("Sin resultados para \"" + tf.getText().trim() + "\"");
                        empty.getStyleClass().add("cmd-empty");
                        empty.setMaxWidth(Double.MAX_VALUE);
                        resultsBox.getChildren().add(empty);
                    }
                });
            });
        });

        tf.textProperty().addListener((obs, ov, nv) -> { debounce.stop(); debounce.playFromStart(); });

        // ── Keyboard nav ──────────────────────────────────────────────────────
        tf.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ESCAPE -> { dismiss.run(); e.consume(); }
                case ENTER  -> {
                    if (selIdx[0] >= 0 && selIdx[0] < itemNodes.size()) {
                        Runnable act = (Runnable) itemNodes.get(selIdx[0]).getUserData();
                        dismiss.run(); act.run();
                    }
                    e.consume();
                }
                case DOWN -> {
                    if (!itemNodes.isEmpty()) {
                        itemNodes.forEach(n -> n.getStyleClass().remove("cmd-item-selected"));
                        selIdx[0] = (selIdx[0] < itemNodes.size() - 1) ? selIdx[0] + 1 : 0;
                        itemNodes.get(selIdx[0]).getStyleClass().add("cmd-item-selected");
                    }
                    e.consume();
                }
                case UP -> {
                    if (!itemNodes.isEmpty()) {
                        itemNodes.forEach(n -> n.getStyleClass().remove("cmd-item-selected"));
                        selIdx[0] = (selIdx[0] > 0) ? selIdx[0] - 1 : itemNodes.size() - 1;
                        itemNodes.get(selIdx[0]).getStyleClass().add("cmd-item-selected");
                    }
                    e.consume();
                }
                default -> {}
            }
        });

        // Click outside panel → dismiss
        backdrop.setOnMouseClicked(e -> {
            if (!panel.getBoundsInParent().contains(e.getX(), e.getY()))
                dismiss.run();
        });

        outerStack.getChildren().add(backdrop);
        Platform.runLater(tf::requestFocus);

        // ── Entrance animation ────────────────────────────────────────────────
        backdrop.setOpacity(0);
        panel.setTranslateY(-14);
        panel.setScaleY(0.95);
        FadeTransition ft = new FadeTransition(Duration.millis(190), backdrop);
        ft.setFromValue(0); ft.setToValue(1);
        TranslateTransition tr = new TranslateTransition(Duration.millis(220), panel);
        tr.setFromY(-14); tr.setToY(0); tr.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition sc = new ScaleTransition(Duration.millis(220), panel);
        sc.setFromY(0.95); sc.setToY(1); sc.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(ft, tr, sc).play();
    }

    private static String hexToRgba(String hex, double alpha) {
        hex = hex.replace("#", "");
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return String.format("rgba(%d,%d,%d,%.2f)", r, g, b, alpha);
    }
}
