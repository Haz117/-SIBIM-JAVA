package com.sibim.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class FormatUtils {

    private static final Locale LOCALE_MX = new Locale("es", "MX");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(LOCALE_MX);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private FormatUtils() {}

    public static String formatCurrency(BigDecimal value) {
        if (value == null) return "$0.00";
        return CURRENCY_FORMAT.format(value);
    }

    public static String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DATE_FMT);
    }

    public static String formatDateTime(LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(DATETIME_FMT);
    }

    public static String formatStock(int stock, String unidad) {
        return stock + " " + unidad;
    }

    public static long diasHastaVencimiento(LocalDate fechaVencimiento) {
        if (fechaVencimiento == null) return Long.MAX_VALUE;
        return ChronoUnit.DAYS.between(LocalDate.now(), fechaVencimiento);
    }
}
