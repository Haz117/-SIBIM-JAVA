package com.sibim.controller.dialogs;

import com.sibim.service.CalidadDatosService;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Under a field where a person's name is typed: "¿Es «TANIA EJEMPLO FICTICIO»?" when what
 * is typed looks like someone already in the inventory under another spelling (a title
 * more or less, a letter apart), with a link that writes that spelling. Between 28-oct and
 * 2-nov-2026 four people got a second spelling this way, each one from a transfer.
 * It only suggests: a new person with a close name stays as typed.
 */
public final class AvisoNombreParecido extends HBox {

    private final Label texto = new Label();
    private final Hyperlink usar = new Hyperlink("Usar ese nombre");
    private final TextInputControl campo;
    private final Supplier<Collection<String>> conocidos;
    private String sugerido;

    /** @param conocidos the names already in use, read each time the text changes */
    public AvisoNombreParecido(TextInputControl campo, Supplier<Collection<String>> conocidos) {
        super(4);
        this.campo = campo;
        this.conocidos = conocidos;
        texto.getStyleClass().addAll("field-hint", "field-hint-warn");
        texto.setWrapText(true);
        usar.getStyleClass().add("field-hint");
        usar.setOnAction(e -> {
            String nombre = sugerido;   // writing it checks again and clears the suggestion
            if (nombre == null) return;
            campo.setText(nombre);
            campo.positionCaret(nombre.length());
        });
        getChildren().addAll(texto, usar);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("aviso-nombre-parecido");
        mostrar(false);
        campo.textProperty().addListener((o, antes, ahora) -> revisar());
        revisar();
    }

    /** Looks again, e.g. once the list of names has loaded. */
    public void revisar() {
        Optional<String> parecido = CalidadDatosService.parecidoA(campo.getText(), conocidos.get());
        sugerido = parecido.orElse(null);
        parecido.ifPresent(n -> texto.setText("¿Es «" + n + "»? Así está escrito en el inventario."));
        mostrar(parecido.isPresent());
    }

    /** The spelling it proposes now, or null. */
    public String sugerido() { return sugerido; }

    private void mostrar(boolean si) {
        setVisible(si);
        setManaged(si);
    }
}
