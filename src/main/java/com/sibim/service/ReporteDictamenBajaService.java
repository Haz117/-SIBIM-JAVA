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
import java.util.Map;

/**
 * "Dictamen técnico de baja": the área técnica that inspected a bien (Sistemas
 * for computers, the taller for vehicles…) certifies what it found, whether it
 * can be repaired, whether the baja proceeds and the recommended final
 * destination. It goes attached to the área's solicitud de baja
 * ({@link ReporteSolicitudBajaService}), which lists it among its anexos.
 * Printed with the bien's data and photo, or blank to fill everything by hand.
 * It records nothing: Patrimonio registers the baja in SIBIM afterwards.
 */
public class ReporteDictamenBajaService extends ReporteFormatoBajaBase {

    /** products.tipo_destino_baja → the option it marks on the form (a bien already dado de baja). */
    private static final Map<String, String> DESTINOS = Map.of(
        "DESTRUCCION", "Destrucción",
        "DONACION", "Donación",
        "SUBASTA", "Subasta / enajenación",
        "TRANSFERENCIA_ENTE", "Transferencia a otro ente",
        "OTRO", "Otro");

    public ReporteDictamenBajaService() { super(); }

    /** One bien → one page; several → one PDF with a page per bien. */
    public File exportDictamenBaja(List<Producto> bienes) throws Exception {
        return unaPaginaPorBien("dictamenes_baja", bienes, p -> exportUno(p, false));
    }

    /** The same page with every field empty, for an área to fill in by hand. */
    public File exportDictamenBajaEnBlanco() throws Exception {
        return exportUno(new Producto(), true);
    }

