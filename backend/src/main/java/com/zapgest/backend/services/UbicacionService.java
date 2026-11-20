package com.zapgest.backend.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.repositories.UbicacionRepository;

@Service
public class UbicacionService {

    // RN-01: maximo 10 ubicaciones activas por negocio
    private static final int MAX_UBICACIONES_ACTIVAS = 10;
    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1
    private static final int MAX_NOMBRE = 80;   // igual que la columna ubicacion.nombre

    private final UbicacionRepository repository;

    public UbicacionService(UbicacionRepository repository) {
        this.repository = repository;
    }

    public List<Ubicacion> listar() {
        return repository.findAllByNegocioId(NEGOCIO_ID);
    }

    public Ubicacion crear(Ubicacion ubicacion) {
        String nombre = validarNombre(ubicacion == null ? null : ubicacion.getNombre());
        // Sin repetidos, sin importar mayusculas ("Local" y "LOCAL" son la misma)
        if (repository.existsByNegocioIdAndNombreIgnoreCase(NEGOCIO_ID, nombre)) {
            throw new IllegalArgumentException("Ya existe una ubicación con ese nombre.");
        }

        long activas = repository.countByNegocioIdAndActivaTrue(NEGOCIO_ID);
        if (activas >= MAX_UBICACIONES_ACTIVAS) {
            throw new IllegalStateException("Ha alcanzado el límite máximo permitido de 10 ubicaciones");
        }

        ubicacion.setId(null);              // el id lo pone la base de datos
        ubicacion.setNegocioId(NEGOCIO_ID);
        ubicacion.setNombre(nombre);
        ubicacion.setActiva(true);
        return repository.save(ubicacion);
    }

    public Ubicacion cambiarEstado(Long id, Boolean activa) {
        if (activa == null) {
            throw new IllegalArgumentException("El estado activa es obligatorio.");
        }
        Ubicacion ubicacion = repository.findByIdAndNegocioId(id, NEGOCIO_ID)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la ubicación."));
        if (Boolean.TRUE.equals(activa) && !Boolean.TRUE.equals(ubicacion.getActiva())
                && repository.countByNegocioIdAndActivaTrue(NEGOCIO_ID) >= MAX_UBICACIONES_ACTIVAS) {
            throw new IllegalStateException("Ha alcanzado el límite máximo permitido de 10 ubicaciones");
        }
        // Sprint 2 (decision del PO): no se desactiva una ubicacion que todavia tiene pares,
        // para no dejar stock "huerfano". Primero hay que sacar o trasladar esas unidades.
        if (Boolean.FALSE.equals(activa) && Boolean.TRUE.equals(ubicacion.getActiva())) {
            // Sprint 3: tampoco si participa en traslados solicitados o en movimiento
            long traslados = repository.trasladosPendientes(ubicacion.getId());
            if (traslados > 0) {
                throw new IllegalArgumentException("No se puede desactivar la ubicación: tiene " + traslados
                        + (traslados == 1 ? " traslado pendiente" : " traslados pendientes")
                        + ". Primero deben recibirse o cancelarse.");
            }
            long pares = repository.paresEnInventario(ubicacion.getId());
            if (pares > 0) {
                throw new IllegalArgumentException("No se puede desactivar la ubicación: tiene " + pares
                        + (pares == 1 ? " par" : " pares") + " en inventario. Primero deben salir o trasladarse.");
            }
        }
        ubicacion.setActiva(activa);
        return repository.save(ubicacion);
    }

    // PUT /api/ubicaciones/{id}: solo cambia el nombre.
    // No toca "activa", asi que NO cuenta para el limite de 10 activas.
    public Ubicacion editar(Long id, String nombreNuevo) {
        Ubicacion ubicacion = repository.findByIdAndNegocioId(id, NEGOCIO_ID)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la ubicación."));
        String nombre = validarNombre(nombreNuevo);
        if (repository.existsByNegocioIdAndNombreIgnoreCaseAndIdNot(NEGOCIO_ID, nombre, id)) {
            throw new IllegalArgumentException("Ya existe una ubicación con ese nombre.");
        }
        ubicacion.setNombre(nombre);
        return repository.save(ubicacion);
    }

    // Nombre obligatorio, sin espacios de mas y maximo 80 caracteres
    private String validarNombre(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El nombre de la ubicación es obligatorio.");
        }
        String nombre = texto.trim().replaceAll("\\s+", " ");
        if (nombre.length() > MAX_NOMBRE) {
            throw new IllegalArgumentException(
                    "El nombre de la ubicación no puede superar los " + MAX_NOMBRE + " caracteres.");
        }
        return nombre;
    }
}
