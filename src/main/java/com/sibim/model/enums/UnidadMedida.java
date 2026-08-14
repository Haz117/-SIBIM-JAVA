package com.sibim.model.enums;

public enum UnidadMedida {
    PIEZA("pieza", "Pieza"),
    UNIDAD("unidad", "Unidad"),
    EQUIPO("equipo", "Equipo"),
    JUEGO("juego", "Juego"),
    LOTE("lote", "Lote"),
    KG("kg", "Kilogramo"),
    LITRO("litro", "Litro");

    private final String codigo;
    private final String etiqueta;

    UnidadMedida(String codigo, String etiqueta) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
    }

    public String getCodigo() { return codigo; }
    public String getEtiqueta() { return etiqueta; }

    public static UnidadMedida fromCodigo(String codigo) {
        for (UnidadMedida u : values()) {
            if (u.codigo.equalsIgnoreCase(codigo)) return u;
        }
        return PIEZA;
    }

    @Override
    public String toString() { return etiqueta; }
}
