package com.zapgest.backend.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.repositories.UbicacionRepository;

@Service
public class UbicacionService {

    // RN-01: maximo 10 ubicaciones activas por negocio
    private static final int MAX_UBICACIONES_ACTIVAS = 10;
    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1

    private final UbicacionRepository repository;

    public UbicacionService(UbicacionRepository repository) {
        this.repository = repository;
    }

    public List<Ubicacion> listar() {
        return repository.findAll();
    }

    public Ubicacion crear(Ubicacion ubicacion) {
        if (ubicacion.getNombre() == null || ubicacion.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de la ubicación es obligatorio.");
        }

        long activas = repository.countByNegocioIdAndActivaTrue(NEGOCIO_ID);
        if (activas >= MAX_UBICACIONES_ACTIVAS) {
            throw new IllegalStateException("Ya tienes " + MAX_UBICACIONES_ACTIVAS
                    + " ubicaciones activas. Desactiva una antes de crear otra.");
        }

        ubicacion.setId(null);              // el id lo pone la base de datos
        ubicacion.setNegocioId(NEGOCIO_ID);
        ubicacion.setNombre(ubicacion.getNombre().trim());
        ubicacion.setActiva(true);
        return repository.save(ubicacion);
    }
}
