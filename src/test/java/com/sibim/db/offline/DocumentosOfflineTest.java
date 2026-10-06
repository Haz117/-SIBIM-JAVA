package com.sibim.db.offline;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.integration.IntegrationTestBase;
import com.sibim.model.*;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.*;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Resguardos, préstamos, comodatos, actas and área PDFs made offline are kept
 *  on the PC and reach the server (with a real folio) when SyncService runs. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentosOfflineTest extends IntegrationTestBase {

    private static final String AREA = "Dirección de Catastro";
    private String productoId;

    @BeforeEach
    void preparar() throws Exception {
        try (Statement st = OfflineStore.sharedConnection().createStatement()) {
            st.executeUpdate("DELETE FROM doc_outbox");
            st.executeUpdate("DELETE FROM doc_cache");
        }
        Usuario admin = new Usuario();
        admin.setId("test-admin"); admin.setUsername("admin"); admin.setNombre("Admin Test"); admin.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(admin);
        productoId = UUID.randomUUID().toString();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO categories (id, nombre, color) VALUES (?, ?, '#3B82F6') ON CONFLICT DO NOTHING")) {
            ps.setString(1, "cat-docs"); ps.setString(2, "Cat docs"); ps.executeUpdate();
        }
        Producto p = new Producto();
        p.setId(productoId); p.setNombre("Escritorio"); p.setCodigo("DOC-" + productoId.substring(0, 6));
        p.setCategoriaId("cat-docs"); p.setPrecioCompra(BigDecimal.TEN); p.setPrecioVenta(BigDecimal.TEN);
        p.setStockActual(1); p.setStockMaximo(10); p.setUnidad(UnidadMedida.fromCodigo("pieza")); p.setArea(AREA);
        new ProductoRepository().saveOnline(p);
    }

    @AfterEach
    void online() throws Exception {
        DatabaseConfig.setOfflineMode(false);
        SessionManager.logout();
        try (Statement st = OfflineStore.sharedConnection().createStatement()) {   // other tests count the queue
            st.executeUpdate("DELETE FROM doc_outbox");
            st.executeUpdate("DELETE FROM doc_cache");
        }
    }

    @Test
    void sinConexion_seCreanYSeVen_yAlReconectarLleganConFolioReal() throws Exception {
        DatabaseConfig.setOfflineMode(true);

        Resguardo r = new Resguardo();
        r.setResguardanteNombre("Ana Pérez"); r.setResguardanteArea(AREA);
        ResguardoItem it = new ResguardoItem();
        it.setProductoId(productoId); it.setProductoNombre("Escritorio"); it.setCantidad(1);
        r.getItems().add(it);
        var resguardos = new ResguardoRepository();
        resguardos.save(r);
        assertTrue(r.getNumero().contains("-PROV-"), "folio provisional mientras no hay conexión");
        assertEquals(1, resguardos.findAll().size(), "se ve sin conexión");
        assertEquals(1, resguardos.findAll().get(0).getItems().size(), "con sus bienes, para imprimirlo");
        assertEquals(1, resguardos.countActivos());

        Prestamo pr = new Prestamo();
        pr.setProductoId(productoId); pr.setProductoNombre("Escritorio"); pr.setAreaOrigen(AREA);
        pr.setAreaDestino("Archivo Municipal"); pr.setResponsableNombre("Luis");
        pr.setFechaDevolucionPrevista(LocalDate.now().plusDays(5));
        var prestamos = new PrestamoRepository();
        prestamos.save(pr);
        assertTrue(prestamos.existeActivoPorProducto(productoId));
        prestamos.devolver(pr.getId(), LocalDate.now());
        assertFalse(prestamos.existeActivoPorProducto(productoId), "devuelto sin conexión");

        Comodato co = new Comodato();
        co.setProductoId(productoId); co.setProductoNombre("Escritorio"); co.setEntidadReceptora("DIF");
        co.setContactoNombre("Marta"); co.setFechaInicio(LocalDate.now());
        var comodatos = new ComodatoRepository();
        comodatos.save(co);

        ActaEntregaRecepcion acta = new ActaEntregaRecepcion();
        acta.setAdminSaliente("A"); acta.setAdminEntrante("B"); acta.setFechaEntrega(LocalDate.now());
        new ActaRepository().save(acta);

        var areaRes = new AreaResguardoRepository();
        String pdfId = areaRes.save(AREA, new byte[]{1, 2, 3}, "firmado.pdf", "Firmado", LocalDate.now());
        assertArrayEquals(new byte[]{1, 2, 3}, areaRes.leerPdf(pdfId).orElseThrow(), "el PDF subido se abre sin conexión");
        assertEquals(1, areaRes.findByArea(AREA).size());

        // Back online
        DatabaseConfig.setOfflineMode(false);
        AtomicInteger ok = new AtomicInteger(), mal = new AtomicInteger();
        SyncService.syncDocumentos(ok, mal);
        assertEquals(0, mal.get(), "nada falló");
        assertEquals(6, ok.get(), "resguardo, préstamo + devolución, comodato, acta, PDF");

        assertEquals("ACTIVO", valor("SELECT estado FROM resguardos WHERE id = ?", r.getId()));
        assertFalse(valor("SELECT numero FROM resguardos WHERE id = ?", r.getId()).contains("PROV"), "folio real");
        assertEquals("1", valor("SELECT COUNT(*)::text FROM resguardo_items WHERE resguardo_id = ?", r.getId()));
        assertEquals("Ana Pérez", valor("SELECT resguardante FROM products WHERE id = ?", productoId));
        assertEquals("DEVUELTO", valor("SELECT estado FROM prestamos WHERE id = ?", pr.getId()));
        assertEquals("VIGENTE", valor("SELECT estado FROM comodatos WHERE id = ?", co.getId()));
        assertFalse(valor("SELECT numero FROM actas_entrega_recepcion WHERE id = ?", acta.getId()).contains("PROV"));
        assertEquals("3", valor("SELECT length(pdf)::text FROM area_resguardos WHERE id = ?", pdfId));
        assertTrue(OfflineDocs.pendientes().isEmpty());
        assertFalse(OfflineDocs.uno(OfflineDocs.RESGUARDO, r.getId(), Resguardo.class).orElseThrow()
            .getNumero().contains("PROV"), "la copia de la PC ya muestra el folio real");
    }

    @Test
    void conConexion_loQueSeConsultaQuedaEnLaPc() throws Exception {
        Prestamo pr = new Prestamo();
        pr.setProductoId(productoId); pr.setProductoNombre("Escritorio"); pr.setAreaOrigen(AREA);
        pr.setAreaDestino("Archivo Municipal"); pr.setResponsableNombre("Luis");
        pr.setFechaDevolucionPrevista(LocalDate.now().minusDays(1));
        var prestamos = new PrestamoRepository();
        prestamos.save(pr);
        prestamos.findAll();

        DatabaseConfig.setOfflineMode(true);
        List<Prestamo> sinRed = prestamos.findAll();
        assertEquals(1, sinRed.stream().filter(x -> x.getId().equals(pr.getId())).count());
        assertEquals(1, prestamos.countVencidos(), "vencido por fecha aunque no haya corrido el UPDATE");
    }

    @Test
    void unAreaSoloVeLoSuyoSinConexion() throws Exception {
        DatabaseConfig.setOfflineMode(true);
        Resguardo r = new Resguardo();
        r.setResguardanteNombre("Ana"); r.setResguardanteArea(AREA);
        new ResguardoRepository().save(r);

        Usuario otra = new Usuario();
        otra.setId("u-otra"); otra.setRol(Rol.DIRECCION); otra.setArea("Archivo Municipal");
        SessionManager.setCurrentUser(otra);
        assertTrue(new ResguardoRepository().findAll().isEmpty());
    }

    @Test
    void unResguardoHechoSinConexion_noDuplicaElQueOtraPcHizoMientras() throws Exception {
        DatabaseConfig.setOfflineMode(true);
        Resguardo sinRed = resguardo("Ana Pérez");
        var resguardos = new ResguardoRepository();
        resguardos.save(sinRed);

        DatabaseConfig.setOfflineMode(false);            // meanwhile, another PC online
        Resguardo otraPc = resguardo("Luis Gómez");
        resguardos.save(otraPc);

        AtomicInteger ok = new AtomicInteger(), mal = new AtomicInteger();
        SyncService.syncDocumentos(ok, mal);

        assertEquals(1, mal.get());
        assertEquals("0", valor("SELECT COUNT(*)::text FROM resguardos WHERE id = ?", sinRed.getId()),
            "no quedan dos resguardos vigentes del mismo bien");
        assertEquals("Luis Gómez", valor("SELECT resguardante FROM products WHERE id = ?", productoId));
        assertDescartado(otraPc.getNumero());
        assertTrue(OfflineDocs.uno(OfflineDocs.RESGUARDO, sinRed.getId(), Resguardo.class).isEmpty(),
            "la PC deja de mostrar el resguardo que nunca llegará");
    }

    @Test
    void unPrestamoHechoSinConexion_seDescartaSiElBienQuedoEnComodato() throws Exception {
        DatabaseConfig.setOfflineMode(true);
        Prestamo pr = prestamo();
        new PrestamoRepository().save(pr);

        DatabaseConfig.setOfflineMode(false);
        Comodato co = new Comodato();
        co.setProductoId(productoId); co.setProductoNombre("Escritorio"); co.setEntidadReceptora("DIF");
        co.setContactoNombre("Marta"); co.setFechaInicio(LocalDate.now());
        new ComodatoRepository().save(co);

        AtomicInteger ok = new AtomicInteger(), mal = new AtomicInteger();
        SyncService.syncDocumentos(ok, mal);

        assertEquals(1, mal.get());
        assertEquals("0", valor("SELECT COUNT(*)::text FROM prestamos WHERE id = ?", pr.getId()));
        assertDescartado(co.getNumero());
    }

    @Test
    void unPrestamoHechoSinConexion_seDescartaSiElBienSeDioDeBaja() throws Exception {
        DatabaseConfig.setOfflineMode(true);
        Prestamo pr = prestamo();
        new PrestamoRepository().save(pr);

        DatabaseConfig.setOfflineMode(false);
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE products SET fecha_baja = CURRENT_DATE WHERE id = ?")) {
            ps.setString(1, productoId);
            ps.executeUpdate();
        }

        AtomicInteger ok = new AtomicInteger(), mal = new AtomicInteger();
        SyncService.syncDocumentos(ok, mal);

        assertEquals(1, mal.get());
        assertEquals("0", valor("SELECT COUNT(*)::text FROM prestamos WHERE id = ?", pr.getId()));
        assertDescartado("dado de baja");
    }

    private Resguardo resguardo(String resguardante) {
        Resguardo r = new Resguardo();
        r.setResguardanteNombre(resguardante); r.setResguardanteArea(AREA);
        ResguardoItem it = new ResguardoItem();
        it.setProductoId(productoId); it.setProductoNombre("Escritorio"); it.setCantidad(1);
        r.getItems().add(it);
        return r;
    }

    private Prestamo prestamo() {
        Prestamo pr = new Prestamo();
        pr.setProductoId(productoId); pr.setProductoNombre("Escritorio"); pr.setAreaOrigen(AREA);
        pr.setAreaDestino("Archivo Municipal"); pr.setResponsableNombre("Luis");
        pr.setFechaDevolucionPrevista(LocalDate.now().plusDays(5));
        return pr;
    }

    /** Discarded at once (not retried), with the reason the admin will read. */
    private static void assertDescartado(String motivoContiene) throws Exception {
        try (Statement st = OfflineStore.sharedConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT status, error FROM doc_outbox")) {
            assertTrue(rs.next());
            assertEquals("DISCARDED", rs.getString(1));
            assertTrue(rs.getString(2).contains(motivoContiene), rs.getString(2));
            assertFalse(rs.next());
        }
    }

    private String valor(String sql, String id) throws Exception {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), sql);
                return rs.getString(1);
            }
        }
    }
}
