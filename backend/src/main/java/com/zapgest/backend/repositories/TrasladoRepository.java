package com.zapgest.backend.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zapgest.backend.models.Traslado;

import jakarta.persistence.LockModeType;

public interface TrasladoRepository extends JpaRepository<Traslado, Long> {

    // Lista para la pantalla: trae todo lo que se muestra en una sola consulta (sin N+1), el mas nuevo primero
    @Query("""
            select t from Traslado t
            join fetch t.origen join fetch t.destino join fetch t.solicitadoPor
            join fetch t.variante v join fetch v.referencia join fetch v.color join fetch v.talla join fetch v.calidad
            where t.negocioId = :negocioId
            order by t.id desc
            """)
    List<Traslado> listar(@Param("negocioId") Long negocioId);

    // Para cambiar de estado: BLOQUEA la fila hasta que termine la transaccion.
    // Si dos personas despachan el mismo traslado a la vez, la segunda espera y luego ve
    // que ya no esta SOLICITADO (409), asi el stock nunca sale dos veces.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Traslado t where t.id = :id and t.negocioId = :negocioId")
    Optional<Traslado> buscarParaCambiar(@Param("id") Long id, @Param("negocioId") Long negocioId);
}
