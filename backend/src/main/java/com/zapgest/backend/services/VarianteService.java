package com.zapgest.backend.services;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zapgest.backend.exceptions.RecursoNoEncontradoException;
import com.zapgest.backend.models.Color;
import com.zapgest.backend.models.Referencia;
import com.zapgest.backend.models.ReferenciaCalidad;
import com.zapgest.backend.models.Talla;
import com.zapgest.backend.models.Variante;
import com.zapgest.backend.repositories.ReferenciaRepository;
import com.zapgest.backend.repositories.VarianteRepository;

// HU-06: generar automaticamente las variantes de una referencia.
// No hay pantalla para crear variantes: se crean solas la primera vez que entra mercancia (HU-07).
@Service
public class VarianteService {

    private static final Long NEGOCIO_ID = 1L; // por ahora solo existe el negocio 1

    private final VarianteRepository varianteRepository;
    private final ReferenciaRepository referenciaRepository;

    public VarianteService(VarianteRepository varianteRepository, ReferenciaRepository referenciaRepository) {
        this.varianteRepository = varianteRepository;
        this.referenciaRepository = referenciaRepository;
    }

    // Devuelve la variante si ya existe; si no, la crea heredando los precios de su calidad.
    // Corre dentro de la transaccion de quien la llame (el ingreso de mercancia).
    @Transactional
    public Variante obtenerOCrear(Long referenciaId, Long colorId, Long tallaId, Long calidadId) {
        if (referenciaId == null || colorId == null || tallaId == null || calidadId == null) {
            throw new IllegalArgumentException("Debes elegir referencia, color, talla y calidad.");
        }

        Referencia referencia = referenciaRepository.findByIdAndNegocioId(referenciaId, NEGOCIO_ID)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la referencia."));

        if (!Boolean.TRUE.equals(referencia.getActiva())) {
            throw new IllegalArgumentException("La referencia está inactiva.");
        }

        // El color, la talla y la calidad deben ser de los que maneja la referencia (HU-05)
        Color color = referencia.getColores().stream()
                .filter(c -> c.getId().equals(colorId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El color no pertenece a esta referencia."));

        Talla talla = referencia.getTallas().stream()
                .filter(t -> t.getId().equals(tallaId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La talla no pertenece a esta referencia."));

        ReferenciaCalidad precios = referencia.getPrecios().stream()
                .filter(p -> p.getCalidad().getId().equals(calidadId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La calidad no pertenece a esta referencia."));

        // Si ya existe se reutiliza tal cual: sus precios no se pisan (eso es HU-12/HU-13)
        Optional<Variante> existente = varianteRepository
                .findByReferenciaIdAndColorIdAndTallaIdAndCalidadId(referenciaId, colorId, tallaId, calidadId);
        if (existente.isPresent()) {
            return existente.get();
        }

        Variante nueva = new Variante();
        nueva.setNegocioId(NEGOCIO_ID);
        nueva.setReferencia(referencia);
        nueva.setColor(color);
        nueva.setTalla(talla);
        nueva.setCalidad(precios.getCalidad());
        // Herencia de precios desde el catalogo
        nueva.setCostoAdquisicion(precios.getCostoAdquisicion());
        nueva.setPrecioMinimo(precios.getPrecioMinimo());
        nueva.setPrecioSugerido(precios.getPrecioSugerido());
        nueva.setActiva(true);
        return varianteRepository.save(nueva);
    }
}
