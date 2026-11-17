package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.session.SessionManager;

import java.util.Objects;

/** The last bien saved from the form in this session, whose purchase data the next one can
 *  copy («Copiar del anterior»): from 13 to 17-nov-2026, 173 of 437 edits typed again exactly
 *  what the bien before had (factura, folio, proveedor, fecha, color, tipo, vida útil). Only
 *  for the same account: another person's last bien is not offered. */
public final class BienAnterior {

    private static Producto ultimo;
    private static String usuario;

    private BienAnterior() {}

    /** After a bien is saved from the form. */
    public static synchronized void recordar(Producto guardado) {
        ultimo = guardado == null ? null : guardado.copia();
        usuario = actual();
    }

    /** The last bien saved by this account, or null. */
    public static synchronized Producto get() {
        return ultimo != null && Objects.equals(usuario, actual()) ? ultimo : null;
    }

    public static synchronized void olvidar() {
        ultimo = null;
        usuario = null;
    }

    private static String actual() {
        Usuario u = SessionManager.getCurrentUser();
        return u != null ? u.getId() : null;
    }
}
