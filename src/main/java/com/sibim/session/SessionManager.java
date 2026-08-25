package com.sibim.session;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.config.Areas;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SessionManager {

    private static volatile Usuario currentUser;

    private SessionManager() {}

    public static void setCurrentUser(Usuario user) {
        currentUser = user;
    }

    public static Usuario getCurrentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static void logout() {
        currentUser = null;
    }

    public static boolean isAdmin() {
        return currentUser != null && currentUser.getRol() == Rol.ADMIN;
    }

    public static boolean isSecretario() {
        return currentUser != null && currentUser.getRol() == Rol.SECRETARIO;
    }

    public static boolean isDireccion() {
        return currentUser != null && currentUser.getRol() == Rol.DIRECCION;
    }

    /**
     * Returns the set of area names accessible to the current user.
     * Returns null for admin (all areas accessible).
     */
    public static Set<String> getAccessibleAreas() {
        if (currentUser == null) return Collections.emptySet();
        if (isAdmin()) return null; // null = no restriction

        String rawArea = currentUser.getArea();
        if (rawArea == null || rawArea.isBlank()) {
            return Collections.emptySet();
        }

        String area = rawArea.trim();

        Set<String> areas = new LinkedHashSet<>();
        areas.add(area);

        if (isSecretario()) {
            areas.addAll(Areas.getDireccionesDeSecretaria(area));
        }

        return Collections.unmodifiableSet(areas);
    }

    public static boolean isAreaAccessible(String area) {
        if (isAdmin()) return true;
        if (area == null || area.isBlank()) return false;
        Set<String> accessible = getAccessibleAreas();
        if (accessible == null) return true; // admin
        return accessible.contains(area.trim());
    }
}
