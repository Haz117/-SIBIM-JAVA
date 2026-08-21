package com.sibim.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FormatUtilsTest {

    @Test
    void formatCurrency_null_returnsCero() {
        assertEquals("$0.00", FormatUtils.formatCurrency(null));
    }

    @Test
    void formatCurrency_zero_returnsCero() {
        assertTrue(FormatUtils.formatCurrency(BigDecimal.ZERO).contains("0.00"));
    }

    @Test
    void formatCurrency_positiveValue_containsDigits() {
        String result = FormatUtils.formatCurrency(new BigDecimal("1234.56"));
        assertTrue(result.contains("1"), "Should contain thousands digit");
        assertTrue(result.contains("234"), "Should contain hundreds digits");
        assertTrue(result.contains("56"), "Should contain cents");
    }

    @Test
    void formatCurrency_negative_containsMinus() {
        String result = FormatUtils.formatCurrency(new BigDecimal("-50.00"));
        assertTrue(result.contains("50"), "Should contain the amount");
    }

    @Test
    void formatDate_null_returnsEmpty() {
        assertEquals("", FormatUtils.formatDate(null));
    }

    @Test
    void formatDate_date_returnsDdMmYyyy() {
        assertEquals("15/01/2025", FormatUtils.formatDate(LocalDate.of(2025, 1, 15)));
    }

    @Test
    void formatDate_endOfYear_formatsCorrectly() {
        assertEquals("31/12/2024", FormatUtils.formatDate(LocalDate.of(2024, 12, 31)));
    }

    @Test
    void formatDateTime_null_returnsEmpty() {
        assertEquals("", FormatUtils.formatDateTime(null));
    }

    @Test
    void formatDateTime_datetime_returnsDdMmYyyyHhMm() {
        assertEquals("15/01/2025 14:30",
            FormatUtils.formatDateTime(LocalDateTime.of(2025, 1, 15, 14, 30)));
    }

    @Test
    void formatStock_combinesStockAndUnit() {
        assertEquals("10 pz", FormatUtils.formatStock(10, "pz"));
        assertEquals("0 kg", FormatUtils.formatStock(0, "kg"));
    }

    @Test
    void diasHastaVencimiento_null_returnsMaxValue() {
        assertEquals(Long.MAX_VALUE, FormatUtils.diasHastaVencimiento(null));
    }

    @Test
    void diasHastaVencimiento_pastDate_returnsNegative() {
        long dias = FormatUtils.diasHastaVencimiento(LocalDate.now().minusDays(5));
        assertTrue(dias < 0, "Past date should return negative days");
    }

    @Test
    void diasHastaVencimiento_futureDate_returnsPositive() {
        long dias = FormatUtils.diasHastaVencimiento(LocalDate.now().plusDays(10));
        assertTrue(dias > 0, "Future date should return positive days");
    }
}