    private File exportUno(Producto p, boolean enBlanco) throws Exception {
        File file = tempFile("dictamen_baja_" + (enBlanco ? "en_blanco" : nombreArchivo(p)), ".pdf");

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        // A non-breaking space isn't blank, so fila() leaves the value cell
        // empty to write on instead of printing "—".
        String vacio = enBlanco ? " " : null;

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            doc.setMargins(24, 36, 22, 36);

            encabezado(doc, "DICTAMEN TÉCNICO DE BAJA",
                "Lo emite el área técnica que revisó el bien · se anexa a la solicitud de baja",
                "(mismo folio de la solicitud)", bold, regular);
            doc.add(spacer(4));

            // ── Código + área usuaria ──
            Table band = new Table(new float[]{1f, 1.4f}).useAllAvailableWidth().setFixedLayout();
            band.addCell(bandCell("CÓDIGO DEL BIEN", enBlanco ? vacio : p.getCodigo(), 17f, bold, regular));
            band.addCell(bandCell("ÁREA USUARIA", enBlanco ? vacio : p.getArea(), 11f, bold, regular));
            doc.add(band);
            doc.add(spacer(6));

            // ── Datos del bien (left) + fotografía (right) ──
            // Fixed layout: in the blank format there's no content to size the columns by.
            Table datosYFoto = new Table(new float[]{1.75f, 1f}).useAllAvailableWidth().setFixedLayout();
            Table datos = new Table(new float[]{1.25f, 2f}).useAllAvailableWidth().setFixedLayout();
            int i = 0;
            fila(datos, "Nombre / descripción", enBlanco ? vacio : p.getNombre(), bold, regular, i++);
            fila(datos, "Marca", enBlanco ? vacio : p.getMarca(), bold, regular, i++);
            fila(datos, "Modelo", enBlanco ? vacio : p.getModelo(), bold, regular, i++);
            fila(datos, "N° de serie", enBlanco ? vacio : p.getNumeroSerie(), bold, regular, i++);
            fila(datos, "Resguardante", enBlanco ? vacio : p.getResguardante(), bold, regular, i++);
            fila(datos, "Fecha de adquisición", enBlanco ? vacio
                : p.getFechaAdquisicion() != null ? FormatUtils.formatDate(p.getFechaAdquisicion()) : null, bold, regular, i++);
            fila(datos, "Valor de adquisición", enBlanco ? vacio
                : FormatUtils.formatCurrency(p.getPrecioCompra()), bold, regular, i++);
            datosYFoto.addCell(new Cell().add(sectionTitle("DATOS DEL BIEN", bold, COLOR_HEADER)).add(datos)
                .setBorder(Border.NO_BORDER).setPaddingRight(10));
            datosYFoto.addCell(fotoCell(p, bold, regular, 150));
            doc.add(datosYFoto);
            doc.add(spacer(6));

            // ── Quién dictamina ──
            doc.add(sectionTitle("ÁREA QUE DICTAMINA", bold, COLOR_HEADER));
            doc.add(new Paragraph("Área técnica: ____________________________________   "
                + "Fecha de revisión: ____/____/______").setFont(regular).setFontSize(9f).setMarginBottom(4));

            // ── Lo que se encontró ──
            doc.add(sectionTitle("ESTADO FÍSICO ENCONTRADO", bold, COLOR_HEADER));
            doc.add(casillas(4, regular, List.of("Bueno", "Regular", "Malo", "Inservible"), null));
            doc.add(spacer(3));
            doc.add(sectionTitle("DIAGNÓSTICO TÉCNICO  (fallas o daños encontrados)", bold, COLOR_HEADER));
            doc.add(renglones(3, 17));
            doc.add(spacer(5));

            doc.add(sectionTitle("CAUSA DE LA BAJA  (marque una)", bold, COLOR_HEADER));
            doc.add(casillas(3, regular, List.of("Daño irreparable", "Obsolescencia tecnológica", "Desgaste por uso",
                                                 "Reparación incosteable", "Siniestro", "Robo / extravío"), null));
            doc.add(new Paragraph("¿Es reparable?   [   ] Sí    [   ] No        "
                + "Costo estimado de reparación: $ ________________").setFont(regular).setFontSize(9f).setMarginTop(3));
            doc.add(spacer(5));

            // ── Dictamen ──
            doc.add(sectionTitle("DICTAMEN", bold, COLOR_HEADER));
            doc.add(new Table(new float[]{1f, 1f}).useAllAvailableWidth()
                .addCell(new Cell().add(new Paragraph("[   ]  PROCEDE LA BAJA").setFont(bold).setFontSize(10f).setFontColor(DARK))
                    .setBackgroundColor(BG_BAND).setPadding(7).setBorder(Border.NO_BORDER))
                .addCell(new Cell().add(new Paragraph("[   ]  NO PROCEDE LA BAJA").setFont(bold).setFontSize(10f).setFontColor(DARK))
                    .setBackgroundColor(BG_BAND).setPadding(7).setBorder(Border.NO_BORDER)));
            doc.add(spacer(4));
            doc.add(sectionTitle("DESTINO FINAL RECOMENDADO", bold, COLOR_HEADER));
            doc.add(casillas(3, regular, List.of("Destrucción", "Donación", "Subasta / enajenación",
                                                 "Transferencia a otro ente", "Otro"),
                enBlanco || p.getTipoDestinoBaja() == null ? null : DESTINOS.get(p.getTipoDestinoBaja())));
            doc.add(spacer(4));

            // ── Observaciones (the committee's resolution when the baja is already registered) ──
            doc.add(sectionTitle("OBSERVACIONES", bold, COLOR_HEADER));
            String resolucion = enBlanco ? null : p.getDictamenBaja();
            if (resolucion != null && !resolucion.isBlank()) {
                doc.add(new Paragraph(resolucion).setFont(regular).setFontSize(9f).setFontColor(DARK).setMarginBottom(2));
                doc.add(renglones(1, 16));
            } else {
                doc.add(renglones(2, 16));
            }
            doc.add(spacer(22));

            doc.add(firmas(bold, regular,
                new String[]{"Elaboró", "Técnico que revisó el bien"},
                new String[]{"Vo. Bo.", "Titular del área técnica"},
                new String[]{"Enterado", "Titular del área usuaria"}));

            doc.add(spacer(6));
            pie(doc, "Este dictamen no da de baja el bien por sí mismo: se anexa a la solicitud de baja y "
                + "Patrimonio registra la baja en SIBIM, que emite el Acta de baja patrimonial.", regular);
        }
        return file;
    }
}
