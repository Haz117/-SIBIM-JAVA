package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.sibim.model.enums.Rol;

import java.io.File;
import java.util.List;

/** The área accounts with their passwords, to print and hand out. Only the
 *  accounts just created carry a password: the others keep theirs. */
public class ReporteCuentasService extends ReporteService {

    public File exportCuentas(List<CuentasAreaService.Cuenta> cuentas) throws Exception {
        File file = tempFile("cuentas_por_area", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            addPdfHeader(doc, "Cuentas de acceso por área", null, null);
            doc.add(new Paragraph("Cada área usa una sola cuenta en todas sus computadoras. Entrega a cada área "
                + "solo su renglón y guarda esta hoja en un lugar seguro.")
                .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA)).setFontSize(9f).setMarginBottom(8));
            Table t = createPdfTable(new String[]{"Área", "Usuario", "Contraseña", "Permisos"},
                new float[]{3.2f, 1.8f, 1.6f, 2.2f});
            for (CuentasAreaService.Cuenta c : cuentas) {
                t.addCell(cell(c.area()));
                t.addCell(cell(c.usuario()));
                t.addCell(cell(c.contrasena() != null ? c.contrasena() : "(ya existía)"));
                t.addCell(cell(permisos(c.rol())));
            }
            doc.add(t);
            addPdfFooter(doc, cuentas.size());
        }
        return file;
    }

    private static String permisos(Rol rol) {
        return switch (rol) {
            case ADMIN -> "Administrador (todo)";
            case SECRETARIO -> "Su secretaría y sus direcciones";
            case DIRECCION -> "Solo su área";
        };
    }
}
