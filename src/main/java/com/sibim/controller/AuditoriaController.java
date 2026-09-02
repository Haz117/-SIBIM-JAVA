package com.sibim.controller;

import com.sibim.model.AuditLog;
import com.sibim.repository.AuditLogRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.NotificacionUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AuditoriaController {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaController.class);

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final DateTimeFormatter FECHA_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML private HBox filterBar;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> entidadFilter;
    @FXML private TextField usuarioFilter;
    @FXML private DatePicker desdeField;
    @FXML private DatePicker hastaField;
    @FXML private Label lblTotal;
    @FXML private TableView<AuditLog> table;
    @FXML private TableColumn<AuditLog, String> colFecha;
    @FXML private TableColumn<AuditLog, String> colEntidad;
    @FXML private TableColumn<AuditLog, String> colNombre;
    @FXML private TableColumn<AuditLog, String> colAccion;
    @FXML private TableColumn<AuditLog, String> colUsuario;
    @FXML private TableColumn<AuditLog, String> colDetalle;
    @FXML private Label lblPagina;
    @FXML private Button btnPrimera;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private Button btnUltima;
    @FXML private Button btnRefresh;
    @FXML private ComboBox<Integer> pageSizeBox;
    @FXML private HBox paginationBar;

    private final AuditLogRepository auditRepo = new AuditLogRepository();

    private int currentPage = 0;
    private int pageSize    = DEFAULT_PAGE_SIZE;
    private int totalCount  = 0;

    private PauseTransition searchDebounce;

    @FXML
    public void initialize() {
        setupColumns();
        setupEntidadFilter();
        setupPageSizeBox();

        searchDebounce = new PauseTransition(Duration.millis(280));
        searchDebounce.setOnFinished(e -> {
            currentPage = 0;
            loadData();
        });

        AnimationUtils.fadeInDown(filterBar, 220, 0);
        loadData();

        if (searchField != null)
            javafx.application.Platform.runLater(() -> searchField.requestFocus());

        if (table != null) {
            table.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.F5) loadData();
            });
        }
    }

    private void setupColumns() {
        colFecha.setCellValueFactory(c -> {
            if (c.getValue().getCreadoEn() == null) return new SimpleStringProperty("—");
            return new SimpleStringProperty(c.getValue().getCreadoEn().format(FECHA_FMT));
        });

        colEntidad.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getEntidad() != null ? c.getValue().getEntidad() : "—"));

        colNombre.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getEntidadNombre() != null ? c.getValue().getEntidadNombre() : "—"));
        colNombre.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });

        colAccion.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getAccion() != null ? c.getValue().getAccion() : "—"));
        colAccion.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("audit-pill-green","audit-pill-red","audit-pill-blue",
                                          "audit-pill-orange","audit-pill-slate","audit-pill-purple");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                String cls = switch (item.toLowerCase()) {
                    case "login"              -> "audit-pill-green";
                    case "logout"             -> "audit-pill-slate";
                    case "save", "edicion",
                         "edición", "alta"   -> "audit-pill-blue";
                    case "delete", "baja"     -> "audit-pill-red";
                    case "conteo"             -> "audit-pill-purple";
                    default                   -> "audit-pill-orange";
                };
                getStyleClass().add(cls);
            }
        });

        colUsuario.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getUsuarioNombre() != null ? c.getValue().getUsuarioNombre() : "—"));

        colDetalle.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getDetalle() != null ? c.getValue().getDetalle() : "—"));
        colDetalle.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            {
                tip.setWrapText(true);
                tip.setMaxWidth(400);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
    }

    private void setupEntidadFilter() {
        if (entidadFilter == null) return;
        entidadFilter.getItems().addAll(
            "Todas", "sesion", "movimiento", "producto", "categoria",
            "usuario", "backup", "conteo"
        );
        entidadFilter.getSelectionModel().selectFirst();
    }

    private void setupPageSizeBox() {
        if (pageSizeBox == null) return;
        pageSizeBox.getItems().addAll(25, 50, 100);
        pageSizeBox.getSelectionModel().select(Integer.valueOf(DEFAULT_PAGE_SIZE));
        pageSizeBox.setOnAction(e -> {
            Integer sel = pageSizeBox.getSelectionModel().getSelectedItem();
            if (sel != null) { pageSize = sel; currentPage = 0; loadData(); }
        });
    }

    private void loadData() {
        String busqueda = searchField != null ? searchField.getText() : null;
        String entidad  = getEntidadValue();
        String usuario  = usuarioFilter != null ? usuarioFilter.getText() : null;
        LocalDate desde = desdeField != null ? desdeField.getValue() : null;
        LocalDate hasta = hastaField != null ? hastaField.getValue() : null;
        int offset = currentPage * pageSize;

        AppExecutor.submit(() -> {
            try {
                int total = auditRepo.countFiltrado(busqueda, entidad, usuario, desde, hasta);
                List<AuditLog> rows = auditRepo.findPaginated(pageSize, offset,
                    busqueda, entidad, usuario, desde, hasta);
                Platform.runLater(() -> {
                    totalCount = total;
                    table.getItems().setAll(rows);
                    AnimationUtils.staggerTableRows(table);
                    updatePaginationUI();
                });
            } catch (SecurityException se) {
                Platform.runLater(() -> {
                    table.getItems().clear();
                    if (lblTotal != null) lblTotal.setText("Acceso denegado — se requiere rol Administrador");
                    updatePaginationDisabled();
                });
            } catch (Exception ex) {
                log.error("Error al cargar registros de auditoría", ex);
                Platform.runLater(() -> {
                    javafx.scene.Scene scene = table.getScene();
                    if (scene != null)
                        NotificacionUtil.error(scene, "No se pudo cargar el registro de auditoría");
                });
            }
        });
    }

    private String getEntidadValue() {
        if (entidadFilter == null) return null;
        String sel = entidadFilter.getSelectionModel().getSelectedItem();
        return (sel == null || sel.equals("Todas")) ? null : sel;
    }

    private void updatePaginationUI() {
        int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);

        if (lblTotal != null) {
            lblTotal.setText(totalCount == 1
                ? "1 registro encontrado"
                : totalCount + " registros encontrados");
        }
        if (lblPagina != null) {
            lblPagina.setText("Página " + (currentPage + 1) + " de " + totalPages);
        }

        boolean isFirst = currentPage == 0;
        boolean isLast  = currentPage >= totalPages - 1;

        if (btnPrimera   != null) btnPrimera.setDisable(isFirst);
        if (btnAnterior  != null) btnAnterior.setDisable(isFirst);
        if (btnSiguiente != null) btnSiguiente.setDisable(isLast);
        if (btnUltima    != null) btnUltima.setDisable(isLast);
    }

    private void updatePaginationDisabled() {
        if (lblTotal  != null) lblTotal.setText("");
        if (lblPagina != null) lblPagina.setText("—");
        if (btnPrimera   != null) btnPrimera.setDisable(true);
        if (btnAnterior  != null) btnAnterior.setDisable(true);
        if (btnSiguiente != null) btnSiguiente.setDisable(true);
        if (btnUltima    != null) btnUltima.setDisable(true);
    }

    @FXML private void onSearch() {
        if (searchDebounce != null) { searchDebounce.stop(); searchDebounce.playFromStart(); }
    }

    @FXML private void onFilter() {
        currentPage = 0;
        loadData();
    }

    @FXML private void onLimpiar() {
        if (searchField   != null) searchField.clear();
        if (usuarioFilter != null) usuarioFilter.clear();
        if (desdeField    != null) desdeField.setValue(null);
        if (hastaField    != null) hastaField.setValue(null);
        if (entidadFilter != null) entidadFilter.getSelectionModel().selectFirst();
        currentPage = 0;
        loadData();
    }

    @FXML private void onRefresh() { loadData(); }

    @FXML private void onPrimera() {
        if (currentPage == 0) return;
        currentPage = 0;
        loadData();
    }

    @FXML private void onAnterior() {
        if (currentPage <= 0) return;
        currentPage--;
        loadData();
    }

    @FXML private void onSiguiente() {
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (currentPage >= totalPages - 1) return;
        currentPage++;
        loadData();
    }

    @FXML private void onUltima() {
        int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);
        if (currentPage >= totalPages - 1) return;
        currentPage = totalPages - 1;
        loadData();
    }
}
