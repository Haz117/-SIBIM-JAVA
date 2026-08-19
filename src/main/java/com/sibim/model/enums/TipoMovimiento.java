package com.sibim.model.enums;

public enum TipoMovimiento {
    ENTRADA("entrada", "Entrada", "#14B8A6"),
    SALIDA("salida", "Salida", "#F43F5E"),
    AJUSTE("ajuste", "Ajuste", "#F59E0B"),
    TRANSFERENCIA("transferencia", "Transferencia", "#818CF8");

    private final String codigo;
    private final String etiqueta;
    private final String colorHex;

    TipoMovimiento(String codigo, String etiqueta, String colorHex) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
        this.colorHex = colorHex;
    }

    public String getCodigo() { return codigo; }
    public String getEtiqueta() { return etiqueta; }
    public String getColorHex() { return colorHex; }

    public static TipoMovimiento fromCodigo(String codigo) {
        for (TipoMovimiento t : values()) {
            if (t.codigo.equalsIgnoreCase(codigo)) return t;
        }
        throw new IllegalArgumentException("Tipo de movimiento desconocido: " + codigo);
    }

    @Override
    public String toString() { return etiqueta; }
}
