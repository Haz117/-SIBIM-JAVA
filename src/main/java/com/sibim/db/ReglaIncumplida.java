package com.sibim.db;

import java.sql.SQLException;

/**
 * A movement the inventory rules do not allow (more than there is, a bien
 * dado de baja, not the most recent movement...), found with the bien locked
 * inside the transaction. It is a SQLException so that raising it rolls the
 * transaction back like any database error; MovimientoService tells it apart
 * by its type and shows its message to the user as it is, instead of a generic
 * "error de base de datos".
 */
public class ReglaIncumplida extends SQLException {

    public ReglaIncumplida(String mensaje) {
        super(mensaje);
    }
}
