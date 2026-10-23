package com.sibim.db.integration;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.AreaResguardoRepository;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.FolioRepository;
import com.sibim.repository.LoginAttemptRepository;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.repository.UsuarioRepository;
import com.sibim.service.AccesosEquipoService;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** scripts/sql/rol_areas.sql against a real Postgres: an área's PC, connected
 *  as sibim_areas, can do everything an área does in SIBIM (through the app's
 *  own code) and none of Patrimonio's changes. */
class RolAreasIntegrationTest extends IntegrationTestBase {

    private static final String CLAVE_APP   = "clave-de-prueba";
    private static final String CLAVE_AREAS = "clave-de-areas";
    private static final String AREA        = "Dirección de Catastro";
    private static final String CAT_ID      = "cat-areas";
    private static final String BIEN        = "bien-areas";

    private PGSimpleDataSource comoAreas;
    private HikariDataSource poolAreas;
    private Usuario admin;

    @BeforeAll
    void aplicarScripts() throws Exception {
        Path base = Path.of(System.getProperty("basedir", ".."));
        String app = Files.readString(base.resolve("scripts/sql/rol_app_minimo.sql"), StandardCharsets.UTF_8)
            .replace("clave TEXT := 'CAMBIA-ESTA-CONTRASENA';", "clave TEXT := '" + CLAVE_APP + "';");
        String areas = Files.readString(base.resolve("scripts/sql/rol_areas.sql"), StandardCharsets.UTF_8)
            .replace("clave TEXT := 'CAMBIA-ESTA-CONTRASENA';", "clave TEXT := '" + CLAVE_AREAS + "';");
        String url;
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            url = c.getMetaData().getURL();
            st.execute(app);
            st.execute(areas);
            st.execute(areas);   // a second run leaves the same permissions
        }
        comoAreas = new PGSimpleDataSource();
        comoAreas.setURL(url);
        comoAreas.setUser("sibim_areas");
        comoAreas.setPassword(CLAVE_AREAS);
        HikariConfig cfg = new HikariConfig();
        cfg.setDataSource(comoAreas);
        cfg.setMaximumPoolSize(3);
        poolAreas = new HikariDataSource(cfg);
    }

    @AfterAll
    void cerrarPool() {
        DatabaseConfig.clearDataSourceForTest(poolAreas);
        if (poolAreas != null) poolAreas.close();
    }

    /** As Patrimonio (the owner): a category, a bien of the área, a dirección
     *  account and a transfer to the área waiting for its acuse. */
    @BeforeEach
    void datosDePatrimonio() throws Exception {
        admin = SessionManager.getCurrentUser();
        DatabaseConfig.setDataSourceForTest(dataSource);
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO categories (id, nombre, color) VALUES ('" + CAT_ID + "', 'Mobiliario', '#3B82F6')");
            st.execute("INSERT INTO users (id, username, password, nombre, role, area) VALUES "
                + "('u-dir', 'catastro', '$2a$10$viejo', 'Catastro', 'direccion', '" + AREA + "')");
        }
        new ProductoRepository().saveOnline(bien(BIEN, AREA));
    }

    @AfterEach
    void volverAPatrimonio() {
        SessionManager.setCurrentUser(admin);
    }

    private void comoDireccion() {
        Usuario u = new Usuario();
        u.setId("u-dir");
        u.setUsername("catastro");
        u.setNombre("Catastro");
        u.setRol(Rol.DIRECCION);
        u.setArea(AREA);
        SessionManager.setCurrentUser(u);
        DatabaseConfig.setDataSourceForTest(poolAreas);
    }

    // ── Through SIBIM's own code ────────────────────────────────────────────

    @Test
    void actualizarDatos_guardaLoDelArea_yNadaMas() throws Exception {
        comoDireccion();
        ProductoService service = new ProductoService();
        Producto p = new ProductoRepository().findById(BIEN).orElseThrow();
        p.setUbicacion("Oficina 3");
        p.setDescripcion("Con llave");
        p.setEstadoFisico("Bueno");
        p.setNombre("Otro nombre");                      // not an área's to change
        p.setPrecioCompra(BigDecimal.valueOf(1));

        Producto guardado = service.save(p);
        service.saveFotos(BIEN, List.of("https://ejemplo/foto1.jpg", "https://ejemplo/foto2.jpg"));

        assertNotNull(guardado.getActualizadoEn());
        try (Connection c = getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre, precio_compra, ubicacion, descripcion, estado_fisico, area "
                 + "FROM products WHERE id = '" + BIEN + "'")) {
            assertTrue(rs.next());
            assertEquals("Silla", rs.getString("nombre"));
            assertEquals(0, BigDecimal.valueOf(1000).compareTo(rs.getBigDecimal("precio_compra")));
            assertEquals("Oficina 3", rs.getString("ubicacion"));
            assertEquals("Con llave", rs.getString("descripcion"));
            assertEquals("Bueno", rs.getString("estado_fisico"));
            assertEquals(AREA, rs.getString("area"));
        }
        assertEquals(2, contar("SELECT count(*) FROM product_fotos WHERE producto_id = '" + BIEN + "'"));
        assertTrue(contar("SELECT count(*) FROM audit_log WHERE entidad_id = '" + BIEN + "'") > 0);
    }

    @Test
    void acuseDeRecibo_cambiarContrasena_yRastroDelEquipo() throws Exception {
        // A bien of another área that Patrimonio transfers to this one.
        new ProductoRepository().saveOnline(bien("bien-llega", "Tesorería Municipal"));
        MovimientoRepository movimientos = new MovimientoRepository();
        Movimiento m = new Movimiento();
        m.setProductoId("bien-llega");
        m.setTipo(TipoMovimiento.TRANSFERENCIA);
        m.setCantidad(1);
        m.setMotivo("Prueba");
        m.setUsuarioId("test-admin");
        m.setUsuarioNombre("Admin Test");
        m.setAreaDestino(AREA);
        movimientos.addMovimientoPendiente(m);
        movimientos.aprobarTransferencia(m.getId());

        comoDireccion();
        new MovimientoService().confirmarRecepcion(m);
        new UsuarioRepository().completarCambioPassword("u-dir", "$2a$10$nuevo");
        new AuditLogRepository().log("producto", BIEN, "Silla", "consultar", "Prueba");
        LoginAttemptRepository intentos = new LoginAttemptRepository();
        intentos.registrarFallo("catastro", 60_000);
        intentos.registrarFallo("catastro", 60_000);
        intentos.limpiar("catastro");
        new AccesosEquipoService().registrar("u-dir");
        new AccesosEquipoService().registrar("u-dir");
        String folio = new FolioRepository().next("ALE");
        AreaResguardoRepository resguardos = new AreaResguardoRepository();
        String id = resguardos.save(AREA, new byte[]{1, 2, 3}, "resguardo.pdf", "Prueba", LocalDate.now());
        resguardos.delete(id);

        assertNotNull(folio);
        assertEquals(1, contar("SELECT count(*) FROM movements WHERE id = '" + m.getId() + "' AND recibido_por = 'Catastro'"));
        assertEquals(1, contar("SELECT count(*) FROM users WHERE id = 'u-dir' AND password = '$2a$10$nuevo'"));
        assertEquals(1, contar("SELECT count(*) FROM accesos_equipo WHERE user_id = 'u-dir'"));
        assertEquals(0, contar("SELECT count(*) FROM area_resguardos"));
    }

    @Test
    void loDePatrimonio_noSePuedeDesdeUnaPcDeArea() throws Exception {
        comoDireccion();
        Producto nuevo = bien(BIEN, AREA);
        nuevo.setId(null);
        assertThrows(ProductoService.ValidationException.class, () -> new ProductoService().save(nuevo));

        Movimiento entrada = new Movimiento();
        entrada.setProductoId(BIEN);
        entrada.setTipo(TipoMovimiento.ENTRADA);
        entrada.setCantidad(1);
        entrada.setUsuarioId("u-dir");
        entrada.setUsuarioNombre("Catastro");
        SQLException ex = assertThrows(SQLException.class,
            () -> new MovimientoRepository().addMovimientoAtomicOnline(entrada, null));
        assertEquals("42501", ex.getSQLState());
    }

    // ── Straight SQL: what the user can and can't do, whatever the app does ─

    @Test
    void enLaBase_noPuedeCambiarLoQueNoEsDeUnArea() throws Exception {
        try (Connection c = comoAreas.getConnection(); Statement st = c.createStatement()) {
            assertTrue(contar(st, "SELECT count(*) FROM products") > 0, "lee el inventario");
            assertTrue(contar(st, "SELECT count(*) FROM users") > 0);

            for (String sql : List.of(
                    "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad) VALUES ('x', 'X', 'X-1', '" + CAT_ID + "', 'A', 'pieza')",
                    "DELETE FROM products",
                    "UPDATE products SET precio_compra = 1",
                    "UPDATE products SET area = 'Otra'",
                    "UPDATE products SET codigo = 'X-9'",
                    "UPDATE products SET fecha_baja = CURRENT_DATE",
                    "INSERT INTO movements (id, producto_id, tipo, cantidad) VALUES ('m', '" + BIEN + "', 'SALIDA', 1)",
                    "DELETE FROM movements",
                    "UPDATE movements SET cantidad = 9",
                    "UPDATE users SET role = 'admin'",
                    "INSERT INTO users (id, username, password, nombre, role) VALUES ('u-x', 'x', 'x', 'X', 'admin')",
                    "DELETE FROM users",
                    "INSERT INTO configuracion (clave, valor) VALUES ('x', 'y')",
                    "UPDATE configuracion SET valor = 'y'",
                    "INSERT INTO categories (id, nombre, color) VALUES ('c-x', 'X', '#000000')",
                    "DELETE FROM audit_log",
                    "UPDATE audit_log SET detalle = 'x'",
                    "INSERT INTO actualizaciones (version) VALUES ('9.9.9')",
                    "CREATE TABLE intrusa (id INT)")) {
                SQLException ex = assertThrows(SQLException.class, () -> st.execute(sql), sql);
                assertEquals("42501", ex.getSQLState(), sql + ": " + ex.getMessage());
            }
        }
    }

    @Test
    void enLaBase_noPuedeQuitarleElAccesoAPatrimonio() throws Exception {
        try (Connection c = comoAreas.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password = 'x' WHERE id = 'test-admin'")) {
            assertEquals(0, ps.executeUpdate(), "la contraseña de una cuenta de administrador no se toca");
        }
        assertEquals(1, contar("SELECT count(*) FROM users WHERE id = 'test-admin' AND password = '$2a$10$placeholder'"));
    }

    // ────────────────────────────────────────────────────────────────────────

    private static Producto bien(String id, String area) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Silla");
        p.setCodigo(id.toUpperCase());
        p.setCategoriaId(CAT_ID);
        p.setPrecioCompra(BigDecimal.valueOf(1000));
        p.setPrecioVenta(BigDecimal.valueOf(1000));
        p.setStockActual(1);
        p.setStockMaximo(1);
        p.setUnidad(UnidadMedida.fromCodigo("pieza"));
        p.setArea(area);
        return p;
    }

    private long contar(String sql) throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            return contar(st, sql);
        }
    }

    private static long contar(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
