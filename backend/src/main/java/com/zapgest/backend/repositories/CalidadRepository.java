package com.zapgest.backend.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zapgest.backend.models.Calidad;

public interface CalidadRepository extends JpaRepository<Calidad, Long> {

    List<Calidad> findAllByNegocioIdOrderByIdAsc(Long negocioId);

    boolean existsByNegocioIdAndNombreIgnoreCase(Long negocioId, String nombre);

    // Para editar: repetido en OTRA calidad (se ignora la propia)
    boolean existsByNegocioIdAndNombreIgnoreCaseAndIdNot(Long negocioId, String nombre, Long id);

    Optional<Calidad> findByIdAndNegocioId(Long id, Long negocioId);
}
