package com.sibim.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Resguardo {
    public static final String ESTADO_ACTIVO    = "ACTIVO";
    public static final String ESTADO_CANCELADO = "CANCELADO";

    private String id;
    private String numero;
    private String resguardanteNombre;
    private String resguardanteCargo;
    private String resguardanteArea;
    private String creadoPorId;
    private String creadoPorNombre;
    private String observaciones;
    private String estado = ESTADO_ACTIVO;
    private LocalDateTime creadoEn;
    private List<ResguardoItem> items = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getResguardanteNombre() { return resguardanteNombre; }
    public void setResguardanteNombre(String v) { this.resguardanteNombre = v; }
    public String getResguardanteCargo() { return resguardanteCargo; }
    public void setResguardanteCargo(String v) { this.resguardanteCargo = v; }
    public String getResguardanteArea() { return resguardanteArea; }
    public void setResguardanteArea(String v) { this.resguardanteArea = v; }
    public String getCreadoPorId() { return creadoPorId; }
    public void setCreadoPorId(String v) { this.creadoPorId = v; }
    public String getCreadoPorNombre() { return creadoPorNombre; }
    public void setCreadoPorNombre(String v) { this.creadoPorNombre = v; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String v) { this.observaciones = v; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime v) { this.creadoEn = v; }
    public List<ResguardoItem> getItems() { return items; }
    public void setItems(List<ResguardoItem> items) { this.items = items != null ? items : new ArrayList<>(); }
    public boolean isActivo() { return ESTADO_ACTIVO.equals(estado); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Resguardo r)) return false;
        return Objects.equals(id, r.id);
    }
    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
