package com.zapgest.backend.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
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
        String nombre = validarNombre(datos == null ? null : datos.getNombre());
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

    // PUT /api/calidades/{id}: solo cambia el nombre
    public Calidad editar(Long id, String nombreNuevo) {
        Calidad calidad = repository.findByIdAndNegocioId(id, NEGOCIO_ID)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la calidad."));
        String nombre = validarNombre(nombreNuevo);
        // Repetido en OTRA calidad; la propia no cuenta (se puede guardar igual o cambiar mayúsculas)
        if (repository.existsByNegocioIdAndNombreIgnoreCaseAndIdNot(NEGOCIO_ID, nombre, id)) {
            throw new IllegalArgumentException("Ya existe una calidad con ese nombre.");
        }
        calidad.setNombre(nombre);
        return repository.save(calidad);
    }

    // Nombre obligatorio, sin espacios de mas y maximo 50 caracteres
    private String validarNombre(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El nombre de la calidad es obligatorio.");
        }
        String nombre = texto.trim().replaceAll("\\s+", " ");
        if (nombre.length() > MAX_NOMBRE) {
            throw new IllegalArgumentException(
                    "El nombre de la calidad no puede superar los " + MAX_NOMBRE + " caracteres.");
        }
        return nombre;
    }
}
