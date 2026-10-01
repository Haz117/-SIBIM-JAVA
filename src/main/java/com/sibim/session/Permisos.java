package com.sibim.session;

/**
 * What the signed-in user may do, by name. Screens ask here to decide which
 * buttons to show and services ask here before acting, so a rule lives in one
 * place instead of being repeated as role checks on both sides.
 */
public final class Permisos {

    private Permisos() {}

    /** Préstamos, resguardos and comodatos: only Patrimonio (administrator)
     *  creates, returns, cancels or closes them; áreas see and print them. */
    public static boolean gestionaDocumentos() { return SessionManager.isAdmin(); }

    /** Approves or rejects what the áreas ask for (transfers, préstamos, resguardos). */
    public static boolean atiendeSolicitudes() { return SessionManager.isAdmin(); }

    /** Asks Patrimonio for a préstamo or a resguardo of one of its own bienes. */
    public static boolean pideDocumentos() { return SessionManager.isLoggedIn() && !SessionManager.isAdmin(); }

    /** Publishes a new version of SIBIM for every PC. */
    public static boolean publicaActualizaciones() { return SessionManager.isAdmin(); }

    /** Fails with {@code mensaje} unless the caller manages documents. */
    public static void exigirGestionDeDocumentos(String mensaje) {
        if (!gestionaDocumentos()) throw new SecurityException(mensaje);
    }
}
