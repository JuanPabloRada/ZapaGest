package com.zapgest.backend.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zapgest.backend.models.Referencia;

public interface ReferenciaRepository extends JpaRepository<Referencia, Long> {

    boolean existsByNegocioIdAndCodigoIgnoreCase(Long negocioId, String codigo);

    // Para editar: codigo repetido en OTRA referencia (se ignora la propia)
    boolean existsByNegocioIdAndCodigoIgnoreCaseAndIdNot(Long negocioId, String codigo, Long id);

    Optional<Referencia> findByIdAndNegocioId(Long id, Long negocioId);

    List<Referencia> findByNegocioIdOrderByCodigoAsc(Long negocioId);
}
