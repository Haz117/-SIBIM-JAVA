package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;

import java.io.File;
import java.util.List;

/**
 * "Solicitud de baja de bien patrimonial": the form an área fills in, signs and
 * sends to Recursos Materiales y Patrimonio to ask for a bien to be written off.
 * Same look as the ficha técnica (datos del bien + its photo), plus the parts
 * the área completes by hand: motivo, estado físico, documentos anexos, firmas.
 * It records nothing and burns no folio — Patrimonio assigns one on receipt and
 * the baja itself is still registered in SIBIM (which issues the Acta de baja).
 */
public class ReporteSolicitudBajaService extends ReporteFormatoBajaBase {

    public ReporteSolicitudBajaService() { super(); }

    /** One bien → one page; several → one PDF with a page per bien. */
    public File exportSolicitudBaja(List<Producto> bienes) throws Exception {
        return unaPaginaPorBien("solicitudes_baja", bienes, this::exportUna);
    }

    private File exportUna(Producto p) throws Exception {
        File file = tempFile("solicitud_baja_" + nombreArchivo(p), ".pdf");

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            doc.setMargins(26, 36, 26, 36);

            encabezado(doc, "SOLICITUD DE BAJA DE BIEN PATRIMONIAL",
                "Para entregar a Recursos Materiales y Patrimonio", "(folio lo asigna Patrimonio)", bold, regular);
            doc.add(spacer(5));

            // ── Código + área solicitante ──
            Table band = new Table(new float[]{1f, 1.4f}).useAllAvailableWidth();
            band.addCell(bandCell("CÓDIGO DEL BIEN", p.getCodigo(), 17f, bold, regular));
            band.addCell(bandCell("ÁREA SOLICITANTE", p.getArea(), 11f, bold, regular));
            doc.add(band);
            doc.add(spacer(8));

            // ── Datos del bien (left) + fotografía (right) ──
            Table datosYFoto = new Table(new float[]{1.75f, 1f}).useAllAvailableWidth();
            Table datos = new Table(new float[]{1.25f, 2f}).useAllAvailableWidth();
            int i = 0;
            fila(datos, "Nombre / descripción", p.getNombre(), bold, regular, i++);
            fila(datos, "Categoría", p.getCategoriaNombre(), bold, regular, i++);
            fila(datos, "Marca", p.getMarca(), bold, regular, i++);
            fila(datos, "Modelo", p.getModelo(), bold, regular, i++);
            fila(datos, "N° de serie", p.getNumeroSerie(), bold, regular, i++);
            fila(datos, "Ubicación", p.getUbicacion(), bold, regular, i++);
            fila(datos, "Resguardante", p.getResguardante(), bold, regular, i++);
            fila(datos, "Fecha de adquisición",
                p.getFechaAdquisicion() != null ? FormatUtils.formatDate(p.getFechaAdquisicion()) : null, bold, regular, i++);
            fila(datos, "Valor de adquisición", FormatUtils.formatCurrency(p.getPrecioCompra()), bold, regular, i++);
            if (p.getValorDepreciado() != null)
                fila(datos, "Valor actual (depreciado)", FormatUtils.formatCurrency(p.getValorDepreciado())
                    + (p.getPorcentajeDepreciado() != null ? "  (" + p.getPorcentajeDepreciado() + "%)" : ""),
                    bold, regular, i++);
            datosYFoto.addCell(new Cell().add(sectionTitle("DATOS DEL BIEN", bold, COLOR_HEADER)).add(datos)
                .setBorder(Border.NO_BORDER).setPaddingRight(10));
            datosYFoto.addCell(fotoCell(p, bold, regular, 190));
            doc.add(datosYFoto);
            doc.add(spacer(8));

            // ── Motivo ──
            doc.add(sectionTitle("MOTIVO DE LA SOLICITUD  (marque uno)", bold, COLOR_HEADER));
            doc.add(casillas(3, regular, List.of("Inservible / descompuesto", "Obsoleto", "Desgaste por uso",
                                                 "Extravío", "Robo (anexar denuncia)", "Siniestro"), null));
            doc.add(new Paragraph("Otro: ________________________________________________________________________")
                .setFont(regular).setFontSize(9f).setMarginTop(2));
            doc.add(spacer(6));

            // ── Estado actual (to be written by hand) ──
            doc.add(sectionTitle("DESCRIPCIÓN DEL ESTADO ACTUAL DEL BIEN", bold, COLOR_HEADER));
            doc.add(renglones(3, 18));
            doc.add(spacer(8));

            // ── Documentos anexos ──
            doc.add(sectionTitle("DOCUMENTOS QUE SE ANEXAN", bold, COLOR_HEADER));
            doc.add(casillas(2, regular, List.of("Dictamen técnico del bien", "Fotografías del estado actual",
                                                 "Copia del resguardo", "Acta circunstanciada / denuncia (robo o extravío)"), null));
            doc.add(spacer(26));

            doc.add(firmas(bold, regular,
                new String[]{"Solicita", "Titular del área"},
                new String[]{"Entrega", "Resguardante del bien"},
                new String[]{"Recibe", "Recursos Materiales y Patrimonio"}));

            doc.add(spacer(8));
            pie(doc, "Esta solicitud no da de baja el bien por sí misma: Patrimonio la revisa y, "
                + "si procede, registra la baja en SIBIM, que emite el Acta de baja patrimonial.", regular);
        }
        return file;
    }
}
