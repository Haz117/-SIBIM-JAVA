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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What "Transferir arrastrando" in Movimientos does with the bienes dropped on an
 * área, in the order Patrimonio would do it by hand: each one leaves the resguardo
 * that names who holds it (a transfer can't contradict a signed document), is
 * transferred (taking the next free número of the destination área), and — when a
 * new resguardante is given — all of them go into one new resguardo in that área.
 * A bien that can't be moved is reported and the rest go on.
 */
public class TransferenciaRapidaService {

    /** @param salidas by bien id, the resguardo each moved bien left — to put it back on Deshacer */
    public record Resultado(List<Movimiento> hechos, List<String> errores, Resguardo resguardo,
                            Map<String, Salida> salidas) {
        public Resultado(List<Movimiento> hechos, List<String> errores, Resguardo resguardo) {
            this(hechos, errores, resguardo, Map.of());
        }
    }

    /** A bien taken out of {@code resguardo}: its renglón as it was, and whether
     *  that left the resguardo empty (and so cancelled). */
    public record Salida(Resguardo resguardo, ResguardoItem renglon, boolean loCancelo) {}

    /** What Deshacer did: "DCS/01 → SGM/07" for each bien back in its área. */
    public record Deshecho(List<String> regresados, List<String> errores) {}

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
        Map<String, Salida> salidas = new HashMap<>();
        for (Producto p : bienes) {
            String bien = p.getCodigo() + " " + p.getNombre();
            Resguardo quitadoDe = null;
            Salida salida = null;
            try {
                Optional<String> impide = impedimento.de(p.getId());
                if (impide.isPresent()) { errores.add(bien + ": " + impide.get()); continue; }
                // Refused by the transfer itself, but only after it had left its resguardo.
                if (p.getStockActual() <= 0) { errores.add(bien + ": " + MovimientoService.SIN_EXISTENCIA); continue; }
                Optional<Resguardo> vigente = resguardos.getByProductoId(p.getId()).stream()
                    .filter(Resguardo::isActivo).findFirst();
                if (vigente.isPresent()) {
                    ResguardoItem renglon = renglonGuardado(vigente.get(), p);
                    boolean cancelado = resguardos.quitarBien(vigente.get(), renglon,
                        "transferencia a " + destino + ": " + motivo.trim());
                    quitadoDe = vigente.get();
                    salida = new Salida(quitadoDe, renglon, cancelado);
                }
                hechos.add(movimientos.registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, p.getStockActual(),
                    motivo.trim(), null, destino));
                movidos.add(p);
                if (salida != null) salidas.put(p.getId(), salida);
            } catch (Exception e) {
                // Said plainly, so whoever reads it knows the bien is now out of that resguardo.
                errores.add(bien + ": " + (e.getMessage() != null ? e.getMessage() : "error desconocido")
                    + (quitadoDe != null ? " (ya había salido del resguardo " + quitadoDe.getNumero()
                        + "; sigue en " + p.getArea() + ", vuelve a transferirlo)" : ""));
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
        return new Resultado(hechos, errores, nuevo, salidas);
    }

    /**
     * "Deshacer" on a batch dropped on the wrong área: the resguardo made for it is
     * cancelled, each transfer is taken back (the bien returns to its área with its
     * old número if still free) and each bien goes back into the resguardo it had
     * left. If one of them was lent or given in resguardo since, nothing is undone:
     * half a batch back would leave the rest without the resguardo made for them.
     */
    public Deshecho deshacer(Resultado lote) {
        if (!SessionManager.isAdmin())
            throw new SecurityException(MovimientoService.SOLO_ADMIN_MOVIMIENTOS);
        List<String> regresados = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        String nuestro = lote.resguardo() != null ? lote.resguardo().getId() : null;
        for (Movimiento m : lote.hechos()) {
            String bien = (m.getCodigoNuevo() + " " + Objects.requireNonNullElse(m.getProductoNombre(), "")).trim();
            try {
                Optional<String> impide = impedimento.de(m.getProductoId());
                if (impide.isPresent()) { errores.add(bien + ": " + impide.get()); continue; }
                resguardos.getByProductoId(m.getProductoId()).stream()
                    .filter(r -> r.isActivo() && !r.getId().equals(nuestro)).findFirst()
                    .ifPresent(r -> errores.add(bien + ": ya está en el resguardo " + r.getNumero()
                        + "; quítalo primero de ese resguardo"));
                // Only the latest movement of a bien can be taken back (newest first).
                movimientos.getByProducto(m.getProductoId()).stream()
                    .filter(x -> x.getEstado() == null || Movimiento.ESTADO_APROBADO.equals(x.getEstado()))
                    .findFirst().filter(x -> !x.getId().equals(m.getId()))
                    .ifPresent(x -> errores.add(bien + ": tuvo otro movimiento después de esta transferencia"));
            } catch (Exception e) {
                errores.add(bien + ": " + (e.getMessage() != null ? e.getMessage() : "error desconocido"));
            }
        }
        if (!errores.isEmpty()) {
            errores.add("No se deshizo nada.");
            return new Deshecho(regresados, errores);
        }
        // First: while it is in force, that resguardo says who holds them in the other área.
        if (lote.resguardo() != null) {
            try {
                resguardos.cancelar(lote.resguardo().getId());
            } catch (Exception e) {
                errores.add("No se pudo cancelar el resguardo " + lote.resguardo().getNumero() + ": "
                    + (e.getMessage() != null ? e.getMessage() : "error desconocido") + ". No se deshizo nada.");
                return new Deshecho(regresados, errores);
            }
        }
        Set<String> cancelados = new HashSet<>();
        lote.salidas().values().stream().filter(Salida::loCancelo).forEach(s -> cancelados.add(s.resguardo().getId()));
        for (Movimiento m : lote.hechos()) {
            String bien = (m.getCodigoNuevo() + " " + Objects.requireNonNullElse(m.getProductoNombre(), "")).trim();
            String codigo;
            try {
                codigo = movimientos.deshacerTransferencia(m);
            } catch (Exception e) {
                errores.add(bien + ": " + (e.getMessage() != null ? e.getMessage() : "error desconocido"));
                continue;
            }
            regresados.add(m.getCodigoNuevo() + " → " + codigo);
            Salida salida = lote.salidas().get(m.getProductoId());
            if (salida == null) continue;
            ResguardoItem renglon = salida.renglon();
            renglon.setProductoCodigo(codigo);
            if (m.getAreaOrigen() != null) renglon.setArea(m.getAreaOrigen());
            try {
                resguardos.regresarBien(salida.resguardo(), renglon,
                    "se deshizo su transferencia a " + m.getAreaDestino(), cancelados.contains(salida.resguardo().getId()));
            } catch (Exception e) {
                errores.add(codigo + ": regresó a " + m.getAreaOrigen() + " pero no a su resguardo "
                    + salida.resguardo().getNumero() + " (" + (e.getMessage() != null ? e.getMessage() : "error desconocido")
                    + "); asígnaselo de nuevo en Resguardos");
            }
        }
        return new Deshecho(regresados, errores);
    }

    /** The bien's renglón as the resguardo has it (same id, descripción and valor),
     *  to put it back exactly; built from the bien if the resguardo can't be read. */
    private ResguardoItem renglonGuardado(Resguardo r, Producto p) throws SQLException {
        Resguardo completo = resguardos.findById(r.getId());
        if (completo != null && completo.getItems() != null)
            for (ResguardoItem it : completo.getItems())
                if (p.getId().equals(it.getProductoId())) return it;
        return renglon(p, p.getCodigo(), p.getArea());
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
