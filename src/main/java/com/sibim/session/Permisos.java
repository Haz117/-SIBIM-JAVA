package com.sibim.session;

/**
 * What the signed-in user may do, by name. Screens ask here to decide which
 * buttons to show and services ask here before acting, so a rule lives in one
 * place instead of being repeated as role checks on both sides.
 *
 * <p>Who does what (decided 2026-10-01, préstamos and resguardos revised 2026-10-04):
 * <ul>
 *   <li>Movimientos, comodatos, préstamos and resguardos: only Patrimonio
 *       (administrator). Secretarios and direcciones neither register them nor
 *       have those screens.</li>
 *   <li>The áreas do not send requests through SIBIM: what they need is asked
 *       of Finanzas, outside the system.</li>
 *   <li>The áreas keep four things of a bien current — ubicación, descripción,
 *       estado físico and its photos; the rest of the record is Patrimonio's.</li>
 *   <li>Control reports, spreadsheet exports (Excel/CSV), the Movimientos and
 *       Comodatos screens: only Patrimonio. Amounts in pesos: Patrimonio and
 *       secretarios, not direcciones.</li>
 * </ul>
 */
public final class Permisos {

    private Permisos() {}

    /** Comodatos, cancelling a resguardo, and every movement: only Patrimonio. */
    public static boolean gestionaDocumentos() { return SessionManager.isAdmin(); }

    /** Registers a préstamo or its return. */
    public static boolean prestaBienes() { return SessionManager.isAdmin(); }

    /** Assigns a resguardo to someone. */
    public static boolean creaResguardos() { return SessionManager.isAdmin(); }

    /** Whether {@code area} is one the caller answers for: any área for
     *  Patrimonio; for a secretario his secretaría and its direcciones; for a
     *  dirección only itself. */
    public static boolean esAreaPropia(String area) { return SessionManager.isAreaAccessible(area); }

    /** Approves or rejects the requests the áreas sent before they stopped
     *  sending them (transfers, préstamos, resguardos still pending). */
    public static boolean atiendeSolicitudes() { return SessionManager.isAdmin(); }

    /** Changes any field of a bien. Everyone else only updates its ubicación,
     *  descripción, estado físico and photos (see ProductoService.save). */
    public static boolean editaFichaCompleta() { return SessionManager.isAdmin(); }

    /** Reports that audit the inventory as a whole — movimientos, bajas,
     *  auditoría, entrega-recepción, parque vehicular. Nobody signed in counts
     *  as allowed: scheduled jobs and tests run without a session. */
    public static boolean veReportesDeControl() { return !SessionManager.isLoggedIn() || SessionManager.isAdmin(); }

    /** Takes data out as an editable spreadsheet (Excel or CSV); the áreas print PDFs. */
    public static boolean exportaHojasDeCalculo() { return !SessionManager.isLoggedIn() || SessionManager.isAdmin(); }

    /** Sees what the bienes are worth. A dirección does not. */
    public static boolean veValores() { return !SessionManager.isLoggedIn() || !SessionManager.isDireccion(); }

    /** An amount as text, or a dash for whoever may not see amounts. */
    public static String pesos(java.math.BigDecimal valor) {
        return veValores() ? com.sibim.util.FormatUtils.formatCurrency(valor) : "—";
    }

    public static final String SOLO_PATRIMONIO_REPORTE = "Este reporte es solo de Patrimonio (administrador).";
    public static final String SOLO_PDF =
        "Las áreas exportan en PDF; Excel y CSV son solo de Patrimonio (administrador).";

    /** Publishes a new version of SIBIM for every PC. */
    public static boolean publicaActualizaciones() { return SessionManager.isAdmin(); }

    /** Fails with {@code mensaje} unless the caller is Patrimonio. */
    public static void exigirGestionDeDocumentos(String mensaje) {
        if (!gestionaDocumentos()) throw new SecurityException(mensaje);
    }
}
