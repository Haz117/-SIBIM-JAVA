package com.sibim.session;

import java.util.prefs.Preferences;

/**
 * Who uses a shared área account on this PC, remembered so it is asked once
 * per PC instead of at every sign-in; the user menu changes it. Kept per
 * account in this Windows user's registry: another PC, or another account on
 * this one, asks again.
 */
public final class OperadorRecordado {

    private static final Preferences PREFS = Preferences.userRoot().node("sibim/operador");

    private OperadorRecordado() {}

    /** The name given last time on this PC for {@code cuentaId}, or null. */
    public static String de(String cuentaId) {
        if (cuentaId == null) return null;
        try {
            String n = PREFS.get(cuentaId, null);
            return n != null && n.trim().length() >= 3 ? n.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void recordar(String cuentaId, String persona) {
        if (cuentaId == null || persona == null || persona.trim().length() < 3) return;
        try {
            PREFS.put(cuentaId, persona.trim());
        } catch (Exception ignored) {
            // Not remembered: it is asked again at the next sign-in.
        }
    }
}
