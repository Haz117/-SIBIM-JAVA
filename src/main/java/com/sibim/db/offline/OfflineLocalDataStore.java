package com.sibim.db.offline;

import com.sibim.db.LocalDataStore;
import com.sibim.model.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/** {@link LocalDataStore} implementation that delegates every call to the
 *  static methods of {@link OfflineStore}. */
public final class OfflineLocalDataStore implements LocalDataStore {

    // Productos

    @Override
    public List<Producto> findAllProductos(Set<String> areas) throws SQLException {
        return OfflineStore.findAllProductos(areas);
    }

    @Override
    public Optional<Producto> findProductoById(String id) throws SQLException {
        return OfflineStore.findProductoById(id);
    }

    @Override
    public Optional<Producto> findProductoByCodigo(String codigo) throws SQLException {
        return OfflineStore.findProductoByCodigo(codigo);
    }

    @Override
    public boolean existsByCodigo(String codigo, String excludeId) throws SQLException {
        return OfflineStore.existsByCodigo(codigo, excludeId);
    }

    @Override
    public void saveProducto(Producto p) throws SQLException {
        OfflineStore.saveProducto(p);
    }

    @Override
    public void darDeBajaProducto(String id, String motivo) throws SQLException {
        OfflineStore.darDeBajaProducto(id, motivo);
    }

    @Override
    public void reactivarProducto(String id) throws SQLException {
        OfflineStore.reactivarProducto(id);
    }

    @Override
    public void updateProductoStock(String id, int stock) throws SQLException {
        OfflineStore.updateProductoStock(id, stock);
    }

    // Movimientos

    @Override
    public List<Movimiento> findAllMovimientos(Set<String> areas) throws SQLException {
        return OfflineStore.findAllMovimientos(areas);
    }

    @Override
    public List<Movimiento> findMovimientosByProducto(String productoId, Set<String> areas) throws SQLException {
        return OfflineStore.findMovimientosByProducto(productoId, areas);
    }

    @Override
    public List<Movimiento> findMovimientosByDateRange(LocalDate desde, LocalDate hasta, Set<String> areas) throws SQLException {
        return OfflineStore.findMovimientosByDateRange(desde, hasta, areas);
    }

    @Override
    public Optional<String> findProductoIdByMovimientoId(String movimientoId) throws SQLException {
        return OfflineStore.findProductoIdByMovimientoId(movimientoId);
    }

    @Override
    public void addMovimiento(Movimiento m, Integer expectedStockAnterior) throws SQLException {
        OfflineStore.addMovimiento(m, expectedStockAnterior);
    }

    @Override
    public void deleteMovimiento(String id) throws SQLException {
        OfflineStore.deleteMovimiento(id);
    }

    // Categorías

    @Override
    public List<Categoria> findAllCategorias() throws SQLException {
        return OfflineStore.findAllCategorias();
    }

    @Override
    public Optional<Categoria> findCategoriaById(String id) throws SQLException {
        return OfflineStore.findCategoriaById(id);
    }

    @Override
    public void saveCategoria(Categoria c) throws SQLException {
        OfflineStore.saveCategoria(c);
    }

    @Override
    public void deleteCategoria(String id) throws SQLException {
        OfflineStore.deleteCategoria(id);
    }

    @Override
    public boolean tieneProductosEnCategoria(String categoriaId) throws SQLException {
        return OfflineStore.tieneProductosEnCategoria(categoriaId);
    }
}
