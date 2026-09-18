package com.sibim.controller.dialogs;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.repository.PriceHistoryRepository;
import com.sibim.service.MovimientoService;
import com.sibim.service.ReporteService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.QrUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

/** Read-only detail dialog for a {@link Producto}.
 *  Extracted from ProductosController to keep it under 700 lines. */
public final class ProductoDetailDialog {

    private ProductoDetailDialog() {}

    /** Everything show() needs from the DB, loaded off the FX thread in one batch —
     *  see the comment on show() below for why this exists. */
    private record DetalleData(
        List<Movimiento> movimientos,
        List<String> fotos,
        List<com.sibim.model.Resguardo> resguardos,
        List<com.sibim.model.Prestamo> prestamos,
        List<com.sibim.service.MantenimientoService.Alerta> alertas
    ) {}

    /** Opens the read-only bien detail dialog. Loads movimientos, fotos, resguardos,
     *  préstamos and alertas de mantenimiento — 5 sequential JDBC round-trips — on a
     *  background thread first; doing them inline on the FX thread (as this used to)
     *  freezes the whole app on every double-click of a row in Bienes/Depreciación/
     *  Organigrama for as long as those 5 queries take. */
    public static void show(Producto p, Scene scene, MovimientoService movimientoService, Logger log) {
        DialogUtil.runAsyncWithProgress(scene, "Cargando detalle del bien…",
            () -> {
                List<Movimiento> movimientos = List.of();
                try {
                    List<Movimiento> result = movimientoService.getByProducto(p.getId());
                    if (result != null) movimientos = result;
                } catch (Exception ex) { log.warn("No se pudo cargar historial de movimientos para '{}': {}", p.getCodigo(), ex.getMessage()); }

                List<String> fotosGaleria = List.of();
                try { fotosGaleria = new com.sibim.repository.ProductoRepository().findFotos(p.getId()); }
                catch (Exception ex) { log.warn("No se pudo cargar galería de fotos para '{}': {}", p.getCodigo(), ex.getMessage()); }

                List<com.sibim.model.Resguardo> resguardoHistory = List.of();
                try { resguardoHistory = new com.sibim.service.ResguardoService().getByProductoId(p.getId()); }
                catch (Exception ex) { log.warn("No se pudo cargar historial de resguardos para '{}': {}", p.getCodigo(), ex.getMessage()); }

                List<com.sibim.model.Prestamo> prestamoHistory = List.of();
                try { prestamoHistory = new com.sibim.service.PrestamoService().getByProductoId(p.getId()); }
                catch (Exception ex) { log.warn("No se pudo cargar historial de préstamos para '{}': {}", p.getCodigo(), ex.getMessage()); }

                List<com.sibim.service.MantenimientoService.Alerta> alertas = List.of();
                try { alertas = new com.sibim.service.MantenimientoService().getAlertas(p.getId()); }
                catch (Exception ex) { log.warn("No se pudo cargar alertas de mantenimiento para '{}': {}", p.getCodigo(), ex.getMessage()); }

                return new DetalleData(movimientos, fotosGaleria, resguardoHistory, prestamoHistory, alertas);
            },
            data -> buildAndShow(p, scene, log, data),
            ex -> {
                log.error("No se pudo cargar el detalle del bien '{}'", p.getCodigo(), ex);
                NotificacionUtil.error(scene, "No se pudo cargar el detalle del bien");
            }
        );
    }

