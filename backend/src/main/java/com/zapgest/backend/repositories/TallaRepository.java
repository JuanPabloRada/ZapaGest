package com.zapgest.backend.repositories;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zapgest.backend.models.Talla;

public interface TallaRepository extends JpaRepository<Talla, Long> {

    Optional<Talla> findByValor(BigDecimal valor);
}
