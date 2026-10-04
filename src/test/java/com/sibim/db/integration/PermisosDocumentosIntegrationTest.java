package com.sibim.db.integration;

import com.sibim.config.Areas;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Prestamo;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.service.PrestamoService;
import com.sibim.service.ResguardoService;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Who registers what (decided 2026-10-01):
 * a secretario lends bienes of his secretaría, and only to its own direcciones;
 * a dirección lends nothing; both assign resguardos of their own bienes;
 * only Patrimonio cancels a resguardo.
 */
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class PermisosDocumentosIntegrationTest extends IntegrationTestBase {

    private final PrestamoService prestamos = new PrestamoService();
    private final ResguardoService resguardos = new ResguardoService();

    private String secretaria, direccion, ajena;
    private final LocalDate devolucion = LocalDate.now().plusDays(10);

    @BeforeEach
    void bienes() throws SQLException {
        secretaria = Areas.getAllAreaNames().stream()
            .filter(a -> !Areas.getDireccionesDeSecretaria(a).isEmpty()).findFirst().orElseThrow();
        direccion = Areas.getDireccionesDeSecretaria(secretaria).get(0);
        ajena = Areas.getAllAreaNames().stream()
            .filter(a -> !a.equals(secretaria) && !Areas.getDireccionesDeSecretaria(secretaria).contains(a))
            .findFirst().orElseThrow();
        try (Connection c = DatabaseConfig.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO categories (id, nombre, color) VALUES ('cat-p', 'Permisos', '#22C55E')")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO products (id, nombre, codigo, categoria_id, area, stock_actual) VALUES (?,?,?,?,?,1)")) {
                for (String[] b : new String[][]{
                        {"p-sec", "Laptop", "PRM/01", secretaria}, {"p-dir", "Proyector", "PRM/02", direccion},
                        {"p-dir2", "Escritorio", "PRM/04", direccion}, {"p-ajeno", "Silla", "PRM/03", ajena}}) {
                    ps.setString(1, b[0]); ps.setString(2, b[1]); ps.setString(3, b[2]);
                    ps.setString(4, "cat-p"); ps.setString(5, b[3]);
                    ps.executeUpdate();
                }
            }
        }
    }

    @AfterEach
    void salir() { SessionManager.logout(); }

    @Test
    void elSecretarioNoPrestaNiDevuelve() throws Exception {
        entrarComo(Rol.ADMIN, null);
        Prestamo dePatrimonio = prestamos.crear("p-sec", direccion, "Ana Ruiz", "Analista", "Evento", devolucion);
        assertNotNull(dePatrimonio.getNumero());

        entrarComo(Rol.SECRETARIO, secretaria);
        SecurityException e = assertThrows(SecurityException.class,
            () -> prestamos.crear("p-dir", secretaria, "Luis Mora", null, null, devolucion),
            "ni siquiera entre las áreas de su propia secretaría");
        assertTrue(e.getMessage().contains("Patrimonio"));
        assertThrows(SecurityException.class, () -> prestamos.devolver(dePatrimonio.getId(), LocalDate.now()));

        entrarComo(Rol.ADMIN, null);
        prestamos.devolver(dePatrimonio.getId(), LocalDate.now());
        assertEquals(Prestamo.ESTADO_DEVUELTO, prestamos.getAll().stream()
            .filter(p -> p.getId().equals(dePatrimonio.getId())).findFirst().orElseThrow().getEstado());
    }

    @Test
    void unaDireccionNoPrestaNiDevuelve() throws Exception {
        entrarComo(Rol.ADMIN, null);
        Prestamo dePatrimonio = prestamos.crear("p-dir", ajena, "Ana Ruiz", null, null, devolucion);

        entrarComo(Rol.DIRECCION, direccion);
        assertThrows(SecurityException.class,
            () -> prestamos.crear("p-dir2", secretaria, "Ana Ruiz", null, null, devolucion));
        assertThrows(SecurityException.class, () -> prestamos.devolver(dePatrimonio.getId(), LocalDate.now()));

        entrarComo(Rol.SECRETARIO, secretaria);
        assertThrows(SecurityException.class, () -> prestamos.devolver(dePatrimonio.getId(), LocalDate.now()),
            "el secretario tampoco");
    }

    @Test
    void lasAreasNoAsignanResguardos() throws Exception {
        entrarComo(Rol.DIRECCION, direccion);
        SecurityException e = assertThrows(SecurityException.class,
            () -> resguardos.crear("Luis Mora", "Jefe", direccion, List.of(item("p-dir", "Proyector", "PRM/02")), null),
            "ni de un bien de su propia área");
        assertTrue(e.getMessage().contains("Patrimonio"));

        entrarComo(Rol.SECRETARIO, secretaria);
        assertThrows(SecurityException.class,
            () -> resguardos.crear("Eva Sol", null, direccion, List.of(item("p-dir2", "Escritorio", "PRM/04")), null),
            "el secretario tampoco");

        entrarComo(Rol.ADMIN, null);
        Resguardo propio = resguardos.crear("Luis Mora", "Jefe", direccion, List.of(item("p-dir", "Proyector", "PRM/02")), null);
        assertNotNull(propio.getNumero());

        entrarComo(Rol.DIRECCION, direccion);
        assertThrows(SecurityException.class, () -> resguardos.cancelar(propio.getId()),
            "cancelar libera el bien: solo Patrimonio");

        entrarComo(Rol.ADMIN, null);
        resguardos.cancelar(propio.getId());
        assertFalse(resguardos.findById(propio.getId()).isActivo());
    }

    private static ResguardoItem item(String productoId, String nombre, String codigo) {
        ResguardoItem it = new ResguardoItem();
        it.setProductoId(productoId);
        it.setProductoNombre(nombre);
        it.setProductoCodigo(codigo);
        it.setCantidad(1);
        return it;
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
