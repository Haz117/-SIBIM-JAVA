package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.model.Comodato;
import com.sibim.model.Producto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ComodatoRepository;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.repository.PrestamoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests validation logic in ComodatoService.crear().
 * All asserted exceptions are thrown before any repository call, so
 * demo mode is sufficient (no live DB required).
 */
class ComodatoServiceValidationTest {

    private ComodatoService service;

    // Arbitrary valid values to use when a field is NOT the one under test
    private static final String  VALID_PRODUCTO_ID     = "p-01";
    private static final String  VALID_ENTIDAD         = "Cruz Roja Municipal";
    private static final String  VALID_CONTACTO        = "Juan Pérez";
    private static final LocalDate VALID_FECHA_INICIO  = LocalDate.now();
    private static final LocalDate VALID_FECHA_FIN     = LocalDate.now().plusMonths(6);

    @BeforeEach
    void setUp() {
        DatabaseConfig.setDemoMode(true);
        // Admin has unrestricted area access — needed by ProductoRepository.findById
        // which checks accessible areas.
        Usuario admin = new Usuario();
        admin.setId("test-admin");
        admin.setNombre("Test Admin");
        admin.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(admin);

        service = new ComodatoService();
    }

    @AfterEach
    void tearDown() {
        DatabaseConfig.setDemoMode(false);
        SessionManager.logout();
    }

    @Test
    void testCrear_entidadReceptoraBlank_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                "   ",           // blank entidad — should throw
                VALID_CONTACTO, null, null, null, null,
                VALID_FECHA_INICIO, VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("entidad"),
            "El mensaje debe mencionar 'entidad': " + ex.getMessage());
    }

    @Test
    void testCrear_entidadReceptoraNula_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                null,            // null entidad — should throw
                VALID_CONTACTO, null, null, null, null,
                VALID_FECHA_INICIO, VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("entidad"),
            "El mensaje debe mencionar 'entidad': " + ex.getMessage());
    }

    @Test
    void testCrear_contactoNombreBlank_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                VALID_ENTIDAD,
                "  ",            // blank contacto — should throw
                null, null, null, null,
                VALID_FECHA_INICIO, VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("contacto"),
            "El mensaje debe mencionar 'contacto': " + ex.getMessage());
    }

    @Test
    void testCrear_fechaFinBeforeFechaInicio_throws() {
        LocalDate inicio = LocalDate.now();
        LocalDate fin    = inicio.minusDays(1);   // one day BEFORE inicio — invalid

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null,
                inicio, fin
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("fecha"),
            "El mensaje debe mencionar 'fecha': " + ex.getMessage());
    }

    @Test
    void testCrear_fechaFinEqualsFechaInicio_throws() {
        // "fin" equal to "inicio" is also invalid — fechaFin must be AFTER fechaInicio
        LocalDate same = LocalDate.now();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null,
                same, same
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("fecha"),
            "El mensaje debe mencionar 'fecha': " + ex.getMessage());
    }

    @Test
    void testCrear_productoIdNull_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                null,            // null productoId — should throw first
                VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null,
                VALID_FECHA_INICIO, VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("bien") ||
                   ex.getMessage().toLowerCase().contains("producto"),
            "El mensaje debe mencionar 'bien' o 'producto': " + ex.getMessage());
    }

    @Test
    void testCrear_productoIdBlank_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                "   ",           // blank productoId
                VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null,
                VALID_FECHA_INICIO, VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("bien") ||
                   ex.getMessage().toLowerCase().contains("producto"),
            "El mensaje debe mencionar 'bien' o 'producto': " + ex.getMessage());
    }

    @Test
    void testCrear_fechaInicioNull_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null,
                null,            // null fechaInicio — should throw
                VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("inicio") ||
                   ex.getMessage().toLowerCase().contains("fecha"),
            "El mensaje debe mencionar 'inicio' o 'fecha': " + ex.getMessage());
    }

    @Test
    void testCrear_valido_guardaYAudita() throws Exception {
        ComodatoRepository comodatoRepo = mock(ComodatoRepository.class);
        ProductoRepository productoRepo = mock(ProductoRepository.class);
        PrestamoRepository prestamoRepo = mock(PrestamoRepository.class);
        AuditLogRepository auditRepo = mock(AuditLogRepository.class);
        Producto producto = new Producto();
        producto.setNombre("Laptop de préstamo");
        producto.setCodigo("EC-001");
        when(productoRepo.findById(VALID_PRODUCTO_ID)).thenReturn(java.util.Optional.of(producto));
        when(comodatoRepo.save(any(Comodato.class))).thenAnswer(invocation -> {
            Comodato saved = invocation.getArgument(0);
            saved.setId("comodato-1");
            saved.setNumero("CDT-001");
            return saved;
        });

        ComodatoService isolated = new ComodatoService(comodatoRepo, productoRepo,
            prestamoRepo, mock(ConfiguracionRepository.class), auditRepo);
        Comodato result = isolated.crear(VALID_PRODUCTO_ID, VALID_ENTIDAD, VALID_CONTACTO,
            null, null, null, null, VALID_FECHA_INICIO, VALID_FECHA_FIN);

        assertEquals("comodato-1", result.getId());
        verify(comodatoRepo).save(any(Comodato.class));
        verify(auditRepo).log(eq("comodato"), eq("comodato-1"), eq("Laptop de préstamo"),
            eq("crear"), anyString());
    }

    @Test
    void testCrear_bienConComodatoVigente_throws() throws Exception {
        ComodatoRepository comodatoRepo = mock(ComodatoRepository.class);
        ProductoRepository productoRepo = mock(ProductoRepository.class);
        Producto producto = new Producto();
        producto.setNombre("Laptop");
        when(productoRepo.findById(VALID_PRODUCTO_ID)).thenReturn(java.util.Optional.of(producto));
        when(comodatoRepo.existeVigentePorProducto(VALID_PRODUCTO_ID)).thenReturn(true);

        ComodatoService isolated = new ComodatoService(comodatoRepo, productoRepo,
            mock(PrestamoRepository.class), mock(ConfiguracionRepository.class),
            mock(AuditLogRepository.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            isolated.crear(VALID_PRODUCTO_ID, VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null, VALID_FECHA_INICIO, VALID_FECHA_FIN));
        assertTrue(ex.getMessage().contains("comodato vigente"));
    }

    @Test
    void testCrear_bienConPrestamoActivo_throws() throws Exception {
        ComodatoRepository comodatoRepo = mock(ComodatoRepository.class);
        ProductoRepository productoRepo = mock(ProductoRepository.class);
        PrestamoRepository prestamoRepo = mock(PrestamoRepository.class);
        Producto producto = new Producto();
        producto.setNombre("Laptop");
        when(productoRepo.findById(VALID_PRODUCTO_ID)).thenReturn(java.util.Optional.of(producto));
        when(prestamoRepo.existsActivoForProducto(VALID_PRODUCTO_ID)).thenReturn(true);

        ComodatoService isolated = new ComodatoService(comodatoRepo, productoRepo,
            prestamoRepo, mock(ConfiguracionRepository.class), mock(AuditLogRepository.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            isolated.crear(VALID_PRODUCTO_ID, VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null, VALID_FECHA_INICIO, VALID_FECHA_FIN));
        assertTrue(ex.getMessage().contains("préstamo activo"));
    }
}
