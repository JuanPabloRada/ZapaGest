package com.zapgest.backend.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zapgest.backend.models.Calidad;
import com.zapgest.backend.repositories.CalidadRepository;

// HU-03: el negocio define libremente sus calidades (Original, 1.1, AAA, Nacional...).
@Service
public class CalidadService {

    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1
    private static final int MAX_NOMBRE = 50;  // igual que la columna calidad.nombre

    private final CalidadRepository repository;

    public CalidadService(CalidadRepository repository) {
        this.repository = repository;
    }

    public List<Calidad> listar() {
        return repository.findAllByNegocioIdOrderByIdAsc(NEGOCIO_ID);
    }

    public Calidad crear(Calidad datos) {
        if (datos == null || datos.getNombre() == null || datos.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de la calidad es obligatorio.");
        }

        // Sin espacios de mas, igual que hace la pantalla
        String nombre = datos.getNombre().trim().replaceAll("\\s+", " ");
        if (nombre.length() > MAX_NOMBRE) {
            throw new IllegalArgumentException(
                    "El nombre de la calidad no puede superar los " + MAX_NOMBRE + " caracteres.");
        }
        if (repository.existsByNegocioIdAndNombreIgnoreCase(NEGOCIO_ID, nombre)) {
            throw new IllegalArgumentException("Ya existe una calidad con ese nombre.");
        }

        // Se crea un objeto nuevo: del cliente SOLO se toma el nombre. Si viniera un id,
        // un negocioId o activa, se ignoran (con un id existente, save() sobrescribiria
        // otra calidad).
        Calidad nueva = new Calidad();
        nueva.setNegocioId(NEGOCIO_ID);
        nueva.setNombre(nombre);
        nueva.setActiva(true);
        return repository.save(nueva);
    }
}
