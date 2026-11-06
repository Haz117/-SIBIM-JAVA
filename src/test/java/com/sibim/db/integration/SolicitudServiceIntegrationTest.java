package com.sibim.db.integration;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Solicitud;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.service.SolicitudService;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** The áreas no longer send requests through SIBIM. Patrimonio can still answer
 *  the ones that were waiting from before: approve (the document is created) or
 *  reject with a reason. */
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class SolicitudServiceIntegrationTest extends IntegrationTestBase {

    private static final String AREA  = "Dirección de Catastro";
    private static final String OTRA  = "Dirección de Ecología";
    private final SolicitudService servicio = new SolicitudService();

    @BeforeEach
    void bienes() throws SQLException {
        try (Connection c = DatabaseConfig.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO categories (id, nombre, color) VALUES ('cat-s', 'Solicitudes', '#22C55E')")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO products (id, nombre, codigo, categoria_id, area, stock_actual) VALUES (?,?,?,?,?,1)")) {
                for (String[] b : new String[][]{{"b-1", "Laptop", "CAT/01", AREA}, {"b-2", "Proyector", "ECO/01", OTRA}}) {
                    ps.setString(1, b[0]); ps.setString(2, b[1]); ps.setString(3, b[2]);
                    ps.setString(4, "cat-s"); ps.setString(5, b[3]);
                    ps.executeUpdate();
                }
            }
        }
    }

    @AfterEach
    void salir() { SessionManager.logout(); }

    @Test
    void lasAreasYaNoEnvianSolicitudes() throws Exception {
        LocalDate devolucion = LocalDate.now().plusDays(10);
        for (Rol rol : new Rol[]{ Rol.DIRECCION, Rol.SECRETARIO, Rol.ADMIN }) {
            entrarComo(rol, rol == Rol.ADMIN ? null : AREA);
            assertThrows(SecurityException.class,
                () -> servicio.solicitarPrestamo("b-1", OTRA, "Ana Ruiz", "Analista", "Evento", devolucion), rol.name());
            assertThrows(SecurityException.class,
                () -> servicio.solicitarResguardo("b-1", "Luis Mora", "Jefe", null), rol.name());
        }
        assertEquals(0, contar("SELECT COUNT(*) FROM solicitudes"));
    }

    @Test
    void patrimonioApruebaElPrestamoQueQuedoPendiente() throws Exception {
        Solicitud s = pendiente(Solicitud.TIPO_PRESTAMO, "b-1", "Laptop", "CAT/01", AREA, OTRA, "Ana Ruiz");

        entrarComo(Rol.DIRECCION, AREA);
        assertEquals(1, servicio.deMisAreas().size());
        assertTrue(servicio.pendientes().isEmpty(), "la bandeja es solo del administrador");
        assertThrows(SecurityException.class, () -> servicio.aprobar(s.id()));

        entrarComo(Rol.ADMIN, null);
        assertEquals(1, servicio.countPendientes());
        String folio = servicio.aprobar(s.id());
        assertEquals(1, contar("SELECT COUNT(*) FROM prestamos WHERE numero = '" + folio + "' AND producto_id = 'b-1'"));
        assertEquals(0, servicio.countPendientes());
        assertThrows(IllegalStateException.class, () -> servicio.aprobar(s.id()), "ya atendida");

        entrarComo(Rol.DIRECCION, AREA);
        Solicitud vista = servicio.deMisAreas().get(0);
        assertEquals(Solicitud.ESTADO_APROBADA, vista.estado());
        assertEquals(folio, vista.documento());
    }

    @Test
    void resguardoAprobadoYRechazoConMotivo() throws Exception {
        Solicitud resguardo = pendiente(Solicitud.TIPO_RESGUARDO, "b-1", "Laptop", "CAT/01", AREA, null, "Luis Mora");
        Solicitud otra = pendiente(Solicitud.TIPO_RESGUARDO, "b-2", "Proyector", "ECO/01", OTRA, null, "Eva Sol");

        entrarComo(Rol.ADMIN, null);
        String folio = servicio.aprobar(resguardo.id());
        assertEquals(1, contar("SELECT COUNT(*) FROM resguardos WHERE numero = '" + folio + "'"));
        assertThrows(IllegalArgumentException.class, () -> servicio.rechazar(otra.id(), " "));
        servicio.rechazar(otra.id(), "Falta el oficio");

        entrarComo(Rol.DIRECCION, OTRA);
        Solicitud vista = servicio.deMisAreas().get(0);
        assertEquals(Solicitud.ESTADO_RECHAZADA, vista.estado());
        assertEquals("Falta el oficio", vista.respuesta());
        assertEquals(1, servicio.deMisAreas().size(), "cada área ve solo lo suyo");
    }

    // ── Solicitudes de baja (5-nov-2026) ─────────────────────────────────────

    @Test
    void bajaPedidaPorElArea_llegaAPatrimonio_yAlDarDeBajaSeCierra() throws Exception {
        entrarComo(Rol.DIRECCION, AREA);
        var bien = new com.sibim.service.ProductoService().findById("b-1").orElseThrow();
        var envio = servicio.solicitarBaja(java.util.List.of(bien), "Obsoleto", "  Ya no enciende ");
        assertEquals(1, envio.enviadas().size());
        Solicitud s = envio.enviadas().get(0);
        assertEquals(Solicitud.TIPO_BAJA, s.tipo());
        assertEquals("Obsoleto: Ya no enciende", s.motivo());
        assertEquals("Prueba " + Rol.DIRECCION, s.solicitante());
        var otraVez = servicio.solicitarBaja(java.util.List.of(bien), "Obsoleto", null);
        assertTrue(otraVez.enviadas().isEmpty(), "no se manda dos veces");
        assertEquals(1, otraVez.yaEnEspera().size());

        entrarComo(Rol.ADMIN, null);
        assertEquals(1, servicio.countPendientes(Solicitud.TIPO_BAJA), "a Patrimonio le llega el aviso");
        assertEquals(0, servicio.countPendientes(Solicitud.TIPO_PRESTAMO));
        assertEquals(1, servicio.pendientes().size());
        assertThrows(IllegalStateException.class, () -> servicio.aprobar(s.id()), "una baja se da con «Dar de baja»");
        new com.sibim.service.ProductoService().darDeBaja("b-1", "Obsoleto: Ya no enciende · Solicitud de baja: OF-12",
            null, null, "AB-3", null);
        assertEquals(0, servicio.countPendientes(Solicitud.TIPO_BAJA), "dar de baja cierra la solicitud");

        entrarComo(Rol.DIRECCION, AREA);
        Solicitud vista = servicio.deMisAreas().get(0);
        assertEquals(Solicitud.ESTADO_APROBADA, vista.estado());
        assertEquals("Acta de baja AB-3", vista.documento());
        assertEquals("OF-12", vista.formatoFolio(), "la baja pide el folio del formato: queda como recibido");
        assertNotNull(vista.formatoRecibidoEn());
        assertThrows(IllegalArgumentException.class,
            () -> servicio.solicitarBaja(java.util.List.of(bien), "Obsoleto", null), "ya está dado de baja");
    }

    /** The área hands in the signed formato at Recursos Materiales y Patrimonio; Patrimonio notes its folio. */
    @Test
    void elFormatoFirmado_seRecibeConSuFolio_ySoloPatrimonio() throws Exception {
        entrarComo(Rol.DIRECCION, AREA);
        var bien = new com.sibim.service.ProductoService().findById("b-1").orElseThrow();
        Solicitud s = servicio.solicitarBaja(java.util.List.of(bien), "Obsoleto", null).enviadas().get(0);
        assertTrue(s.faltaFormato(), "recién enviada, falta el formato");
        assertThrows(SecurityException.class, () -> servicio.recibirFormato(s.id(), "SB-1"), "el área no lo marca");

        entrarComo(Rol.ADMIN, null);
        assertThrows(IllegalArgumentException.class, () -> servicio.recibirFormato(s.id(), "  "));
        servicio.recibirFormato(s.id(), " SB-021/2026 ");
        Solicitud recibida = servicio.pendientes().get(0);
        assertFalse(recibida.faltaFormato());
        assertEquals("SB-021/2026", recibida.formatoFolio());
        assertEquals("Prueba " + Rol.ADMIN, recibida.formatoRecibidoPor());

        Solicitud prestamo = pendiente(Solicitud.TIPO_PRESTAMO, "b-2", "Proyector", "ECO/01", OTRA, AREA, "Eva Sol");
        entrarComo(Rol.ADMIN, null);
        assertThrows(IllegalArgumentException.class, () -> servicio.recibirFormato(prestamo.id(), "X-1"));
        servicio.rechazar(s.id(), "Se puede reparar");
        assertThrows(IllegalStateException.class, () -> servicio.recibirFormato(s.id(), "SB-2"), "ya atendida");
    }

    @Test
    void bajaQueNoProcede_elAreaVeElMotivo() throws Exception {
        entrarComo(Rol.SECRETARIO, AREA);
        var bien = new com.sibim.service.ProductoService().findById("b-1").orElseThrow();
        Solicitud s = servicio.solicitarBaja(java.util.List.of(bien), "Otro", "Lo mordió el perro").enviadas().get(0);
        assertEquals("Lo mordió el perro", s.motivo(), "con «Otro» el motivo es lo que escribieron");

        entrarComo(Rol.ADMIN, null);
        servicio.rechazar(s.id(), "Se puede reparar en Taller Municipal");
        assertEquals(0, servicio.countPendientes(Solicitud.TIPO_BAJA));

        entrarComo(Rol.SECRETARIO, AREA);
        Solicitud vista = servicio.deMisAreas().get(0);
        assertEquals(Solicitud.ESTADO_RECHAZADA, vista.estado());
        assertEquals("Se puede reparar en Taller Municipal", vista.respuesta());
        assertEquals(1, servicio.solicitarBaja(java.util.List.of(bien), "Obsoleto", null).enviadas().size(),
            "después de un «No procede» se puede volver a pedir");
    }

    @Test
    void bajaSoloDeSusBienes_conMotivo_yNoPatrimonio() throws Exception {
        entrarComo(Rol.ADMIN, null);
        var productos = new com.sibim.service.ProductoService();
        var suyo = productos.findById("b-1").orElseThrow();
        var ajeno = productos.findById("b-2").orElseThrow();
        assertThrows(IllegalStateException.class,
            () -> servicio.solicitarBaja(java.util.List.of(suyo), "Obsoleto", null), "Patrimonio da de baja directo");

        entrarComo(Rol.DIRECCION, AREA);
        assertThrows(SecurityException.class,
            () -> servicio.solicitarBaja(java.util.List.of(suyo, ajeno), "Obsoleto", null));
        assertThrows(IllegalArgumentException.class, () -> servicio.solicitarBaja(java.util.List.of(suyo), " ", null));
        assertThrows(IllegalArgumentException.class, () -> servicio.solicitarBaja(java.util.List.of(suyo), "Otro", " "));
        assertEquals(0, contar("SELECT COUNT(*) FROM solicitudes"), "si uno no se puede, no se manda ninguno");
    }

    @Test
    void unaSolaBajaEnEsperaPorBien_aunqueLleguenJuntas() throws Exception {
        pendiente(Solicitud.TIPO_BAJA, "b-1", "Laptop", "CAT/01", AREA, null, "Luis Mora");
        SQLException e = assertThrows(SQLException.class,
            () -> pendiente(Solicitud.TIPO_BAJA, "b-1", "Laptop", "CAT/01", AREA, null, "Luis Mora"));
        assertEquals("23505", e.getSQLState());
        pendiente(Solicitud.TIPO_RESGUARDO, "b-1", "Laptop", "CAT/01", AREA, null, "Luis Mora");
    }

    /** A request as the áreas used to leave it, written straight to the table. */
    private Solicitud pendiente(String tipo, String productoId, String nombre, String codigo, String area,
                                String areaDestino, String responsable) throws SQLException {
        entrarComo(Rol.DIRECCION, area);
        return new com.sibim.repository.SolicitudRepository().save(new Solicitud(null, tipo, productoId, nombre, codigo,
            area, "Prueba " + Rol.DIRECCION, areaDestino, responsable, null, "Evento",
            Solicitud.TIPO_PRESTAMO.equals(tipo) ? LocalDate.now().plusDays(10) : null,
            Solicitud.ESTADO_PENDIENTE, null, null, null, null, null));
    }

    private int contar(String sql) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             ResultSet rs = c.createStatement().executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void entrarComo(Rol rol, String area) {
        Usuario u = new Usuario();
        u.setId("test-admin");
        u.setNombre("Prueba " + rol);
        u.setRol(rol);
        u.setArea(area);
        SessionManager.setCurrentUser(u);
    }
}
