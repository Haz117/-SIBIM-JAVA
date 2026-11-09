package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import com.sibim.model.Solicitud;
import com.sibim.model.enums.Rol;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The área accounts with their passwords, to print and hand out. Only the
 * accounts just created (or with a new password) carry one: the others keep theirs.
 *
 * <p>9-nov-2026: it was one table with every área's password and «entrega a cada
 * área solo su renglón» — with 57 áreas that meant cutting the sheet with scissors,
 * or every área seeing the others' passwords. Now each área has a page of its own,
 * to hand over as it is: its usuario and contraseña, what it can see, and what to
 * do the first day. With more than one, a first page is Patrimonio's control,
 * with a column to note who received each sheet.
 */
public class ReporteCuentasService extends ReporteService {

    private static final DeviceRgb GUINDA = new DeviceRgb(162, 35, 45);
    private static final DeviceRgb TEXTO = new DeviceRgb(31, 41, 55);
    private static final DeviceRgb GRIS = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb FONDO = new DeviceRgb(252, 240, 241);

    /** What an área account does the first day, in order (Patrimonio's own account has none). */
    static final List<String> PRIMER_DIA = List.of(
        "Abre SIBIM en tu computadora. Si no lo tiene, pídelo a " + Solicitud.ENTREGAR_FORMATO_EN + ".",
        "Escribe el usuario y la contraseña de arriba. SIBIM te pide tu nombre: escríbelo, así lo que hagas queda a tu nombre.",
        "En tu Inicio verás tus tres pasos. Da clic en «Revisar mis bienes».",
        "En cada bien di «Sí es» si está en tu área, o «No es» y escribe por qué (por ejemplo: «lo tiene otra área», «no lo encontramos»).",
        "Con «Completar» ponle foto, ubicación y estado físico.",
        "Cuando Patrimonio te entregue las etiquetas, pégalas y marca «Ya la tiene».");