    // TableColumn<PriceHistoryRepository.PriceHistoryEntry,?>... varargs to addAll() triggers
    // Java's inherent generic-array-creation warning — inescapable with this API, not a real risk.
    @SuppressWarnings("unchecked")
    private static void buildAndShow(Producto p, Scene scene, Logger log, DetalleData loaded) {
        final List<Movimiento> movs = loaded.movimientos();
        final List<String> _fotosGaleria = loaded.fotos();
        final java.util.List<com.sibim.model.Resguardo> _resguardos = loaded.resguardos();
        final java.util.List<com.sibim.model.Prestamo> _prestamos = loaded.prestamos();

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Detalle del Bien");

        ButtonType fichaBtn = new ButtonType("Imprimir ficha", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(fichaBtn, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(560);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        Button btnFicha = (Button) dialog.getDialogPane().lookupButton(fichaBtn);
        btnFicha.getStyleClass().add("btn-secondary");
        btnFicha.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            DialogUtil.runAsyncWithProgress(
                scene,
                "Generando ficha técnica…",
                () -> new ReporteService().exportFichaTecnica(p, movs),
                file -> DialogUtil.showExportResultDialog(scene, file),
                ex -> NotificacionUtil.error(scene, "No se pudo generar la ficha técnica")
            );
        });

        VBox root = new VBox(14);
        root.setPadding(new Insets(4, 0, 0, 0));

        // ── Header card ────────────────────────────────────────────────
        HBox headerCard = new HBox(14);
        headerCard.setAlignment(Pos.CENTER_LEFT);
        headerCard.setPadding(new Insets(14, 18, 14, 18));
        headerCard.getStyleClass().add("dlg-detail-header");

        // Thumbnail / category monogram
        StackPane thumbPane = new StackPane();
        thumbPane.setMinSize(52, 52); thumbPane.setMaxSize(52, 52);
        boolean photoLoaded = false;
        if (p.getFotoUrl() != null && !p.getFotoUrl().isBlank()) {
            try {
                ImageView iv = new ImageView(
                    new Image(Path.of(p.getFotoUrl()).toUri().toString(), 52, 52, true, true, true));
                iv.setFitWidth(52); iv.setFitHeight(52); iv.setPreserveRatio(true);
                thumbPane.getChildren().add(iv);
                thumbPane.getStyleClass().addAll("dlg-thumb-photo", "foto-cell-box-clickable");
                thumbPane.setOnMouseClicked(e -> DialogUtil.showPhotoViewer(p.getFotoUrl(), p.getNombre()));
                photoLoaded = true;
            } catch (Exception ex) { log.warn("No se pudo cargar thumbnail de detalle: {}", p.getFotoUrl(), ex); }
        }
        if (!photoLoaded) {
            String monogramBg  = p.getCategoriaColor() != null ? p.getCategoriaColor() + "22" : "#EEF2FF";
            String monogramFg  = p.getCategoriaColor() != null ? p.getCategoriaColor() : "#4338CA";
            String monogramTxt = p.getCategoriaNombre() != null && !p.getCategoriaNombre().isBlank()
                ? String.valueOf(p.getCategoriaNombre().charAt(0)).toUpperCase() : "B";
            Label monogram = new Label(monogramTxt);
            monogram.getStyleClass().add("dlg-monogram");
            monogram.setStyle("-fx-text-fill: " + monogramFg + ";");
            thumbPane.getChildren().add(monogram);
            thumbPane.getStyleClass().add("dlg-thumb-monogram");
            thumbPane.setStyle("-fx-background-color: " + monogramBg + ";");
        }

        // Name + meta row
        VBox nameSection = new VBox(5);
        HBox.setHgrow(nameSection, Priority.ALWAYS);

        Label nameLbl = new Label(p.getNombre());
        nameLbl.getStyleClass().add("dlg-detail-name");
        nameLbl.setWrapText(true); nameLbl.setMaxWidth(280);

        Label statusBadge = new Label(p.getEstado().getEtiqueta());
        statusBadge.getStyleClass().add(switch (p.getEstado()) {
            case AGOTADO    -> "dlg-status-danger";
            case BAJO_STOCK -> "dlg-status-warn";
            case VENCIDO    -> "dlg-status-purple";
            default         -> "dlg-status-ok";
        });
        Label codeLbl = new Label("# " + p.getCodigo());
        codeLbl.getStyleClass().add("dlg-detail-code");

        HBox meta = new HBox(8, codeLbl, statusBadge);
        meta.setAlignment(Pos.CENTER_LEFT);
        nameSection.getChildren().addAll(nameLbl, meta);

        // QR thumbnail — small preview, click to expand
        javafx.scene.image.Image qrSmall = QrUtils.generateQr(
            p.getCodigo() != null ? p.getCodigo() : p.getNombre(), 104);
        StackPane qrPane = new StackPane();
        qrPane.setMinSize(52, 52); qrPane.setMaxSize(52, 52);
        qrPane.getStyleClass().add("dlg-qr-thumb");
        if (qrSmall != null) {
            ImageView qrIv = new ImageView(qrSmall);
            qrIv.setFitWidth(44); qrIv.setFitHeight(44); qrIv.setPreserveRatio(true);
            qrPane.getChildren().add(qrIv);
            Tooltip qrTip = new Tooltip("Código QR — clic para ampliar");
            Tooltip.install(qrPane, qrTip);
            qrPane.setOnMouseClicked(e -> showQrPopup(p, qrSmall, scene));
            qrPane.getStyleClass().add("dlg-qr-thumb-clickable");
        }

        headerCard.getChildren().addAll(thumbPane, nameSection, qrPane);

        // ── Detail grid ────────────────────────────────────────────────
        GridPane g = new GridPane();
        g.setHgap(16); g.setVgap(9);
        g.setPadding(new Insets(0, 0, 4, 0));
        g.getColumnConstraints().addAll(colConstraint(140, false), colConstraint(300, true));

        String stockClass = switch (p.getEstado()) {
            case AGOTADO    -> "dlg-detail-stock-low";
            case BAJO_STOCK -> "dlg-detail-stock-warn";
            default         -> "dlg-detail-stock-ok";
        };

        // Build rows dynamically so new fields (marca/modelo/serie) are shown only when present
        record Row(String key, String val, String styleClass) {}
        java.util.ArrayList<Row> rowList = new java.util.ArrayList<>();
        rowList.add(new Row("Categoría",       p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "—", null));
        rowList.add(new Row("Área",            p.getArea() != null ? p.getArea() : "—", null));
        rowList.add(new Row("Resguardante",    p.getResguardante() != null && !p.getResguardante().isBlank() ? p.getResguardante() : "—", null));
        String marca = p.getMarca();
        if (marca != null && !marca.isBlank()) rowList.add(new Row("Marca", marca, null));
        String modelo = p.getModelo();
        if (modelo != null && !modelo.isBlank()) rowList.add(new Row("Modelo", modelo, null));
        String serie = p.getNumeroSerie();
        if (serie != null && !serie.isBlank()) rowList.add(new Row("Número de serie", serie, null));
        rowList.add(new Row("Stock actual",    String.valueOf(p.getStockActual()), stockClass));
        rowList.add(new Row("Stock mín / máx", p.getStockMinimo() + " / " + p.getStockMaximo(), null));
        rowList.add(new Row("Unidad",          p.getUnidad() != null ? p.getUnidad().getEtiqueta() : "—", null));
        rowList.add(new Row("Precio compra",   FormatUtils.formatCurrency(p.getPrecioCompra()), null));
        rowList.add(new Row("Precio venta",    FormatUtils.formatCurrency(p.getPrecioVenta()), null));
        rowList.add(new Row("Valor total",     FormatUtils.formatCurrency(p.getValorTotal()), "dlg-detail-total"));
        rowList.add(new Row("Proveedor",       p.getProveedor() != null ? p.getProveedor() : "—", null));
        rowList.add(new Row("Ubicación",       p.getUbicacion() != null ? p.getUbicacion() : "—", null));
        rowList.add(new Row("Vencimiento",     p.getFechaVencimiento() != null ? FormatUtils.formatDate(p.getFechaVencimiento()) : "—", null));
        if (p.getProximaRevision() != null)
            rowList.add(new Row("Próxima revisión", FormatUtils.formatDate(p.getProximaRevision()), null));
        if (p.getNotasMantenimiento() != null && !p.getNotasMantenimiento().isBlank())
            rowList.add(new Row("Notas mantenimiento", p.getNotasMantenimiento(), null));
        for (int i = 0; i < rowList.size(); i++) {
            Row row = rowList.get(i);
            Label key = DialogUtil.fieldLabel(row.key());
            Label val = new Label(row.val());
            val.setWrapText(true); val.setMaxWidth(310);
            if (row.styleClass() != null) val.getStyleClass().add(row.styleClass());
            g.add(key, 0, i); g.add(val, 1, i);
        }

        // ── Foto de factura (si existe) ────────────────────────────────
        String facturaUrl = p.getFacturaUrl();
        if (facturaUrl != null && !facturaUrl.isBlank()) {
            try {
                Image factImg = new Image(Path.of(facturaUrl).toUri().toString(), 100, 75, true, true, true);
                ImageView factIv = new ImageView(factImg);
                factIv.setFitWidth(100); factIv.setFitHeight(75); factIv.setPreserveRatio(true);
                final String fUrl = facturaUrl;
                javafx.scene.layout.StackPane factPane = new javafx.scene.layout.StackPane(factIv);
                factPane.getStyleClass().addAll("dlg-img-box", "foto-cell-box-clickable");
                factPane.setOnMouseClicked(e -> DialogUtil.showPhotoViewer(fUrl, "Factura — " + p.getNombre()));
                VBox factBox = new VBox(4, DialogUtil.fieldLabel("Foto de factura"), factPane);
                int nextRow = rowList.size();
                g.add(factBox, 0, nextRow, 2, 1);
            } catch (Exception ex) { log.warn("No se pudo cargar foto de factura: {}", facturaUrl, ex); }
        }

        // ── Galería de fotos (solo si hay más de una) ─────────────────
        VBox gallerySection = null;
        if (_fotosGaleria.size() >= 2) {
            final List<String> galFotos = List.copyOf(_fotosGaleria);
            Label galleryTitle = DialogUtil.fieldLabel("Galería de fotos (" + _fotosGaleria.size() + ")");
            HBox thumbnails = new HBox(8);
            thumbnails.setPadding(new Insets(4, 0, 4, 0));
            for (int fi = 0; fi < _fotosGaleria.size(); fi++) {
                final int idx = fi;
                String fotoPath = _fotosGaleria.get(fi);
                try {
                    ImageView iv = new ImageView(
                        new Image(Path.of(fotoPath).toUri().toString(), 90, 70, true, true, true));
                    iv.setFitWidth(90); iv.setFitHeight(70); iv.setPreserveRatio(true);
                    iv.getStyleClass().add("foto-thumbnail-clickable");
                    iv.setOnMouseClicked(e -> openLightbox(galFotos, idx));
                    StackPane cell = new StackPane(iv);
                    cell.getStyleClass().add("dlg-img-box");
                    thumbnails.getChildren().add(cell);
                } catch (Exception ex) { log.warn("No se pudo cargar foto de galería: {}", fotoPath, ex); }
            }
            ScrollPane galleryScroll = new ScrollPane(thumbnails);
            galleryScroll.setFitToHeight(true);
            galleryScroll.setPrefHeight(96);
            galleryScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            galleryScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            galleryScroll.getStyleClass().add("dlg-tabs-scroll");
            gallerySection = new VBox(4, galleryTitle, galleryScroll);
        }

        // ── Depreciación ──────────────────────────────────────────────
        BigDecimal valorDep = p.getValorDepreciado();
        if (valorDep != null) {
            Separator sep = new Separator();
            sep.getStyleClass().add("form-separator");
            root.getChildren().addAll(headerCard, g);
            if (gallerySection != null) root.getChildren().add(gallerySection);
            root.getChildren().add(sep);

            GridPane gDep = new GridPane();
            gDep.setHgap(16); gDep.setVgap(9);
            gDep.setPadding(new Insets(0, 0, 4, 0));
            gDep.getColumnConstraints().addAll(colConstraint(140, false), colConstraint(300, true));

            Integer pct = p.getPorcentajeDepreciado();
            String adqStr = p.getFechaAdquisicion() != null ? FormatUtils.formatDate(p.getFechaAdquisicion()) : "—";
            String vidaStr = p.getVidaUtilAnios() != null ? p.getVidaUtilAnios() + " años" : "—";
            String residualStr = p.getValorResidual() != null ? FormatUtils.formatCurrency(p.getValorResidual()) : "$0.00";

            int dr = 0;
            Label depTitle = new Label("Depreciación (línea recta)");
            depTitle.getStyleClass().add("dialog-field-label");
            gDep.add(depTitle, 0, dr, 2, 1); dr++;

            gDep.add(DialogUtil.fieldLabel("Fecha adquisición"), 0, dr);
            gDep.add(new Label(adqStr), 1, dr++);

            gDep.add(DialogUtil.fieldLabel("Vida útil"), 0, dr);
            gDep.add(new Label(vidaStr), 1, dr++);

            gDep.add(DialogUtil.fieldLabel("Valor residual"), 0, dr);
            gDep.add(new Label(residualStr), 1, dr++);

            gDep.add(DialogUtil.fieldLabel("Valor actual"), 0, dr);
            Label lblValorDep = new Label(FormatUtils.formatCurrency(valorDep));
            lblValorDep.getStyleClass().add("dlg-detail-total");
            gDep.add(lblValorDep, 1, dr++);

            gDep.add(DialogUtil.fieldLabel("% Depreciado"), 0, dr);
            VBox pctBox = new VBox(4);
            if (pct != null) {
                ProgressBar pb = new ProgressBar(pct / 100.0);
                pb.setMaxWidth(Double.MAX_VALUE);
                String barVariant = pct >= 90 ? "dep-progress-danger" : pct >= 50 ? "dep-progress-warn" : "dep-progress-ok";
                pb.getStyleClass().addAll("dep-progress-bar", barVariant);
                String pctClass = pct >= 90 ? "dlg-detail-stock-low"
                    : pct >= 50 ? "dlg-detail-stock-warn"
                    : "dlg-detail-stock-ok";
                Label pctLbl = new Label(pct + "% depreciado");
                pctLbl.getStyleClass().add(pctClass);
                pctBox.getChildren().addAll(pb, pctLbl);
            } else {
                pctBox.getChildren().add(new Label("—"));
            }
            gDep.add(pctBox, 1, dr);

            root.getChildren().add(gDep);
        } else {
            root.getChildren().addAll(headerCard, g);
            if (gallerySection != null) root.getChildren().add(gallerySection);
        }
        // ── Movimientos recientes ──────────────────────────────────────
        if (!movs.isEmpty()) {
            Separator sepMovs = new Separator();
            sepMovs.getStyleClass().add("form-separator");
            root.getChildren().add(sepMovs);

            Label movsTitle = new Label("Movimientos recientes");
            movsTitle.getStyleClass().add("dialog-field-label");

            VBox movsList = new VBox(4);
            movs.stream().limit(6).forEach(m -> {
                String tipoLabel = m.getTipo() != null ? m.getTipo().getEtiqueta() : "—";
                String signo = switch (m.getTipo()) {
                    case ENTRADA -> "+";
                    case SALIDA  -> "−";
                    default      -> "~";
                };
                FontIcon icon = new FontIcon(switch (m.getTipo()) {
                    case ENTRADA       -> "mdi2a-arrow-down-circle-outline";
                    case SALIDA        -> "mdi2a-arrow-up-circle-outline";
                    case TRANSFERENCIA -> "mdi2s-swap-horizontal-circle-outline";
                    default            -> "mdi2a-adjust";
                });
                icon.setIconSize(14);
                icon.getStyleClass().add(switch (m.getTipo()) {
                    case ENTRADA -> "icon-entrada";
                    case SALIDA  -> "icon-salida";
                    default      -> "icon-ajuste";
                });
                Label lTipo = new Label(tipoLabel);
                lTipo.getStyleClass().add("muted-sm");
                Label lQty = new Label(signo + m.getCantidad());
                lQty.getStyleClass().add(switch (m.getTipo()) {
                    case ENTRADA -> "dlg-detail-stock-ok";
                    case SALIDA  -> "dlg-detail-stock-low";
                    default      -> "dlg-detail-code";
                });
                Label lFecha = new Label(m.getCreadoEn() != null ? FormatUtils.formatDateTime(m.getCreadoEn()) : "");
                lFecha.getStyleClass().add("muted-sm");
                Label lUser = new Label(m.getUsuarioNombre() != null ? m.getUsuarioNombre() : "");
                lUser.getStyleClass().add("muted-sm");
                HBox row = new HBox(8, icon, lTipo, lQty, new javafx.scene.layout.Region(), lFecha, lUser);
                HBox.setHgrow(row.getChildren().get(3), Priority.ALWAYS);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("mov-history-row");
                movsList.getChildren().add(row);
            });
            if (movs.size() > 6) {
                Label mas = new Label("… y " + (movs.size() - 6) + " más (ver ficha técnica)");
                mas.getStyleClass().add("muted-sm");
                movsList.getChildren().add(mas);
            }
            root.getChildren().addAll(movsTitle, movsList);
        }

        // ── Historial de precios ──────────────────────────────────────
        java.util.List<PriceHistoryRepository.PriceHistoryEntry> priceHistory = java.util.List.of();
        if (p.getId() != null) {
            try { priceHistory = new PriceHistoryRepository().findByProducto(p.getId()); }
            catch (Exception ex) { log.warn("No se pudo cargar historial de precios: {}", ex.getMessage()); }
        }
        if (!priceHistory.isEmpty()) {
            Separator sepPrices = new Separator();
            sepPrices.getStyleClass().add("form-separator");
            root.getChildren().add(sepPrices);

            Label pricesTitle = new Label("Historial de precios");
            pricesTitle.getStyleClass().add("dialog-field-label");

            TableView<PriceHistoryRepository.PriceHistoryEntry> priceTable = new TableView<>();
            priceTable.setPrefHeight(Math.min(priceHistory.size() * 32 + 32, 160));
            priceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            priceTable.getStyleClass().add("data-table");

            TableColumn<PriceHistoryRepository.PriceHistoryEntry, String> colCampo = new TableColumn<>("Campo");
            colCampo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                "precio_compra".equals(c.getValue().campo()) ? "Precio compra" : "Precio venta"));
            colCampo.setPrefWidth(100);

