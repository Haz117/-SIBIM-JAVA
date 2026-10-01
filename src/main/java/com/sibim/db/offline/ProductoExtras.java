package com.sibim.db.offline;

import com.sibim.model.Producto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bien fields the offline mirror ({@code products}) and its outbox
 * ({@code product_outbox}) didn't carry: depreciation data, the inventario
 * físico / vehicle columns and the baja dictamen. Without them an offline
 * edit synced every one of them back to the server as NULL (saveOnline
 * writes the whole row), and offline screens (Depreciación, ficha técnica,
 * parque vehicular) showed them empty. Kept as one list so the mirror, the
 * outbox, the reader and the sync merge can't drift apart again. Stored as
 * TEXT in SQLite, like the other dates and amounts there.
 */
final class ProductoExtras {

    private static final Logger log = LoggerFactory.getLogger(ProductoExtras.class);

    private record Campo(String columna, Function<Producto, Object> get, BiConsumer<Producto, String> set) {}

    private static final List<Campo> CAMPOS = List.of(
        new Campo("fecha_adquisicion", Producto::getFechaAdquisicion, (p, v) -> p.setFechaAdquisicion(fecha(v))),
        new Campo("vida_util_anios", Producto::getVidaUtilAnios, (p, v) -> p.setVidaUtilAnios(v == null ? null : Integer.valueOf(v))),
        new Campo("valor_residual", Producto::getValorResidual, (p, v) -> p.setValorResidual(v == null ? null : new BigDecimal(v))),
        new Campo("proxima_revision", Producto::getProximaRevision, (p, v) -> p.setProximaRevision(fecha(v))),
        new Campo("notas_mantenimiento", Producto::getNotasMantenimiento, Producto::setNotasMantenimiento),
        new Campo("estado_fisico", Producto::getEstadoFisico, Producto::setEstadoFisico),
        new Campo("numero_factura", Producto::getNumeroFactura, Producto::setNumeroFactura),
        new Campo("clave_armonizada", Producto::getClaveArmonizada, Producto::setClaveArmonizada),
        new Campo("color", Producto::getColor, Producto::setColor),
        new Campo("no_motor", Producto::getNoMotor, Producto::setNoMotor),
        new Campo("tipo_bien", Producto::getTipoBien, Producto::setTipoBien),
        new Campo("no_tarjeta_circulacion", Producto::getNoTarjetaCirculacion, Producto::setNoTarjetaCirculacion),
        new Campo("no_poliza_seguro", Producto::getNoPolizaSeguro, Producto::setNoPolizaSeguro),
        new Campo("tipo_destino_baja", Producto::getTipoDestinoBaja, Producto::setTipoDestinoBaja),
        new Campo("dictamen_baja", Producto::getDictamenBaja, Producto::setDictamenBaja),
        new Campo("numero_acta_baja", Producto::getNumeroActaBaja, Producto::setNumeroActaBaja),
        new Campo("fecha_dictamen", Producto::getFechaDictamen, (p, v) -> p.setFechaDictamen(fecha(v))));

    private ProductoExtras() {}

    /** Adds the missing columns to an existing offline.db (idempotent). */
    static void migrar(Connection c, String tabla) {
        for (Campo campo : CAMPOS) {
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE " + tabla + " ADD COLUMN " + campo.columna() + " TEXT");
            } catch (SQLException ya) {
                log.debug("Offline migration step already applied (idempotent)", ya);
            }
        }
    }

    /** ", col1, col2…" to append to an INSERT column list. */
    static String columnas() {
        return CAMPOS.stream().map(c -> ", " + c.columna()).collect(Collectors.joining());
    }

    /** ",?,?…" matching {@link #columnas()}. */
    static String marcadores() {
        return ",?".repeat(CAMPOS.size());
    }

    /** ", col1=excluded.col1…" for an ON CONFLICT DO UPDATE SET. */
    static String actualizar() {
        return CAMPOS.stream().map(c -> ", " + c.columna() + "=excluded." + c.columna()).collect(Collectors.joining());
    }

    /** Binds the values from {@code desde}; returns the next parameter index. */
    static int enlazar(PreparedStatement ps, int desde, Producto p) throws SQLException {
        for (Campo c : CAMPOS) {
            Object v = c.get().apply(p);
            ps.setString(desde++, v == null ? null : (v instanceof BigDecimal b ? b.toPlainString() : v.toString()));
        }
        return desde;
    }

    static void leer(ResultSet rs, Producto p) throws SQLException {
        for (Campo c : CAMPOS) {
            String v = rs.getString(c.columna());
            try {
                c.set().accept(p, v == null || v.isBlank() ? null : v);
            } catch (RuntimeException malformado) {
                log.debug("Valor offline ilegible en {} del bien {}: '{}'", c.columna(), p.getId(), v, malformado);
            }
        }
    }

    /** Fills every one of these fields that {@code offline} lacks with the
     *  server's value. Changes queued by an older SIBIM (or from a mirror
     *  not refreshed since the upgrade) have them all NULL, and saveOnline
     *  would otherwise wipe them on the server. The cost: clearing one of
     *  these fields while offline doesn't reach the server. */
    static void completarConServidor(Producto offline, Producto servidor) {
        if (servidor == null) return;
        for (Campo c : CAMPOS) {
            Object v = c.get().apply(offline);
            if (v == null || (v instanceof String s && s.isBlank())) {
                Object delServidor = c.get().apply(servidor);
                if (delServidor != null)
                    c.set().accept(offline, delServidor instanceof BigDecimal b ? b.toPlainString() : delServidor.toString());
            }
        }
    }

    private static LocalDate fecha(String v) {
        return v == null ? null : LocalDate.parse(v);
    }
}
