package com.sibim.util;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The order of the bienes' códigos wherever they are listed: by área prefix, then by número. */
public final class OrdenCodigo {

    private OrdenCodigo() {}

    private static final Pattern NUMERO_FINAL = Pattern.compile("(.*?)(\\d+)\\s*");

    /** "DGOB/09" before "DGOB/10" before "DGOB/100": an área can have more than 99 bienes.
     *  Compares (prefix, número, whole código) so that it is a consistent order even for
     *  old códigos without a número, which a sort would otherwise reject. */
    public static final Comparator<String> POR_CODIGO = (a, b) -> {
        String[] ka = clave(a), kb = clave(b);
        for (int i = 0; i < ka.length; i++) {
            int c = i == 2 && ka[2].length() != kb[2].length() ? Integer.compare(ka[2].length(), kb[2].length())
                : ka[i].compareTo(kb[i]);
            if (c != 0) return c;
        }
        return 0;
    };

    /** {prefix, "0" with a número / "1" without, the número without leading zeros, the whole código}. */
    private static String[] clave(String codigo) {
        String c = codigo.trim().toLowerCase();
        Matcher m = NUMERO_FINAL.matcher(c);
        if (!m.matches()) return new String[]{c, "1", "", c};
        return new String[]{m.group(1), "0", m.group(2).replaceFirst("^0+(?=.)", ""), c};
    }
}
