package com.sibim.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** The XML of a factura (made-up data, the shapes of the SAT's CFDI). */
public class FacturaXmlTest {

    public static final String CFDI_40 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4" xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
            Version="4.0" Serie="A" Folio="667" Fecha="2023-12-11T10:15:00" SubTotal="5237.94" Descuento="100.00"
            Moneda="MXN" Total="5960.01" TipoDeComprobante="I">
          <cfdi:Emisor Rfc="AAA010101AAA" Nombre="Papelería de Ejemplo  SA de CV" RegimenFiscal="601"/>
          <cfdi:Receptor Rfc="XAXX010101000" Nombre="MUNICIPIO DE EJEMPLO" UsoCFDI="G03"/>
          <cfdi:Conceptos>
            <cfdi:Concepto ClaveProdServ="56101504" Cantidad="2" ClaveUnidad="H87" Descripcion="SILLA SEMI EJECUTIVA RESPALDO DE MALLA"
                ValorUnitario="2068.97" Importe="4137.94" ObjetoImp="02">
              <cfdi:Impuestos><cfdi:Traslados>
                <cfdi:Traslado Base="4137.94" Impuesto="002" TipoFactor="Tasa" TasaOCuota="0.160000" Importe="662.07"/>
              </cfdi:Traslados></cfdi:Impuestos>
            </cfdi:Concepto>
            <cfdi:Concepto ClaveProdServ="56101703" Cantidad="1" ClaveUnidad="H87" Descripcion="ESCRITORIO EJECUTIVO EN L"
                ValorUnitario="1100.00" Importe="1100.00" Descuento="100.00" ObjetoImp="02">
              <cfdi:Impuestos><cfdi:Traslados>
                <cfdi:Traslado Base="1000.00" Impuesto="002" TipoFactor="Tasa" TasaOCuota="0.160000" Importe="160.00"/>
              </cfdi:Traslados></cfdi:Impuestos>
            </cfdi:Concepto>
          </cfdi:Conceptos>
          <cfdi:Complemento>
            <tfd:TimbreFiscalDigital Version="1.1" UUID="a1b2c3d4-0000-4000-8000-000000000001" FechaTimbrado="2023-12-11T10:16:00"/>
          </cfdi:Complemento>
        </cfdi:Comprobante>
        """;

    static final String CFDI_32 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/3" version="3.2" serie="B" folio="87" fecha="2016-05-03T09:00:00"
            subTotal="1000.00" total="1160.00">
          <cfdi:Emisor rfc="AAA010101AAA" nombre="Muebles de Ejemplo"/>
          <cfdi:Conceptos><cfdi:Concepto cantidad="4" unidad="PZA" descripcion="SILLA PLEGABLE" valorUnitario="250.00" importe="1000.00"/></cfdi:Conceptos>
          <cfdi:Impuestos totalImpuestosTrasladados="160.00"><cfdi:Traslados>
            <cfdi:Traslado impuesto="IVA" tasa="16.00" importe="160.00"/>
          </cfdi:Traslados></cfdi:Impuestos>
          <cfdi:Complemento><tfd:TimbreFiscalDigital xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
            UUID="B9919A80-CF6F-11ED-AA71-AD3327DCEBEF"/></cfdi:Complemento>
        </cfdi:Comprobante>
        """;

    static FacturaXml.Factura leer(String xml) throws Exception {
        return FacturaXml.leer(new ByteArrayInputStream(xml.strip().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void cfdi40_folioFechaProveedorYCostoConIva() throws Exception {
        var f = leer(CFDI_40);
        assertEquals("A1B2C3D4-0000-4000-8000-000000000001", f.uuid());
        assertEquals("A 667", f.numero());
        assertEquals(LocalDate.of(2023, 12, 11), f.fecha());
        assertEquals("Papelería de Ejemplo SA de CV", f.emisor());
        assertTrue(f.enPesos());
        assertEquals(2, f.conceptos().size());
        assertEquals(new BigDecimal("2400.01"), f.conceptos().get(0).costoPorPieza(), "(4,137.94 + 662.07) / 2, con el IVA de la factura");
        assertEquals(new BigDecimal("1160.00"), f.conceptos().get(1).costoPorPieza(), "con su descuento");
    }

    @Test
    void cfdi32_conAtributosEnMinusculas_yElIvaDeTodaLaFactura() throws Exception {
        var f = leer(CFDI_32);
        assertEquals("B9919A80-CF6F-11ED-AA71-AD3327DCEBEF", f.uuid());
        assertEquals("B 87", f.numero());
        assertEquals(LocalDate.of(2016, 5, 3), f.fecha());
        assertEquals("Muebles de Ejemplo", f.emisor());
        assertEquals(new BigDecimal("290.00"), f.conceptos().get(0).costoPorPieza());
    }

    @Test
    void enDolares_noEsEnPesos() throws Exception {
        assertFalse(leer(CFDI_40.replace("Moneda=\"MXN\"", "Moneda=\"USD\"")).enPesos());
    }

    @Test
    void loQueNoEsFactura_loDice() {
        var e = assertThrows(FacturaXml.NoEsFactura.class, () -> leer("<factura numero=\"1\"/>"));
        assertTrue(e.getMessage().contains("no es una factura"));
        e = assertThrows(FacturaXml.NoEsFactura.class, () -> leer(CFDI_40.replaceAll("(?s)<cfdi:Complemento>.*</cfdi:Complemento>", "")));
        assertTrue(e.getMessage().contains("timbre"));
        assertThrows(FacturaXml.NoEsFactura.class, () -> leer("esto no es XML"));
    }

    /** A file that asks for another file of the PC (XXE) is not read. */
    @Test
    void noLeeEntidadesExternas() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE c [<!ENTITY e SYSTEM \"file:///c:/windows/win.ini\">]>"
            + "<cfdi:Comprobante xmlns:cfdi=\"http://www.sat.gob.mx/cfd/4\" Folio=\"&e;\"/>";
        assertThrows(FacturaXml.NoEsFactura.class, () -> leer(xxe));
    }
}
