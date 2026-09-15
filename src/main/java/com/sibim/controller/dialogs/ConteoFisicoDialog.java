package com.sibim.controller.dialogs;

import com.sibim.model.ConteoFisico;
import com.sibim.model.ConteoItem;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.repository.ConteoRepository;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.sibim.service.MovimientoService;
import com.sibim.service.ReporteConteoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/** A physical inventory count (toma de inventario): walks the user through
 *  every bien in scope, lets them enter what they actually counted, assign a
 *  condition state, and add per-item notes. On confirmation it persists the
 *  session and reconciles stock discrepancies via audited AJUSTE movements. */
public final class ConteoFisicoDialog {

    private static final Logger log = LoggerFactory.getLogger(ConteoFisicoDialog.class);

    private ConteoFisicoDialog() {}

    enum EstadoConteo {
        ENCONTRADO("Encontrado"),
        MAL_ESTADO("Mal estado"),
        EN_OTRA_AREA("En otra área"),
        FALTANTE("Faltante");

        private final String label;
        EstadoConteo(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private record DraftValues(int contado, EstadoConteo estado, String nota) {}
    private record Row(Producto producto, Spinner<Integer> contado, ComboBox<EstadoConteo> estado,
                       TextField notaField, BooleanProperty touched) {}
    private record Captured(Producto producto, int contado, String estadoConteo, String nota) {}

    private static final Preferences DRAFT_PREFS = Preferences.userRoot().node("sibim/conteo/draft");

    private static String draftKey() {
        Usuario u = SessionManager.getCurrentUser();
        return u != null ? u.getId() : "anon";
    }

    private static void saveDraft(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (Row r : rows) {
            if (!sb.isEmpty()) sb.append(';');
            String estadoName = r.estado().getValue() != null ? r.estado().getValue().name() : "ENCONTRADO";
            String nota = r.notaField().getText();
            String notaEnc = nota != null && !nota.isBlank()
                ? Base64.getEncoder().encodeToString(nota.getBytes(StandardCharsets.UTF_8)) : "";
            sb.append(r.producto().getId()).append('=').append(r.contado().getValue())
              .append('|').append(estadoName).append('|').append(notaEnc);
        }
        DRAFT_PREFS.put(draftKey(), sb.toString());
        DRAFT_PREFS.put(draftKey() + ".fecha", LocalDate.now().toString());
    }

    private static void clearDraft() {
        DRAFT_PREFS.remove(draftKey());
        DRAFT_PREFS.remove(draftKey() + ".fecha");
    }

    private static Map<String, DraftValues> loadDraft() {
        String raw = DRAFT_PREFS.get(draftKey(), "");
        if (raw.isBlank()) return Collections.emptyMap();
        Map<String, DraftValues> map = new HashMap<>();
        for (String item : raw.split(";")) {
            int eq = item.indexOf('=');
            if (eq <= 0) continue;
            String id = item.substring(0, eq);
            String rest = item.substring(eq + 1);
            String[] parts = rest.split("\\|", 3);
            if (parts.length == 0) continue;
            try {
                int count = Integer.parseInt(parts[0]);
                EstadoConteo estado = parts.length > 1 ? parseEstado(parts[1]) : EstadoConteo.ENCONTRADO;
                String nota = "";
                if (parts.length > 2 && !parts[2].isBlank()) {
                    try { nota = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8); }
                    catch (Exception ignored) {}
                }
                map.put(id, new DraftValues(count, estado, nota));
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    private static EstadoConteo parseEstado(String s) {
        try { return EstadoConteo.valueOf(s); }
        catch (Exception e) { return EstadoConteo.ENCONTRADO; }
    }

    // ── Feature 1: Scope selector ─────────────────────────────────────────────

    private static Optional<List<Producto>> seleccionarAlcance(List<Producto> todos) {
        List<String> areas = todos.stream()
            .map(p -> p.getArea() != null && !p.getArea().isBlank() ? p.getArea() : "Sin área")
            .distinct().sorted().toList();
        List<String> categorias = todos.stream()
            .map(p -> p.getCategoriaNombre() != null && !p.getCategoriaNombre().isBlank()
                ? p.getCategoriaNombre() : "Sin categoría")
            .distinct().sorted().toList();

        ButtonType btnContinuar = new ButtonType("Continuar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar  = new ButtonType("Cancelar",  ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().addAll(btnContinuar, btnCancelar);
        dlg.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2m-magnify-scan", "Alcance del conteo",
            "Selecciona qué bienes incluir en este levantamiento", "#0891B2", "#0E7490");

        RadioButton rbTodo = new RadioButton("Todo el inventario (" + todos.size() + " bienes)");
        RadioButton rbArea = new RadioButton("Por área");
        RadioButton rbCat  = new RadioButton("Por categoría");
        ToggleGroup tg = new ToggleGroup();
        rbTodo.setToggleGroup(tg); rbArea.setToggleGroup(tg); rbCat.setToggleGroup(tg);
        rbTodo.setSelected(true);

        ComboBox<String> areaCombo = new ComboBox<>();
        areaCombo.getItems().addAll(areas);
        if (!areas.isEmpty()) areaCombo.setValue(areas.get(0));
        areaCombo.setMaxWidth(Double.MAX_VALUE);
        areaCombo.setDisable(true);

        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.getItems().addAll(categorias);
        if (!categorias.isEmpty()) catCombo.setValue(categorias.get(0));
        catCombo.setMaxWidth(Double.MAX_VALUE);
        catCombo.setDisable(true);

        rbArea.selectedProperty().addListener((o, a, b) -> areaCombo.setDisable(!b));
        rbCat.selectedProperty().addListener((o, a, b)  -> catCombo.setDisable(!b));

        VBox content = new VBox(10, rbTodo, rbArea, areaCombo, rbCat, catCombo);
        content.setPadding(new Insets(16, 22, 20, 22));
        dlg.getDialogPane().setContent(new VBox(header, content));

        Node okBtn = dlg.getDialogPane().lookupButton(btnContinuar);
        okBtn.getStyleClass().add("dialog-ok-btn");

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != btnContinuar) return Optional.empty();

        if (rbArea.isSelected() && areaCombo.getValue() != null) {
            String sel = areaCombo.getValue();
            return Optional.of(todos.stream()
                .filter(p -> sel.equals(p.getArea() != null && !p.getArea().isBlank() ? p.getArea() : "Sin área"))
                .toList());
        }
        if (rbCat.isSelected() && catCombo.getValue() != null) {
            String sel = catCombo.getValue();
            return Optional.of(todos.stream()
                .filter(p -> sel.equals(p.getCategoriaNombre() != null && !p.getCategoriaNombre().isBlank()
                    ? p.getCategoriaNombre() : "Sin categoría"))
                .toList());
        }
        return Optional.of(todos);
    }

    // ── Feature 5: Resumen modal before finalizing ────────────────────────────

    private static boolean showResumen(List<Row> rows) {
        long concordantes = rows.stream()
            .filter(r -> r.contado().getValue() == r.producto().getStockActual()
                      && r.estado().getValue() == EstadoConteo.ENCONTRADO).count();
        long conDiff    = rows.stream()
            .filter(r -> r.contado().getValue() != r.producto().getStockActual()).count();
        long malEstado  = rows.stream()
            .filter(r -> r.estado().getValue() == EstadoConteo.MAL_ESTADO).count();
        long otraArea   = rows.stream()
            .filter(r -> r.estado().getValue() == EstadoConteo.EN_OTRA_AREA).count();
        long faltante   = rows.stream()
            .filter(r -> r.estado().getValue() == EstadoConteo.FALTANTE).count();
        long sinRevisar = rows.stream().filter(r -> !r.touched().get()).count();

        ButtonType btnConfirmar = new ButtonType("Confirmar y finalizar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnVolver    = new ButtonType("Volver",                ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().addAll(btnConfirmar, btnVolver);
        dlg.getDialogPane().setPrefWidth(440);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox hdr = DialogUtil.gradientHeader("mdi2c-clipboard-check-outline", "Resumen del conteo",
            "Revisa los resultados antes de finalizar", "#0891B2", "#0E7490");

        VBox stats = new VBox(8);
        stats.setPadding(new Insets(16, 22, 20, 22));
        stats.getChildren().addAll(
            summaryRow("Concordantes (stock OK)", concordantes, "text-ok"),
            summaryRow("Con diferencias de stock", conDiff,    conDiff    > 0 ? "text-warn" : "text-ok"),
            summaryRow("Mal estado",               malEstado,  malEstado  > 0 ? "text-warn" : "muted"),
            summaryRow("En otra área",             otraArea,   otraArea   > 0 ? "text-warn" : "muted"),
            summaryRow("Faltante",                 faltante,   faltante   > 0 ? "text-warn" : "muted")
        );
        if (sinRevisar > 0)
            stats.getChildren().add(summaryRow("Sin revisar en esta sesión", sinRevisar, "text-warn"));
        if (conDiff > 0) {
            Label note = new Label("Se registrarán " + conDiff + " ajuste(s) de stock al confirmar.");
            note.getStyleClass().add("muted-sm");
            note.setWrapText(true);
            stats.getChildren().add(note);
        }

        dlg.getDialogPane().setContent(new VBox(hdr, stats));
        dlg.getDialogPane().lookupButton(btnConfirmar).getStyleClass().add("dialog-ok-btn");

        return dlg.showAndWait().filter(b -> b == btnConfirmar).isPresent();
    }

    private static HBox summaryRow(String label, long count, String countStyle) {
        HBox row = new HBox();
        row.setPadding(new Insets(2, 0, 2, 0));
        Label lbl = new Label(label);
        lbl.getStyleClass().add("dlg-detail-value");
        HBox.setHgrow(lbl, Priority.ALWAYS);
        Label val = new Label(String.valueOf(count));
        val.getStyleClass().addAll("dlg-detail-value", countStyle);
        row.getChildren().addAll(lbl, val);
        return row;
    }

    // ── Main dialog ───────────────────────────────────────────────────────────

    public static void show(List<Producto> productos, MovimientoService movimientoService, Runnable onReconciled) {

        // Feature 1: scope selector (skip if empty)
        List<Producto> scope;
        if (productos.isEmpty()) {
            scope = productos;
        } else {
            Optional<List<Producto>> scopeOpt = seleccionarAlcance(productos);
            if (scopeOpt.isEmpty()) return; // user cancelled
            scope = scopeOpt.get();
        }

        // Draft check
        Map<String, DraftValues> draftMap;
        String draftFecha = DRAFT_PREFS.get(draftKey() + ".fecha", "");
        Map<String, DraftValues> rawDraft = loadDraft();
        boolean hasDraft = !rawDraft.isEmpty();
        if (hasDraft) {
            boolean resume = ConfirmacionUtil.confirmar("Reanudar borrador",
                "Hay un borrador guardado el " + draftFecha + " con " + rawDraft.size() + " bien(es).\n"
                + "¿Deseas retomarlo? (Cancelar = empezar desde cero)");
            draftMap = resume ? rawDraft : Collections.emptyMap();
            if (!resume) clearDraft();
        } else {
            draftMap = Collections.emptyMap();
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(730);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-list-outline", "Conteo Físico de Inventario",
            "Captura lo contado y compáralo contra el sistema — " + com.sibim.util.FormatUtils.formatDate(LocalDate.now()),
            "#0891B2", "#0E7490");

        // Toolbar
        TextField searchField = new TextField();
        searchField.setPromptText("🔍  Buscar por nombre o código...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        CheckBox soloDiferencias = new CheckBox("Solo incidencias");
        Button btnHistorial = new Button("Historial");
        btnHistorial.setGraphic(new FontIcon("mdi2c-clipboard-list-outline"));
        btnHistorial.setContentDisplay(ContentDisplay.LEFT);
        btnHistorial.getStyleClass().add("btn-secondary");
        btnHistorial.setOnAction(e -> {
            if (com.sibim.session.SessionManager.isAdmin()) {
                HistorialConteosDialog.show(dialog.getDialogPane().getScene());
            } else {
                NotificacionUtil.info(dialog.getDialogPane().getScene(),
                    "Solo el administrador puede ver el historial de conteos");
            }
        });
        Button btnScan = new Button("Escanear");
        btnScan.setGraphic(new FontIcon("mdi2q-qrcode-scan"));
        btnScan.getStyleClass().add("btn-secondary");
        btnScan.setContentDisplay(ContentDisplay.LEFT);
        btnScan.setDisable(scope.isEmpty());

        HBox toolbar = new HBox(10, searchField, soloDiferencias, btnScan, btnHistorial);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(14, 14, 8, 14));

        // Feature 4: progress bar
        int totalItems = scope.size();
        SimpleIntegerProperty touchedCount = new SimpleIntegerProperty(0);
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        Label progressLabel = new Label(totalItems == 0 ? "" : "Revisados 0 / " + totalItems);
        progressLabel.getStyleClass().add("muted-sm");
        HBox progressBox = new HBox(10, progressBar, progressLabel);
        progressBox.setAlignment(Pos.CENTER_LEFT);
        progressBox.setPadding(new Insets(0, 14, 8, 14));
        progressBox.setVisible(!scope.isEmpty());
        progressBox.setManaged(!scope.isEmpty());

        // Column headers
        Label colHeaders = new Label(
            "BIEN                                   SIST.   CONT.   DIFF    ESTADO              NOTA");
        colHeaders.getStyleClass().add("nav-section-label");
        colHeaders.setPadding(new Insets(0, 0, 0, 14));

        // Row list
        VBox list = new VBox(5);
        list.setPadding(new Insets(4));
        List<Row> rows = new ArrayList<>();

        for (Producto p : scope) {
            DraftValues dv = draftMap.get(p.getId());
            int initialCount    = dv != null ? dv.contado() : p.getStockActual();
            EstadoConteo initEst = dv != null ? dv.estado()  : EstadoConteo.ENCONTRADO;
            String initialNota  = dv != null && dv.nota() != null ? dv.nota() : "";

            // Spinner
            Spinner<Integer> contado = new Spinner<>(0, Integer.MAX_VALUE, initialCount);
            contado.setEditable(true);
            contado.setPrefWidth(90);
            DialogUtil.commitOnFocusLoss(contado);

            // Feature 2: estado ComboBox
            ComboBox<EstadoConteo> estadoCb = new ComboBox<>();
            estadoCb.getItems().addAll(EstadoConteo.values());
            estadoCb.setValue(initEst);
            estadoCb.setPrefWidth(120);

            // Feature 3: note field (collapsible)
            TextField notaField = new TextField();
            notaField.setPromptText("Observación...");
            notaField.getStyleClass().add("form-input");
            VBox.setMargin(notaField, new Insets(0, 12, 4, 60));
            if (!initialNota.isBlank()) notaField.setText(initialNota);
            notaField.setVisible(!initialNota.isBlank());
            notaField.setManaged(!initialNota.isBlank());

            Button btnNota = new Button();
            btnNota.setGraphic(new FontIcon("mdi2n-note-text-outline"));
            btnNota.getStyleClass().add("btn-secondary");
            btnNota.setTooltip(new Tooltip("Agregar observación"));
            btnNota.setOnAction(e -> {
                boolean show = !notaField.isVisible();
                notaField.setVisible(show);
                notaField.setManaged(show);
                if (show) notaField.requestFocus();
            });

            // Feature 4: touched tracking
            BooleanProperty touched = new SimpleBooleanProperty(false);

            // Delta label
            Label delta = new Label();
            delta.setMinWidth(55);

            // Row container — defined first so updateDelta can reference it via getUserData
            HBox rowHBox = new HBox(10);

            Runnable updateDelta = () -> {
                int diff = contado.getValue() - p.getStockActual();
                delta.setText(diff == 0 ? "✓" : (diff > 0 ? "+" + diff : String.valueOf(diff)));
                delta.getStyleClass().removeAll("dlg-stock-new-ok", "dlg-stock-new-warn");
                delta.getStyleClass().add(diff == 0 ? "dlg-stock-new-ok" : "dlg-stock-new-warn");
                if (rowHBox.getUserData() instanceof Runnable r) r.run();
            };
            updateDelta.run();

            contado.valueProperty().addListener((o, a, b) -> { touched.set(true); updateDelta.run(); });
            estadoCb.valueProperty().addListener((o, a, b) -> {
                if (b != EstadoConteo.ENCONTRADO) touched.set(true);
                updateDelta.run();
            });

            VBox info = new VBox(1);
            Label nombre = new Label(p.getNombre() + "  [" + p.getCodigo() + "]");
            nombre.getStyleClass().add("dlg-detail-value");
            Label area = new Label(p.getArea() != null ? p.getArea() : "Sin área");
            area.getStyleClass().add("muted-sm");
            info.getChildren().addAll(nombre, area);
            HBox.setHgrow(info, Priority.ALWAYS);

            Label sistema = new Label(String.valueOf(p.getStockActual()));
            sistema.getStyleClass().add("dlg-stock-val");
            sistema.setMinWidth(45);

            rowHBox.getStyleClass().add("dlg-detail-header");
            rowHBox.setPadding(new Insets(8, 12, 8, 12));
            rowHBox.setAlignment(Pos.CENTER_LEFT);
            rowHBox.getChildren().addAll(info, sistema, contado, delta, estadoCb, btnNota);

            VBox rowWrapper = new VBox(0, rowHBox, notaField);

            // Visibility filter (captures rowWrapper, contado, estadoCb — defined after rowWrapper)
            Runnable applyVisibility = () -> {
                String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
                boolean matchesSearch = q.isBlank()
                    || p.getNombre().toLowerCase().contains(q)
                    || p.getCodigo().toLowerCase().contains(q);
                boolean hasIncidencia = contado.getValue() != p.getStockActual()
                    || estadoCb.getValue() != EstadoConteo.ENCONTRADO;
                boolean visible = matchesSearch && (!soloDiferencias.isSelected() || hasIncidencia);
                rowWrapper.setVisible(visible);
                rowWrapper.setManaged(visible);
            };
            rowHBox.setUserData(applyVisibility);   // used by updateDelta
            rowWrapper.setUserData(applyVisibility); // used by applyAllVisibility

            list.getChildren().add(rowWrapper);
            rows.add(new Row(p, contado, estadoCb, notaField, touched));
        }

        // Wire progress bar
        for (Row r : rows) {
            r.touched().addListener((o, a, b) -> {
                if (b) {
                    int n = touchedCount.get() + 1;
                    touchedCount.set(n);
                    progressLabel.setText("Revisados " + n + " / " + totalItems);
                    progressBar.setProgress(totalItems > 0 ? (double) n / totalItems : 0);
                }
            });
        }

        if (!list.getChildren().isEmpty())
            AnimationUtils.staggeredFadeInUp(list.getChildren(), 180, 38);

        Runnable applyAllVisibility = () -> list.getChildren().forEach(n -> {
            if (n.getUserData() instanceof Runnable r) r.run();
        });
        searchField.textProperty().addListener((o, a, b) -> applyAllVisibility.run());
        soloDiferencias.selectedProperty().addListener((o, a, b) -> applyAllVisibility.run());

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(370);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        // Footer
        Label summary = new Label(scope.isEmpty()
            ? "No hay bienes en el alcance seleccionado."
            : "Ajusta la columna \"Contado\" y presiona \"Finalizar conteo\" para guardarlo.");
        summary.getStyleClass().add("muted-sm");
        summary.setWrapText(true);

        Button btnBorrador = new Button("Guardar borrador");
        btnBorrador.setGraphic(new FontIcon("mdi2c-content-save-outline"));
        btnBorrador.getStyleClass().add("btn-secondary");
        btnBorrador.setDisable(scope.isEmpty());
        btnBorrador.setOnAction(e -> {
            saveDraft(rows);
            NotificacionUtil.exito(dialog.getDialogPane().getScene(),
                "Borrador guardado — puedes retomarlo en el próximo conteo");
            dialog.close();
        });

        ReporteConteoService reporteService = new ReporteConteoService();
        String tituloConteo = "Conteo del " + com.sibim.util.FormatUtils.formatDate(LocalDate.now());
        Usuario userForPdf = SessionManager.getCurrentUser();
        String userNameForPdf = userForPdf != null ? userForPdf.getNombre() : "Sistema";

        Button btnExportarPdf = new Button("Exportar PDF");
        btnExportarPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        btnExportarPdf.getStyleClass().add("btn-secondary");
        btnExportarPdf.setContentDisplay(ContentDisplay.LEFT);
        btnExportarPdf.setDisable(scope.isEmpty());

        btnExportarPdf.setOnAction(ev -> {
            List<ConteoItem> pdfItems = rows.stream().map(r -> {
                ConteoItem ci = new ConteoItem();
                ci.setProductoId(r.producto().getId());
                ci.setProductoNombre(r.producto().getNombre());
                ci.setProductoCodigo(r.producto().getCodigo());
                ci.setArea(r.producto().getArea());
                ci.setStockSistema(r.producto().getStockActual());
                ci.setStockContado(r.contado().getValue());
                ci.setEstadoConteo(r.estado().getValue() != null ? r.estado().getValue().name() : "ENCONTRADO");
                ci.setNota(r.notaField().getText() != null ? r.notaField().getText() : "");
                return ci;
            }).toList();
            javafx.scene.Scene scene = dialog.getDialogPane().getScene();
            AppExecutor.submit(() -> {
                try {
                    java.io.File f = reporteService.exportConteoPdf(tituloConteo, userNameForPdf, pdfItems);
                    javafx.application.Platform.runLater(() -> {
                        if (scene != null) DialogUtil.showExportResultDialog(scene, f);
                    });
                } catch (Exception ex) {
                    log.error("Error al exportar conteo físico a PDF", ex);
                    javafx.application.Platform.runLater(() -> {
                        if (scene != null) NotificacionUtil.error(scene, "No se pudo generar el PDF");
                    });
                }
            });
        });

        Button btnExportarExcel = new Button("Exportar Excel");
        btnExportarExcel.setGraphic(new FontIcon("mdi2f-file-excel"));
        btnExportarExcel.getStyleClass().add("btn-secondary");
        btnExportarExcel.setContentDisplay(ContentDisplay.LEFT);
        btnExportarExcel.setDisable(scope.isEmpty());
        btnExportarExcel.setOnAction(ev -> {
            List<ConteoItem> xlsItems = rows.stream().map(r -> {
                ConteoItem ci = new ConteoItem();
                ci.setProductoId(r.producto().getId());
                ci.setProductoNombre(r.producto().getNombre());
                ci.setProductoCodigo(r.producto().getCodigo());
                ci.setArea(r.producto().getArea());
                ci.setStockSistema(r.producto().getStockActual());
                ci.setStockContado(r.contado().getValue());
                ci.setEstadoConteo(r.estado().getValue() != null ? r.estado().getValue().name() : "ENCONTRADO");
                ci.setNota(r.notaField().getText() != null ? r.notaField().getText() : "");
                return ci;
            }).toList();
            javafx.scene.Scene scene = dialog.getDialogPane().getScene();
            AppExecutor.submit(() -> {
                try {
                    java.io.File f = reporteService.exportConteoExcel(tituloConteo, userNameForPdf, xlsItems);
                    javafx.application.Platform.runLater(() -> {
                        if (scene != null) DialogUtil.showExportResultDialog(scene, f);
                    });
                } catch (Exception ex) {
                    log.error("Error al exportar conteo a Excel", ex);
                    javafx.application.Platform.runLater(() -> {
                        if (scene != null) NotificacionUtil.error(scene, "No se pudo generar el Excel");
                    });
                }
            });
        });

        // Wire scan button after scroll/list/rows are ready
        btnScan.setOnAction(e -> showScanDialog(dialog, scroll, list, rows));

        Button btnFinalizar = new Button("Finalizar conteo");
        btnFinalizar.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
        btnFinalizar.getStyleClass().add("btn-primary");
        btnFinalizar.setDisable(scope.isEmpty());

        HBox actions = new HBox(10, summary, btnBorrador, btnExportarPdf, btnExportarExcel, btnFinalizar);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(10, 4, 4, 4));
        HBox.setHgrow(summary, Priority.ALWAYS);

        btnFinalizar.setOnAction(e -> {
            btnFinalizar.setDisable(true);

            // Feature 5: resumen modal (replaces plain ConfirmacionUtil.confirmar)
            if (!showResumen(rows)) {
                btnFinalizar.setDisable(false);
                return;
            }

            btnFinalizar.setText("Guardando...");
            btnFinalizar.setGraphic(new FontIcon("mdi2l-loading"));

            // Snapshot on FX thread — background thread must not touch live nodes
            List<Captured> snapshot = new ArrayList<>();
            for (Row r : rows) {
                r.contado().setDisable(true);
                r.estado().setDisable(true);
                r.notaField().setDisable(true);
                snapshot.add(new Captured(
                    r.producto(),
                    r.contado().getValue(),
                    r.estado().getValue() != null ? r.estado().getValue().name() : "ENCONTRADO",
                    r.notaField().getText()
                ));
            }

            String motivo = "Conteo físico del " + com.sibim.util.FormatUtils.formatDate(LocalDate.now());
            Usuario currentUser = SessionManager.getCurrentUser();

            AppExecutor.submit(() -> {
                int ok = 0;
                List<String> fallidos = new ArrayList<>();
                List<ConteoItem> items = new ArrayList<>();

                for (Captured r : snapshot) {
                    boolean esDiscrepancia = r.contado() != r.producto().getStockActual();
                    boolean ajustado = false;
                    if (esDiscrepancia) {
                        try {
                            movimientoService.registrarAjusteVerificado(r.producto().getId(),
                                r.contado(), motivo, "Conteo físico", r.producto().getStockActual());
                            ajustado = true;
                            ok++;
                        } catch (Exception ex) {
                            fallidos.add(r.producto().getNombre());
                            log.error("No se pudo aplicar el ajuste de conteo físico para '{}' (id={})",
                                r.producto().getNombre(), r.producto().getId(), ex);
                        }
                    }
                    ConteoItem item = new ConteoItem();
                    item.setProductoId(r.producto().getId());
                    item.setProductoNombre(r.producto().getNombre());
                    item.setProductoCodigo(r.producto().getCodigo());
                    item.setArea(r.producto().getArea());
                    item.setStockSistema(r.producto().getStockActual());
                    item.setStockContado(r.contado());
                    item.setAjustado(ajustado);
                    item.setEstadoConteo(r.estadoConteo());
                    item.setNota(r.nota().isBlank() ? null : r.nota());
                    items.add(item);
                }

                long totalDiscrepancias = items.stream()
                    .filter(i -> i.getStockContado() != i.getStockSistema()).count();

                ConteoFisico conteo = new ConteoFisico();
                if (currentUser != null) {
                    conteo.setUsuarioId(currentUser.getId());
                    conteo.setUsuarioNombre(currentUser.getNombre());
                } else {
                    conteo.setUsuarioNombre("Sistema");
                }
                conteo.setTotalContados(items.size());
                conteo.setTotalDiscrepancias((int) totalDiscrepancias);
                conteo.setItems(items);

                boolean saved = true;
                try {
                    new ConteoRepository().guardar(conteo);
                } catch (Exception ex) {
                    saved = false;
                    log.error("No se pudo guardar el registro del conteo físico", ex);
                }

                int okFinal = ok;
                List<String> fallidosFinal = List.copyOf(fallidos);
                boolean savedFinal = saved;
                javafx.application.Platform.runLater(() -> {
                    String base;
                    if (totalDiscrepancias == 0) {
                        base = "Conteo guardado — sin diferencias en " + items.size() + " bien(es)";
                    } else if (fallidosFinal.isEmpty()) {
                        base = okFinal + " ajuste(s) aplicado(s) correctamente";
                    } else {
                        String nombres = fallidosFinal.stream().limit(3)
                            .collect(Collectors.joining(", "))
                            + (fallidosFinal.size() > 3 ? "…" : "");
                        base = okFinal + " ajuste(s) aplicado(s), " + fallidosFinal.size()
                            + " fallaron: " + nombres;
                    }
                    if (!savedFinal) base += " (no se pudo guardar el registro del conteo)";
                    javafx.scene.Scene scene = dialog.getDialogPane().getScene();
                    if (scene != null) {
                        if (!fallidosFinal.isEmpty() || !savedFinal) NotificacionUtil.advertencia(scene, base);
                        else if (totalDiscrepancias == 0) NotificacionUtil.exitoConteo(scene, items.size());
                        else NotificacionUtil.exito(scene, base);
                    }
                    clearDraft();
                    if (onReconciled != null) onReconciled.run();
                    dialog.close();
                });
            });
        });

        AnimationUtils.staggeredFadeInUp(
            java.util.List.of(header, toolbar, progressBox, colHeaders, scroll, actions), 260, 60);
        dialog.getDialogPane().setContent(
            new VBox(0, header, toolbar, progressBox, colHeaders, scroll, actions));

        dialog.setOnShowing(ev -> dialog.getDialogPane().getScene().getWindow().addEventFilter(
            javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, we -> {
                long changed = rows.stream()
                    .filter(r -> r.contado().getValue() != r.producto().getStockActual()
                              || r.estado().getValue() != EstadoConteo.ENCONTRADO
                              || !r.notaField().getText().isBlank()).count();
                if (changed > 0 && !ConfirmacionUtil.confirmar("Salir sin guardar",
                        changed + " bien(es) con diferencias o notas registradas se perderán.\n¿Seguro que quieres salir?"))
                    we.consume();
            }));

        dialog.showAndWait();
    }

    private static com.google.zxing.LuminanceSource bufferedImageToLuminance(java.awt.image.BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        byte[] luminances = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                luminances[y * width + x] = (byte) ((r * 299 + g * 587 + b * 114 + 500) / 1000);
            }
        }
        return new com.google.zxing.LuminanceSource(width, height) {
            @Override public byte[] getRow(int y, byte[] row) {
                if (row == null || row.length < width) row = new byte[width];
                System.arraycopy(luminances, y * width, row, 0, width);
                return row;
            }
            @Override public byte[] getMatrix() { return luminances; }
        };
    }

