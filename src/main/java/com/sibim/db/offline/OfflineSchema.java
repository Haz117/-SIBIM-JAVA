package com.sibim.db.offline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The schema of this PC's offline copy (offline.db): creating it from
 * offline.sql and bringing an older file up to date. Stateless — every method
 * works on the connection it is given — so it lives apart from
 * {@link OfflineStore}, which owns the connection and the cached data.
 */
final class OfflineSchema {

    private static final Logger log = LoggerFactory.getLogger(OfflineSchema.class);

    private OfflineSchema() {}

    static boolean schemaExists(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='products'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /** Idempotent column/table additions for existing offline.db files that pre-date schema changes.
     *  SQLite doesn't support IF NOT EXISTS in ALTER TABLE, so we swallow the duplicate-column
     *  error: on a fresh DB these succeed; on an old one they're silent no-ops.
     *  New tables use CREATE TABLE IF NOT EXISTS — always idempotent. */
    static void runOfflineMigrations(Connection c) {
        // M1 (2025): server_snapshot_at for offline conflict detection
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE product_outbox ADD COLUMN server_snapshot_at TEXT");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M1b (2026): preserve pending transfer state in the local mirror.
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE movements ADD COLUMN estado TEXT NOT NULL DEFAULT 'APROBADO'");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE movement_outbox ADD COLUMN estado TEXT NOT NULL DEFAULT 'APROBADO'");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M1c (2026-09): a transfer's old/new código, same as Postgres V22.
        for (String col : List.of("codigo_anterior", "codigo_nuevo")) {
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE movements ADD COLUMN " + col + " TEXT");
            } catch (SQLException ignored) {
                log.debug("Offline migration step already applied (idempotent)", ignored);
            }
        }
        // M2 (2025): conteo físico offline outbox
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS conteo_outbox (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conteo_id TEXT NOT NULL, usuario_id TEXT, usuario_nombre TEXT NOT NULL,
                    total_contados INTEGER NOT NULL DEFAULT 0, total_discrepancias INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', error TEXT)""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS conteo_items_outbox (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, conteo_id TEXT NOT NULL,
                    item_id TEXT NOT NULL, producto_id TEXT NOT NULL, producto_nombre TEXT NOT NULL,
                    producto_codigo TEXT,
                    area TEXT, stock_sistema INTEGER NOT NULL, stock_contado INTEGER NOT NULL,
                    ajustado INTEGER NOT NULL DEFAULT 0,
                    estado_conteo TEXT DEFAULT 'ENCONTRADO', nota TEXT)""");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M3 (2025): audit log offline outbox
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS audit_log_outbox (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, audit_id TEXT NOT NULL,
                    entidad TEXT NOT NULL, entidad_id TEXT NOT NULL, entidad_nombre TEXT,
                    accion TEXT NOT NULL, detalle TEXT, usuario_id TEXT,
                    usuario_nombre TEXT NOT NULL, created_at TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING', error TEXT)""");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M4 (2026): cached_at para caducidad del caché de credenciales offline (30 días).
        // Columna nullable — filas previas quedan con NULL, que findCachedUserByUsername
        // trata como "expirado", forzando re-autenticación online la primera vez.
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE users_cache ADD COLUMN cached_at TEXT");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M5: factura_url para foto de factura
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE products ADD COLUMN factura_url TEXT");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE product_outbox ADD COLUMN factura_url TEXT");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M6: numero_serie, marca, modelo
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE products ADD COLUMN numero_serie TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE products ADD COLUMN marca TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE products ADD COLUMN modelo TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE product_outbox ADD COLUMN numero_serie TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE product_outbox ADD COLUMN marca TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE product_outbox ADD COLUMN modelo TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M8 (2026): etiquetado y fotos_urls
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE products ADD COLUMN etiquetado INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE products ADD COLUMN fotos_urls TEXT");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE product_outbox ADD COLUMN etiquetado INTEGER DEFAULT 0");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE product_outbox ADD COLUMN fotos_urls TEXT");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M9 (2026): producto_codigo, estado_conteo, nota en conteo_items_outbox
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE conteo_items_outbox ADD COLUMN producto_codigo TEXT");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE conteo_items_outbox ADD COLUMN estado_conteo TEXT DEFAULT 'ENCONTRADO'");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE conteo_items_outbox ADD COLUMN nota TEXT");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M10 (2026): activo para users_cache — offline.db previos a este campo
        // en offline.sql se quedaron sin la columna; sin esta migración, cacheUser()
        // falla al reautenticar offline a cualquier usuario existente.
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE users_cache ADD COLUMN activo INTEGER NOT NULL DEFAULT 1");
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M11 (2026): retry_count en las tablas outbox — sin esto, una fila que
        // sigue fallando (p.ej. un movimiento que ya no cabe en el stock del
        // servidor) se reencola como PENDING en cada tick para siempre y la app
        // nunca vuelve a modo online aunque el servidor esté disponible.
        // SyncService la descarta tras MAX_RETRY_ATTEMPTS intentos.
        for (String table : new String[]{
                "product_outbox", "movement_outbox", "category_outbox",
                "conteo_outbox", "audit_log_outbox"}) {
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        }
        // M12 (2026): estado_fisico, numero_factura y codigo_conac — offline.db
        // previos al inventario físico MLA se quedaron sin estas columnas, aunque
        // offline.sql ya las incluye para instalaciones nuevas. Sin esta migración,
        // SyncService.syncProductos/syncCategorias truena con "no such column" en
        // cualquier PC que ya tuviera un offline.db de antes de ese cambio.
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE products ADD COLUMN estado_fisico TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE products ADD COLUMN numero_factura TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE product_outbox ADD COLUMN estado_fisico TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE product_outbox ADD COLUMN numero_factura TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE categories ADD COLUMN codigo_conac TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE category_outbox ADD COLUMN codigo_conac TEXT"); } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M(2026-09): server_updated_at — the server's own updated_at as last
        // mirrored. Offline writes stamp updated_at with this PC's clock, so it
        // can't serve as the conflict baseline (see serverSnapshot()). Backfill
        // from updated_at for bienes with nothing queued, where it still is the
        // server's value.
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE products ADD COLUMN server_updated_at TEXT");
            st.execute("UPDATE products SET server_updated_at = updated_at WHERE id NOT IN "
                + "(SELECT producto_id FROM product_outbox WHERE status NOT IN ('SYNCED','DISCARDED') "
                + "AND producto_id IS NOT NULL)");   // a NULL in NOT IN would match no row at all
        } catch (SQLException ignored) {
            log.debug("Offline migration step already applied (idempotent)", ignored);
        }
        // M(2026-09c): depreciation, inventario físico, vehicle and dictamen
        // columns — without them an offline edit wiped them on the server.
        try { OfflineDocs.crearTablas(c); } catch (SQLException e) {
            log.warn("OfflineStore: no se pudieron crear las tablas de documentos offline", e);
        }
        ProductoExtras.migrar(c, "products");
        ProductoExtras.migrar(c, "product_outbox");
        codigoUnicoSoloEntreActivos(c);
    }

    /** M(2026-09b): Postgres (V14) only requires a código to be unique among
     *  ACTIVE bienes — a bien dado de baja keeps its old código and that número
     *  is handed out again. The offline mirror still had a global UNIQUE, so
     *  caching the server's inventory failed as a whole (empty inventory
     *  offline) and an offline alta reusing a freed número was rejected.
     *  SQLite can't drop a column constraint: rebuild the table once. */
    static void codigoUnicoSoloEntreActivos(Connection c) {
        try {
            String ddl;
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE type='table' AND name='products'")) {
                ddl = rs.next() ? rs.getString(1) : null;
            }
            java.util.regex.Pattern unico = java.util.regex.Pattern.compile(
                "(?i)(\\bcodigo\\s+TEXT\\s+)UNIQUE\\s+");
            if (ddl != null && unico.matcher(ddl).find()) {
                String nuevo = unico.matcher(ddl).replaceFirst("$1")
                    .replaceFirst("(?i)CREATE TABLE\\s+(IF NOT EXISTS\\s+)?\"?products\"?", "CREATE TABLE products__nuevo");
                boolean auto = c.getAutoCommit();
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    st.execute("DROP TABLE IF EXISTS products__nuevo");
                    st.execute(nuevo);
                    st.execute("INSERT INTO products__nuevo SELECT * FROM products");
                    st.execute("DROP TABLE products");
                    st.execute("ALTER TABLE products__nuevo RENAME TO products");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_offline_products_area ON products(area)");
                    c.commit();
                    log.info("offline.db: código único solo entre bienes activos (igual que el servidor)");
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(auto);
                }
            }
            try (Statement st = c.createStatement()) {
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_offline_products_codigo_activo "
                    + "ON products(codigo) WHERE fecha_baja IS NULL");
            }
        } catch (SQLException e) {
            log.warn("offline.db: no se pudo ajustar la unicidad del código: {}", e.getMessage());
        }
    }

    static void runSchema(Connection c) throws SQLException {
        try (InputStream in = OfflineSchema.class.getResourceAsStream("/offline.sql")) {
            if (in == null) throw new SQLException("No se encontró offline.sql en el classpath");
            String sql;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                // Drop "-- …" comments before splitting on ';' — several comments in
                // offline.sql contain semicolons, which cut CREATE TABLE users_cache and
                // product_outbox in half, so a brand-new offline.db (fresh install, or
                // after the cache file is removed) came up without those tables.
                sql = r.lines()
                    .map(line -> line.replaceFirst("--.*$", ""))
                    .collect(Collectors.joining("\n"));
            }
            // Use addBatch/executeBatch instead of per-statement execute() to keep a
            // strong reference to the Statement throughout, preventing the JIT from
            // prematurely clearing it for GC while the loop is still running — a
            // known SQLite JDBC quirk on JDK 21 with concurrent GC.
            try (Statement st = c.createStatement()) {
                for (String stmt : sql.split(";")) {
                    String trimmed = stmt.strip();
                    if (!trimmed.isEmpty()) st.addBatch(trimmed);
                }
                st.executeBatch();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
