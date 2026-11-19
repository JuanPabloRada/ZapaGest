package com.zapgest.backend.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.Ubicacion;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.InventarioRepository;
import com.zapgest.backend.repositories.UbicacionRepository;
import com.zapgest.backend.repositories.VarianteRepository;

// HU-07: el stock de cada variante en cada ubicacion.
// El ingreso de mercancia (de Joseph) llama a sumarStock dentro de SU transaccion,
// junto con VarianteService.obtenerOCrear y MovimientoService.registrarEntrada.
@Service
public class InventarioService {

    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1

    private final InventarioRepository inventarioRepository;
    private final VarianteRepository varianteRepository;
    private final UbicacionRepository ubicacionRepository;

    public InventarioService(InventarioRepository inventarioRepository,
            VarianteRepository varianteRepository,
            UbicacionRepository ubicacionRepository) {
        this.inventarioRepository = inventarioRepository;
        this.varianteRepository = varianteRepository;
        this.ubicacionRepository = ubicacionRepository;
    }

    // Suma "cantidad" unidades de la variante en la ubicacion y devuelve el stock que queda ahi.
    // Es segura con ingresos simultaneos: la suma la hace la base de datos en una sola sentencia.
    @Transactional
    public int sumarStock(Long varianteId, Long ubicacionId, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }

        Ubicacion ubicacion = ubicacionRepository.findByIdAndNegocioId(ubicacionId, NEGOCIO_ID)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la ubicación."));
        if (!Boolean.TRUE.equals(ubicacion.getActiva())) {
            throw new IllegalArgumentException("La ubicación está inactiva.");
        }

        Variante variante = varianteRepository.findById(varianteId)
                .filter(v -> NEGOCIO_ID.equals(v.getNegocioId()))
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la variante."));

        inventarioRepository.sumar(NEGOCIO_ID, variante.getId(), ubicacion.getId(), cantidad);

        return inventarioRepository.cantidadEn(variante.getId(), ubicacion.getId())
                .orElseThrow(() -> new IllegalStateException("No se pudo leer el stock después del ingreso."));
    }
}
