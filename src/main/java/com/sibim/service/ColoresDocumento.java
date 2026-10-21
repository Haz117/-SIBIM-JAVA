package com.sibim.service;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.sibim.util.AppColors;
import org.apache.poi.xssf.usermodel.XSSFColor;

/** The brand color for the PDFs and spreadsheets, taken from {@link AppColors#PRIMARY}
 *  (the guinda, Pantone 1805 C) so a new tone is set in one place for screens and documents. */
final class ColoresDocumento {
    private ColoresDocumento() {}

    static final DeviceRgb MARCA = pdf(AppColors.PRIMARY);

    static DeviceRgb pdf(String hex) {
        int[] c = AppColors.rgb(hex);
        return new DeviceRgb(c[0], c[1], c[2]);
    }

    /** A new one each time: POI ties an XSSFColor to the workbook that uses it. */
    static XSSFColor marcaXlsx() {
        int[] c = AppColors.rgb(AppColors.PRIMARY);
        return new XSSFColor(new byte[] { (byte) c[0], (byte) c[1], (byte) c[2] }, null);
    }
}
