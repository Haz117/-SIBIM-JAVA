package com.sibim.util;

/** Named color constants for dialog gradient headers, button accents and the PDFs.
 *  Semantic pairs: each color has a _D (dark) variant used as the gradient endpoint.
 *
 *  <p>The brand color is defined ONCE, in {@link #PRIMARY} and its darker steps;
 *  every other guinda here, the PDFs and spreadsheets ({@code ColoresDocumento})
 *  and the e-mails refer to it. Its CSS counterpart is the token {@code sibim-marca}
 *  in 00-tokens-base.css. To give the system another tone, change both. */
public final class AppColors {
    private AppColors() {}

    public static final String SUCCESS      = "#059669";
    public static final String SUCCESS_D    = "#047857";
    public static final String SUCCESS_DD   = "#065F46";
    public static final String GREEN        = "#166534";
    public static final String GREEN_D      = "#15803D";
    public static final String WARNING      = "#D97706";
    public static final String WARNING_D    = "#B45309";
    public static final String WARNING_DD   = "#92400E";
    public static final String DANGER       = "#DC2626";
    public static final String DANGER_D     = "#B91C1C";
    // The brand color (names kept from when it was indigo): the institutional
    // guinda, Pantone 1805 C, as in the PDFs and the CSS tokens sibim-marca*.
    public static final String PRIMARY      = "#A2232D";
    public static final String PRIMARY_D    = "#891D26";
    public static final String INDIGO       = "#6F1820";
    public static final String INDIGO_D     = "#5A141A";
    public static final String DEEP_PURPLE  = "#4A1016";
    // INFO, PURPLE and CYAN were the blue, violet and teal dialog accents:
    // guinda steps now, like the rest of the interface. Green, amber and red
    // keep their meaning (done, careful, danger).
    public static final String INFO         = PRIMARY;
    public static final String INFO_D       = PRIMARY_D;
    public static final String PURPLE       = PRIMARY_D;
    public static final String PURPLE_D     = INDIGO;
    public static final String CYAN         = PRIMARY_D;
    public static final String CYAN_D       = INDIGO;
    public static final String SLATE        = "#475569";
    public static final String SLATE_D      = "#334155";
    public static final String SCENE_BG     = "#0F172A";

    /** {r, g, b} of a "#RRGGBB" constant — for the PDFs and spreadsheets, which take numbers. */
    public static int[] rgb(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return new int[] { (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF };
    }
}
