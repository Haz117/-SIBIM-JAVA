package com.sibim.model;

import java.time.LocalDateTime;

/** A single entry in the generic change audit trail — covers entities that
 *  don't already have their own history (bienes, categorías, usuarios).
 *  Movimientos remain their own audit trail for stock changes. */
public class AuditLog {
    private String id;
    private String entidad;
    private String entidadId;
    private String entidadNombre;
    private String accion;
    private String detalle;
    private String usuarioId;
    private String usuarioNombre;
    private LocalDateTime creadoEn;

    public AuditLog() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEntidad() { return entidad; }
    public void setEntidad(String entidad) { this.entidad = entidad; }

    public String getEntidadId() { return entidadId; }
    public void setEntidadId(String entidadId) { this.entidadId = entidadId; }

    public String getEntidadNombre() { return entidadNombre; }
    public void setEntidadNombre(String entidadNombre) { this.entidadNombre = entidadNombre; }

    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }

    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
