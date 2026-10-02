package com.zapgest.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zapgest.backend.models.Ubicacion;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {

    // Spring arma la consulta solo a partir del nombre del metodo:
    // cuenta las ubicaciones de un negocio que tengan activa = true
    long countByNegocioIdAndActivaTrue(Long negocioId);
}
