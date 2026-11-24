package com.sibim.util;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableView;
import javafx.scene.control.skin.TableColumnHeader;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The title of every column whole, and every column in view. At 1366×768 the tables
 * came out with "Sol…", "Cantid…", "Valor U…" or "F…" for titles (five of eleven in
 * Bajas): the minimum widths written in each screen didn't know the header's padding
 * nor the size of the text. Each column measures its title as drawn; when the minimums
 * then add up to more than the table, the columns that had more than their title needs
 * (Nombre, Área, Motivo: free text, cut with "…" and whole on hover) give up the
 * difference, and the widths are shared so that they fill the table exactly: the last
 * column doesn't go out of sight. When not even the titles fit, the table stays as the
 * screen wrote it. Measured again when the window changes size.
 */
public final class ColumnasLegibles {

    /** Room for the sort arrow next to the title, in the column the table is sorted by. */
    private static final double FLECHA = 14;
    /** The vertical scroll bar and the table's border. */
    private static final double MARGEN = 18;
    private static final String MIN_ORIGINAL = "columnasLegibles.min";
    private static final String PREF_ORIGINAL = "columnasLegibles.pref";

    private ColumnasLegibles() {}

    /** Every table inside {@code raiz}, as soon as its headers exist. */
    public static void instalarTodas(Node raiz) {
        if (raiz != null) buscar(raiz);
    }

    /** lookupAll doesn't go into a ScrollPane before it is shown (Inicio is one): its
     *  content, and each tab's, are walked by hand. */
    private static void buscar(Node n) {
        if (n instanceof TableView<?> t) instalar(t);
        if (n instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) buscar(sp.getContent());
        if (n instanceof javafx.scene.control.TabPane tp)
            for (var tab : tp.getTabs()) if (tab.getContent() != null) buscar(tab.getContent());
        if (n instanceof javafx.scene.control.TitledPane tp && tp.getContent() != null) buscar(tp.getContent());
        if (n instanceof javafx.scene.Parent p)
            for (Node c : p.getChildrenUnmodifiable()) buscar(c);
    }

    public static void instalar(TableView<?> tabla) {
        if (tabla.getProperties().putIfAbsent(ColumnasLegibles.class, Boolean.TRUE) != null) return;
        PauseTransition espera = new PauseTransition(Duration.millis(120));
        espera.setOnFinished(e -> ajustar(tabla));
        Runnable luego = () -> Platform.runLater(espera::playFromStart);
        if (tabla.getSkin() == null) tabla.skinProperty().addListener((o, antes, skin) -> { if (skin != null) luego.run(); });
        else luego.run();
        tabla.widthProperty().addListener((o, antes, ancho) -> luego.run());
        // A column shown from the columns menu gets its header then.
        tabla.getVisibleLeafColumns().addListener((ListChangeListener<Object>) c -> luego.run());
        // The arrow shows only in the column sorted by: that one needs its room.
        tabla.getSortOrder().addListener((ListChangeListener<Object>) c -> luego.run());
    }

    /** A visible column: what the screen asked for (minimum and width), its maximum and
     *  what its title needs. */
    record Columna(TableColumnBase<?, ?> col, double original, double pref, double max, double titulo, boolean fija) {
        Columna(TableColumnBase<?, ?> col, double original, double pref, double max, double titulo) {
            this(col, original, pref, max, titulo, false);
        }
        double meta() { return Math.max(original, titulo); }
        /** What it can shrink to: its title; all it asked for when it holds a pill or a date. */
        double piso() { return fija ? meta() : Math.min(titulo, meta()); }
    }

    private static final String FIJA = "columnasLegibles.fija";

    /** Columns whose content doesn't shrink: a pill («Transferencia») or a date cut to
     *  «Transfere…» or «13 nov 14…» says nothing (19-nov-2026, with «Texto: Grande»). */
    public static void fijas(TableColumnBase<?, ?>... columnas) {
        for (TableColumnBase<?, ?> c : columnas) if (c != null) c.getProperties().put(FIJA, Boolean.TRUE);
    }

