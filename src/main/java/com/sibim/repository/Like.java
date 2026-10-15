package com.sibim.repository;

/** A LIKE pattern for "contains {@code texto}": a % or _ the user typed is
 *  searched as that character, not as a wildcard ("50%" used to find every
 *  bien, and offline the same search found only the literal text). Relies on
 *  PostgreSQL's default LIKE escape character, the backslash. */
final class Like {

    private Like() {}

    static String contiene(String texto) {
        return "%" + texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
