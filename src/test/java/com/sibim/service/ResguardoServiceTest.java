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

    private static Resguardo deAna(String id, String numero, String area, int dia, String cargo, String... productos) {
        Resguardo r = new Resguardo();
        r.setId(id); r.setNumero(numero); r.setResguardanteNombre(dia == 3 ? "LIC. ANA PRUEBA SOTO" : "lic. ana prueba soto");
        r.setResguardanteArea(area); r.setResguardanteCargo(cargo); r.setEstado(Resguardo.ESTADO_ACTIVO);
        r.setCreadoEn(java.time.LocalDateTime.of(2026, 11, dia, 10, 0));
        List<ResguardoItem> items = new java.util.ArrayList<>();
        for (String p : productos) {
            ResguardoItem it = new ResguardoItem();
            it.setId("i-" + id + "-" + p); it.setProductoId(p); it.setProductoCodigo("RMP/" + p); it.setProductoNombre("Silla " + p);
            items.add(it);
        }
        r.setItems(items);
        return r;
    }

    /** Several resguardos of one person in one área become one: all their bienes once,
     *  with the newest spelling and cargo; the old ones are cancelled with a note. */
    @Test
    void juntar_hazUnoConTodosSusBienes() throws Exception {
        Resguardo a = deAna("r-a", "RSG-2026-000301", "Recursos Materiales y Patrimonio", 1, "Auxiliar", "p1", "p2");
        Resguardo b = deAna("r-b", "RSG-2026-000302", "Recursos Materiales y Patrimonio", 3, "Directora", "p3");
        Resguardo c = deAna("r-c", "RSG-2026-000303", "Recursos Materiales y Patrimonio", 2, null, "p2", "p4");
        when(mockRepo.juntar(any(), any(), any())).thenAnswer(inv -> {
            Resguardo n = inv.getArgument(0);
            n.setId("r-nuevo"); n.setNumero("RSG-2026-000400");
            return n;
        });

        Resguardo nuevo = service.juntar(List.of(b, a, c));

        @SuppressWarnings("unchecked") org.mockito.ArgumentCaptor<List<String>> ids = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<String> nota = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mockRepo).juntar(same(nuevo), ids.capture(), nota.capture());
        assertEquals(List.of("r-a", "r-c", "r-b"), ids.getValue(), "del más viejo al más nuevo");
        assertTrue(nota.getValue().contains("{folio}"), nota.getValue());
        assertEquals(List.of("p1", "p2", "p4", "p3"), nuevo.getItems().stream().map(ResguardoItem::getProductoId).toList(),
            "cada bien una vez");
        assertTrue(nuevo.getItems().stream().noneMatch(it -> it.getId() != null && it.getId().startsWith("i-")),
            "renglones nuevos: los viejos se quedan con su papel");
        assertEquals("LIC. ANA PRUEBA SOTO", nuevo.getResguardanteNombre(), "como lo escribe el más nuevo");
        assertEquals("Directora", nuevo.getResguardanteCargo());
        assertEquals("Recursos Materiales y Patrimonio", nuevo.getResguardanteArea());
        assertTrue(nuevo.getObservaciones().contains("RSG-2026-000301, RSG-2026-000303, RSG-2026-000302"));
        verify(mockAudit, times(3)).log(eq("resguardo"), any(), any(), eq("cancelar"), argThat(d -> d.contains("RSG-2026-000400")));
        verify(mockAudit).log(eq("resguardo"), eq("r-nuevo"), any(), eq("crear"), argThat(d -> d.contains("4 bienes")));
    }

    /** Only the same person in the same área, in force, and Patrimonio. */
    @Test
    void juntar_soloDeLaMismaPersonaYArea() throws Exception {
        Resguardo a = deAna("r-a", "RSG-1", "Contraloría Municipal", 1, null, "p1");
        Resguardo otraArea = deAna("r-b", "RSG-2", "Parque Municipal", 2, null, "p2");
        Resguardo otro = deAna("r-c", "RSG-3", "Contraloría Municipal", 2, null, "p3");
        otro.setResguardanteNombre("MARIO PEREZ SOTO");
        Resguardo cancelado = deAna("r-d", "RSG-4", "Contraloría Municipal", 2, null, "p4");
        cancelado.setEstado(Resguardo.ESTADO_CANCELADO);
        assertThrows(IllegalArgumentException.class, () -> service.juntar(List.of(a, otraArea)));
        assertThrows(IllegalArgumentException.class, () -> service.juntar(List.of(a, otro)));
        assertThrows(IllegalArgumentException.class, () -> service.juntar(List.of(a, cancelado)));
        assertThrows(IllegalArgumentException.class, () -> service.juntar(List.of(a)));
        entrarComo(com.sibim.model.enums.Rol.DIRECCION);
        assertThrows(SecurityException.class, () -> service.juntar(List.of(a, deAna("r-e", "RSG-5", "Contraloría Municipal", 2, null, "p5"))));
        verify(mockRepo, never()).juntar(any(), any(), any());
    }

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

    private static Resguardo resguardo(String numero, String nombre, String area, String estado) {
        Resguardo r = new Resguardo();
        r.setId("id-" + numero); r.setNumero(numero); r.setResguardanteNombre(nombre);
        r.setResguardanteArea(area); r.setEstado(estado);
        r.setItems(List.of(item()));
        return r;
    }

    private static com.sibim.model.Producto bien(String id, String codigo, String resguardante, String area) {
        com.sibim.model.Producto p = new com.sibim.model.Producto();
        p.setId(id); p.setCodigo(codigo); p.setNombre("Bien " + codigo);
        p.setResguardante(resguardante); p.setArea(area); p.setStockActual(1);
        return p;
    }

    /** Reportes › Resguardos por resguardante: the name is typed by hand on each
     *  resguardo, so case, accents and double spaces don't make two people; and the
     *  inventory brings the people that never had a resguardo registered (90 of 316
     *  in the backup of 28-oct-2026). */
    @Test
    void porResguardante_juntaLosResguardosDeLaMismaPersona_yLosBienesSinResguardo() {
        Resguardo vigente = resguardo("RSG-3", "Juan Pérez", "Tesorería Municipal", Resguardo.ESTADO_ACTIVO);
        com.sibim.model.Producto baja = bien("p-09", "SGM/09", "Luis Soto", "Obras Públicas");
        baja.setFechaBaja(java.time.LocalDate.of(2026, 10, 1));
        List<ResguardoService.Resguardante> personas = ResguardoService.porResguardante(List.of(
                vigente,
                resguardo("RSG-2", "Ángela Ruiz", "Contraloría", Resguardo.ESTADO_ACTIVO),
                resguardo("RSG-1", "JUAN  PEREZ ", "Obras Públicas", Resguardo.ESTADO_CANCELADO),
                resguardo("RSG-0", "  ", null, Resguardo.ESTADO_ACTIVO)),
            List.of(bien("p-01", "TES/01", "Juan Pérez", "Tesorería Municipal"),   // in RSG-3 (item p-01)
                    bien("p-10", "TES/10", "juan perez", "Tesorería Municipal"),
                    bien("p-02", "TES/2", "JUAN PÉREZ", "Tesorería Municipal"),
                    bien("p-05", "OP/05", "Beatriz Ortiz", "Obras Públicas"),
                    baja,
                    bien("p-06", "OP/06", null, "Obras Públicas")));
        assertEquals(List.of("Ángela Ruiz", "Beatriz Ortiz", "Juan Pérez"),
            personas.stream().map(ResguardoService.Resguardante::nombre).toList(),
            "Á va con la A; el resguardo más nuevo da el nombre; una baja no trae a nadie");
        ResguardoService.Resguardante juan = personas.get(2);
        assertEquals(List.of("RSG-3", "RSG-1"), juan.resguardos().stream().map(Resguardo::getNumero).toList());
        assertEquals(1, juan.activos());
        assertEquals(List.of("TES/2", "TES/10"), juan.sinResguardo().stream().map(com.sibim.model.Producto::getCodigo).toList(),
            "el que ya está en su resguardo vigente no se repite; orden por número");
        assertEquals("Juan Pérez · 2 áreas", juan.texto());
        assertEquals("Ángela Ruiz · Contraloría", personas.get(0).texto());
        ResguardoService.Resguardante beatriz = personas.get(1);
        assertTrue(beatriz.resguardos().isEmpty());
        assertEquals("Beatriz Ortiz · Obras Públicas", beatriz.texto());
    }

    @Test
    void lo_elegido_deUnaPersona_saleEnUnSoloPdf() throws Exception {
        when(mockCfg.get(any(), any())).thenReturn("");
        Resguardo r11 = resguardo("RSG-2026-000011", "Ana García", "Contraloría", Resguardo.ESTADO_ACTIVO);
        Resguardo r12 = resguardo("RSG-2026-000012", "Ana García", "Contraloría", Resguardo.ESTADO_ACTIVO);
        ResguardoService.Resguardante ana = new ResguardoService.Resguardante("Ana García", List.of(r12, r11),
            List.of(bien("p-20", "CON/20", "Ana García", "Contraloría")));
        java.io.File pdf;
        com.sibim.db.DatabaseConfig.setDemoMode(true);   // the header logo reads configuracion
        try {
            pdf = service.exportarDePersona(ana, List.of(r11, r12), true);
            assertThrows(IllegalArgumentException.class, () -> service.exportarDePersona(ana, List.of(), false));
        } finally { com.sibim.db.DatabaseConfig.setDemoMode(false); }
        try (var d = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(pdf))) {
            assertEquals(3, d.getNumberOfPages());
            assertTrue(com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(d.getPage(1)).contains("RSG-2026-000011"));
            assertTrue(com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(d.getPage(2)).contains("RSG-2026-000012"));
            String sueltos = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(d.getPage(3));
            assertTrue(sueltos.contains("CON/20") && sueltos.contains("RSP-"), sueltos);
        }
        // Each read is a query over the internet: one person can have 33 resguardos.
        org.mockito.Mockito.verify(mockCfg, org.mockito.Mockito.times(1)).get(org.mockito.ArgumentMatchers.eq("nombre_ayuntamiento"), any());
    }

    /** The vehicle sheet says who holds the vehicle today and has no Estado: a cancelled
     *  resguardo printed on it looks in force, under the name of whoever has it now. */
    @Test
    void unResguardoCancelado_deVehiculos_saleEnLaHojaGeneral_queDiceCancelado() throws Exception {
        when(mockCfg.get(any(), any())).thenReturn("");
        java.io.File cancelado, vigente;
        com.sibim.db.DatabaseConfig.setDemoMode(true);
        try {
            com.sibim.model.Producto vehiculo = new ProductoService().getAll().stream()
                .filter(ReporteParqueVehicularService::esVehiculo).findFirst().orElseThrow();
            ResguardoItem renglon = item();
            renglon.setProductoId(vehiculo.getId());
            Resguardo r = resguardo("RSG-2026-000020", "Ana García", "Contraloría", Resguardo.ESTADO_CANCELADO);
            r.setItems(List.of(renglon));
            cancelado = service.exportarPdf(r);
            r.setEstado(Resguardo.ESTADO_ACTIVO);
            vigente = service.exportarPdf(r);
        } finally { com.sibim.db.DatabaseConfig.setDemoMode(false); }
        assertTrue(textoDe(cancelado).contains(Resguardo.ESTADO_CANCELADO), "dice que está cancelado");
        assertTrue(textoDe(cancelado).contains("BIENES ENTREGADOS EN RESGUARDO"), "es la hoja general");
        assertFalse(textoDe(vigente).contains("BIENES ENTREGADOS EN RESGUARDO"), "el vigente sí va en la hoja vehicular");
    }

    private static String textoDe(java.io.File pdf) throws Exception {
        try (var d = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(pdf))) {
            return com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(d.getPage(1));
        }
    }
}
