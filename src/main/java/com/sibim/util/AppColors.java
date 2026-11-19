package com.sibim.util;

/** Named color constants for dialog gradient headers and button accents.
 *  Semantic pairs: each color has a _D (dark) variant used as the gradient endpoint. */
public final class AppColors {
    private AppColors() {}

    public static final String SUCCESS      = "#059669";
    public static final String SUCCESS_D    = "#047857";
    public static final String SUCCESS_DD   = "#065F46";
    public static final String GREEN        = "#166534";
    public static final String GREEN_D      = "#15803D";
    // INFO and CYAN were the neutral dialog accents (blue, teal): guinda shades
    // now, like the rest of the interface. Green, amber and red keep their
    // meaning (done, careful, danger).
    public static final String INFO         = "#9F2241";
    public static final String INFO_D       = "#861C38";
    public static final String WARNING      = "#D97706";
    public static final String WARNING_D    = "#B45309";
    public static final String WARNING_DD   = "#92400E";
    public static final String DANGER       = "#DC2626";
    public static final String DANGER_D     = "#B91C1C";
    // The brand color (names kept from when it was indigo): the institutional
    // guinda, Pantone 1805 C, as in the PDFs and the CSS tokens sibim-indigo*.
    public static final String PRIMARY      = "#9F2241";
    public static final String PRIMARY_D    = "#861C38";
    public static final String INDIGO       = "#691C32";
    public static final String INDIGO_D     = "#521628";
    // Dialog accents that used to be violet: guinda shades now, so no dialog
    // header or button stays purple next to the guinda interface.
    public static final String PURPLE       = "#861C38";
    public static final String PURPLE_D     = "#691C32";
    public static final String DEEP_PURPLE  = "#41111F";
    public static final String CYAN         = "#861C38";
    public static final String CYAN_D       = "#691C32";
    public static final String SLATE        = "#3F4C5E";
    public static final String SLATE_D      = "#334155";
    public static final String SCENE_BG     = "#0F172A";

    /** A color given as an icon on a white row: an icon needs 3:1 against what is behind it
     *  (WCAG 1.4.11). A category color picked by hand that doesn't reach it (18-nov-2026 in
     *  Categorías: amber #F59E0B 2.15:1, cyan #06B6D4 2.43:1, emerald #10B981 2.43:1) is
     *  darkened, keeping its hue, until it does (3.3:1, for the tinted rows); one that already
     *  does comes back as it was. */
    public static String legibleSobreBlanco(String hex) {
        javafx.scene.paint.Color c;
        try { c = javafx.scene.paint.Color.web(hex); }
        catch (IllegalArgumentException | NullPointerException e) { return hex; }
        if (contrasteConBlanco(c) >= 3.3) return hex;
        for (int i = 0; i < 30 && contrasteConBlanco(c) < 3.3; i++) c = c.deriveColor(0, 1, 0.93, 1);
        return String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255),
            (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    static double contrasteConBlanco(javafx.scene.paint.Color c) {
        double[] v = {c.getRed(), c.getGreen(), c.getBlue()};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        double l = 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
        return 1.05 / (l + 0.05);
    }
}
