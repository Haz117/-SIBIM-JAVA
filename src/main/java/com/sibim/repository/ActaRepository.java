package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.OfflineDocs;
import com.sibim.model.ActaEntregaRecepcion;
import com.sibim.session.SessionManager;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ActaRepository {

    private final FolioRepository folioRepo = new FolioRepository();

    public List<ActaEntregaRecepcion> findAll() throws SQLException {
        if (DatabaseConfig.isDemoMode()) return List.of();
        if (OfflineDocs.activo())
            return OfflineDocs.todos(OfflineDocs.ACTA, ActaEntregaRecepcion.class).stream()
                .sorted(java.util.Comparator.comparing(ActaEntregaRecepcion::getCreadoEn,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
        List<ActaEntregaRecepcion> list = new ArrayList<>();
        String sql = "SELECT * FROM actas_entrega_recepcion ORDER BY created_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        OfflineDocs.guardarTodos(OfflineDocs.ACTA, list, ActaEntregaRecepcion::getId);
        return list;
    }

    public ActaEntregaRecepcion save(ActaEntregaRecepcion acta) throws SQLException {
        if (!SessionManager.isAdmin())
            throw new SecurityException("Solo el administrador puede generar actas de entrega-recepción");
        if (DatabaseConfig.isDemoMode())
            throw new IllegalStateException("Actas no disponibles en modo demo");
        if (acta.getId() == null) acta.setId(UUID.randomUUID().toString());
        com.sibim.model.Usuario u = SessionManager.getCurrentUser();
        if (u != null) {
            acta.setCreadoPorId(u.getId());
            acta.setCreadoPorNombre(u.getNombre());
        }
        if (OfflineDocs.activo()) {
            acta.setNumero(OfflineDocs.folioProvisional("AER", acta.getId()));
            acta.setCreadoEn(LocalDateTime.now());
            OfflineDocs.registrar(OfflineDocs.ACTA, "CREAR", acta.getId(), acta);
            return acta;
        }
        return saveOnline(acta);
    }

    /** Server write regardless of the offline flag (SyncService replays offline
     *  actas here); a provisional folio is replaced by the next real one. */
    public ActaEntregaRecepcion saveOnline(ActaEntregaRecepcion acta) throws SQLException {
        if (acta.getId() == null) acta.setId(UUID.randomUUID().toString());
        if (acta.getNumero() == null || acta.getNumero().contains("-PROV-")) acta.setNumero(nextNumero());

        String sql = """
            INSERT INTO actas_entrega_recepcion
              (id, numero, admin_saliente, cargo_saliente, admin_entrante, cargo_entrante,
               fecha_entrega, observaciones, total_bienes, valor_total,
               creado_por_id, creado_por_nombre, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,NOW())
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, acta.getId());
            ps.setString(2, acta.getNumero());
            ps.setString(3, acta.getAdminSaliente());
            ps.setString(4, acta.getCargoSaliente());
            ps.setString(5, acta.getAdminEntrante());
            ps.setString(6, acta.getCargoEntrante());
            ps.setDate(7, Date.valueOf(acta.getFechaEntrega()));
            ps.setString(8, acta.getObservaciones());
            ps.setInt(9, acta.getTotalBienes());
            ps.setBigDecimal(10, acta.getValorTotal());
            ps.setString(11, acta.getCreadoPorId());
            ps.setString(12, acta.getCreadoPorNombre());
            ps.executeUpdate();
        }
        acta.setCreadoEn(LocalDateTime.now());
        new AuditLogRepository().log("acta", acta.getId(), acta.getNumero(), "crear",
            "Acta de entrega-recepción " + acta.getNumero() + " · " + acta.getAdminSaliente()
                + " → " + acta.getAdminEntrante() + " · " + acta.getTotalBienes() + " bienes");
        return acta;
    }

    public String nextNumero() throws SQLException {
        return folioRepo.next("AER");
    }

    private ActaEntregaRecepcion mapRow(ResultSet rs) throws SQLException {
        ActaEntregaRecepcion a = new ActaEntregaRecepcion();
        a.setId(rs.getString("id"));
        a.setNumero(rs.getString("numero"));
        a.setAdminSaliente(rs.getString("admin_saliente"));
        a.setCargoSaliente(rs.getString("cargo_saliente"));
        a.setAdminEntrante(rs.getString("admin_entrante"));
        a.setCargoEntrante(rs.getString("cargo_entrante"));
        Date fecha = rs.getDate("fecha_entrega");
        if (fecha != null) a.setFechaEntrega(fecha.toLocalDate());
        a.setObservaciones(rs.getString("observaciones"));
        a.setTotalBienes(rs.getInt("total_bienes"));
        BigDecimal vt = rs.getBigDecimal("valor_total");
        if (vt != null) a.setValorTotal(vt);
        a.setCreadoPorId(rs.getString("creado_por_id"));
        a.setCreadoPorNombre(rs.getString("creado_por_nombre"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) a.setCreadoEn(ts.toLocalDateTime());
        return a;
    }
}
