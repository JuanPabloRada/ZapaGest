package com.zapgest.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zapgest.backend.models.Movimiento;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {
}