            TableColumn<PriceHistoryRepository.PriceHistoryEntry, java.math.BigDecimal> colAnt = new TableColumn<>("Anterior");
            colAnt.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().valorAnterior()));
            colAnt.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(java.math.BigDecimal v, boolean empty) {
                    super.updateItem(v, empty); setText(empty || v == null ? "—" : FormatUtils.formatCurrency(v));
                }
            });
            colAnt.setPrefWidth(90);

            TableColumn<PriceHistoryRepository.PriceHistoryEntry, java.math.BigDecimal> colNuevo = new TableColumn<>("Nuevo");
            colNuevo.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().valorNuevo()));
            colNuevo.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(java.math.BigDecimal v, boolean empty) {
                    super.updateItem(v, empty); setText(empty || v == null ? "—" : FormatUtils.formatCurrency(v));
                }
            });
            colNuevo.setPrefWidth(90);

            TableColumn<PriceHistoryRepository.PriceHistoryEntry, String> colUsuario = new TableColumn<>("Usuario");
            colUsuario.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().usuarioNombre()));
            colUsuario.setPrefWidth(110);

            TableColumn<PriceHistoryRepository.PriceHistoryEntry, String> colFecha = new TableColumn<>("Fecha");
            colFecha.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().creadoEn() != null ? FormatUtils.formatDateTime(c.getValue().creadoEn()) : "—"));
            colFecha.setPrefWidth(130);

            priceTable.getColumns().addAll(colCampo, colAnt, colNuevo, colUsuario, colFecha);
            priceTable.getItems().setAll(priceHistory);
            root.getChildren().addAll(pricesTitle, priceTable);
        }

        // ── Historial de resguardos ────────────────────────────────────
        if (!_resguardos.isEmpty()) {
            Separator sepRsg = new Separator();
            sepRsg.getStyleClass().add("form-separator");
            root.getChildren().add(sepRsg);

            Label rsgTitle = new Label("Resguardos (" + _resguardos.size() + ")");
            rsgTitle.getStyleClass().add("dialog-field-label");

            VBox rsgList = new VBox(4);
            for (com.sibim.model.Resguardo rsg : _resguardos) {
                String fecha = rsg.getCreadoEn() != null
                    ? rsg.getCreadoEn().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—";
                String estadoBadgeClass = com.sibim.model.Resguardo.ESTADO_ACTIVO.equals(rsg.getEstado())
                    ? "cell-badge-ok" : "cell-badge-muted";
                Label lblFolio = new Label(rsg.getNumero() != null ? rsg.getNumero() : "—");
                lblFolio.getStyleClass().add("dlg-detail-code");
                Label lblFecha = new Label(fecha);
                lblFecha.getStyleClass().add("muted-sm");
                Label lblEstado = new Label(rsg.getEstado() != null ? rsg.getEstado() : "—");
                lblEstado.getStyleClass().add(estadoBadgeClass);
                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(8, lblFolio, lblFecha, spacer, lblEstado);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("mov-history-row");
                rsgList.getChildren().add(row);
            }
            root.getChildren().addAll(rsgTitle, rsgList);
        }

        // ── Historial de préstamos ─────────────────────────────────────
        if (!_prestamos.isEmpty()) {
            Separator sepPrs = new Separator();
            sepPrs.getStyleClass().add("form-separator");
            root.getChildren().add(sepPrs);

            Label prsTitle = new Label("Préstamos (" + _prestamos.size() + ")");
            prsTitle.getStyleClass().add("dialog-field-label");

            java.time.format.DateTimeFormatter fmtPrs = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            VBox prsList = new VBox(4);
            for (com.sibim.model.Prestamo prs : _prestamos) {
                String fecha = prs.getFechaPrestamo() != null ? prs.getFechaPrestamo().format(fmtPrs) : "—";
                String badgeClass = switch (prs.getEstado() != null ? prs.getEstado() : "") {
                    case "DEVUELTO" -> "cell-badge-muted";
                    case "VENCIDO"  -> "cell-badge-warn";
                    default         -> "cell-badge-ok";
                };
                Label lblFolio = new Label(prs.getNumero() != null ? prs.getNumero() : "—");
                lblFolio.getStyleClass().add("dlg-detail-code");
                Label lblResponsable = new Label(prs.getResponsableNombre() != null ? prs.getResponsableNombre() : "—");
                lblResponsable.getStyleClass().add("muted-sm");
                Label lblFecha = new Label(fecha);
                lblFecha.getStyleClass().add("muted-sm");
                Label lblEstado = new Label(prs.getEstado() != null ? prs.getEstado() : "—");
                lblEstado.getStyleClass().add(badgeClass);
                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(8, lblFolio, lblResponsable, lblFecha, spacer, lblEstado);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("mov-history-row");
                prsList.getChildren().add(row);
            }
            root.getChildren().addAll(prsTitle, prsList);
        }

        // ── Alertas de mantenimiento ───────────────────────────────────
        {
            com.sibim.service.MantenimientoService mantSvc = new com.sibim.service.MantenimientoService();
            java.util.List<com.sibim.service.MantenimientoService.Alerta> alertasIniciales = loaded.alertas();

            Separator sepMant = new Separator();
            sepMant.getStyleClass().add("form-separator");
            root.getChildren().add(sepMant);

            HBox mantHeader = new HBox(8);
            mantHeader.setAlignment(Pos.CENTER_LEFT);
            Label mantTitle = new Label("Mantenimiento" + (alertasIniciales.isEmpty() ? "" : " (" + alertasIniciales.size() + ")"));
            mantTitle.getStyleClass().add("dialog-field-label");
            javafx.scene.layout.Region mantSpacer = new javafx.scene.layout.Region();
            HBox.setHgrow(mantSpacer, Priority.ALWAYS);
            Button btnAgregarMant = new Button("+ Agregar alerta");
            btnAgregarMant.getStyleClass().add("btn-link");
            mantHeader.getChildren().addAll(mantTitle, mantSpacer, btnAgregarMant);

            VBox mantList = new VBox(4);
            root.getChildren().addAll(mantHeader, mantList);

            java.time.format.DateTimeFormatter fmtMant = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            // Use a holder to allow self-referential Runnable (rebuild triggers itself via buttons)
            Runnable[] rebuildHolder = { null };
            java.util.function.Consumer<java.util.List<com.sibim.service.MantenimientoService.Alerta>> renderAlertas = current -> {
                mantList.getChildren().clear();
                mantTitle.setText("Mantenimiento" + (current.isEmpty() ? "" : " (" + current.size() + ")"));
                if (current.isEmpty()) {
                    Label empty = new Label("Sin alertas de mantenimiento.");
                    empty.getStyleClass().add("muted-sm");
                    mantList.getChildren().add(empty);
                    return;
                }
                for (com.sibim.service.MantenimientoService.Alerta a : current) {
                    if (a.completada()) continue;
                    boolean vencida = a.fecha() != null && a.fecha().isBefore(java.time.LocalDate.now());
                    Label lblDesc = new Label(a.descripcion());
                    lblDesc.getStyleClass().add(vencida ? "cell-badge-warn" : "muted-sm");
                    Label lblFecha = new Label(a.fecha() != null ? a.fecha().format(fmtMant) : "—");
                    lblFecha.getStyleClass().add(vencida ? "cell-badge-warn" : "muted-sm");
                    Button btnOk = new Button("✓");
                    btnOk.getStyleClass().add("btn-link");
                    btnOk.setOnAction(ev -> DialogUtil.runAsync(
                        () -> { mantSvc.marcarCompletada(a.id()); return null; },
                        v -> rebuildHolder[0].run(),
                        ex -> NotificacionUtil.error(scene, "No se pudo completar la alerta")
                    ));
                    javafx.scene.layout.Region sp2 = new javafx.scene.layout.Region();
                    HBox.setHgrow(sp2, Priority.ALWAYS);
                    HBox row = new HBox(8, lblDesc, sp2, lblFecha, btnOk);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("mov-history-row");
                    mantList.getChildren().add(row);
                }
            };
            // Rebuilding re-fetches (an "agregar"/"completar" may have changed the list on the
            // server) — done off the FX thread like the initial load, instead of the JDBC call
            // that used to run straight on the FX thread every time this fired.
            rebuildHolder[0] = () -> DialogUtil.runAsync(
                () -> mantSvc.getAlertas(p.getId()),
                renderAlertas,
                ex -> NotificacionUtil.error(scene, "No se pudieron actualizar las alertas de mantenimiento")
            );
            renderAlertas.accept(alertasIniciales);

            btnAgregarMant.setOnAction(e -> showAgregarMantenimientoDialog(p, mantSvc, scene, rebuildHolder[0]));
        }

        AnimationUtils.staggeredFadeInUp(root.getChildren(), 270, 70);

        // Same overflow risk as the create/edit "Nuevo Bien" dialog: the header
        // card + 12-row detail grid + depreciación block easily exceed a
        // laptop's usable screen height with nothing bounding it. Capping it
        // in a ScrollPane keeps the dialog (and its Close button) on-screen.
        ScrollPane rootScroll = new ScrollPane(root);
        rootScroll.setFitToWidth(true);
        rootScroll.setMaxHeight(520);
        rootScroll.getStyleClass().add("dlg-tabs-scroll");
        dialog.getDialogPane().setContent(rootScroll);
        dialog.showAndWait();
    }

    private static void showAgregarMantenimientoDialog(Producto p,
                                                        com.sibim.service.MantenimientoService mantSvc,
                                                        Scene scene, Runnable onSaved) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Agregar alerta de mantenimiento");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        VBox form = new VBox(10);
        form.setPadding(new Insets(16));

        Label lblDesc = new Label("Descripción / tarea:");
        lblDesc.getStyleClass().add("dialog-field-label");
        TextField tfDesc = new TextField();
        tfDesc.setPromptText("ej. Calibración anual, Limpieza de filtros…");
        tfDesc.getStyleClass().add("form-field");

        Label lblFecha = new Label("Fecha de revisión:");
        lblFecha.getStyleClass().add("dialog-field-label");
        DatePicker dpFecha = new DatePicker(java.time.LocalDate.now().plusMonths(6));
        dpFecha.getStyleClass().add("form-field");
        dpFecha.setPrefWidth(Double.MAX_VALUE);

        form.getChildren().addAll(lblDesc, tfDesc, lblFecha, dpFecha);
        dlg.getDialogPane().setContent(form);

        Button btnOk = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        btnOk.getStyleClass().add("btn-primary");
        btnOk.setDisable(true);
        tfDesc.textProperty().addListener((obs, ov, nv) -> btnOk.setDisable(nv.isBlank()));

        dlg.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK && !tfDesc.getText().isBlank() && dpFecha.getValue() != null) {
                String descripcion = tfDesc.getText().trim();
                java.time.LocalDate fecha = dpFecha.getValue();
                DialogUtil.runAsync(
                    () -> { mantSvc.agregarAlerta(p.getId(), descripcion, fecha); return null; },
                    v -> onSaved.run(),
                    ex -> NotificacionUtil.error(scene, "No se pudo agregar la alerta")
                );
            }
        });
    }

    private static void showQrPopup(Producto p, javafx.scene.image.Image qrSmall, Scene scene) {
        javafx.scene.image.Image qrFull = QrUtils.generateQr(
            p.getCodigo() != null ? p.getCodigo() : p.getNombre(), 300);
        if (qrFull == null) return;

        ButtonType savePng = new ButtonType("Guardar PNG", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Código QR — " + p.getNombre());
        dlg.getDialogPane().getButtonTypes().addAll(savePng, ButtonType.CLOSE);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        ImageView iv = new ImageView(qrFull);
        iv.setFitWidth(260); iv.setFitHeight(260); iv.setPreserveRatio(true);
        Label lblCodigo = new Label(p.getCodigo());
        lblCodigo.getStyleClass().add("dlg-detail-value");
        Label lblNombre = new Label(p.getNombre());
        lblNombre.getStyleClass().add("muted-sm");

        VBox content = new VBox(8, iv, lblCodigo, lblNombre);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(16));
        dlg.getDialogPane().setContent(content);

        dlg.showAndWait().ifPresent(result -> {
            if (result != savePng) return;
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Guardar código QR como imagen");
            fc.setInitialFileName("QR_" + p.getCodigo() + ".png");
            fc.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Imagen PNG (*.png)", "*.png"));
            java.io.File dest = fc.showSaveDialog(scene != null ? scene.getWindow() : null);
            if (dest != null) {
                try {
                    QrUtils.saveAsPng(qrFull, dest);
                    DialogUtil.showExportResultDialog(scene, dest);
                } catch (Exception ex) {
                    NotificacionUtil.error(scene, "No se pudo guardar el QR");
                }
            }
        });
    }

    private static ColumnConstraints colConstraint(double width, boolean grow) {
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPrefWidth(width);
        if (grow) cc.setHgrow(Priority.ALWAYS);
        return cc;
    }

    private static void openLightbox(List<String> fotos, int startIndex) {
        int[] idx = { startIndex };

        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(700);
        dlg.getDialogPane().setPrefHeight(560);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        ImageView bigImg = new ImageView();
        bigImg.setFitWidth(620);
        bigImg.setFitHeight(440);
        bigImg.setPreserveRatio(true);
        bigImg.getStyleClass().add("lightbox-image");

        Label counter = new Label();
        counter.getStyleClass().add("lightbox-counter");

        Runnable refresh = () -> {
            String path = fotos.get(idx[0]);
            try {
                bigImg.setImage(new Image(new java.io.FileInputStream(path), 620, 440, true, true));
            } catch (Exception ex) {
                bigImg.setImage(null);
            }
            counter.setText((idx[0] + 1) + " / " + fotos.size());
        };

        Button btnPrev = new Button();
        btnPrev.setGraphic(new FontIcon("mdi2c-chevron-left"));
        btnPrev.getStyleClass().addAll("btn-secondary", "lightbox-nav");
        btnPrev.setDisable(fotos.size() <= 1);
        btnPrev.setOnAction(e -> { idx[0] = (idx[0] - 1 + fotos.size()) % fotos.size(); refresh.run(); });

        Button btnNext = new Button();
        btnNext.setGraphic(new FontIcon("mdi2c-chevron-right"));
        btnNext.getStyleClass().addAll("btn-secondary", "lightbox-nav");
        btnNext.setDisable(fotos.size() <= 1);
        btnNext.setOnAction(e -> { idx[0] = (idx[0] + 1) % fotos.size(); refresh.run(); });

        BorderPane navRow = new BorderPane();
        navRow.setLeft(btnPrev);
        navRow.setCenter(counter);
        navRow.setRight(btnNext);
        navRow.getStyleClass().add("lightbox-nav-bar");

        VBox lightboxContent = new VBox(12, bigImg, navRow);
        lightboxContent.getStyleClass().add("lightbox-content");
        lightboxContent.setAlignment(Pos.CENTER);
        lightboxContent.setPadding(new Insets(16));

        dlg.getDialogPane().setContent(lightboxContent);
        refresh.run();
        dlg.showAndWait();
    }
}
