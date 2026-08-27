package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.model.Categoria;
import com.sibim.model.Producto;
import com.sibim.service.ProductoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ImportacionBienesDialog {

    private static final String[] TEMPLATE_HEADERS = {
        "Nombre", "Codigo", "Categoria", "Area", "Resguardante",
        "Cantidad", "Stock Min", "Stock Max",
        "Precio Compra", "Precio Venta",
        "Proveedor", "Ubicacion", "Descripcion", "Fecha Adquisicion"
    };

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    record ParsedRow(int num, String status, String error, Producto producto) {}

    public static void show(Scene ownerScene, List<Categoria> categorias,
                            ProductoService productoService, Runnable onSuccess) {
        ButtonType IMPORTAR = new ButtonType("Importar", ButtonBar.ButtonData.OK_DONE);
        Dialog<List<ParsedRow>> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(IMPORTAR, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(740);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        Button btnImportar = (Button) dialog.getDialogPane().lookupButton(IMPORTAR);
        btnImportar.setDisable(true);
        btnImportar.getStyleClass().add("dialog-ok-btn");

        // ── Header ───────────────────────────────────────────────────
        HBox header = DialogUtil.gradientHeader("mdi2u-upload-outline",
            "Importar Bienes desde CSV",
            "Carga un archivo CSV con tu inventario para registrar múltiples bienes de una sola vez.",
            "#4338CA", "#6366F1");

        // ── Instructions card ─────────────────────────────────────────
        HBox step1 = stepRow("1", "Descarga la plantilla CSV — incluye las columnas y dos filas de ejemplo.");
        Button btnPlantilla = new Button("Descargar plantilla");
        btnPlantilla.getStyleClass().add("btn-secondary");
        btnPlantilla.setGraphic(new FontIcon("mdi2d-download-outline"));
        btnPlantilla.setContentDisplay(ContentDisplay.LEFT);
        step1.getChildren().add(btnPlantilla);

        HBox step2 = stepRow("2", "Abre la plantilla en Excel o LibreOffice Calc, llena los datos y guarda como CSV (UTF-8).");
        HBox step3 = stepRow("3", "Selecciona el archivo abajo — se validará cada fila antes de importar.");

        VBox instructions = new VBox(8, step1, step2, step3);
        instructions.setPadding(new Insets(14, 18, 14, 18));
        instructions.getStyleClass().add("import-instructions-card");

        // ── File picker row ───────────────────────────────────────────
        Button btnSeleccionar = new Button("Seleccionar archivo CSV…");
        btnSeleccionar.getStyleClass().add("btn-primary");
        btnSeleccionar.setGraphic(new FontIcon("mdi2f-folder-open-outline"));
        btnSeleccionar.setContentDisplay(ContentDisplay.LEFT);

        Label lblArchivo = new Label("Ningún archivo seleccionado");
        lblArchivo.getStyleClass().add("muted");
        HBox.setHgrow(lblArchivo, Priority.ALWAYS);

        HBox fileRow = new HBox(12, btnSeleccionar, lblArchivo);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        fileRow.setPadding(new Insets(0, 18, 0, 18));

        // ── Summary label ─────────────────────────────────────────────
        Label lblResumen = new Label();
        lblResumen.getStyleClass().add("import-summary-label");
        lblResumen.setVisible(false);
        lblResumen.setManaged(false);
        lblResumen.setPadding(new Insets(0, 18, 0, 18));

        // ── Preview table ─────────────────────────────────────────────
        TableView<ParsedRow> preview = new TableView<>();
        preview.setVisible(false);
        preview.setManaged(false);
        preview.setPrefHeight(260);
        preview.getStyleClass().addAll("data-table", "import-preview-table");

        TableColumn<ParsedRow, String> colNum = new TableColumn<>("#");
        colNum.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().num())));
        colNum.setPrefWidth(44); colNum.setMinWidth(44); colNum.setMaxWidth(44);

        TableColumn<ParsedRow, String> colStatus = new TableColumn<>("");
        colStatus.setPrefWidth(36); colStatus.setMinWidth(36); colStatus.setMaxWidth(36);
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().status()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null); setGraphic(null);
                if (empty || item == null) return;
                FontIcon icon;
                if ("ok".equals(item)) {
                    icon = new FontIcon("mdi2c-check-circle");
                    icon.getStyleClass().add("import-row-ok");
                } else {
                    icon = new FontIcon("mdi2a-alert-circle");
                    icon.getStyleClass().add("import-row-error");
                }
                icon.setIconSize(15);
                setGraphic(icon);
            }
        });

        TableColumn<ParsedRow, String> colNombre = new TableColumn<>("Nombre");
        colNombre.setPrefWidth(180);
        colNombre.setCellValueFactory(c -> {
            Producto p = c.getValue().producto();
            return new javafx.beans.property.SimpleStringProperty(p != null && p.getNombre() != null ? p.getNombre() : "—");
        });

        TableColumn<ParsedRow, String> colCat = new TableColumn<>("Categoría");
        colCat.setPrefWidth(140);
        colCat.setCellValueFactory(c -> {
            Producto p = c.getValue().producto();
            return new javafx.beans.property.SimpleStringProperty(p != null && p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "—");
        });

        TableColumn<ParsedRow, String> colArea = new TableColumn<>("Área");
        colArea.setPrefWidth(160);
        colArea.setCellValueFactory(c -> {
            Producto p = c.getValue().producto();
            return new javafx.beans.property.SimpleStringProperty(p != null && p.getArea() != null ? p.getArea() : "—");
        });

        TableColumn<ParsedRow, String> colCant = new TableColumn<>("Stock");
        colCant.setPrefWidth(58);
        colCant.setCellValueFactory(c -> {
            Producto p = c.getValue().producto();
            return new javafx.beans.property.SimpleStringProperty(p != null ? String.valueOf(p.getStockActual()) : "—");
        });

        TableColumn<ParsedRow, String> colError = new TableColumn<>("Observación");
        colError.setPrefWidth(200);
        colError.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            c.getValue().error() != null ? c.getValue().error() : ""));
        colError.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setText(null); setGraphic(null); return; }
                Label lbl = new Label(item);
                lbl.getStyleClass().add("import-row-error-text");
                lbl.setWrapText(true);
                setGraphic(lbl); setText(null);
            }
        });

        preview.getColumns().addAll(colNum, colStatus, colNombre, colCat, colArea, colCant, colError);
        preview.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        VBox content = new VBox(12, header, instructions, new Separator(), fileRow, lblResumen, preview);
        content.setPadding(new Insets(0, 0, 12, 0));
        dialog.getDialogPane().setContent(content);

        // Mutable state shared between closures
        AtomicReference<List<ParsedRow>> parsedRows = new AtomicReference<>(List.of());

        // ── Template download ─────────────────────────────────────────
        btnPlantilla.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Guardar plantilla CSV");
            fc.setInitialFileName("plantilla_bienes.csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"));
            File dest = fc.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
            if (dest == null) return;
            try {
                writeTemplate(dest);
                NotificacionUtil.exito(ownerScene, "Plantilla guardada en " + dest.getName());
                try { java.awt.Desktop.getDesktop().open(dest); } catch (Exception ignored) {}
            } catch (Exception ex) {
                NotificacionUtil.error(ownerScene, "No se pudo guardar la plantilla");
            }
        });

        // ── File selection & parsing ──────────────────────────────────
        btnSeleccionar.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Seleccionar archivo CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"));
            File file = fc.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file == null) return;

            lblArchivo.setText("Procesando " + file.getName() + "…");
            preview.setVisible(false); preview.setManaged(false);
            lblResumen.setVisible(false); lblResumen.setManaged(false);
            btnImportar.setDisable(true);

            List<ParsedRow> rows;
            try {
                rows = parseFile(file, categorias);
            } catch (Exception ex) {
                lblArchivo.setText("Error al leer el archivo: " + ex.getMessage());
                lblArchivo.getStyleClass().add("field-hint-error");
                return;
            }

            parsedRows.set(rows);
            long validas = rows.stream().filter(r -> "ok".equals(r.status())).count();
            long errores = rows.size() - validas;

            lblArchivo.getStyleClass().remove("field-hint-error");
            lblArchivo.setText(file.getName() + "  ·  " + rows.size() + " fila(s) leídas");

            if (validas > 0) {
                lblResumen.setText("✓  " + validas + " fila(s) listas para importar"
                    + (errores > 0 ? "  ·  ⚠  " + errores + " con error(es) (se omitirán)" : ""));
                lblResumen.getStyleClass().removeAll("import-summary-warn", "import-summary-ok");
                lblResumen.getStyleClass().add(errores > 0 ? "import-summary-warn" : "import-summary-ok");
            } else {
                lblResumen.setText("⚠  Ninguna fila es válida. Corrige los errores e intenta de nuevo.");
                lblResumen.getStyleClass().removeAll("import-summary-warn", "import-summary-ok");
                lblResumen.getStyleClass().add("import-summary-warn");
            }
            lblResumen.setVisible(true); lblResumen.setManaged(true);

            preview.setItems(javafx.collections.FXCollections.observableArrayList(rows));
            preview.setVisible(true); preview.setManaged(true);
            AnimationUtils.fadeInUp(preview, 240, 0);

            btnImportar.setDisable(validas == 0);
            btnImportar.setText("Importar " + validas + " registro(s)");
        });

        // ── Result converter ─────────────────────────────────────────
        dialog.setResultConverter(bt -> bt == IMPORTAR ? parsedRows.get() : null);

        // ── Show & handle result ──────────────────────────────────────
        dialog.showAndWait().ifPresent(rows -> {
            List<Producto> validas = rows.stream()
                .filter(r -> "ok".equals(r.status()))
                .map(ParsedRow::producto)
                .toList();
            if (validas.isEmpty()) return;

            DialogUtil.runAsyncWithProgress(ownerScene, "Importando " + validas.size() + " bien(es)…",
                () -> {
                    int ok = 0; int fail = 0;
                    for (Producto p : validas) {
                        try { productoService.save(p); ok++; }
                        catch (Exception ex) { fail++; }
                    }
                    return new int[]{ok, fail};
                },
                result -> {
                    onSuccess.run();
                    int ok   = result[0];
                    int fail = result[1];
                    if (fail == 0)
                        NotificacionUtil.exito(ownerScene, ok + " bien(es) importado(s) correctamente");
                    else
                        NotificacionUtil.advertencia(ownerScene,
                            ok + " importado(s), " + fail + " no pudieron guardarse (códigos duplicados u otro error)");
                },
                ex -> NotificacionUtil.error(ownerScene, "Error durante la importación")
            );
        });
    }

    // ── CSV parsing ───────────────────────────────────────────────────────

    private static List<ParsedRow> parseFile(File file, List<Categoria> categorias) throws Exception {
        List<ParsedRow> result = new ArrayList<>();
        Set<String> areaNames  = Areas.getAllAreaNames();
        Map<String, String> catByNorm   = new LinkedHashMap<>();
        Map<String, String> catIdByNorm = new LinkedHashMap<>();
        for (Categoria c : categorias) {
            String norm = normalize(c.getNombre());
            catByNorm.put(norm, c.getNombre());
            catIdByNorm.put(norm, c.getId());
        }
        Map<String, String> areaByNorm = new LinkedHashMap<>();
        for (String a : areaNames) areaByNorm.put(normalize(a), a);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {

            String headerLine = br.readLine();
            if (headerLine == null) throw new IOException("El archivo está vacío");
            // Strip UTF-8 BOM if present
            if (headerLine.startsWith("﻿")) headerLine = headerLine.substring(1);

            String[] headers = parseCsvLine(headerLine);
            Map<String, Integer> colIdx = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIdx.put(normalize(headers[i]), i);
            }

            // Verify required columns exist
            if (!colIdx.containsKey("nombre"))
                throw new IOException("Columna 'Nombre' no encontrada. Verifica que el archivo use la plantilla correcta.");
            if (!colIdx.containsKey("categoria"))
                throw new IOException("Columna 'Categoria' no encontrada. Verifica que el archivo use la plantilla correcta.");

            String line;
            int rowNum = 0;
            Set<String> usedCodes = new HashSet<>();

            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                rowNum++;
                if (rowNum > 2000) {
                    result.add(new ParsedRow(rowNum, "error", "Límite de 2000 filas por importación", null));
                    break;
                }

                String[] cols = parseCsvLine(line);
                String error = null;
                Producto p = new Producto();

                String nombre    = col(cols, colIdx, "nombre");
                String codigo    = col(cols, colIdx, "codigo");
                String catNombre = col(cols, colIdx, "categoria");
                String area      = col(cols, colIdx, "area");
                String resguard  = col(cols, colIdx, "resguardante");
                String cantStr   = col(cols, colIdx, "cantidad");
                String stMinStr  = col(cols, colIdx, "stock min");
                String stMaxStr  = col(cols, colIdx, "stock max");
                String pcStr     = col(cols, colIdx, "precio compra");
                String pvStr     = col(cols, colIdx, "precio venta");
                String proveed   = col(cols, colIdx, "proveedor");
                String ubicac    = col(cols, colIdx, "ubicacion");
                String desc      = col(cols, colIdx, "descripcion");
                String fechaStr  = col(cols, colIdx, "fecha adquisicion");

                // Nombre
                if (nombre.isBlank()) {
                    error = "Nombre requerido";
                } else {
                    p.setNombre(nombre.length() > 200 ? nombre.substring(0, 200) : nombre);
                }

                // Codigo — auto-generate if blank
                if (error == null) {
                    if (codigo.isBlank()) {
                        String base = "IMP-" + String.format("%04d", rowNum);
                        String generated = base;
                        int suffix = 2;
                        while (usedCodes.contains(generated)) generated = base + "-" + suffix++;
                        codigo = generated;
                    } else if (codigo.length() > 50) {
                        codigo = codigo.substring(0, 50);
                    }
                    if (usedCodes.contains(codigo)) {
                        error = "Código duplicado en el archivo: " + codigo;
                    } else {
                        usedCodes.add(codigo);
                        p.setCodigo(codigo);
                    }
                }

                // Categoria
                if (error == null) {
                    String catNorm = normalize(catNombre);
                    String matchedName = catByNorm.get(catNorm);
                    if (matchedName == null) {
                        // fuzzy: partial contains
                        for (Map.Entry<String, String> e : catByNorm.entrySet()) {
                            if (e.getKey().contains(catNorm) || catNorm.contains(e.getKey())) {
                                matchedName = e.getValue(); break;
                            }
                        }
                    }
                    if (matchedName == null) {
                        error = "Categoría no encontrada: \"" + catNombre + "\"";
                    } else {
                        p.setCategoriaNombre(matchedName);
                        p.setCategoriaId(catIdByNorm.get(normalize(matchedName)));
                    }
                }

                // Area
                if (error == null) {
                    if (area.isBlank()) {
                        error = "Área requerida";
                    } else {
                        String areaNorm = normalize(area);
                        String matchedArea = areaByNorm.get(areaNorm);
                        if (matchedArea == null) {
                            // partial match
                            for (Map.Entry<String, String> e : areaByNorm.entrySet()) {
                                if (e.getKey().contains(areaNorm) || areaNorm.contains(e.getKey())) {
                                    matchedArea = e.getValue(); break;
                                }
                            }
                        }
                        if (matchedArea == null) {
                            error = "Área no reconocida: \"" + area + "\". Usa un área válida del organigrama.";
                        } else {
                            p.setArea(matchedArea);
                        }
                    }
                }

                if (error == null) {
                    // Optional fields — parse silently, use defaults on failure
                    if (!resguard.isBlank()) p.setResguardante(resguard);
                    if (!proveed.isBlank())  p.setProveedor(proveed.length() > 200 ? proveed.substring(0, 200) : proveed);
                    if (!ubicac.isBlank())   p.setUbicacion(ubicac.length() > 200 ? ubicac.substring(0, 200) : ubicac);
                    if (!desc.isBlank())     p.setDescripcion(desc.length() > 1000 ? desc.substring(0, 1000) : desc);

                    int cant  = parseIntSafe(cantStr, 0);
                    int stMin = parseIntSafe(stMinStr, 0);
                    int stMax = parseIntSafe(stMaxStr, Math.max(cant, 1));
                    if (stMax < stMin) stMax = stMin;
                    if (stMax < cant)  stMax = cant;
                    p.setStockActual(Math.max(0, cant));
                    p.setStockMinimo(Math.max(0, stMin));
                    p.setStockMaximo(Math.max(0, stMax));

                    BigDecimal pc = parseBigDecimalSafe(pcStr, BigDecimal.ZERO);
                    BigDecimal pv = parseBigDecimalSafe(pvStr, pc);
                    p.setPrecioCompra(pc.signum() < 0 ? BigDecimal.ZERO : pc);
                    p.setPrecioVenta(pv.signum() < 0  ? BigDecimal.ZERO : pv);

                    if (!fechaStr.isBlank()) {
                        LocalDate fecha = parseDateSafe(fechaStr);
                        if (fecha != null && !fecha.isAfter(LocalDate.now()))
                            p.setFechaAdquisicion(fecha);
                    }
                }

                result.add(new ParsedRow(rowNum, error == null ? "ok" : "error", error,
                    error == null ? p : null));
            }
        }
        return result;
    }

    // ── CSV line parser (handles quoted fields) ───────────────────────────

    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"'); i++;
                    } else { inQuotes = false; }
                } else { current.append(c); }
            } else {
                if (c == '"') { inQuotes = true; }
                else if (c == ',') { fields.add(current.toString().trim()); current = new StringBuilder(); }
                else { current.append(c); }
            }
        }
        fields.add(current.toString().trim());
        return fields.toArray(new String[0]);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String col(String[] cols, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i >= cols.length) return "";
        return cols[i] == null ? "" : cols[i].trim();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase();
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.replaceAll("[^\\d-]", "")); }
        catch (Exception e) { return def; }
    }

    private static BigDecimal parseBigDecimalSafe(String s, BigDecimal def) {
        try {
            String clean = s.replace("$", "").replace(",", "").trim();
            return new BigDecimal(clean);
        } catch (Exception e) { return def; }
    }

    private static LocalDate parseDateSafe(String s) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(s.trim(), fmt); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private static HBox stepRow(String num, String text) {
        Label numLbl = new Label(num);
        numLbl.getStyleClass().add("import-step-num");
        Label textLbl = new Label(text);
        textLbl.getStyleClass().add("import-step-text");
        textLbl.setWrapText(true);
        HBox.setHgrow(textLbl, Priority.ALWAYS);
        HBox row = new HBox(10, numLbl, textLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static void writeTemplate(File dest) throws Exception {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(dest), java.nio.charset.StandardCharsets.UTF_8))) {
            pw.println(String.join(",", TEMPLATE_HEADERS));
            pw.println("Laptop Dell XPS,,Equipo de Cómputo,Direccion de Tecnologias de la Informacion,Juan Pérez García,1,1,3,24999.00,27500.00,Dell,Sala de Servidores,Laptop i7 16GB RAM,2024-01-15");
            pw.println("Silla Ejecutiva,,Mobiliario,Despacho de la Presidencia,María García López,2,1,5,3200.00,3500.00,OfficeMax,Oficina Presidencia,,2024-02-01");
        }
    }
}