    private static void showScanDialog(Dialog<?> parent, ScrollPane scroll, VBox list, List<Row> rows) {
        Dialog<ButtonType> scanDlg = new Dialog<>();
        DialogUtil.applyOwner(scanDlg);
        scanDlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        scanDlg.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(scanDlg.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2q-qrcode-scan", "Escanear código",
            "Escribe o pega el código, o carga una imagen con QR/código de barras",
            "#0891B2", "#0E7490");

        TextField codeField = new TextField();
        codeField.setPromptText("Código del bien (ej: BIEN-001)");
        codeField.setMaxWidth(Double.MAX_VALUE);

        Button btnCargar = new Button("Cargar imagen…");
        btnCargar.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("mdi2f-file-image-outline"));
        btnCargar.getStyleClass().add("btn-secondary");
        btnCargar.setOnAction(ev -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Seleccionar imagen con QR o código de barras");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                "Imágenes (*.png, *.jpg, *.bmp)", "*.png", "*.jpg", "*.jpeg", "*.bmp"));
            java.io.File file = fc.showOpenDialog(scanDlg.getDialogPane().getScene().getWindow());
            if (file == null) return;
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file);
                com.google.zxing.LuminanceSource source = bufferedImageToLuminance(img);
                com.google.zxing.BinaryBitmap bitmap = new com.google.zxing.BinaryBitmap(
                    new com.google.zxing.common.HybridBinarizer(source));
                com.google.zxing.Result result = new com.google.zxing.MultiFormatReader().decode(bitmap);
                codeField.setText(result.getText());
            } catch (Exception e) {
                com.sibim.util.NotificacionUtil.advertencia(
                    scanDlg.getDialogPane().getScene(), "No se pudo decodificar la imagen");
            }
        });

        Button btnBuscar = new Button("Buscar");
        btnBuscar.getStyleClass().add("btn-primary");
        btnBuscar.setDefaultButton(true);
        btnBuscar.setOnAction(ev -> {
            String code = codeField.getText() == null ? "" : codeField.getText().trim();
            if (code.isBlank()) return;
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                if (code.equalsIgnoreCase(r.producto().getCodigo())
                        || code.equalsIgnoreCase(r.producto().getNombre())) {
                    int finalI = i;
                    javafx.application.Platform.runLater(() -> {
                        double totalHeight = list.getBoundsInLocal().getHeight();
                        if (totalHeight > 0 && list.getChildren().size() > finalI) {
                            double nodeY = list.getChildren().get(finalI).getBoundsInParent().getMinY();
                            scroll.setVvalue(nodeY / totalHeight);
                        }
                        list.getChildren().get(finalI).getStyleClass().add("row-highlight-flash");
                    });
                    scanDlg.close();
                    return;
                }
            }
            com.sibim.util.NotificacionUtil.advertencia(
                scanDlg.getDialogPane().getScene(),
                "No se encontró ningún bien con el código: " + code);
        });

        VBox content = new VBox(10, codeField, new HBox(8, btnCargar, btnBuscar));
        content.setPadding(new Insets(14));
        scanDlg.getDialogPane().setContent(new VBox(0, header, content));
        scanDlg.showAndWait();
    }
}
