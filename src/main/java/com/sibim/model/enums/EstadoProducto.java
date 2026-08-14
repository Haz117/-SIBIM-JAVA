package com.sibim.model.enums;

public enum EstadoProducto {
    ACTIVO("activo", "Activo", "#14B8A6"),
    BAJO_STOCK("bajo_stock", "Bajo Stock", "#F59E0B"),
    AGOTADO("agotado", "Agotado", "#F43F5E"),
    VENCIDO("vencido", "Vencido", "#A78BFA");

    private final String codigo;
    private final String etiqueta;
    private final String colorHex;

    EstadoProducto(String codigo, String etiqueta, String colorHex) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
        this.colorHex = colorHex;
    }

    public String getCodigo() { return codigo; }
    public String getEtiqueta() { return etiqueta; }
    public String getColorHex() { return colorHex; }

    @Override
    public String toString() { return etiqueta; }
}
