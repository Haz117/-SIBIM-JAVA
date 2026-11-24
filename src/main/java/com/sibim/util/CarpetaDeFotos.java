package com.sibim.util;

import java.io.File;
import java.util.prefs.Preferences;

/** The folder the last photos of a bien were picked from, remembered on this PC: the photos of a
 *  visit are all in one folder (24-nov-2026: 418 photos for 241 bienes in four days, each one
 *  looked for again from the start). */
public final class CarpetaDeFotos {

    private static final String NODO = "sibim/fotos", CLAVE = "carpeta";

    private CarpetaDeFotos() {}

    /** @return the folder, or null when none was used yet or it is gone (a USB taken out) */
    public static File ultima() {
        try {
            String s = Preferences.userRoot().node(NODO).get(CLAVE, null);
            File f = s == null ? null : new File(s);
            return f != null && f.isDirectory() ? f : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @param archivoOCarpeta a photo just picked, or its folder */
    public static void recordar(File archivoOCarpeta) {
        if (archivoOCarpeta == null) return;
        File c = archivoOCarpeta.isDirectory() ? archivoOCarpeta : archivoOCarpeta.getParentFile();
        if (c == null) return;
        try {
            Preferences.userRoot().node(NODO).put(CLAVE, c.getAbsolutePath());
        } catch (RuntimeException ignored) {
            // without preferences it opens where Windows says, as before
        }
    }
}
