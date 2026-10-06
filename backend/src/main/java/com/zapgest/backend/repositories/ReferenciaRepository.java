package com.zapgest.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zapgest.backend.models.Referencia;

public interface ReferenciaRepository extends JpaRepository<Referencia, Long> {

    boolean existsByNegocioIdAndCodigoIgnoreCase(Long negocioId, String codigo);

    List<Referencia> findByNegocioIdOrderByCodigoAsc(Long negocioId);
}
