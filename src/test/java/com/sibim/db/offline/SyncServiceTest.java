package com.sibim.db.offline;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.integration.IntegrationTestBase;
import com.sibim.model.Producto;
import com.sibim.model.enums.UnidadMedida;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SyncService: verifies that every outbox table
 * (category, product, movement, audit_log) is correctly replayed against
 * Postgres and that the outbox row status is updated accordingly.
 *
 * Runs against embedded Postgres (via IntegrationTestBase) + the real
 * local SQLite file (~/.sibim/offline.db) — the outbox tables are
 * truncated before each test so tests are fully isolated.
 *
 * Tests are in the same package (com.sibim.db.offline) as SyncService
 * and OfflineStore so they can access package-private methods directly,
 * bypassing Platform.runLater() / MainController dependencies entirely.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncServiceTest extends IntegrationTestBase {

    // ─── SQLite setup / teardown ─────────────────────────────────────────

    @BeforeEach
    void clearOutboxTables() throws SQLException {
        Connection c = OfflineStore.sharedConnection();
        try (Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM audit_log_outbox");
            st.executeUpdate("DELETE FROM conteo_items_outbox");
            st.executeUpdate("DELETE FROM conteo_outbox");
            st.executeUpdate("DELETE FROM movement_outbox");
            st.executeUpdate("DELETE FROM product_outbox");
            st.executeUpdate("DELETE FROM category_outbox");
        }
        // Tests run with offline mode off so resolveConflicto doesn't call Platform.runLater()
        DatabaseConfig.setOfflineMode(false);
    }

    // ─── SQLite insert helpers ───────────────────────────────────────────

    private int insertCategoryOutbox(String operacion, String catId, String nombre) throws SQLException {
        String sql = """
            INSERT INTO category_outbox (operacion, categoria_id, nombre, descripcion, color, created_at, status)
            VALUES (?, ?, ?, null, '#3B82F6', datetime('now'), 'PENDING')
            """;
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, operacion);
            ps.setString(2, catId);
            ps.setString(3, nombre);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private int insertProductOutbox(String operacion, String productoId, String nombre,
                                     String codigo, String catId,
                                     String serverSnapshotAt, String motivoBaja) throws SQLException {
        String sql = """
            INSERT INTO product_outbox (operacion, producto_id, nombre, codigo, descripcion,
                categoria_id, precio_compra, precio_venta, stock_actual, stock_minimo, stock_maximo,
                unidad, area, created_at, status, server_snapshot_at, motivo_baja)
            VALUES (?, ?, ?, ?, null, ?, 0, 0, 10, 0, 100, 'pieza', 'Almacen',
                    datetime('now'), 'PENDING', ?, ?)
            """;
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, operacion);
            ps.setString(2, productoId);
            ps.setString(3, nombre);
            ps.setString(4, codigo);
            ps.setString(5, catId);
            ps.setString(6, serverSnapshotAt);
            ps.setString(7, motivoBaja);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private int insertMovementOutbox(String movId, String productoId,
                                      String tipo, int cantidad) throws SQLException {
        String sql = """
            INSERT INTO movement_outbox (operacion, movimiento_id, producto_id, tipo, cantidad,
                motivo, referencia, area_destino, usuario_id, usuario_nombre, estado, created_at, status)
            VALUES ('ADD', ?, ?, ?, ?, 'Sincronización offline', null, null,
                    'test-admin', 'Admin Test', 'APROBADO', datetime('now'), 'PENDING')
            """;
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, movId);
            ps.setString(2, productoId);
            ps.setString(3, tipo);
            ps.setInt(4, cantidad);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private int insertAuditOutbox(String auditId, String entidadId) throws SQLException {
        String sql = """
            INSERT INTO audit_log_outbox (audit_id, entidad, entidad_id, entidad_nombre,
                accion, detalle, usuario_id, usuario_nombre, created_at, status)
            VALUES (?, 'producto', ?, 'Bien de prueba', 'save', 'Creado offline',
                    'test-admin', 'Admin Test', strftime('%Y-%m-%dT%H:%M:%S', 'now'), 'PENDING')
            """;
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, auditId);
            ps.setString(2, entidadId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private String getOutboxStatus(String table, int rowId) throws SQLException {
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT status FROM " + table + " WHERE id = ?")) {
            ps.setInt(1, rowId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // ─── Postgres insert / query helpers ─────────────────────────────────

    private String insertPgCategory(String nombre) throws SQLException {
        String id = UUID.randomUUID().toString();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO categories (id, nombre, descripcion, color, created_at) " +
                "VALUES (?, ?, null, '#3B82F6', NOW())")) {
            ps.setString(1, id);
            ps.setString(2, nombre);
            ps.executeUpdate();
        }
        return id;
    }

    private String insertPgProduct(String nombre, String codigo, String catId) throws SQLException {
        String id = UUID.randomUUID().toString();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO products (id, nombre, codigo, categoria_id, area, " +
                "precio_compra, precio_venta, stock_actual, stock_minimo, stock_maximo, " +
                "unidad, valor_residual, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'Almacen', 0, 0, 10, 0, 100, 'pieza', 0, NOW(), NOW())")) {
            ps.setString(1, id);
            ps.setString(2, nombre);
            ps.setString(3, codigo);
            ps.setString(4, catId);
            ps.executeUpdate();
        }
        return id;
    }

    private boolean pgExists(String table, String id) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int outboxId(String table, String idColumn, String value) throws SQLException {
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT id FROM " + table + " WHERE " + idColumn + " = ?")) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    // ════════════════════ syncCategorias ═════════════════════════════════

    @Test
    void syncCategorias_SAVE_replicaEnPostgresYMarcaSynced() throws Exception {
        String catId = UUID.randomUUID().toString();
        int rowId = insertCategoryOutbox("SAVE", catId, "Electrónica");
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        SyncService.syncCategorias(synced, failed);

        assertEquals(1, synced.get());
        assertEquals(0, failed.get());
        assertTrue(pgExists("categories", catId));
        assertEquals("SYNCED", getOutboxStatus("category_outbox", rowId));
    }

    @Test
    void syncCategorias_DELETE_eliminaDePostgresYMarcaSynced() throws Exception {
        String catId = insertPgCategory("Mobiliario");
        int rowId = insertCategoryOutbox("DELETE", catId, "Mobiliario");
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        SyncService.syncCategorias(synced, failed);

        assertEquals(1, synced.get());
        assertFalse(pgExists("categories", catId));
        assertEquals("SYNCED", getOutboxStatus("category_outbox", rowId));
    }

    @Test
    void syncCategorias_violacionUNIQUE_marcaFailed() throws Exception {
        // Categoria existente con nombre "Vehículos" → intentar SAVE con mismo nombre, distinto id
        insertPgCategory("Vehículos");
        int rowId = insertCategoryOutbox("SAVE", UUID.randomUUID().toString(), "Vehículos");
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        SyncService.syncCategorias(synced, failed);

        assertEquals(0, synced.get());
        assertEquals(1, failed.get());
        assertEquals("FAILED", getOutboxStatus("category_outbox", rowId));
    }

    // ════════════════════ syncProductos ══════════════════════════════════

    @Test
    void syncProductos_SAVE_sinConflicto_replicaEnPostgres() throws Exception {
        String catId = insertPgCategory("Cómputo");
        String prodId = UUID.randomUUID().toString();
        int rowId = insertProductOutbox("SAVE", prodId, "Laptop HP", "LAP-001", catId, null, null);
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        List<ConflictoInfo> conflicts = SyncService.syncProductos(synced, failed);

        assertEquals(1, synced.get());
        assertEquals(0, failed.get());
        assertTrue(conflicts.isEmpty());
        assertTrue(pgExists("products", prodId));
        assertEquals("SYNCED", getOutboxStatus("product_outbox", rowId));
    }

    @Test
    void syncProductos_SAVE_snapshotAtNulo_omiteDeteccionDeConflicto() throws Exception {
        String catId = insertPgCategory("Cómputo 2");
        String prodId = insertPgProduct("Monitor LG", "MON-001", catId);
        // server_snapshot_at null → conflict check skipped, upsert proceeds
        int rowId = insertProductOutbox("SAVE", prodId, "Monitor LG actualizado", "MON-001", catId, null, null);
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        List<ConflictoInfo> conflicts = SyncService.syncProductos(synced, failed);

        assertEquals(1, synced.get());
        assertTrue(conflicts.isEmpty());
        assertEquals("SYNCED", getOutboxStatus("product_outbox", rowId));
    }

    @Test
    void syncProductos_SAVE_servidorModificadoDespuesDelSnapshot_marcaConflicto() throws Exception {
        String catId = insertPgCategory("Cómputo 3");
        // Product inserted NOW() → updated_at is current
        String prodId = insertPgProduct("Router Cisco", "ROU-001", catId);
        // Snapshot from yesterday → server is newer → CONFLICT
        String snapshotAyer = LocalDateTime.now().minusDays(1).toString();
        int rowId = insertProductOutbox("SAVE", prodId, "Router Cisco offline", "ROU-001", catId, snapshotAyer, null);
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        List<ConflictoInfo> conflicts = SyncService.syncProductos(synced, failed);

        assertEquals(0, synced.get());
        assertEquals(1, conflicts.size());
        assertEquals(prodId, conflicts.get(0).versionOffline().getId());
        assertEquals("Router Cisco offline", conflicts.get(0).versionOffline().getNombre());
        assertEquals("CONFLICT", getOutboxStatus("product_outbox", rowId));
    }

    @Test
    void syncProductos_SAVE_snapshotMasRecienteQueServidor_sincronizaSinConflicto() throws Exception {
        String catId = insertPgCategory("Cómputo 4");
        String prodId = insertPgProduct("Impresora HP", "IMP-001", catId);
        // Force server updated_at to 3 days ago
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                "UPDATE products SET updated_at = ? WHERE id = ?")) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusDays(3)));
            ps.setString(2, prodId);
            ps.executeUpdate();
        }
        // Snapshot from yesterday → newer than server → no conflict
        String snapshotAyer = LocalDateTime.now().minusDays(1).toString();
        int rowId = insertProductOutbox("SAVE", prodId, "Impresora HP actualizada", "IMP-001", catId, snapshotAyer, null);
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        List<ConflictoInfo> conflicts = SyncService.syncProductos(synced, failed);

        assertEquals(1, synced.get());
        assertTrue(conflicts.isEmpty());
        assertEquals("SYNCED", getOutboxStatus("product_outbox", rowId));
    }

    @Test
    void syncProductos_BAJA_aplicaDarDeBajaEnPostgres() throws Exception {
        String catId = insertPgCategory("Maquinaria");
        String prodId = insertPgProduct("Compresor Ingersoll", "COM-001", catId);
        int rowId = insertProductOutbox("BAJA", prodId, "Compresor Ingersoll", "COM-001", catId, null, "Obsoleto");
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        SyncService.syncProductos(synced, failed);

        assertEquals(1, synced.get());
        assertEquals("SYNCED", getOutboxStatus("product_outbox", rowId));
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT fecha_baja FROM products WHERE id = ?")) {
            ps.setString(1, prodId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNotNull(rs.getDate("fecha_baja"), "fecha_baja debe ser no nula tras la baja patrimonial");
            }
        }
    }

    @Test
    void syncProductos_REACTIVAR_quitaFechaBaja() throws Exception {
        String catId = insertPgCategory("Herramientas");
        String prodId = insertPgProduct("Taladro Bosch", "TAL-001", catId);
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                "UPDATE products SET fecha_baja = CURRENT_DATE, motivo_baja = 'Prueba' WHERE id = ?")) {
            ps.setString(1, prodId);
            ps.executeUpdate();
        }
        int rowId = insertProductOutbox("REACTIVAR", prodId, "Taladro Bosch", "TAL-001", catId, null, null);
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        SyncService.syncProductos(synced, failed);

        assertEquals(1, synced.get());
        assertEquals("SYNCED", getOutboxStatus("product_outbox", rowId));
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT fecha_baja FROM products WHERE id = ?")) {
            ps.setString(1, prodId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNull(rs.getDate("fecha_baja"), "fecha_baja debe ser null tras reactivar");
            }
        }
    }

    // ════════════════════ syncMovimientos ════════════════════════════════

    @Test
    void syncMovimientos_ADD_creaMovimientoEnPostgresYActualizaStock() throws Exception {
        String catId = insertPgCategory("Transporte");
        String prodId = insertPgProduct("Camioneta Ford", "CAM-001", catId);
        String movId = UUID.randomUUID().toString();
        int rowId = insertMovementOutbox(movId, prodId, "entrada", 5);
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        SyncService.syncMovimientos(synced, failed);

        assertEquals(1, synced.get());
        assertEquals(0, failed.get());
        assertTrue(pgExists("movements", movId));
        assertEquals("SYNCED", getOutboxStatus("movement_outbox", rowId));
        // Stock should have increased by 5 (from 10 to 15)
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT stock_actual FROM products WHERE id = ?")) {
            ps.setString(1, prodId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(15, rs.getInt(1), "El stock debe incrementar en 5 tras la entrada");
            }
        }
    }

    // ════════════════════ syncAuditLog ═══════════════════════════════════

    @Test
    void syncAuditLog_replicaEntradaEnPostgres() throws Exception {
        String auditId = UUID.randomUUID().toString();
        String entidadId = UUID.randomUUID().toString();
        int rowId = insertAuditOutbox(auditId, entidadId);
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        SyncService.syncAuditLog(synced, failed);

        assertEquals(1, synced.get());
        assertTrue(pgExists("audit_log", auditId));
        assertEquals("SYNCED", getOutboxStatus("audit_log_outbox", rowId));
    }

    // ════════════════════ requeueFailedChanges ═══════════════════════════

    @Test
    void requeueFailedChanges_convierteFailedEnPendingEnTodasLasTablas() throws Exception {
        Connection c = OfflineStore.sharedConnection();
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                INSERT INTO product_outbox (operacion, producto_id, nombre, codigo, categoria_id,
                    stock_actual, stock_minimo, stock_maximo, area, created_at, status)
                VALUES ('SAVE','prod-fail','Prod Fail','COD-F','cat-f',0,0,100,'A',datetime('now'),'FAILED')
                """);
            st.executeUpdate("""
                INSERT INTO category_outbox (operacion, categoria_id, nombre, color, created_at, status)
                VALUES ('SAVE','cat-fail','Cat Fail','#fff',datetime('now'),'FAILED')
                """);
            st.executeUpdate("""
                INSERT INTO movement_outbox (operacion, movimiento_id, producto_id, tipo, cantidad,
                    motivo, usuario_nombre, estado, created_at, status)
                VALUES ('ADD','mov-fail','prod-fail','entrada',1,'x','Test','APROBADO',datetime('now'),'FAILED')
                """);
        }

        SyncService.requeueFailedChanges();

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT status FROM product_outbox WHERE producto_id = 'prod-fail'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("PENDING", rs.getString(1), "product_outbox FAILED debe quedar PENDING");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT status FROM category_outbox WHERE categoria_id = 'cat-fail'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("PENDING", rs.getString(1), "category_outbox FAILED debe quedar PENDING");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT status FROM movement_outbox WHERE movimiento_id = 'mov-fail'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("PENDING", rs.getString(1), "movement_outbox FAILED debe quedar PENDING");
        }
    }

    // ════════════════════ countPending ═══════════════════════════════════

    @Test
    void countPending_sumaPendingYFailed_ignoraSyncedConflictDiscarded() throws Exception {
        Connection c = OfflineStore.sharedConnection();
        try (Statement st = c.createStatement()) {
            // product_outbox: 2 PENDING, 1 FAILED, 1 SYNCED, 1 CONFLICT  → contribuye 3
            for (String s : new String[]{"PENDING", "PENDING", "FAILED", "SYNCED", "CONFLICT"}) {
                st.executeUpdate(
                    "INSERT INTO product_outbox (operacion, producto_id, nombre, codigo, categoria_id," +
                    "stock_actual, stock_minimo, stock_maximo, area, created_at, status) VALUES " +
                    "('SAVE','" + UUID.randomUUID() + "','N','C-" + UUID.randomUUID() + "','cat-x'," +
                    "0,0,100,'A',datetime('now'),'" + s + "')");
            }
            // category_outbox: 1 PENDING → contribuye 1
            st.executeUpdate(
                "INSERT INTO category_outbox (operacion, categoria_id, nombre, color, created_at, status) " +
                "VALUES ('SAVE','" + UUID.randomUUID() + "','Cat P','#f00',datetime('now'),'PENDING')");
        }

        int count = SyncService.countPending();

        assertEquals(4, count, "2 PENDING + 1 FAILED de products + 1 PENDING de categories = 4");
    }

    // ════════════════════ resolveConflicto ═══════════════════════════════

    @Test
    void resolveConflicto_conVersionOffline_guardaEnPostgresYMarcaSynced() throws Exception {
        String catId = insertPgCategory("Informática");
        String prodId = UUID.randomUUID().toString();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement("""
            INSERT INTO product_outbox (operacion, producto_id, nombre, codigo, categoria_id,
                stock_actual, stock_minimo, stock_maximo, area, created_at, status)
            VALUES ('SAVE', ?, 'PC Escritorio', 'PC-001', ?, 0, 0, 100, 'Almacen', datetime('now'), 'CONFLICT')
            """)) {
            ps.setString(1, prodId);
            ps.setString(2, catId);
            ps.executeUpdate();
        }
        int rowId = outboxId("product_outbox", "producto_id", prodId);

        Producto offline = new Producto();
        offline.setId(prodId);
        offline.setNombre("PC Escritorio");
        offline.setCodigo("PC-001");
        offline.setCategoriaId(catId);
        offline.setPrecioCompra(BigDecimal.ZERO);
        offline.setPrecioVenta(BigDecimal.ZERO);
        offline.setStockActual(0);
        offline.setStockMinimo(0);
        offline.setStockMaximo(100);
        offline.setUnidad(UnidadMedida.PIEZA);
        offline.setArea("Almacen");

        SyncService.resolveConflicto(rowId, offline);

        assertTrue(pgExists("products", prodId));
        assertEquals("SYNCED", getOutboxStatus("product_outbox", rowId));
    }

    @Test
    void resolveConflicto_sinVersionOffline_descartaYMarcaDiscarded() throws Exception {
        String prodId = UUID.randomUUID().toString();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement("""
            INSERT INTO product_outbox (operacion, producto_id, nombre, codigo, categoria_id,
                stock_actual, stock_minimo, stock_maximo, area, created_at, status)
            VALUES ('SAVE', ?, 'Laptop Obsoleta', 'LAP-DIS', 'cat-dis', 0, 0, 100, 'Almacen', datetime('now'), 'CONFLICT')
            """)) {
            ps.setString(1, prodId);
            ps.executeUpdate();
        }
        int rowId = outboxId("product_outbox", "producto_id", prodId);

        // offlineMode=false → early return before Platform.runLater()
        SyncService.resolveConflicto(rowId, null);

        assertEquals("DISCARDED", getOutboxStatus("product_outbox", rowId));
    }
}
