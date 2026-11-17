package com.sibim.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The XML of a factura (CFDI 3.2, 3.3 or 4.0), read for the bien's form: its folio fiscal (the
 * UUID of the timbre), número (serie and folio), fecha, emisor and each concepto with its costo
 * per piece including IVA. From 13 to 17-nov-2026 the folio fiscal was typed by hand 308 times
 * and 34 were left wrong. The file is never trusted: no DTD, no external entities.
 */
public final class FacturaXml {

    private FacturaXml() {}

    /** One line of the factura. */
    public record Concepto(String descripcion, BigDecimal cantidad, BigDecimal importe, BigDecimal descuento,
                           BigDecimal tasaIva, BigDecimal ivaImporte) {
        /** What one piece cost with its IVA (as the inventory captures the costo), or null without a quantity:
         *  the IVA the factura charged on the line, or its rate when it doesn't say the amount. */
        public BigDecimal costoPorPieza() {
            if (cantidad == null || cantidad.signum() <= 0 || importe == null) return null;
            BigDecimal neto = importe.subtract(descuento == null ? BigDecimal.ZERO : descuento);
            BigDecimal iva = ivaImporte != null ? ivaImporte
                : neto.multiply(tasaIva == null ? BigDecimal.ZERO : tasaIva);
            return neto.add(iva).divide(cantidad, 2, RoundingMode.HALF_UP);
        }
    }

    public record Factura(String uuid, String numero, LocalDate fecha, String emisor, String rfcEmisor,
                          String moneda, BigDecimal total, List<Concepto> conceptos) {
        /** In pesos (MXN, or no currency said): only then is a costo taken from it. */
        public boolean enPesos() {
            return moneda == null || moneda.isBlank() || moneda.trim().equalsIgnoreCase("MXN");
        }
    }

    /** Not a factura that can be read (not XML, not a CFDI, no timbre). */
    public static final class NoEsFactura extends Exception {
        public NoEsFactura(String mensaje) { super(mensaje); }
    }

    public static Factura leer(Path archivo) throws IOException, NoEsFactura {
        try (InputStream in = Files.newInputStream(archivo)) {
            return leer(in);
        }
    }

    public static Factura leer(InputStream in) throws IOException, NoEsFactura {
        Document doc;
        try {
            doc = constructor().parse(in);
        } catch (org.xml.sax.SAXException e) {
            throw new NoEsFactura("El archivo no es un XML que se pueda leer");
        }
        Element raiz = doc.getDocumentElement();
        if (raiz == null || !"Comprobante".equals(local(raiz)))
            throw new NoEsFactura("El XML no es una factura (CFDI): no tiene «Comprobante»");
        Element timbre = primero(raiz, "TimbreFiscalDigital");
        String uuid = timbre == null ? null : atributo(timbre, "UUID");
        if (uuid == null || uuid.isBlank())
            throw new NoEsFactura("La factura no trae su timbre fiscal (UUID): ¿es el XML timbrado?");
        String serie = atributo(raiz, "Serie"), folio = atributo(raiz, "Folio");
        String numero = ((serie == null ? "" : serie.trim()) + " " + (folio == null ? "" : folio.trim())).trim();
        String fecha = atributo(raiz, "Fecha");
        Element emisor = primero(raiz, "Emisor");
        List<Concepto> conceptos = new ArrayList<>();
        NodeList lista = raiz.getElementsByTagNameNS("*", "Concepto");
        for (int i = 0; i < lista.getLength(); i++) {
            Element c = (Element) lista.item(i);
            Element iva = trasladoIva(c);
            BigDecimal tasa = null, montoIva = null;
            if (iva != null) {
                tasa = numero(atributo(iva, "TasaOCuota"));
                if (tasa == null) tasa = numero(atributo(iva, "tasa"));
                if (tasa != null && tasa.compareTo(BigDecimal.ONE) > 0) tasa = tasa.movePointLeft(2);   // 3.2 says «16.00»
                // In 4.0/3.3 the line's own Traslado has its Importe; a 3.2 one is the whole factura's.
                if (iva.getParentNode() != null && iva.getParentNode().getParentNode() != null
                        && "Impuestos".equals(local(iva.getParentNode().getParentNode()))
                        && iva.getParentNode().getParentNode().getParentNode() == c)
                    montoIva = numero(atributo(iva, "Importe"));
            }
            conceptos.add(new Concepto(limpio(atributo(c, "Descripcion")), numero(atributo(c, "Cantidad")),
                numero(atributo(c, "Importe")), numero(atributo(c, "Descuento")), tasa, montoIva));
        }
        // CFDI 3.2: the IVA is said once for the whole factura, at its rate.
        if (conceptos.stream().allMatch(x -> x.tasaIva() == null)) {
            Element iva = trasladoIva(raiz);
            BigDecimal tasa = iva == null ? null : numero(atributo(iva, "tasa"));
            if (tasa == null && iva != null) tasa = numero(atributo(iva, "TasaOCuota"));
            if (tasa != null && tasa.compareTo(BigDecimal.ONE) > 0) tasa = tasa.movePointLeft(2);
            if (tasa != null) {
                BigDecimal t = tasa;
                conceptos.replaceAll(x -> new Concepto(x.descripcion(), x.cantidad(), x.importe(), x.descuento(), t, null));
            }
        }
        return new Factura(uuid.trim().toUpperCase(Locale.ROOT), numero.isEmpty() ? null : numero,
            fecha != null && fecha.trim().length() >= 10 ? LocalDate.parse(fecha.trim().substring(0, 10)) : null,
            emisor == null ? null : limpio(atributo(emisor, "Nombre")),
            emisor == null ? null : limpio(atributo(emisor, "Rfc")),
            limpio(atributo(raiz, "Moneda")), numero(atributo(raiz, "Total")), conceptos);
    }

    /** The IVA traslado under an element (Impuesto 002, or the 3.2 «IVA»), or null. */
    private static Element trasladoIva(Element bajo) {
        NodeList t = bajo.getElementsByTagNameNS("*", "Traslado");
        for (int i = 0; i < t.getLength(); i++) {
            Element e = (Element) t.item(i);
            String imp = atributo(e, "Impuesto");
            if (imp != null && (imp.trim().equals("002") || imp.trim().equalsIgnoreCase("IVA"))) return e;
        }
        return null;
    }

    private static DocumentBuilder constructor() throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            return b;
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new IOException("No se pudo preparar el lector de XML", e);
        }
    }

    private static String local(Node n) {
        return n.getLocalName() != null ? n.getLocalName() : n.getNodeName().replaceFirst("^.*:", "");
    }

    private static Element primero(Element raiz, String nombre) {
        NodeList l = raiz.getElementsByTagNameNS("*", nombre);
        return l.getLength() > 0 ? (Element) l.item(0) : null;
    }

    /** An attribute by its name in any case: CFDI 3.2 wrote «fecha», «folio», «nombre». */
    private static String atributo(Element e, String nombre) {
        if (e.hasAttribute(nombre)) return e.getAttribute(nombre);
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if (local(a).equalsIgnoreCase(nombre)) return a.getNodeValue();
        }
        return null;
    }

    private static BigDecimal numero(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String limpio(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }
}
