package com.sibim.session;

import java.util.Objects;
import java.util.Optional;

/**
 * Where someone was when their session closed for inactivity, to take them back there when
 * they sign in again: from 28-oct to 13-nov-2026, 23 of 90 sessions ended that way and in
 * 7 of them the same person was back within 5 minutes, landing on Inicio and looking for
 * their screen again. Only in memory (SIBIM stays open on the sign-in screen), only for the
 * same account, and only once; a «Cerrar sesión» by hand leaves nothing.
 */
public final class DondeEstabas {

    /** @param vista    the screen ("productos")
     *  @param busqueda what its search box said, or null */
    public record Lugar(String usuarioId, String vista, String busqueda) {}

    private static volatile Lugar lugar;

    private DondeEstabas() {}

    public static void recordar(String usuarioId, String vista, String busqueda) {
        lugar = usuarioId == null || vista == null || vista.isBlank() ? null
            : new Lugar(usuarioId, vista, busqueda == null || busqueda.isBlank() ? null : busqueda.trim());
    }

    /** The place {@code usuarioId} left, once; empty for anyone else (and forgotten then). */
    public static Optional<Lugar> tomar(String usuarioId) {
        Lugar l = lugar;
        lugar = null;
        return l != null && Objects.equals(l.usuarioId(), usuarioId) ? Optional.of(l) : Optional.empty();
    }
}