    static void ajustar(TableView<?> tabla) {
        Insets borde = tabla.getInsets();
        double disponible = tabla.getWidth() - borde.getLeft() - borde.getRight() - MARGEN;
        if (disponible <= 0) return;
        Map<TableColumnBase<?, ?>, TableColumnHeader> cabeceras = new HashMap<>();
        for (Node n : tabla.lookupAll(".column-header"))
            if (n instanceof TableColumnHeader h && h.getTableColumn() != null) cabeceras.put(h.getTableColumn(), h);
        List<Columna> cols = new ArrayList<>();
        // What each screen asked for is in pixels for «Normal» text: with «Grande» a date or a pill
        // («Transferencia», «Inicio de sesión») came out cut (19-nov-2026); it grows with the text.
        double escala = EscalaTexto.factorActual();
        for (TableColumnBase<?, ?> col : tabla.getVisibleLeafColumns()) {
            double original = escala * (double) col.getProperties().computeIfAbsent(MIN_ORIGINAL, k -> col.getMinWidth());
            double pref = escala * (double) col.getProperties().computeIfAbsent(PREF_ORIGINAL, k -> col.getPrefWidth());
            double titulo = original;
            TableColumnHeader h = cabeceras.get(col);
            if (h != null && col.getText() != null && !col.getText().isBlank() && h.lookup(".label") instanceof Label t) {
                Insets pad = h.getInsets();
                boolean ordena = tabla.getSortOrder().contains(col);
                titulo = Math.ceil(t.prefWidth(-1) + pad.getLeft() + pad.getRight() + (ordena ? FLECHA : 0) + 2);
            }
            cols.add(new Columna(col, original, pref, col.getMaxWidth(), titulo, Boolean.TRUE.equals(col.getProperties().get(FIJA))));
        }
        if (cols.isEmpty()) return;
        double metas = 0, titulos = 0;
        for (Columna c : cols) { metas += c.meta(); titulos += c.piso(); }
        if (titulos > disponible) {
            // Not even the titles: each column at least its title (and a fixed one all it asked
            // for), and the table scrolls sideways. Depreciación with «Texto: Grande» read «Vida …».
            for (Columna c : cols) {
                double m = Math.ceil(c.piso());
                if (c.col().getMaxWidth() < m) c.col().setMaxWidth(m);
                c.col().setMinWidth(m);
                if (c.col().getWidth() < m) c.col().setPrefWidth(m);
            }
            return;
        }
        double[] mins = minimos(cols, disponible, metas, titulos);
        double[] anchos = anchos(cols, mins, disponible);
        for (int i = 0; i < cols.size(); i++) {
            TableColumnBase<?, ?> col = cols.get(i).col();
            double m = Math.floor(mins[i]), w = Math.floor(anchos[i]);
            if (col.getMaxWidth() < m) col.setMaxWidth(m);
            col.setMinWidth(m);
            // The width itself: the resize policy only shares it out again when the window
            // changes size, and a column kept its old width while the last one went out.
            if (Math.abs(col.getWidth() - w) >= 1) col.setPrefWidth(w);
        }
    }

    /** Each column its title and, while there is room, what the screen asked for: what
     *  doesn't fit is taken from the columns that asked for more than their title, in
     *  proportion. Without room even for the titles, what the screen asked for. */
    static double[] minimos(List<Columna> cols, double disponible, double metas, double titulos) {
        double[] m = new double[cols.size()];
        boolean caben = metas <= disponible;
        boolean niLosTitulos = titulos > disponible;
        double f = caben || niLosTitulos || metas == titulos ? 1 : (disponible - titulos) / (metas - titulos);
        for (int i = 0; i < m.length; i++) {
            Columna c = cols.get(i);
            if (niLosTitulos) m[i] = c.original();
            else {
                double piso = c.piso();
                m[i] = piso + (c.meta() - piso) * f;
            }
        }
        return m;
    }

    /** Widths that fill the table: each column its minimum, then what is left shared by
     *  how much more each one asked for, and whatever still remains to the widest ones. */
    static double[] anchos(List<Columna> cols, double[] mins, double disponible) {
        int n = cols.size();
        double[] w = mins.clone();
        double usado = 0;
        for (double x : w) usado += x;
        double resto = disponible - usado;
        if (resto <= 0) return w;
        double[] quiere = new double[n];
        double quieren = 0;
        for (int i = 0; i < n; i++) {
            quiere[i] = Math.max(0, Math.min(cols.get(i).pref(), cols.get(i).max()) - w[i]);
            quieren += quiere[i];
        }
        double parte = Math.min(1, quieren > 0 ? resto / quieren : 0);
        for (int i = 0; i < n; i++) { w[i] += quiere[i] * parte; resto -= quiere[i] * parte; }
        // Still room: to the ones that can grow, by the width they asked for.
        for (int vuelta = 0; vuelta < 3 && resto > 0.5; vuelta++) {
            double peso = 0;
            for (int i = 0; i < n; i++) if (w[i] < cols.get(i).max()) peso += Math.max(1, cols.get(i).pref());
            if (peso == 0) break;
            double repartido = 0;
            for (int i = 0; i < n; i++) {
                if (w[i] >= cols.get(i).max()) continue;
                double mas = Math.min(cols.get(i).max() - w[i], resto * Math.max(1, cols.get(i).pref()) / peso);
                w[i] += mas;
                repartido += mas;
            }
            resto -= repartido;
        }
        return w;
    }
}
