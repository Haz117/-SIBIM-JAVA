package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.session.SessionManager;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * What "Transferir arrastrando" in Movimientos does with the bienes dropped on an
 * área, in the order Patrimonio would do it by hand: each one leaves the resguardo
 * that names who holds it (a transfer can't contradict a signed document), is
 * transferred (taking the next free número of the destination área), and — when a
 * new resguardante is given — all of them go into one new resguardo in that área.
 * A bien that can't be moved is reported and the rest go on.
 */
public class TransferenciaRapidaService {

    public record Resultado(List<Movimiento> hechos, List<String> errores, Resguardo resguardo) {}

    /** Why a bien can't be moved at all — checked before touching its resguardo, so a
     *  lent bien isn't left out of its resguardo and still in its old área. */
    interface Impedimento { Optional<String> de(String productoId) throws SQLException; }

    private final MovimientoService movimientos;
    private final ResguardoService resguardos;
    private final Impedimento impedimento;

    public TransferenciaRapidaService() {
        this(new MovimientoService(), new ResguardoService(), ProductoService::prestamoOComodato);
    }

    TransferenciaRapidaService(MovimientoService movimientos, ResguardoService resguardos, Impedimento impedimento) {
        this.movimientos = movimientos;
        this.resguardos = resguardos;
        this.impedimento = impedimento;
    }

    public Resultado transferir(List<Producto> bienes, String destino, String motivo,
                                String nuevoResguardante, String cargo) {
        if (!SessionManager.isAdmin())
            throw new SecurityException(MovimientoService.SOLO_ADMIN_MOVIMIENTOS);
        if (motivo == null || motivo.isBlank())
            throw new IllegalArgumentException("Escribe el motivo de la transferencia");
        List<Movimiento> hechos = new ArrayList<>();
        List<Producto> movidos = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        for (Producto p : bienes) {
            String bien = p.getCodigo() + " " + p.getNombre();
            try {
                Optional<String> impide = impedimento.de(p.getId());
                if (impide.isPresent()) { errores.add(bien + ": " + impide.get()); continue; }
                Optional<Resguardo> vigente = resguardos.getByProductoId(p.getId()).stream()
                    .filter(Resguardo::isActivo).findFirst();
                if (vigente.isPresent())
                    resguardos.quitarBien(vigente.get(), renglon(p, p.getCodigo(), p.getArea()),
                        "transferencia a " + destino + ": " + motivo.trim());
                hechos.add(movimientos.registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, p.getStockActual(),
                    motivo.trim(), null, destino));
                movidos.add(p);
            } catch (Exception e) {
                errores.add(bien + ": " + (e.getMessage() != null ? e.getMessage() : "error desconocido"));
            }
        }

        Resguardo nuevo = null;
        if (nuevoResguardante != null && !nuevoResguardante.isBlank() && !movidos.isEmpty()) {
            List<ResguardoItem> items = new ArrayList<>();
            for (int i = 0; i < movidos.size(); i++) {
                String codigo = hechos.get(i).getCodigoNuevo() != null ? hechos.get(i).getCodigoNuevo() : movidos.get(i).getCodigo();
                items.add(renglon(movidos.get(i), codigo, destino));
            }
            LinkedHashSet<String> origenes = new LinkedHashSet<>();
            movidos.forEach(p -> origenes.add(p.getArea()));
            String observaciones = "Recibidos por transferencia de " + String.join(", ", origenes) + " el "
                + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ": " + motivo.trim() + ".";
            try {
                nuevo = resguardos.crear(nuevoResguardante.trim(), cargo, destino, items, observaciones);
            } catch (Exception e) {
                errores.add("No se pudo crear el resguardo de " + nuevoResguardante.trim() + ": "
                    + (e.getMessage() != null ? e.getMessage() : "error desconocido"));
            }
        }
        return new Resultado(hechos, errores, nuevo);
    }

    private static ResguardoItem renglon(Producto p, String codigo, String area) {
        ResguardoItem it = new ResguardoItem();
        it.setProductoId(p.getId());
        it.setProductoNombre(p.getNombre());
        it.setProductoCodigo(codigo);
        it.setArea(area);
        it.setNumeroSerie(p.getNumeroSerie());
        it.setCantidad(p.getStockActual());
        it.setValorUnitario(p.getPrecioCompra());
        return it;
    }
}
