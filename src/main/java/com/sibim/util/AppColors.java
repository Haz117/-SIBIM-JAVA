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
}
