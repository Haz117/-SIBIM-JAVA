package com.sibim.util;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/** An área in a table column that keeps what tells it apart. Cut at the end, the ten subáreas of
 *  Seguridad Pública all read «Seguridad Pública ...» in Bienes at 1366 px (27-nov-2026), and
 *  cut in the middle by JavaFX they read «Seguridad...rmamento». A subárea that doesn't fit
 *  shortens the área above it instead: «S. Pública — Armamento», «Tes. — Cuenta Pública», and
 *  then only «Armamento»; the whole name, on hover. */
public final class CeldaArea {

    private CeldaArea() {}

    public static <S> void instalar(TableColumn<S, String> columna) {
        if (columna == null) return;
        columna.setCellFactory(c -> new TableCell<>() {
            {
                widthProperty().addListener((o, a, b) -> mostrar());
            }

            @Override
            protected void updateItem(String area, boolean empty) {
                super.updateItem(area, empty);
                mostrar();
            }

            private void mostrar() {
                String area = isEmpty() ? null : getItem();
                if (area == null || area.isBlank()) {
                    setText(area);
                    setTooltip(null);
                    return;
                }
                double ancho = getWidth() - snappedLeftInset() - snappedRightInset() - 2;
                String visto = ancho > 0 ? queCabe(area, getFont(), ancho) : area;
                setText(visto);
                if (visto.equals(area)) {
                    if (getTooltip() != null && getTooltip().getProperties().containsKey(CeldaArea.class)) setTooltip(null);
                } else if (getTooltip() == null || !area.equals(getTooltip().getText())) {
                    Tooltip t = new Tooltip(area);
                    t.getProperties().put(CeldaArea.class, Boolean.TRUE);
                    setTooltip(t);
                }
            }
        });
    }

    /** The first of {@link #formas} that fits in {@code ancho} with {@code fuente}; the last one
     *  (the subárea alone) when none does, which the cell then cuts at the end as before. */
    static String queCabe(String area, Font fuente, double ancho) {
        List<String> formas = formas(area);
        for (String f : formas) if (ancho(f, fuente) <= ancho) return f;
        return formas.get(formas.size() - 1);
    }

    /** «Seguridad Pública — Armamento», «S. Pública — Armamento», «Armamento»: from the whole name
     *  to the subárea alone. An área without « — » only has its name. */
    static List<String> formas(String area) {
        List<String> out = new ArrayList<>();
        out.add(area);
        int k = area.indexOf(" — ");
        if (k <= 0 || k + 3 >= area.length()) return out;
        String arriba = area.substring(0, k).trim(), sub = area.substring(k + 3).trim();
        String[] palabras = arriba.split("\\s+");
        if (palabras.length >= 2) {
            // Initials of the words with a capital, but the last one whole: «S. O. Públicas».
            StringBuilder corto = new StringBuilder();
            for (int i = 0; i < palabras.length - 1; i++)
                if (!palabras[i].isEmpty() && Character.isUpperCase(palabras[i].charAt(0)))
                    corto.append(palabras[i].charAt(0)).append(". ");
            out.add(corto + palabras[palabras.length - 1] + " — " + sub);
        } else if (arriba.length() > 4) {
            out.add(arriba.substring(0, 3) + ". — " + sub);
        }
        out.add(sub);
        return out;
    }

    private static double ancho(String s, Font fuente) {
        Text t = new Text(s);
        if (fuente != null) t.setFont(fuente);
        return t.getLayoutBounds().getWidth();
    }
}
