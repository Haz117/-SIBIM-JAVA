package com.sibim.util;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.util.List;

public final class PaginationUtils {

    private PaginationUtils() {}

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

        if (lblTotal != null) lblTotal.setText(total + (total == 1 ? " " + singular : " " + plural));
        if (lblPage != null) lblPage.setText(total == 0 ? "—"
            : "Pág " + (clampedPage + 1) + " de " + totalPages + "  ·  " + (from + 1) + "–" + to);
        if (btnPrev != null) btnPrev.setDisable(clampedPage == 0);
        if (btnNext != null) btnNext.setDisable(clampedPage >= totalPages - 1);
    }
}
