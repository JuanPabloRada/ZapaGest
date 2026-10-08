package com.zapgest.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zapgest.backend.models.Calidad;

public interface CalidadRepository extends JpaRepository<Calidad, Long> {

    List<Calidad> findAllByNegocioIdOrderByIdAsc(Long negocioId);

    boolean existsByNegocioIdAndNombreIgnoreCase(Long negocioId, String nombre);
}
