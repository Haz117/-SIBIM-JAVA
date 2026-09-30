package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Signed resguardo PDFs per área (area_resguardos). Server-only: there is
 *  no demo or offline copy, so every call says so instead of doing nothing. */
public class AreaResguardoRepository {

    /** Largest PDF accepted — a scanned multi-page resguardo is a few MB. */
    public static final int MAX_PDF_BYTES = 20 * 1024 * 1024;

    /** @param pdfUrl  local path, only for rows created before V25 (the PDF
     *                 was kept on the uploading PC); null otherwise
     *  @param tienePdf the PDF itself is stored in the database (V25+) */
    public record AreaResguardo(String id, String area, String pdfUrl, String pdfNombre, boolean tienePdf,
                                String descripcion, LocalDate fecha, LocalDateTime creadoEn) {}

    /** Every resguardo of {@code area}, newest first — without the PDF bytes. */
    public List<AreaResguardo> findByArea(String area) throws SQLException {
        DatabaseConfig.exigirServidor("Consultar los resguardos de área");
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.contains(area)) return new ArrayList<>();
        List<AreaResguardo> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, area, pdf_url, pdf_nombre, (pdf IS NOT NULL) AS tiene_pdf, descripcion, fecha, created_at "
                 + "FROM area_resguardos WHERE area = ? ORDER BY created_at DESC")) {
            ps.setString(1, area);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }
        return result;
    }

    /** Stores the PDF itself, so every PC can open it. */
    public String save(String area, byte[] pdf, String pdfNombre, String descripcion, LocalDate fecha)
            throws SQLException {
        DatabaseConfig.exigirServidor("Guardar un resguardo de área");
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("El PDF está vacío");
        if (pdf.length > MAX_PDF_BYTES)
            throw new IllegalArgumentException("El PDF pesa más de " + (MAX_PDF_BYTES / (1024 * 1024)) + " MB");
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.contains(area))
            throw new SecurityException("No tienes acceso al área " + area);
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO area_resguardos (id, area, pdf, pdf_nombre, descripcion, fecha) VALUES (?,?,?,?,?,?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, area);
            ps.setBytes(3, pdf);
            ps.setString(4, pdfNombre);
            ps.setString(5, descripcion);
            ps.setObject(6, fecha);
            ps.executeUpdate();
        }
        new AuditLogRepository().log("area_resguardo", id, area, "crear",
            "Resguardo de área agregado a " + area + (descripcion != null && !descripcion.isBlank() ? " · " + descripcion : ""));
        return id;
    }

    /** The stored PDF, or empty for a pre-V25 row (see {@link AreaResguardo#pdfUrl()}). */
    public Optional<byte[]> leerPdf(String id) throws SQLException {
        DatabaseConfig.exigirServidor("Abrir un resguardo de área");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT area, pdf FROM area_resguardos WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Set<String> accessible = SessionManager.getAccessibleAreas();
                if (accessible != null && !accessible.contains(rs.getString("area")))
                    throw new SecurityException("No tienes acceso a ese resguardo");
                return Optional.ofNullable(rs.getBytes("pdf"));
            }
        }
    }

    public void delete(String id) throws SQLException {
        DatabaseConfig.exigirServidor("Eliminar un resguardo de área");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM area_resguardos WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
        new AuditLogRepository().log("area_resguardo", id, id, "eliminar", "Resguardo de área eliminado");
    }

    private AreaResguardo mapRow(ResultSet rs) throws SQLException {
        LocalDate fecha = rs.getDate("fecha") != null ? rs.getDate("fecha").toLocalDate() : null;
        Timestamp ca = rs.getTimestamp("created_at");
        LocalDateTime creadoEn = ca != null ? ca.toLocalDateTime() : null;
        return new AreaResguardo(rs.getString("id"), rs.getString("area"),
            rs.getString("pdf_url"), rs.getString("pdf_nombre"), rs.getBoolean("tiene_pdf"),
            rs.getString("descripcion"), fecha, creadoEn);
    }
}
