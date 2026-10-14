package com.zapgest.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zapgest.backend.models.Ubicacion;

import java.util.List;
import java.util.Optional;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {

    long countByNegocioIdAndActivaTrue(Long negocioId);

    List<Ubicacion> findAllByNegocioId(Long negocioId);

    Optional<Ubicacion> findByIdAndNegocioId(Long id, Long negocioId);

    boolean existsByNegocioIdAndNombreIgnoreCase(Long negocioId, String nombre);

    // Para editar: repetido en OTRA ubicacion (se ignora la propia)
    boolean existsByNegocioIdAndNombreIgnoreCaseAndIdNot(Long negocioId, String nombre, Long id);
}