    public File exportCuentas(List<CuentasAreaService.Cuenta> cuentas) throws Exception {
        File file = tempFile("cuentas_por_area", ".pdf");
        // Only the accounts with a password to hand out (created or changed now):
        // the ones that already existed only filled the sheet with "(ya existía)".
        List<CuentasAreaService.Cuenta> conContrasena = cuentas.stream().filter(c -> c.contrasena() != null).toList();
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            boolean control = conContrasena.size() > 1;
            if (control) controlDePatrimonio(doc, conContrasena);
            for (int i = 0; i < conContrasena.size(); i++) {
                if (control || i > 0) doc.add(new AreaBreak());
                hojaDelArea(doc, conContrasena.get(i));
            }
            if (conContrasena.isEmpty()) {
                addPdfHeader(doc, "Cuentas de acceso por área", null, null);
                doc.add(new Paragraph("No se creó ninguna cuenta nueva.").setFontSize(10));
            }
        }
        return file;
    }

    /** Page 1: every account at once, for Patrimonio to keep and to note who took each sheet. */
    private void controlDePatrimonio(Document doc, List<CuentasAreaService.Cuenta> cuentas) throws IOException {
        addPdfHeader(doc, "Cuentas de acceso por área — control de Patrimonio", null, null);
        doc.add(new Paragraph("Esta hoja es solo para Patrimonio: guárdala en un lugar seguro. A cada área entrégale "
                + "su propia hoja (una por página, a partir de la siguiente), con su usuario, su contraseña y qué hacer "
                + "el primer día. Anota aquí quién la recibió.")
            .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA)).setFontSize(9f).setMarginBottom(8));
        Table t = createPdfTable(new String[]{"Área", "Usuario", "Contraseña", "Permisos", "Recibió (nombre y firma)"},
            new float[]{2.5f, 1.6f, 2.3f, 1.6f, 2.2f});   // the password must fit on one line
        for (CuentasAreaService.Cuenta c : cuentas) {
            t.addCell(cell(c.area()));
            t.addCell(cell(c.usuario()));
            t.addCell(cell(c.contrasena()));
            t.addCell(cell(permisos(c.rol())));
            t.addCell(cell(" ").setMinHeight(22));
        }
        doc.add(t);
        addPdfFooter(doc, cuentas.size());
    }

    /** The page an área receives: whose account, the two things to type, and what to do. */
    private void hojaDelArea(Document doc, CuentasAreaService.Cuenta c) throws IOException {
        PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont negrita = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont fija = PdfFontFactory.createFont(StandardFonts.COURIER_BOLD);
        addPdfHeader(doc, "Tu cuenta de SIBIM", null, null);

        doc.add(new Paragraph(c.area()).setFont(negrita).setFontSize(17).setFontColor(GUINDA).setMarginTop(4).setMarginBottom(0));
        doc.add(new Paragraph("Con esta cuenta ves " + queVes(c.rol())).setFont(normal).setFontSize(10).setFontColor(GRIS).setMarginBottom(12));

        Table datos = new Table(UnitValue.createPercentArray(new float[]{1.1f, 3f})).useAllAvailableWidth();
        datos.setBackgroundColor(FONDO).setBorder(new SolidBorder(new DeviceRgb(242, 188, 192), 1f));
        for (String[] fila : new String[][]{{"Usuario", c.usuario()}, {"Contraseña", c.contrasena()}}) {
            datos.addCell(new Cell().add(new Paragraph(fila[0]).setFont(normal).setFontSize(11).setFontColor(GRIS))
                .setBorder(Border.NO_BORDER).setPaddingLeft(14).setPaddingTop(9).setPaddingBottom(9));
            datos.addCell(new Cell().add(new Paragraph(fila[1]).setFont(fija).setFontSize(17).setFontColor(TEXTO))
                .setBorder(Border.NO_BORDER).setPaddingTop(7).setPaddingBottom(7));
        }
        doc.add(datos);
        doc.add(new Paragraph("Escríbelos tal cual: las mayúsculas y los guiones cuentan.")
            .setFont(normal).setFontSize(9).setFontColor(GRIS).setMarginTop(4));

        doc.add(new Paragraph("La cuenta es de toda el área: todos la usan, en cualquiera de sus computadoras. No la compartas "
                + "fuera del área. Si se pierde esta hoja o la contraseña la sabe alguien que ya no está, Patrimonio te da una nueva.")
            .setFont(normal).setFontSize(10).setFontColor(TEXTO).setMarginTop(10));

        if (c.rol() != Rol.ADMIN) {
            doc.add(new Paragraph("Qué hacer el primer día").setFont(negrita).setFontSize(13).setFontColor(GUINDA)
                .setMarginTop(16).setMarginBottom(4));
            Table pasos = new Table(UnitValue.createPercentArray(new float[]{0.35f, 9f})).useAllAvailableWidth();
            for (int i = 0; i < PRIMER_DIA.size(); i++) {
                pasos.addCell(new Cell().add(new Paragraph(String.valueOf(i + 1)).setFont(negrita).setFontSize(12).setFontColor(GUINDA))
                    .setBorder(Border.NO_BORDER).setPaddingTop(4));
                pasos.addCell(new Cell().add(new Paragraph(PRIMER_DIA.get(i)).setFont(normal).setFontSize(11).setFontColor(TEXTO))
                    .setBorder(Border.NO_BORDER).setPaddingTop(4).setPaddingBottom(4));
            }
            doc.add(pasos);
            doc.add(new Paragraph("Si te equivocas, cada respuesta se puede deshacer. Patrimonio ve cómo va tu área y te avisa "
                    + "si algo hace falta.")
                .setFont(normal).setFontSize(10).setFontColor(TEXTO).setMarginTop(10));
        }
        doc.add(new Paragraph("Dudas: " + Solicitud.ENTREGAR_FORMATO_EN + " · " + orgName())
            .setFont(normal).setFontSize(9).setFontColor(GRIS).setMarginTop(18)
            .setBorderTop(new SolidBorder(new DeviceRgb(209, 213, 219), 0.5f)).setPaddingTop(4));
    }

    /** The same, told to the área. */
    static String queVes(Rol rol) {
        return switch (rol) {
            case ADMIN -> "todo el inventario (administrador).";
            case SECRETARIO -> "los bienes de tu secretaría y de sus direcciones.";
            case DIRECCION -> "los bienes de tu área.";
        };
    }

    static String permisos(Rol rol) {
        return switch (rol) {
            case ADMIN -> "Administrador (todo)";
            case SECRETARIO -> "Su secretaría y sus direcciones";
            case DIRECCION -> "Solo su área";
        };
    }
}
