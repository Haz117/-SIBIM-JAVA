package com.sibim.service;

import com.sibim.model.Producto;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Bienes › Más › «Fotos de una carpeta»: the photos of a visit, taken with the phone and copied to
 * one folder, given to their bienes one after the other. From 20 to 24-nov-2026 Patrimonio put 418
 * photos on 241 bienes one by one from each form (about 70 s a bien: open it, go to the photos,
 * look for the folder, pick, save); 312 bienes are still without one. The photos come in the order
 * they were taken and the bienes in the order of the list printed for the visit
 * ({@link #EN_ORDEN_DE_VISITA}), so after each bien the next photo not used is already marked.
 */
public final class FotosDeCarpeta {

    private FotosDeCarpeta() {}

    /** What ImageIO reads (ImageUtils resizes them before uploading). */
    private static final Set<String> SE_LEEN = Set.of("jpg", "jpeg", "png");
    /** Photos of a phone that can't be read here: said, not offered. */
    private static final Set<String> NO_SE_LEEN = Set.of("heic", "heif", "webp");

    /** A photo of the folder and when it was taken (its EXIF date, else when the file was written). */
    public record Foto(File archivo, LocalDateTime tomada) {
        public String nombre() { return archivo.getName(); }
    }

    /** The photos to offer, and how many images of another kind (an iPhone's HEIC) were left out. */
    public record Contenido(List<Foto> fotos, int sinLeer) {}

    public enum Orden {
        POR_NOMBRE("Por nombre del archivo"), POR_FECHA("Por la hora en que se tomaron");
        public final String etiqueta;
        Orden(String etiqueta) { this.etiqueta = etiqueta; }
        @Override public String toString() { return etiqueta; }
    }

    /** The folder's photos (not its subfolders), in {@code orden}. */
    public static Contenido leer(File carpeta, Orden orden) {
        File[] archivos = carpeta == null ? null : carpeta.listFiles(File::isFile);
        List<Foto> fotos = new ArrayList<>();
        int sinLeer = 0;
        for (File f : archivos == null ? new File[0] : archivos) {
            String ext = extension(f.getName());
            if (SE_LEEN.contains(ext)) fotos.add(new Foto(f, tomada(f)));
            else if (NO_SE_LEEN.contains(ext)) sinLeer++;
        }
        ordenar(fotos, orden);
        return new Contenido(fotos, sinLeer);
    }

    static void ordenar(List<Foto> fotos, Orden orden) {
        Comparator<Foto> porNombre = (a, b) -> natural(a.nombre(), b.nombre());
        fotos.sort(orden == Orden.POR_FECHA ? Comparator.comparing(Foto::tomada).thenComparing(porNombre) : porNombre);
    }

    private static String extension(String nombre) {
        int i = nombre.lastIndexOf('.');
        return i < 0 ? "" : nombre.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /** «IMG_9.jpg» before «IMG_10.jpg»: the numbers in a name compare as numbers. */
    static int natural(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int fi = i, fj = j;
                while (fi < a.length() && Character.isDigit(a.charAt(fi))) fi++;
                while (fj < b.length() && Character.isDigit(b.charAt(fj))) fj++;
                String na = a.substring(i, fi).replaceFirst("^0+(?=.)", ""), nb = b.substring(j, fj).replaceFirst("^0+(?=.)", "");
                int c = na.length() != nb.length() ? Integer.compare(na.length(), nb.length()) : na.compareTo(nb);
                if (c != 0) return c;
                i = fi;
                j = fj;
            } else {
                int c = Character.compare(Character.toLowerCase(x), Character.toLowerCase(y));
                if (c != 0) return c;
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /** When the photo was taken: the camera's date in its EXIF, else the file's. */
    static LocalDateTime tomada(File f) {
        LocalDateTime exif = fechaExif(f);
        if (exif != null) return exif;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault());
    }

    private static final DateTimeFormatter EXIF = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    /** DateTimeOriginal (or DateTime) of a JPEG's EXIF; null when it has none (a photo sent by
     *  WhatsApp loses it) or it is not a JPEG. Reads only the start of the file. */
    static LocalDateTime fechaExif(File f) {
        byte[] b;
        try (InputStream in = Files.newInputStream(f.toPath())) {
            b = in.readNBytes(256 * 1024);
        } catch (IOException e) {
            return null;
        }
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) return null;
        int i = 2;
        while (i + 4 <= b.length && (b[i] & 0xFF) == 0xFF) {
            int marca = b[i + 1] & 0xFF;
            int largo = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            if (marca == 0xE1 && i + 10 <= b.length && new String(b, i + 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("Exif"))
                return deTiff(b, i + 10, Math.min(b.length, i + 2 + largo));
            if (marca == 0xDA || largo < 2) return null;   // the image itself: no EXIF before it
            i += 2 + largo;
        }
        return null;
    }

    private static LocalDateTime deTiff(byte[] b, int base, int fin) {
        try {
            boolean intel = b[base] == 'I';
            int ifd0 = base + leer(b, base + 4, 4, intel);
            String fecha = null;
            int exifIfd = -1;
            int n = leer(b, ifd0, 2, intel);
            for (int k = 0; k < n; k++) {
                int e = ifd0 + 2 + 12 * k;
                if (e + 12 > fin) return null;
                int tag = leer(b, e, 2, intel);
                if (tag == 0x0132) fecha = ascii(b, base + leer(b, e + 8, 4, intel), 19);
                if (tag == 0x8769) exifIfd = base + leer(b, e + 8, 4, intel);
            }
            if (exifIfd > 0 && exifIfd + 2 <= fin) {
                int m = leer(b, exifIfd, 2, intel);
                for (int k = 0; k < m; k++) {
                    int e = exifIfd + 2 + 12 * k;
                    if (e + 12 > fin) break;
                    if (leer(b, e, 2, intel) == 0x9003) { fecha = ascii(b, base + leer(b, e + 8, 4, intel), 19); break; }
                }
            }
            return fecha == null ? null : LocalDateTime.parse(fecha, EXIF);
        } catch (RuntimeException e) {
            return null;   // an EXIF written otherwise: the file's date
        }
    }

    private static int leer(byte[] b, int i, int bytes, boolean intel) {
        int v = 0;
        for (int k = 0; k < bytes; k++) {
            int x = b[i + (intel ? bytes - 1 - k : k)] & 0xFF;
            v = (v << 8) | x;
        }
        return v;
    }

    private static String ascii(byte[] b, int i, int largo) {
        return new String(b, i, largo, java.nio.charset.StandardCharsets.US_ASCII);
    }

    /** The first photo not used after {@code despuesDe} (-1 for the first of all); -1 when none is left. */
    public static int siguienteLibre(List<Foto> fotos, Set<File> usadas, int despuesDe) {
        for (int i = Math.max(0, despuesDe + 1); i < fotos.size(); i++)
            if (!usadas.contains(fotos.get(i).archivo())) return i;
        return -1;
    }

    /** The order of a visit: by ubicación (as written, the ones without one last), then by número.
     *  The printed list and «Fotos de una carpeta» use it, so the photos taken going down the list
     *  come in the same order as the bienes. */
    public static final Comparator<Producto> EN_ORDEN_DE_VISITA = Comparator
        .comparing((Producto p) -> p.getUbicacion() == null || p.getUbicacion().isBlank())
        .thenComparing(p -> CalidadDatosService.claveTexto(p.getUbicacion()))
        .thenComparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), com.sibim.util.OrdenCodigo.POR_CODIGO);

    public static boolean sinFoto(Producto p) {
        return p.getFotoUrl() == null || p.getFotoUrl().isBlank();
    }

    /** The photos a bien ends with: the new ones first (the principal is one taken on the visit:
     *  112 of the 241 bienes of 20-24 nov had one already, and it replaced it), then the ones it had
     *  unless they are replaced. */
    public static List<String> conLasNuevas(List<String> nuevas, List<String> tenia, boolean reemplazar) {
        List<String> out = new ArrayList<>(nuevas);
        if (!reemplazar) for (String t : tenia) if (!out.contains(t)) out.add(t);
        return out;
    }
}
