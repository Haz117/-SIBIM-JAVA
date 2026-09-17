package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Preventive-maintenance alerts per producto — a supplementary feature, same
 *  demo/offline no-op pattern as {@link PriceHistoryRepository}, not part of
 *  the offline sync scope. */
public class ProductoMantenimientoRepository {

    private static final Logger log = LoggerFactory.getLogger(ProductoMantenimientoRepository.class);

    public record Alerta(String id, String productoId, String descripcion, LocalDate fecha, boolean completada) {}

    public record AlertaGlobal(String productoId, String descripcion, LocalDate fecha) {}

    public List<Alerta> findByProducto(String productoId) {
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return List.of();
        String sql = """
            SELECT id, producto_id, descripcion, fecha, completada
            FROM producto_mantenimiento
            WHERE producto_id = ?
            ORDER BY fecha
            """;
        List<Alerta> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.warn("No se pudieron cargar alertas de mantenimiento para producto {}: {}", productoId, e.getMessage());
        }
        return list;
    }

    public void agregar(String productoId, String descripcion, LocalDate fecha) {
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return;
        String sql = """
            INSERT INTO producto_mantenimiento (id, producto_id, descripcion, fecha, completada)
            VALUES (?, ?, ?, ?, FALSE)
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, productoId);
            ps.setString(3, descripcion);
            ps.setDate(4, Date.valueOf(fecha));
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("No se pudo guardar alerta de mantenimiento para producto {}: {}", productoId, e.getMessage());
        }
    }

    public void marcarCompletada(String id) {
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE producto_mantenimiento SET completada = TRUE WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("No se pudo marcar como completada la alerta de mantenimiento {}: {}", id, e.getMessage());
        }
    }

    public void eliminar(String id) {
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM producto_mantenimiento WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("No se pudo eliminar la alerta de mantenimiento {}: {}", id, e.getMessage());
        }
    }

    /** Alertas pendientes (no completadas) con fecha dentro de los próximos {@code days} días,
     *  en todos los productos — filtrado en SQL en vez de traer toda la tabla a Java. */
    public List<AlertaGlobal> findProximas(int days) {
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return List.of();
        String sql = """
            SELECT producto_id, descripcion, fecha
            FROM producto_mantenimiento
            WHERE NOT completada AND fecha <= ?
            ORDER BY fecha
            """;
        List<AlertaGlobal> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(LocalDate.now().plusDays(days)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AlertaGlobal(
                        rs.getString("producto_id"), rs.getString("descripcion"),
                        rs.getDate("fecha").toLocalDate()));
                }
            }
        } catch (Exception e) {
            log.warn("No se pudieron cargar las alertas de mantenimiento próximas: {}", e.getMessage());
        }
        return list;
    }

    private Alerta mapRow(ResultSet rs) throws SQLException {
        return new Alerta(
            rs.getString("id"), rs.getString("producto_id"), rs.getString("descripcion"),
            rs.getDate("fecha").toLocalDate(), rs.getBoolean("completada"));
    }
}
