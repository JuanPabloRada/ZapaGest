package com.zapgest.backend.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zapgest.backend.models.Variante;

public interface VarianteRepository extends JpaRepository<Variante, Long> {

    // Busca la combinacion exacta referencia + color + talla + calidad
    Optional<Variante> findByReferenciaIdAndColorIdAndTallaIdAndCalidadId(
            Long referenciaId, Long colorId, Long tallaId, Long calidadId);
}
