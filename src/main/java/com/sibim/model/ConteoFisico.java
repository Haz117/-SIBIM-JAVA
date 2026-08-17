package com.sibim.model;

import java.time.LocalDateTime;
import java.util.List;

/** A completed physical inventory count session (toma de inventario) —
 *  persisted so it can be reviewed later, not just represented by whatever
 *  Ajuste movements it happened to generate. */
public class ConteoFisico {
    private String id;
    private String usuarioId;
    private String usuarioNombre;
    private int totalContados;
    private int totalDiscrepancias;
    private LocalDateTime creadoEn;
    private List<ConteoItem> items;

    public ConteoFisico() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public int getTotalContados() { return totalContados; }
    public void setTotalContados(int totalContados) { this.totalContados = totalContados; }

    public int getTotalDiscrepancias() { return totalDiscrepancias; }
    public void setTotalDiscrepancias(int totalDiscrepancias) { this.totalDiscrepancias = totalDiscrepancias; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public List<ConteoItem> getItems() { return items; }
    public void setItems(List<ConteoItem> items) { this.items = items; }
}
