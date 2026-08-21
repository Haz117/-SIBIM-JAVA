package com.sibim.model;

import com.sibim.model.enums.TipoMovimiento;

import java.time.LocalDateTime;

public class Movimiento {
    /** "APROBADO" (default) | "PENDIENTE" | "RECHAZADO" */
    public static final String ESTADO_APROBADO  = "APROBADO";
    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_RECHAZADO = "RECHAZADO";

    private String id;
    private String productoId;
    private String productoNombre;
    private String categoriaColor;
    private TipoMovimiento tipo;
    private int cantidad;
    private int stockAnterior;
    private int stockNuevo;
    private String areaOrigen;
    private String areaDestino;
    private String motivo;
    private String referencia;
    private String usuarioId;
    private String usuarioNombre;
    private LocalDateTime creadoEn;
    private String estado = ESTADO_APROBADO;

    public Movimiento() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }

    public String getProductoNombre() { return productoNombre; }
    public void setProductoNombre(String productoNombre) { this.productoNombre = productoNombre; }

    public String getCategoriaColor() { return categoriaColor; }
    public void setCategoriaColor(String categoriaColor) { this.categoriaColor = categoriaColor; }

    public TipoMovimiento getTipo() { return tipo; }
    public void setTipo(TipoMovimiento tipo) { this.tipo = tipo; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public int getStockAnterior() { return stockAnterior; }
    public void setStockAnterior(int stockAnterior) { this.stockAnterior = stockAnterior; }

    public int getStockNuevo() { return stockNuevo; }
    public void setStockNuevo(int stockNuevo) { this.stockNuevo = stockNuevo; }

    public String getAreaOrigen() { return areaOrigen; }
    public void setAreaOrigen(String areaOrigen) { this.areaOrigen = areaOrigen; }

    public String getAreaDestino() { return areaDestino; }
    public void setAreaDestino(String areaDestino) { this.areaDestino = areaDestino; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public String getEstado() { return estado != null ? estado : ESTADO_APROBADO; }
    public void setEstado(String estado) { this.estado = estado; }

    public boolean isPendiente()  { return ESTADO_PENDIENTE.equals(estado); }
    public boolean isAprobado()   { return ESTADO_APROBADO.equals(estado) || estado == null; }
    public boolean isRechazado()  { return ESTADO_RECHAZADO.equals(estado); }
}
