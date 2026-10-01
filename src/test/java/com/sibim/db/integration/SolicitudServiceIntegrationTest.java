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

/** An área asks for a préstamo or a resguardo; Patrimonio approves (the
 *  document is created) or rejects with a reason. */
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
    void elAreaPideYPatrimonioApruebaElPrestamo() throws Exception {
        entrarComo(Rol.DIRECCION, AREA);
        LocalDate devolucion = LocalDate.now().plusDays(10);
        Solicitud s = servicio.solicitarPrestamo("b-1", OTRA, "Ana Ruiz", "Analista", "Evento", devolucion);
        assertTrue(s.isPendiente());
        assertEquals(AREA, s.area());
        assertEquals(1, servicio.deMisAreas().size());
        assertTrue(servicio.pendientes().isEmpty(), "la bandeja es solo del administrador");
        assertThrows(IllegalArgumentException.class,
            () -> servicio.solicitarPrestamo("b-1", OTRA, "Ana Ruiz", null, null, devolucion),
            "no se repite una solicitud que sigue en espera");
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
        entrarComo(Rol.DIRECCION, AREA);
        Solicitud resguardo = servicio.solicitarResguardo("b-1", "Luis Mora", "Jefe", null);
        assertThrows(IllegalArgumentException.class,
            () -> servicio.solicitarResguardo("b-2", "Luis Mora", null, null),
            "un bien de otra área no se puede pedir");

        entrarComo(Rol.ADMIN, null);
        String folio = servicio.aprobar(resguardo.id());
        assertEquals(1, contar("SELECT COUNT(*) FROM resguardos WHERE numero = '" + folio + "'"));

        entrarComo(Rol.DIRECCION, OTRA);
        Solicitud otra = servicio.solicitarResguardo("b-2", "Eva Sol", null, null);
        entrarComo(Rol.ADMIN, null);
        assertThrows(IllegalArgumentException.class, () -> servicio.rechazar(otra.id(), " "));
        servicio.rechazar(otra.id(), "Falta el oficio");

        entrarComo(Rol.DIRECCION, OTRA);
        Solicitud vista = servicio.deMisAreas().get(0);
        assertEquals(Solicitud.ESTADO_RECHAZADA, vista.estado());
        assertEquals("Falta el oficio", vista.respuesta());
        assertEquals(1, servicio.deMisAreas().size(), "cada área ve solo lo suyo");
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
