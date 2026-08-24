package com.sibim.db;

import com.sibim.model.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/** Abstracción sobre OfflineStore y DemoDataStore para eliminar el if-offline/if-demo por método. */
public interface LocalDataStore {

    // Productos
    List<Producto> findAllProductos(Set<String> areas) throws SQLException;
    Optional<Producto> findProductoById(String id) throws SQLException;
    Optional<Producto> findProductoByCodigo(String codigo) throws SQLException;
    boolean existsByCodigo(String codigo, String excludeId) throws SQLException;
    void saveProducto(Producto p) throws SQLException;
    void darDeBajaProducto(String id, String motivo) throws SQLException;
    void reactivarProducto(String id) throws SQLException;
    void updateProductoStock(String id, int stock) throws SQLException;

    // Movimientos
    List<Movimiento> findAllMovimientos(Set<String> areas) throws SQLException;
    List<Movimiento> findMovimientosByProducto(String productoId, Set<String> areas) throws SQLException;
    List<Movimiento> findMovimientosByDateRange(LocalDate desde, LocalDate hasta, Set<String> areas) throws SQLException;
    Optional<String> findProductoIdByMovimientoId(String movimientoId) throws SQLException;
    void addMovimiento(Movimiento m, Integer expectedStockAnterior) throws SQLException;
    void deleteMovimiento(String id) throws SQLException;

    // Categorías
    List<Categoria> findAllCategorias() throws SQLException;
    Optional<Categoria> findCategoriaById(String id) throws SQLException;
    void saveCategoria(Categoria c) throws SQLException;
    void deleteCategoria(String id) throws SQLException;
    boolean tieneProductosEnCategoria(String categoriaId) throws SQLException;
}
