package com.sibim.util;

/** Central catalogue of java.util.prefs key strings to prevent typos when the
 *  same key is read in one place and written in another. */
public final class PreferenceKeys {

    // ── Preference node paths ────────────────────────────────────────────
    public static final String NODE_FILTROS_ALERTAS      = "sibim/filters/alertas";
    public static final String NODE_FILTROS_AUDITORIA    = "sibim/filters/auditoria";
    public static final String NODE_FILTROS_CATEGORIAS   = "sibim/filters/categorias";
    public static final String NODE_FILTROS_DEPRECIACION = "sibim/filters/depreciacion";
    public static final String NODE_FILTROS_MOVIMIENTOS  = "sibim/filters/movimientos";
    public static final String NODE_FILTROS_ORGANIGRAMA  = "sibim/filters/organigrama";
    public static final String NODE_FILTROS_PRODUCTOS    = "sibim/filters/productos";
    public static final String NODE_FILTROS_REPORTES     = "sibim/filters/reportes";
    public static final String NODE_UI_ANIMATIONS        = "sibim/ui/animations";
    public static final String NODE_UI_DENSITY           = "sibim/ui/density";
    public static final String NODE_UI_TEXT_SCALE        = "sibim/ui/text-scale";
    public static final String NODE_SEARCH_ALERTAS       = "sibim/search-history/alertas";
    public static final String NODE_SEARCH_PRODUCTOS     = "sibim/search-history/productos";
    public static final String NODE_SEARCH_MOVIMIENTOS   = "sibim/search-history/movimientos";
    public static final String NODE_CONTEO_DRAFT         = "sibim/conteo/draft";

    // ── Common filter keys (shared by multiple nodes) ────────────────────
    public static final String KEY_SEARCH   = "search";
    public static final String KEY_AREA     = "area";
    public static final String KEY_DESDE    = "desde";
    public static final String KEY_HASTA    = "hasta";
    public static final String KEY_CATEGORIA = "categoria";
    public static final String KEY_TIPO     = "tipo";
    public static final String KEY_USUARIO  = "usuario";
    public static final String KEY_ENTIDAD  = "entidad";
    public static final String KEY_ACCION   = "accion";
    public static final String KEY_PRESET   = "preset";

    // ── UI state keys ────────────────────────────────────────────────────
    public static final String KEY_DENSITY_INDEX         = "index";
    public static final String KEY_SOLO_ALERTAS          = "soloAlertas";

    // ── Collapsible section keys (alertas view) ──────────────────────────
    public static final String KEY_ALERTAS_AGOTADOS_COL      = "alertas.agotados.colapsado";
    public static final String KEY_ALERTAS_BAJOSTOCK_COL     = "alertas.bajostock.colapsado";
    public static final String KEY_ALERTAS_GARANTIAS_COL     = "alertas.garantias.colapsado";
    public static final String KEY_ALERTAS_MANTENIMIENTO_COL = "alertas.mantenimiento.colapsado";
    public static final String KEY_ALERTAS_COMODATOS_COL     = "alertas.comodatos.colapsado";
    public static final String KEY_ALERTAS_RESUMEN_COL       = "alertas.resumen.colapsado";

    private PreferenceKeys() {}
}
