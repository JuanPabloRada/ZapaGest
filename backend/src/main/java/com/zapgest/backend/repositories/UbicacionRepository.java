package com.zapgest.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // Total de pares que hay hoy en la ubicacion (0 si no tiene inventario).
    // Se usa para no dejar desactivar una ubicacion con stock (decision del PO, Sprint 2).
    @Query("select coalesce(sum(i.cantidad), 0) from Inventario i where i.ubicacion.id = :ubicacionId")
    long paresEnInventario(@Param("ubicacionId") Long ubicacionId);

    // Traslados que todavia usan esta ubicacion (solicitados o en movimiento, como origen o destino).
    // Mientras haya alguno, la ubicacion no se puede desactivar.
    @Query("""
            select count(t) from Traslado t
            where (t.origen.id = :ubicacionId or t.destino.id = :ubicacionId)
            and t.estado in (com.zapgest.backend.models.EstadoTraslado.SOLICITADO,
                             com.zapgest.backend.models.EstadoTraslado.EN_MOVIMIENTO)
            """)
    long trasladosPendientes(@Param("ubicacionId") Long ubicacionId);
}
