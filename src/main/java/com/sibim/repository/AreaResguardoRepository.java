package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.OfflineDocs;
import com.sibim.session.SessionManager;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Signed resguardo PDFs per área (area_resguardos). Offline they come from
 *  this PC's copy (OfflineDocs): the list of every área opened with a
 *  connection, and each PDF once it was opened with a connection. Demo mode
 *  has none, so every call says so instead of doing nothing. */
public class AreaResguardoRepository {

    /** Largest PDF accepted — a scanned multi-page resguardo is a few MB. */
    public static final int MAX_PDF_BYTES = 20 * 1024 * 1024;

    /** @param pdfUrl  local path, only for rows created before V25 (the PDF
     *                 was kept on the uploading PC); null otherwise
     *  @param tienePdf the PDF itself is stored in the database (V25+) */
    public record AreaResguardo(String id, String area, String pdfUrl, String pdfNombre, boolean tienePdf,
                                String descripcion, LocalDate fecha, LocalDateTime creadoEn) {}

    /** How a row is kept in the offline copy (a record doesn't round-trip
     *  through OfflineDocs' field-only JSON). {@code pdf} travels only in the
     *  queue of an upload made offline. */
    public static class Local {
        String id, area, pdfUrl, pdfNombre, descripcion;
        boolean tienePdf;
        LocalDate fecha;
        LocalDateTime creadoEn;
        byte[] pdf;

        public Local() {}
        Local(AreaResguardo r) {
            id = r.id(); area = r.area(); pdfUrl = r.pdfUrl(); pdfNombre = r.pdfNombre();
            tienePdf = r.tienePdf(); descripcion = r.descripcion(); fecha = r.fecha(); creadoEn = r.creadoEn();
        }
        AreaResguardo registro() {
            return new AreaResguardo(id, area, pdfUrl, pdfNombre, tienePdf, descripcion, fecha, creadoEn);
        }
    }

    /** Every resguardo of {@code area}, newest first — without the PDF bytes. */
    public List<AreaResguardo> findByArea(String area) throws SQLException {
        if (DatabaseConfig.isDemoMode()) DatabaseConfig.exigirServidor("Consultar los resguardos de área");
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.contains(area)) return new ArrayList<>();
        if (OfflineDocs.activo()) {
            return OfflineDocs.todos(OfflineDocs.AREA_RESGUARDO, Local.class).stream()
                .filter(l -> area.equals(l.area))
                .map(Local::registro)
                .sorted(Comparator.comparing(AreaResguardo::creadoEn, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        }
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
        OfflineDocs.guardarTodos(OfflineDocs.AREA_RESGUARDO, result.stream().map(Local::new).toList(), l -> l.id);
        return result;
    }

    /** Stores the PDF itself, so every PC can open it. */
    public String save(String area, byte[] pdf, String pdfNombre, String descripcion, LocalDate fecha)
            throws SQLException {
        if (DatabaseConfig.isDemoMode()) DatabaseConfig.exigirServidor("Guardar un resguardo de área");
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("El PDF está vacío");
        if (pdf.length > MAX_PDF_BYTES)
            throw new IllegalArgumentException("El PDF pesa más de " + (MAX_PDF_BYTES / (1024 * 1024)) + " MB");
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.contains(area))
            throw new SecurityException("No tienes acceso al área " + area);
        String id = UUID.randomUUID().toString();
        if (OfflineDocs.activo()) {
            Local l = new Local(new AreaResguardo(id, area, null, pdfNombre, true, descripcion, fecha, LocalDateTime.now()));
            OfflineDocs.guardar(OfflineDocs.AREA_RESGUARDO_PDF, id, pdf);
            l.pdf = pdf;
            OfflineDocs.registrar(OfflineDocs.AREA_RESGUARDO, "CREAR", id, l);
            l.pdf = null;
            OfflineDocs.guardar(OfflineDocs.AREA_RESGUARDO, id, l);   // the list copy without the bytes
        } else {
            saveOnline(id, area, pdf, pdfNombre, descripcion, fecha);
        }
        new AuditLogRepository().log("area_resguardo", id, area, "crear",
            "Resguardo de área agregado a " + area + (descripcion != null && !descripcion.isBlank() ? " · " + descripcion : ""));
        return id;
    }

    /** Server insert regardless of the offline flag — SyncService replays
     *  uploads made offline through here, keeping their id. */
    /** Replays an upload made offline (see SyncService). */
    public void saveOnline(Local l) throws SQLException {
        saveOnline(l.id, l.area, l.pdf, l.pdfNombre, l.descripcion, l.fecha);
    }

    public void saveOnline(String id, String area, byte[] pdf, String pdfNombre, String descripcion, LocalDate fecha)
            throws SQLException {
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
    }

    /** The stored PDF, or empty for a pre-V25 row (see {@link AreaResguardo#pdfUrl()}). */
    public Optional<byte[]> leerPdf(String id) throws SQLException {
        if (DatabaseConfig.isDemoMode()) DatabaseConfig.exigirServidor("Abrir un resguardo de área");
        if (OfflineDocs.activo()) {
            Optional<Local> fila = OfflineDocs.uno(OfflineDocs.AREA_RESGUARDO, id, Local.class);
            Set<String> accessible = SessionManager.getAccessibleAreas();
            if (fila.isPresent() && accessible != null && !accessible.contains(fila.get().area))
                throw new SecurityException("No tienes acceso a ese resguardo");
            Optional<byte[]> pdf = OfflineDocs.uno(OfflineDocs.AREA_RESGUARDO_PDF, id, byte[].class);
            if (pdf.isEmpty() && fila.isPresent() && fila.get().tienePdf)
                throw new IllegalStateException("Este PDF todavía no se ha abierto en esta PC con conexión, "
                    + "así que no hay copia local. Ábrelo cuando vuelva la conexión.");
            return pdf;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT area, pdf FROM area_resguardos WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Set<String> accessible = SessionManager.getAccessibleAreas();
                if (accessible != null && !accessible.contains(rs.getString("area")))
                    throw new SecurityException("No tienes acceso a ese resguardo");
                byte[] pdf = rs.getBytes("pdf");
                // Opened once with a connection → available on this PC offline.
                if (pdf != null && !DatabaseConfig.isDemoMode()) {
                    try { OfflineDocs.guardar(OfflineDocs.AREA_RESGUARDO_PDF, id, pdf); }
                    catch (SQLException e) { /* best-effort copy; the online read still works */ }
                }
                return Optional.ofNullable(pdf);
            }
        }
    }

    public void delete(String id) throws SQLException {
        if (DatabaseConfig.isDemoMode()) DatabaseConfig.exigirServidor("Eliminar un resguardo de área");
        if (OfflineDocs.activo()) {
            Local l = OfflineDocs.uno(OfflineDocs.AREA_RESGUARDO, id, Local.class)
                .orElseThrow(() -> new SQLException("Resguardo de área no encontrado en esta PC: " + id));
            OfflineDocs.registrar(OfflineDocs.AREA_RESGUARDO, "ELIMINAR", id, l);
            OfflineDocs.borrar(OfflineDocs.AREA_RESGUARDO, id);
            OfflineDocs.borrar(OfflineDocs.AREA_RESGUARDO_PDF, id);
            new AuditLogRepository().log("area_resguardo", id, id, "eliminar", "Resguardo de área eliminado");
            return;
        }
        deleteOnline(id);
        new AuditLogRepository().log("area_resguardo", id, id, "eliminar", "Resguardo de área eliminado");
    }

    public void deleteOnline(String id) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM area_resguardos WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
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
