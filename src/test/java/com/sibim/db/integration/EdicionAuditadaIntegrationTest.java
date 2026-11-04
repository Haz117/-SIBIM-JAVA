package com.sibim.db.integration;

import com.sibim.model.Producto;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.ProductoService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

/** An edit saved the whole way, against PostgreSQL, says in the Auditoría what changed.
 *  Only {@code ProductoService.cambios} was tested; in production no edit since
 *  30-oct-2026 said it (nine photos added on 3-nov showed as "Datos del bien actualizados"). */
class EdicionAuditadaIntegrationTest extends IntegrationTestBase {

    private final ProductoService service = new ProductoService();
    private final ProductoRepository repo = new ProductoRepository();

    @Test
    void editarUnBien_quedaEnAuditoriaQueCambio() throws Exception {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO categories (id, nombre, color) VALUES ('cat-aud', 'Muebles', '#3B82F6')")) {
            ps.executeUpdate();
        }
        Producto p = new Producto();
        p.setNombre("Anaquel de prueba");
        p.setCategoriaId("cat-aud");
        p.setArea("Secretaría General Municipal");
        p.setUnidad(UnidadMedida.fromCodigo("pieza"));
        p.setPrecioCompra(BigDecimal.ZERO);
        p.setStockActual(1);
        Producto alta = service.save(p);

        Producto editado = repo.findById(alta.getId()).orElseThrow();
        editado.setFotoUrl("https://ejemplo/foto-de-prueba.jpg");
        editado.setEstadoFisico("REGULAR");
        service.save(editado);

        String detalle;
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT detalle FROM audit_log WHERE entidad_id = ? AND accion = 'actualizar' ORDER BY created_at DESC LIMIT 1")) {
            ps.setString(1, alta.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "la edición no quedó en la auditoría");
                detalle = rs.getString(1);
            }
        }
        assertTrue(detalle.contains("Cambió:") && detalle.contains("foto agregada") && detalle.contains("estado físico"), detalle);
    }
}
