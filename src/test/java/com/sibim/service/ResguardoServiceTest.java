package com.sibim.service;

import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.repository.ResguardoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResguardoServiceTest {

    @Mock ResguardoRepository    mockRepo;
    @Mock ConfiguracionRepository mockCfg;
    @Mock AuditLogRepository     mockAudit;

    private ResguardoService service;

    private static void entrarComo(com.sibim.model.enums.Rol rol) {
        com.sibim.model.Usuario u = new com.sibim.model.Usuario();
        u.setId("u-" + rol);
        u.setNombre("Prueba " + rol);
        u.setRol(rol);
        com.sibim.session.SessionManager.setCurrentUser(u);
    }

    @BeforeEach
    void setUp() {
        entrarComo(com.sibim.model.enums.Rol.ADMIN);
        service = new ResguardoService(mockRepo, mockCfg, mockAudit);
    }

    @org.junit.jupiter.api.AfterEach
    void salir() { com.sibim.session.SessionManager.logout(); }

    @Test
    void sinSesionNoSeCrea_ySoloElAdministradorCancela() {
        // The áreas create resguardos of their own bienes (PermisosDocumentosIntegrationTest
        // covers whose); cancelling one frees the bien and stays with Patrimonio.
        for (com.sibim.model.enums.Rol rol : new com.sibim.model.enums.Rol[]{
                com.sibim.model.enums.Rol.SECRETARIO, com.sibim.model.enums.Rol.DIRECCION }) {
            entrarComo(rol);
            assertThrows(SecurityException.class, () -> service.cancelar("r-01"), rol.name());
        }
        com.sibim.session.SessionManager.logout();
        assertThrows(SecurityException.class,
            () -> service.crear("Ana García", null, null, List.of(item()), null));
    }

    // ── Validación: nombre del resguardante ───────────────────────────────────

    @Test
    void crear_nombreNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear(null, null, null, List.of(item()), null));
    }

    @Test
    void crear_nombreVacio_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("   ", null, null, List.of(item()), null));
    }

    // ── Validación: lista de bienes ───────────────────────────────────────────

    @Test
    void crear_itemsNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("Ana García", null, null, null, null));
    }

    @Test
    void crear_itemsVacio_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("Ana García", null, null, List.of(), null));
    }

    // ── Éxito: crear resguardo ────────────────────────────────────────────────

    @Test
    void crear_datosValidos_llamaRepoSave_yRetornaResguardo() throws Exception {
        Resguardo guardado = new Resguardo();
        guardado.setNumero("RES-0001");
        when(mockRepo.save(any(Resguardo.class))).thenReturn(guardado);

        Resguardo resultado = service.crear("Ana García", "Directora",
            "Tesorería", List.of(item()), "Sin observaciones");

        assertSame(guardado, resultado);
        verify(mockRepo).save(any(Resguardo.class));
    }

    @Test
    void crear_cargoYAreaNulos_noLanzaExcepcion() throws Exception {
        when(mockRepo.save(any(Resguardo.class))).thenReturn(new Resguardo());

        assertDoesNotThrow(() ->
            service.crear("Ana García", null, null, List.of(item()), null));
        verify(mockRepo).save(any(Resguardo.class));
    }

    // ── cancelar ─────────────────────────────────────────────────────────────

    @Test
    void cancelar_llama_repoCancelar() throws SQLException {
        service.cancelar("r-01");
        verify(mockRepo).cancelar("r-01");
    }

    // ── Mensaje de error legible ──────────────────────────────────────────────

    @Test
    void crear_nombreVacio_mensajeContieneNombre() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> service.crear("", null, null, List.of(item()), null));
        assertTrue(ex.getMessage().toLowerCase().contains("nombre"));
    }

    @Test
    void crear_itemsVacio_mensajeContieneBien() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> service.crear("Ana", null, null, List.of(), null));
        assertTrue(ex.getMessage().toLowerCase().contains("bien"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ResguardoItem item() {
        ResguardoItem i = new ResguardoItem();
        i.setProductoId("p-01");
        i.setProductoNombre("Escritorio ejecutivo");
        return i;
    }

    // ── Quitar un bien del resguardo ──────────────────────────────────────────

    private static Resguardo resguardo() {
        Resguardo r = new Resguardo();
        r.setId("r-01");
        r.setNumero("RSG-2026-000010");
        r.setResguardanteNombre("Ana García");
        return r;
    }

    @Test
    void quitarBien_soloElAdministrador_yConMotivo() {
        for (com.sibim.model.enums.Rol rol : new com.sibim.model.enums.Rol[]{
                com.sibim.model.enums.Rol.SECRETARIO, com.sibim.model.enums.Rol.DIRECCION }) {
            entrarComo(rol);
            assertThrows(SecurityException.class, () -> service.quitarBien(resguardo(), item(), "baja"), rol.name());
        }
        entrarComo(com.sibim.model.enums.Rol.ADMIN);
        assertThrows(IllegalArgumentException.class, () -> service.quitarBien(resguardo(), item(), "  "));
        verifyNoInteractions(mockRepo);
    }

    /** The signed paper still lists the bien: the resguardo says when it left and why. */
    @Test
    void quitarBien_anotaFechaBienYMotivo_yLoAudita() throws Exception {
        ResguardoItem it = item();
        it.setProductoCodigo("PRES/27");
        when(mockRepo.quitarBien(eq("r-01"), eq("p-01"), any())).thenReturn(false);

        assertFalse(service.quitarBien(resguardo(), it, " se dará de baja "));

        org.mockito.ArgumentCaptor<String> nota = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mockRepo).quitarBien(eq("r-01"), eq("p-01"), nota.capture());
        String hoy = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertEquals(hoy + ": se quitó PRES/27 Escritorio ejecutivo (se dará de baja).", nota.getValue());
        verify(mockAudit).log(eq("resguardo"), eq("r-01"), eq("Ana García"), eq("actualizar"),
            org.mockito.ArgumentMatchers.contains("se quitó PRES/27"));
    }

    /** A lote is one renglón of several pieces: the printed resguardo says how many
     *  and its total counts all of them (it added the price of one). */
    @Test
    void elPdfDeUnLote_diceSusPiezas_ySuTotalLasCuenta() throws Exception {
        when(mockCfg.get(any(), any())).thenReturn("");
        Resguardo r = new Resguardo();
        r.setId("r-07"); r.setNumero("RSG-2026-000007"); r.setResguardanteNombre("Ana García");
        ResguardoItem sillas = item();
        sillas.setProductoNombre("Silla apilable"); sillas.setProductoCodigo("SGM/07");
        sillas.setCantidad(20); sillas.setValorUnitario(new java.math.BigDecimal("1000"));
        ResguardoItem mesa = item();
        mesa.setProductoNombre("Mesa"); mesa.setProductoCodigo("SGM/08");
        mesa.setValorUnitario(new java.math.BigDecimal("500"));
        r.setItems(List.of(sillas, mesa));

        java.io.File pdf;
        com.sibim.db.DatabaseConfig.setDemoMode(true);   // the header logo reads configuracion
        try { pdf = service.exportarPdf(r); } finally { com.sibim.db.DatabaseConfig.setDemoMode(false); }
        String t;
        try (var d = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(pdf))) {
            t = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(d.getPage(1));
        }
        assertTrue(t.contains("20 piezas: Silla apilable"), t);
        assertFalse(t.contains("piezas: Mesa"), "una sola pieza no lo dice");
        assertTrue(t.contains("20,500.00"), "20 × $1,000 + $500");
    }
}
