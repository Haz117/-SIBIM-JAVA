package com.sibim.db;

import com.sibim.model.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/** {@link LocalDataStore} implementation that delegates every call to the
 *  static methods of {@link DemoDataStore}. */
public final class DemoLocalDataStore implements LocalDataStore {

    // Productos

    @Override
    public List<Producto> findAllProductos(Set<String> areas) throws SQLException {
        return DemoDataStore.findAllProductos(areas);
    }

    @Override
    public Optional<Producto> findProductoById(String id) throws SQLException {
        return DemoDataStore.findProductoById(id);
    }

    @Override
    public Optional<Producto> findProductoByCodigo(String codigo) throws SQLException {
        return DemoDataStore.findProductoByCodigo(codigo);
    }

    @Override
    public boolean existsByCodigo(String codigo, String excludeId) throws SQLException {
        return DemoDataStore.existsByCodigo(codigo, excludeId);
    }

    @Override
    public void saveProducto(Producto p) throws SQLException {
        DemoDataStore.saveProducto(p);
    }

    @Override
    public void darDeBajaProducto(String id, String motivo) throws SQLException {
        DemoDataStore.darDeBajaProducto(id, motivo);
    }

    @Override
    public void reactivarProducto(String id) throws SQLException {
        DemoDataStore.reactivarProducto(id);
    }

    @Override
    public void updateProductoStock(String id, int stock) throws SQLException {
        DemoDataStore.updateProductoStock(id, stock);
    }

    // Movimientos

    @Override
    public List<Movimiento> findAllMovimientos(Set<String> areas) throws SQLException {
        return DemoDataStore.findAllMovimientos(areas);
    }

    @Override
    public List<Movimiento> findMovimientosByProducto(String productoId, Set<String> areas) throws SQLException {
        return DemoDataStore.findMovimientosByProducto(productoId, areas);
    }

    @Override
    public List<Movimiento> findMovimientosByDateRange(LocalDate desde, LocalDate hasta, Set<String> areas) throws SQLException {
        return DemoDataStore.findMovimientosByDateRange(desde, hasta, areas);
    }

    @Override
    public Optional<String> findProductoIdByMovimientoId(String movimientoId) throws SQLException {
        return DemoDataStore.findProductoIdByMovimientoId(movimientoId);
    }

    @Override
    public void addMovimiento(Movimiento m, Integer expectedStockAnterior) throws SQLException {
        DemoDataStore.addMovimiento(m, expectedStockAnterior);
    }

    @Override
    public void deleteMovimiento(String id) throws SQLException {
        DemoDataStore.deleteMovimiento(id);
    }

    // Categorías

    @Override
    public List<Categoria> findAllCategorias() throws SQLException {
        return DemoDataStore.findAllCategorias();
    }

    @Override
    public Optional<Categoria> findCategoriaById(String id) throws SQLException {
        return DemoDataStore.findCategoriaById(id);
    }

    @Override
    public void saveCategoria(Categoria c) throws SQLException {
        DemoDataStore.saveCategoria(c);
    }

    @Override
    public void deleteCategoria(String id) throws SQLException {
        DemoDataStore.deleteCategoria(id);
    }

    @Override
    public boolean tieneProductosEnCategoria(String categoriaId) throws SQLException {
        return DemoDataStore.tieneProductosEnCategoria(categoriaId);
    }
}
