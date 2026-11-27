package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.model.enums.Rol;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductoServiceTest {

    @Mock ProductoRepository mockProductoRepo;
    @Mock AuditLogRepository mockAuditRepo;

    private ProductoService service;
    private Producto productoValido;
    private Usuario admin;
    private Usuario director;

    @BeforeEach
    void setUp() throws Exception {
        admin = new Usuario();
        admin.setId("u-admin"); admin.setNombre("Admin"); admin.setRol(Rol.ADMIN);

        director = new Usuario();
        director.setId("u-dir"); director.setNombre("Director");
        director.setRol(Rol.DIRECCION); director.setArea("Area Sin Acceso");

        SessionManager.setCurrentUser(admin);

        productoValido = new Producto();
        productoValido.setNombre("Silla ejecutiva");
        productoValido.setCodigo("SL-001");
        productoValido.setCategoriaId("cat-mob");
        productoValido.setArea("Secretaria General Municipal");
        productoValido.setPrecioCompra(BigDecimal.valueOf(3000));
        productoValido.setPrecioVenta(BigDecimal.valueOf(3500));
        productoValido.setStockActual(10);
        productoValido.setStockMinimo(2);
        productoValido.setStockMaximo(20);

        // Default happy-path stubs (lenient — not all tests will trigger every call)
        when(mockProductoRepo.findById(any())).thenReturn(Optional.empty());
        when(mockProductoRepo.existsByCodigo(any(), any())).thenReturn(false);
        when(mockProductoRepo.save(any())).thenReturn(productoValido);

        service = new ProductoService(mockProductoRepo, mockAuditRepo);
    }

    @AfterEach
    void tearDown() { SessionManager.logout(); }

    // ── Validaciones de save() ────────────────────────────────────────────────

    @Test void save_nombreBlanco_lanzaValidation() {
        productoValido.setNombre("   ");
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_nombreNull_lanzaValidation() {
        productoValido.setNombre(null);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_codigoBlanco_lanzaValidation() {
        // El código solo se valida a mano al editar — al dar de alta un bien
        // nuevo se asigna automáticamente por área (ver ProductoService#asignarCodigo).
        productoValido.setId("p-01");
        productoValido.setCodigo("");
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_categoriaNull_lanzaValidation() {
        productoValido.setCategoriaId(null);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_areaBlanca_lanzaValidation() {
        productoValido.setArea("   ");
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_precioCompraNegativo_lanzaValidation() {
        productoValido.setPrecioCompra(BigDecimal.valueOf(-1));
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    /** A bien has no sale price: save() overwrites whatever came in with the
     *  purchase price, so a stray negative value can no longer reach the DB. */
    @Test void save_precioVenta_siempreCopiaElDeCompra() throws Exception {
        productoValido.setPrecioVenta(BigDecimal.valueOf(-0.01));
        service.save(productoValido);
        verify(mockProductoRepo).save(argThat(p -> p.getPrecioVenta().compareTo(BigDecimal.valueOf(3000)) == 0));
    }

    @Test void save_stockActualNegativo_lanzaValidation() {
        productoValido.setStockActual(-1);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_stockMinimoMayorQueMaximo_lanzaValidation() {
        productoValido.setStockMinimo(15);
        productoValido.setStockMaximo(10);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_codigoDuplicado_lanzaValidation() throws Exception {
        // El código solo se valida a mano al editar (en alta se asigna automático).
        productoValido.setId("p-01");
        when(mockProductoRepo.existsByCodigo("SL-001", "p-01")).thenReturn(true);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_noAdmin_areaInaccesible_lanzaValidation() {
        SessionManager.setCurrentUser(director);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_valido_devuelveProductoGuardado() throws Exception {
        Producto guardado = new Producto(); guardado.setId("p-nuevo"); guardado.setNombre("Silla ejecutiva");
        when(mockProductoRepo.save(any())).thenReturn(guardado);
        assertEquals("p-nuevo", service.save(productoValido).getId());
    }

    /** The insert of a new bien fails (connection lost). The form reopens with
     *  the same object, which now carries the id the repository gave it: the
     *  next "Guardar" is that alta again, not the edit of a bien that was never
     *  stored ("no existe o no tienes acceso a él" on every retry). */
    @Test void save_reintentoDeUnAltaQueFallo_noSeTomaPorEdicion() throws Exception {
        when(mockProductoRepo.save(any())).thenAnswer(inv -> {
            inv.<Producto>getArgument(0).setId("p-alta-fallida");
            throw new SQLException("conexión perdida", "08006");
        });
        assertThrows(SQLException.class, () -> service.save(productoValido));
        assertEquals("p-alta-fallida", productoValido.getId());

        Producto guardado = new Producto(); guardado.setId("p-alta-fallida"); guardado.setNombre("Silla ejecutiva");
        doReturn(guardado).when(mockProductoRepo).save(any());
        assertEquals("p-alta-fallida", service.save(productoValido).getId());
        verify(mockAuditRepo).log(eq("producto"), eq("p-alta-fallida"), any(), eq("crear"), any());
    }

    // ── Código corregido a mano: sigue la nomenclatura PREFIJO/NN ────────────

    /** The bien being edited as the database has it: Secretaría General
     *  Municipal, prefix SGM. Same cost as the form, so no price change. */
    private void enLaBase(String codigo) throws Exception {
        Producto actual = productoExistente();
        actual.setCodigo(codigo);
        actual.setPrecioCompra(BigDecimal.valueOf(3000));
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(actual));
    }

    private Producto edicionCon(String codigo) {
        productoValido.setId("p-01");
        productoValido.setCodigo(codigo);
        return productoValido;
    }

    @Test void editar_codigoFueraDeLaNomenclatura_lanzaValidation() throws Exception {
        enLaBase("SGM/03");
        var e = assertThrows(ProductoService.ValidationException.class, () -> service.save(edicionCon("ABC-123")));
        assertTrue(e.getMessage().contains("SGM/07"), e.getMessage());
    }

    @Test void editar_codigoDeOtraArea_lanzaValidation() throws Exception {
        enLaBase("SGM/03");
        assertThrows(ProductoService.ValidationException.class, () -> service.save(edicionCon("TICS/03")));
    }

    /** "sgm/7" and "SGM/07" are the same number: it is stored the way the app
     *  writes it, and the duplicate check looks for that one. */
    @Test void editar_codigoEscritoAMano_seGuardaComoLoEscribeLaApp() throws Exception {
        enLaBase("SGM/03");
        service.save(edicionCon(" sgm/7 "));
        verify(mockProductoRepo).existsByCodigo("SGM/07", "p-01");
        verify(mockProductoRepo).save(argThat(p -> "SGM/07".equals(p.getCodigo())));
    }

    @Test void editar_sinTocarElCodigo_noLoRevisa() throws Exception {
        enLaBase("INV-2019-44");
        service.save(edicionCon("INV-2019-44"));
        verify(mockProductoRepo).save(argThat(p -> "INV-2019-44".equals(p.getCodigo())));
    }

    // ── delete() ─────────────────────────────────────────────────────────────

    @Test void delete_productoConMovimientos_muestraMensajeAmigable() throws Exception {
        Producto p = productoExistente();
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(p));
        doThrow(new SQLException("fk", "23503")).when(mockProductoRepo).delete("p-01");
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.delete("p-01"));
        assertTrue(ex.getMessage().contains("movimientos"));
    }

    @Test void delete_productoNoExiste_noLanzaExcepcion() {
        assertDoesNotThrow(() -> service.delete("no-existe"));
    }

    @Test void delete_noAdmin_areaInaccesible_lanzaValidation() throws Exception {
        SessionManager.setCurrentUser(director);
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        assertThrows(ProductoService.ValidationException.class, () -> service.delete("p-01"));
    }

    // ── darDeBaja() ───────────────────────────────────────────────────────────

    @Test void darDeBaja_motivoBlanco_lanzaValidation() throws Exception {
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        assertThrows(ProductoService.ValidationException.class, () -> service.darDeBaja("p-01", "   "));
    }

    @Test void darDeBaja_motivoNull_lanzaValidation() throws Exception {
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        assertThrows(ProductoService.ValidationException.class, () -> service.darDeBaja("p-01", null));
    }

    @Test void darDeBaja_productoNoExiste_lanzaValidation() {
        assertThrows(ProductoService.ValidationException.class, () -> service.darDeBaja("no-existe", "Perdido"));
    }

    @Test void darDeBaja_exitosa_llama_darDeBajaEnRepo() throws Exception {
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        service.darDeBaja("p-01", "Robo");
        verify(mockProductoRepo).darDeBaja(eq("p-01"), eq("Robo"), isNull(), isNull(), isNull(), isNull());
    }

    // ── reactivar() ──────────────────────────────────────────────────────────

    @Test void reactivar_exitosa_llamaReactivarConCodigoEnRepo() throws Exception {
        // productoExistente() vive en "Secretaria General Municipal", un área
        // con prefijo de nomenclatura — reactivar() debe reasignar código
        // (ver AreaCodigos) en vez de solo limpiar la fecha de baja.
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        service.reactivar("p-01");
        verify(mockProductoRepo).reactivarConCodigo(eq("p-01"), any());
        verify(mockProductoRepo, never()).reactivar("p-01");
    }

    @Test void reactivar_productoNoExiste_lanzaValidation() {
        assertThrows(ProductoService.ValidationException.class, () -> service.reactivar("no-existe"));
    }

    @Test void reactivar_noAdmin_areaInaccesible_lanzaValidation() throws Exception {
        SessionManager.setCurrentUser(director);
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        assertThrows(ProductoService.ValidationException.class, () -> service.reactivar("p-01"));
    }

    // ── getVencidosProximos() ────────────────────────────────────────────────

    @Test void getVencidosProximos_incluyeVencidoYProximoAVencer() throws Exception {
        Producto v = productoConVencimiento(LocalDate.now().minusDays(1));
        Producto p = productoConVencimiento(LocalDate.now().plusDays(15));
        when(mockProductoRepo.findVencidosProximos(30)).thenReturn(List.of(v, p));
        assertEquals(2, service.getVencidosProximos(30).size());
    }

    @Test void getVencidosProximos_sinFechaVencimiento_noIncluido() throws Exception {
        when(mockProductoRepo.findVencidosProximos(30)).thenReturn(List.of());
        assertTrue(service.getVencidosProximos(30).isEmpty());
    }

    @Test void getVencidosProximos_ordenadosMasProximosPrimero() throws Exception {
        Producto p1 = productoConVencimiento(LocalDate.now().plusDays(25));
        Producto p2 = productoConVencimiento(LocalDate.now().plusDays(5));
        when(mockProductoRepo.findVencidosProximos(30)).thenReturn(List.of(p2, p1));
        List<Producto> r = service.getVencidosProximos(30);
        assertEquals(p2.getFechaVencimiento(), r.get(0).getFechaVencimiento());
    }

    // ── getAllIncludingBaja() / countAll() ────────────────────────────────────

    @Test void getAllIncludingBaja_llamaFindAllConTrue() throws Exception {
        Producto baja = new Producto(); baja.setFechaBaja(LocalDate.now().minusDays(1));
        when(mockProductoRepo.findAll(true)).thenReturn(List.of(baja));
        List<Producto> r = service.getAllIncludingBaja();
        assertEquals(1, r.size());
        verify(mockProductoRepo).findAll(true);
    }

    @Test void countAll_retornaTotalDeProductosActivos() throws Exception {
        when(mockProductoRepo.countAll()).thenReturn(2L);
        assertEquals(2L, service.countAll());
    }

    // ── Edge cases de validación ──────────────────────────────────────────────

    @Test void save_stockMinimoIgualAMaximo_esValido() {
        productoValido.setStockMinimo(10); productoValido.setStockMaximo(10);
        assertDoesNotThrow(() -> service.save(productoValido));
    }

    @Test void save_precioCompraCero_esValido() {
        productoValido.setPrecioCompra(BigDecimal.ZERO);
        assertDoesNotThrow(() -> service.save(productoValido));
    }

    @Test void save_stockMaximoCero_stockMinimoCero_esValido() {
        productoValido.setStockMinimo(0); productoValido.setStockMaximo(0); productoValido.setStockActual(0);
        assertDoesNotThrow(() -> service.save(productoValido));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Producto productoConStock(int actual, int min, int max) {
        Producto p = new Producto(); p.setStockActual(actual); p.setStockMinimo(min); p.setStockMaximo(max);
        return p;
    }

    private Producto productoConVencimiento(LocalDate fecha) {
        Producto p = new Producto(); p.setFechaVencimiento(fecha); p.setStockActual(5); p.setStockMinimo(1);
        return p;
    }

    private Producto productoExistente() {
        Producto p = new Producto(); p.setId("p-01"); p.setNombre("Escritorio ejecutivo");
        p.setArea("Secretaría General Municipal");
        return p;
    }

    /** An edit used to be audited as "Datos del bien actualizados" alone: the
     *  vehicles edited on 28-oct-2026 got a photo, a vida útil and an estado
     *  físico, and the Auditoría could not say so. */
    @Test void cambios_diceQueCambioYDeQueAQue() {
        Producto antes = new Producto(); antes.setNombre("Camioneta NP300"); antes.setCodigo("SOP/236");
        antes.setProveedor(null); antes.setDescripcion("Doble cabina");
        Producto despues = antes.copia();
        despues.setProveedor("");                          // empty = no data, as before
        despues.setVidaUtilAnios(5);
        despues.setEstadoFisico("DEFICIENTE");
        despues.setFotoUrl("https://x/foto.jpg");
        despues.setDescripcion("Doble cabina, 4x4");
        assertEquals("descripción, estado físico (sin dato → DEFICIENTE), vida útil (sin dato → 5), foto agregada",
            ProductoService.cambios(antes, despues));
        assertEquals("", ProductoService.cambios(antes, antes.copia()), "sin cambios no dice nada");
        despues = antes.copia();
        despues.setNumeroFactura("A1B2C3D4-0000-4000-8000-000000000001-0001-EXTRA");
        assertTrue(ProductoService.cambios(antes, despues).equals("factura (sin dato → A1B2C3D4-0000-4000-8000-000000000001-00…)"),
            ProductoService.cambios(antes, despues));
    }

    /** 26-nov-2026: a photo added beside the principal one read «Se guardó sin cambios». */
    @Test void cambioDeFotos_diceCuantasEranYCuantasSon() {
        assertEquals("fotos (1 → 2)", ProductoService.cambioDeFotos(List.of("a"), List.of("a", "b"), ""));
        assertEquals("fotos (2 → 1)", ProductoService.cambioDeFotos(List.of("a", "b"), List.of("b"), "foto"));
        assertEquals("una foto por otra", ProductoService.cambioDeFotos(List.of("a"), List.of("b"), "foto"));
        assertEquals("", ProductoService.cambioDeFotos(List.of("a", "b"), List.of("b", "a"), "foto"), "solo cambió la principal");
        assertEquals("", ProductoService.cambioDeFotos(List.of(), List.of("a"), "foto agregada"), "ya lo dice");
        assertEquals("foto agregada", ProductoService.cambioDeFotos(List.of(), List.of("a"), ""));
    }

    // ── Patrimonio regresa un aviso «No es del área» (9-nov-2026) ─────────────

    private Producto avisado(String id, String nota) {
        Producto p = productoExistente();
        p.setId(id);
        p.setCodigo("SGM/" + id);
        p.setRevisadoAreaEn(java.time.LocalDateTime.of(2026, 11, 9, 10, 30));
        p.setRevisadoAreaPor("Secretaría General");
        p.setRevisionAreaNota(nota);
        return p;
    }

    @Test void regresarAlArea_conMensaje_loGuardaParaQueElAreaLoLea() throws Exception {
        Producto p = avisado("p-01", "No está en la oficina");
        service.quitarRevision(List.of(p), "  Sí es suyo, está en la bodega  ");
        verify(mockProductoRepo).marcarRevision(List.of("p-01"), null, null, "Sí es suyo, está en la bodega");
        verify(mockAuditRepo).log(eq("producto"), isNull(), eq(p.getArea()), eq("revision_area"),
            argThat((String d) -> d.contains("mensaje al área: «Sí es suyo, está en la bodega»")));
    }

    @Test void regresarAlArea_sinMensaje_quedaSinNada() throws Exception {
        service.quitarRevision(List.of(avisado("p-01", "No es nuestro")), "   ");
        verify(mockProductoRepo).marcarRevision(List.of("p-01"), null, null, null);
    }

    @Test void regresarAlArea_mensajeLargo_seRechaza() throws Exception {
        assertThrows(ProductoService.ValidationException.class,
            () -> service.quitarRevision(List.of(avisado("p-01", "x")), "a".repeat(301)));
        verify(mockProductoRepo, never()).marcarRevision(any(), any(), any(), any());
    }

    @Test void deshacer_dejaCadaBienComoEstaba() throws Exception {
        Producto a = avisado("p-01", "Lo tiene Obras Públicas");
        Producto b = avisado("p-02", null);
        Producto c = productoExistente(); c.setId("p-03"); c.setCodigo("SGM/p-03"); c.setRevisionAreaNota("Búscalo en la bodega");
        service.restaurarRevision(List.of(a, b, c));
        verify(mockProductoRepo).marcarRevision(List.of("p-01"), a.getRevisadoAreaEn(), "Secretaría General", "Lo tiene Obras Públicas");
        verify(mockProductoRepo).marcarRevision(List.of("p-02"), b.getRevisadoAreaEn(), "Secretaría General", null);
        verify(mockProductoRepo).marcarRevision(List.of("p-03"), null, null, "Búscalo en la bodega");
    }

    @Test void deshacer_losQueEstabanIgual_vanEnUnaSolaVuelta() throws Exception {
        List<Producto> sinRevisar = new java.util.ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            Producto p = productoExistente(); p.setId("p-" + i); p.setCodigo("SGM/" + i);
            sinRevisar.add(p);
        }
        service.restaurarRevision(sinRevisar);
        verify(mockProductoRepo, times(1)).marcarRevision(any(), isNull(), isNull(), isNull());
    }

    @Test void unArea_noRegresaBienesDeOtra() throws Exception {
        SessionManager.setCurrentUser(director);
        assertThrows(ProductoService.ValidationException.class,
            () -> service.quitarRevision(List.of(avisado("p-01", "x")), "hola"));
        assertThrows(ProductoService.ValidationException.class,
            () -> service.restaurarRevision(List.of(avisado("p-01", "x"))));
        verify(mockProductoRepo, never()).marcarRevision(any(), any(), any(), any());
    }

    @Test void mensajeDePatrimonio_soloMientrasElAreaNoContesta() {
        Producto p = new Producto();
        assertNull(p.getMensajePatrimonio());
        p.setRevisionAreaNota("Está en la bodega");
        assertEquals("Está en la bodega", p.getMensajePatrimonio());
        assertFalse(p.isObservadoPorArea(), "sin respuesta del área no es un aviso");
        p.setRevisadoAreaEn(java.time.LocalDateTime.now());
        assertNull(p.getMensajePatrimonio(), "ya contestó: la nota es suya");
        assertTrue(p.isObservadoPorArea());
    }

    /** 19-nov-2026: a valor residual that was the 15 % of the costo follows a new costo («Sumarle
     *  el IVA» left 3 at 12.9 %); one typed otherwise, or set by this same change, doesn't. */
    @Test void residualQueSigue_soloElQueEraEl15PorCientoYNoCambioAhora() {
        Producto antes = new Producto();
        antes.setPrecioCompra(new BigDecimal("1000.00")); antes.setValorResidual(new BigDecimal("150.00"));
        Producto despues = antes.copia();
        despues.setPrecioCompra(new BigDecimal("1160.00"));
        assertEquals(0, new BigDecimal("174.00").compareTo(ProductoService.residualQueSigue(antes, despues)));

        despues.setValorResidual(new BigDecimal("100.00"));
        assertNull(ProductoService.residualQueSigue(antes, despues), "este mismo cambio lo pone");
        Producto otro = antes.copia();
        otro.setValorResidual(new BigDecimal("80.00"));
        Producto otroDespues = otro.copia();
        otroDespues.setPrecioCompra(new BigDecimal("1160.00"));
        assertNull(ProductoService.residualQueSigue(otro, otroDespues), "uno distinto del 15 % no se toca");
        Producto igual = antes.copia();
        assertNull(ProductoService.residualQueSigue(antes, igual), "sin cambio de costo, nada");
        Producto sinCosto = antes.copia();
        sinCosto.setPrecioCompra(BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(ProductoService.residualQueSigue(antes, sinCosto)));
        // The 15 % worked out from the factura's unit price, some centavos off, also follows.
        Producto centavos = new Producto();
        centavos.setPrecioCompra(new BigDecimal("26678.00")); centavos.setValorResidual(new BigDecimal("4001.73"));
        Producto conIva = centavos.copia();
        conIva.setPrecioCompra(new BigDecimal("30946.48"));
        assertEquals(0, new BigDecimal("4641.97").compareTo(ProductoService.residualQueSigue(centavos, conIva)));
    }
}
