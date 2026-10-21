package com.sibim.util;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;

import java.util.List;

public final class PaginationUtils {

    private PaginationUtils() {}

    /** "Pág 2 de 5", with the rows shown ("26–50 de 120") in its tooltip: the
     *  short text leaves room for the bottom bar's buttons to keep their names. */
    private static void mostrarPagina(Label lblPage, int pagina, int totalPages, int from, int to, int total) {
        if (lblPage == null) return;
        lblPage.setText(total == 0 ? "—" : "Pág " + (pagina + 1) + " de " + totalPages);
        String filas = total == 0 ? "Sin resultados" : (from + 1) + "–" + to + " de " + total;
        if (lblPage.getTooltip() == null) lblPage.setTooltip(new Tooltip(filas));
        else lblPage.getTooltip().setText(filas);
    }

    /** A row faded out on delete or baja (AnimationUtils.fadeOut) keeps that
     *  opacity when the TableView reuses its node for another item, and would
     *  show as a blank row: every realized row is made visible again. */
    private static void mostrarFilas(TableView<?> table) {
        Platform.runLater(() -> table.lookupAll(".table-row-cell").forEach(r -> {
            r.setOpacity(1);
            r.setTranslateY(0);
        }));
    }

    /**
     * Slices {@code filteredData} into the page identified by {@code currentPage}/{@code pageSize},
     * pushes it into {@code table}, and updates the total/page labels and prev/next button state.
     * All UI parameters are null-safe (skipped if not present in a given screen).
     */
    public static <T> void updatePage(TableView<T> table, List<T> filteredData, int currentPage, int pageSize,
                                       Label lblTotal, Label lblPage, Button btnPrev, Button btnNext,
                                       String singular, String plural) {
        int total = filteredData.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
        // Defensive clamp: currentPage is caller-owned state that isn't
        // always reset when filteredData shrinks out from under it (e.g. a
        // filter/search narrows the list while a later page was active) —
        // without this, `from` could exceed `total` and subList() below
        // throws IllegalArgumentException instead of just showing page 1.
        int clampedPage = Math.max(0, Math.min(currentPage, totalPages - 1));
        int from = clampedPage * pageSize;
        int to = Math.min(from + pageSize, total);

        table.setItems(FXCollections.observableArrayList(filteredData.subList(from, to)));
        mostrarFilas(table);

        if (lblTotal != null) lblTotal.setText(total + (total == 1 ? " " + singular : " " + plural));
        mostrarPagina(lblPage, clampedPage, totalPages, from, to, total);
        if (btnPrev != null) btnPrev.setDisable(clampedPage == 0);
        if (btnNext != null) btnNext.setDisable(clampedPage >= totalPages - 1);
    }

    /**
     * Server-side pagination variant: {@code pageData} already contains exactly
     * the rows for the current page (fetched via LIMIT/OFFSET) and
     * {@code totalFiltered} is the COUNT(*) result from the DB.  No slicing is
     * done here — the list is pushed into the table as-is.
     */
    public static <T> void updatePageServer(TableView<T> table, List<T> pageData,
            int currentPage, int pageSize, int totalFiltered,
            Label lblTotal, Label lblPage, Button btnPrev, Button btnNext,
            String singular, String plural) {
        table.setItems(FXCollections.observableArrayList(pageData));
        mostrarFilas(table);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalFiltered / pageSize));
        int clampedPage = Math.max(0, Math.min(currentPage, totalPages - 1));
        int from = clampedPage * pageSize;
        int to = Math.min(from + pageSize, totalFiltered);
        if (lblTotal != null) lblTotal.setText(totalFiltered + (totalFiltered == 1 ? " " + singular : " " + plural));
        mostrarPagina(lblPage, clampedPage, totalPages, from, to, totalFiltered);
        if (btnPrev != null) btnPrev.setDisable(clampedPage == 0);
        if (btnNext != null) btnNext.setDisable(clampedPage >= totalPages - 1);
    }
}
