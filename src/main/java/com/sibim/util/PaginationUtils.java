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
        int from = currentPage * pageSize;
        if (from >= total && total > 0) { from = 0; currentPage = 0; }
        int to = Math.min(from + pageSize, total);
        int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));

        table.setItems(FXCollections.observableArrayList(filteredData.subList(from, to)));

        if (lblTotal != null) lblTotal.setText(total + (total == 1 ? " " + singular : " " + plural));
        if (lblPage != null) lblPage.setText(total == 0 ? "—"
            : "Pág " + (currentPage + 1) + " de " + totalPages + "  ·  " + (from + 1) + "–" + to);
        if (btnPrev != null) btnPrev.setDisable(currentPage == 0);
        if (btnNext != null) btnNext.setDisable(currentPage >= totalPages - 1);
    }
}
