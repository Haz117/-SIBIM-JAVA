package com.sibim.util;

import javafx.animation.PauseTransition;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.util.function.Consumer;

public final class SearchUtils {

    private SearchUtils() {}

    /**
     * Wires a debounced text listener on {@code field}: {@code onSearch} runs {@code millis}
     * after the user stops typing, instead of on every keystroke.
     */
    public static void debounce(TextField field, int millis, Consumer<String> onSearch) {
        PauseTransition pause = new PauseTransition(Duration.millis(millis));
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            pause.setOnFinished(e -> onSearch.accept(newVal));
            pause.playFromStart();
        });
    }
}
