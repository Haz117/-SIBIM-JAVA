package com.sibim.service;

import com.sibim.model.Categoria;
import com.sibim.repository.CategoriaRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class CategoriaService {

    private final CategoriaRepository categoriaRepo = new CategoriaRepository();

    public List<Categoria> findAll() throws SQLException {
        return categoriaRepo.findAll();
    }

    public Optional<Categoria> findById(String id) throws SQLException {
        return categoriaRepo.findById(id);
    }

    public Categoria save(Categoria c) throws SQLException {
        if (c.getNombre() == null || c.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría no puede estar vacío");
        }
        return categoriaRepo.save(c);
    }

    public void delete(String id) throws SQLException {
        categoriaRepo.delete(id);
    }

    public boolean tieneProductos(String id) throws SQLException {
        return categoriaRepo.tieneProductos(id);
    }
}
