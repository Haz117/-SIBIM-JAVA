package com.sibim.model;

/** One reviewed line within a {@link ConteoFisico} session — every bien that
 *  was in scope, whether it matched the system or not (not just the
 *  discrepancies), so a completed count is fully reviewable later. */
public class ConteoItem {
    private String id;
    private String conteoId;
    private String productoId;
    private String productoNombre;
    private String area;
    private int stockSistema;
    private int stockContado;
    private boolean ajustado;

    public ConteoItem() {}

    public int getDelta() { return stockContado - stockSistema; }
    public boolean isDiscrepancia() { return stockContado != stockSistema; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConteoId() { return conteoId; }
    public void setConteoId(String conteoId) { this.conteoId = conteoId; }

    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }

    public String getProductoNombre() { return productoNombre; }
    public void setProductoNombre(String productoNombre) { this.productoNombre = productoNombre; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public int getStockSistema() { return stockSistema; }
    public void setStockSistema(int stockSistema) { this.stockSistema = stockSistema; }

    public int getStockContado() { return stockContado; }
    public void setStockContado(int stockContado) { this.stockContado = stockContado; }

    public boolean isAjustado() { return ajustado; }
    public void setAjustado(boolean ajustado) { this.ajustado = ajustado; }
}
