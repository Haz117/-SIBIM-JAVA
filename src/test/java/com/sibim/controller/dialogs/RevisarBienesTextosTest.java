package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** What «Revisar mis bienes» writes under each bien to recognise it, and what it lacks. Without windows. */
class RevisarBienesTextosTest {

    @Test
    void detalle_soloLoQueTiene_ySinElSinMarca() {
        Producto p = new Producto();
        p.setMarca("HP"); p.setModelo("ProDesk 400"); p.setNumeroSerie("SERIE-FICTICIA-1"); p.setUbicacion(" Oficina 2 ");
        assertEquals("HP · ProDesk 400 · Serie SERIE-FICTICIA-1 · Oficina 2", RevisarBienesDialog.detalle(p));
        Producto q = new Producto();
        q.setMarca("SIN MARCA"); q.setNumeroSerie("S/N");
        assertEquals("", RevisarBienesDialog.detalle(q), "«SIN MARCA» y «S/N» no dicen nada del bien");
    }

    @Test
    void falta_enPalabras() {
        Producto p = new Producto();
        assertEquals("foto, ubicación, estado físico", RevisarBienesDialog.falta(p));
        p.setFotoUrl("f.jpg"); p.setUbicacion("Bodega"); p.setEstadoFisico("BUENO");
        assertEquals("", RevisarBienesDialog.falta(p));
    }
}
