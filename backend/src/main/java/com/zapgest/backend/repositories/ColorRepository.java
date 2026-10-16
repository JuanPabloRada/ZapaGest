package com.zapgest.backend.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zapgest.backend.models.Color;

public interface ColorRepository extends JpaRepository<Color, Long> {

    Optional<Color> findByNegocioIdAndNombreIgnoreCase(Long negocioId, String nombre);

    List<Color> findByNegocioIdOrderByNombreAsc(Long negocioId);
}
