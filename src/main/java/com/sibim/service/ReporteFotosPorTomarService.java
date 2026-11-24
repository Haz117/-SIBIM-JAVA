package com.sibim.service;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;
import com.sibim.util.ProductoUtils;

import java.io.File;
import java.util.*;

/** Reportes › «Bienes sin foto, para tomarlas»: the list to take to the place, one área per page,
 *  in the order of a visit ({@link FotosDeCarpeta#EN_ORDEN_DE_VISITA}: by ubicación, then número),
 *  with a box to tick. 24-nov-2026: 312 bienes without photo (Bibliotecas 93, Armamento 57, Área
 *  Operativa 53). The number painted on most bienes is still the old inventory's (2,962 without
 *  SIBIM's label), so it goes beside SIBIM's. */
public class ReporteFotosPorTomarService extends ReporteService {

    private static final String[] COLUMNAS = {"Foto", "Número", "Número anterior", "Bien", "Ubicación", "Resguardante", "Serie"};
    private static final String NOTA = "Toma las fotos en el orden de la lista, las de un bien seguidas. En Bienes › Más › "
        + "«Fotos de una carpeta» los bienes salen en este mismo orden y cada foto se propone para el bien que sigue.";

    /** The bienes in force without photo of each área, in the order of the visit. */
    public static Map<String, List<Producto>> porArea(List<Producto> bienes) {
        List<String> organigrama = new ArrayList<>(com.sibim.config.Areas.getAllAreaNames());
        Map<String, List<Producto>> out = new TreeMap<>(Comparator
            .comparingInt((String a) -> organigrama.contains(a) ? organigrama.indexOf(a) : Integer.MAX_VALUE)
            .thenComparing(Comparator.naturalOrder()));
        for (Producto p : bienes)
            if (p.getFechaBaja() == null && FotosDeCarpeta.sinFoto(p))
                out.computeIfAbsent(p.getArea() == null || p.getArea().isBlank() ? "Sin área" : p.getArea(), k -> new ArrayList<>()).add(p);
        out.values().forEach(l -> l.sort(FotosDeCarpeta.EN_ORDEN_DE_VISITA));
        return out;
    }

    /** @return the PDF, or null when none of them lacks a photo */
    public File exportPdf(List<Producto> bienes) throws Exception {
        Map<String, List<Producto>> areas = porArea(bienes);
        if (areas.isEmpty()) return null;
        File file = tempFile("bienes_sin_foto", ".pdf");
        int total = 0;
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.LETTER.rotate())) {
            String folio = generateFolio("FOT");
            boolean primera = true;
            for (var e : areas.entrySet()) {
                if (!primera) doc.add(new AreaBreak());
                primera = false;
                addPdfHeader(doc, "Bienes sin foto — " + e.getKey(), null, null, folio);
                doc.add(new Paragraph(FormatUtils.plural(e.getValue().size(), "bien sin foto", "bienes sin foto")
                    + ", por ubicación y número").setFontSize(10).setMarginTop(4).setMarginBottom(2));
                doc.add(new Paragraph(NOTA).setFontSize(8).setFontColor(new DeviceRgb(107, 114, 128)).setMarginBottom(6));
                Table t = createPdfTable(COLUMNAS, new float[]{0.55f, 1.25f, 1.6f, 4.2f, 2.2f, 2.1f, 1.5f});
                int i = 0;
                for (Producto p : e.getValue()) {
                    boolean alt = (i++ % 2) == 1;
                    // A box to tick (the standard fonts have no ☐).
                    t.addCell(fila("", alt).setKeepTogether(true).add(new com.itextpdf.layout.element.Div()
                        .setWidth(9).setHeight(9).setBorder(new com.itextpdf.layout.borders.SolidBorder(0.8f))));
                    t.addCell(fila(Objects.requireNonNullElse(p.getCodigo(), ""), alt).setKeepTogether(true));
                    t.addCell(fila(Objects.requireNonNullElse(ProductoUtils.inventarioAnterior(p), ""), alt).setKeepTogether(true));
                    t.addCell(fila(Objects.requireNonNullElse(p.getNombre(), ""), alt).setKeepTogether(true));
                    t.addCell(fila(Objects.requireNonNullElse(p.getUbicacion(), "—"), alt).setKeepTogether(true));
                    t.addCell(fila(Objects.requireNonNullElse(p.getResguardante(), "—"), alt).setKeepTogether(true));
                    t.addCell(fila(Objects.requireNonNullElse(p.getNumeroSerie(), ""), alt).setKeepTogether(true));
                }
                doc.add(t);
                total += e.getValue().size();
            }
            addPdfFooter(doc, total, folio);
        }
        return numerarPaginas(file);
    }
}
