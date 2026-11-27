package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.QrUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/**
 * What a window showing a bien's QR holds: the header of the other windows and the QR as on its
 * label — the código large in guinda, the name on up to three lines and its área — with what
 * scanning it does. The same from Bienes › QR and from the ficha's QR (27-nov-2026, «los QR se ven
 * sin diseño»: a bare QR with the código in small type and the name on one line cut at the edge).
 */
public final class QrDelBien {

    private QrDelBien() {}

    public static Node vista(Producto p, String contenido, Image qr) {
        boolean enlace = contenido != null && contenido.startsWith("http");
        HBox header = DialogUtil.gradientHeader("mdi2q-qrcode", "Código QR del bien",
            enlace ? "Con un teléfono abre la ficha del bien"
                   : "Con un teléfono muestra quién lo tiene, dónde está y su estado",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        ImageView iv = new ImageView(qr);
        iv.setFitWidth(230); iv.setFitHeight(230); iv.setPreserveRatio(true); iv.setSmooth(false);
        Label codigo = new Label(p.getCodigo());
        codigo.getStyleClass().add("qr-codigo");
        String n = p.getNombre() == null ? "" : p.getNombre().trim();
        Label nombre = new Label(n.length() > 140 ? n.substring(0, 139).trim() + "…" : n);
        nombre.getStyleClass().add("qr-nombre");
        nombre.setWrapText(true);
        nombre.setMaxWidth(320);
        nombre.setMinHeight(Region.USE_PREF_SIZE);
        nombre.setTextAlignment(TextAlignment.CENTER);
        VBox tarjeta = new VBox(4, iv, codigo, nombre);
        if (p.getArea() != null && !p.getArea().isBlank()) {
            Label area = new Label(p.getArea());
            area.getStyleClass().add("qr-area");
            tarjeta.getChildren().add(area);
        }
        tarjeta.getStyleClass().add("qr-tarjeta");
        tarjeta.setAlignment(Pos.CENTER);
        tarjeta.setMaxWidth(360);

        Label queHace = new Label(QrUtils.queHace(contenido));
        queHace.getStyleClass().add("muted-sm");
        queHace.setWrapText(true);
        queHace.setMaxWidth(340);
        queHace.setMinHeight(Region.USE_PREF_SIZE);
        queHace.setTextAlignment(TextAlignment.CENTER);

        VBox cuerpo = new VBox(12, tarjeta, queHace);
        cuerpo.setAlignment(Pos.CENTER);
        cuerpo.setPadding(new Insets(14, 20, 8, 20));
        return new VBox(header, cuerpo);
    }
}
