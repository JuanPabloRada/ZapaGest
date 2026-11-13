package com.zapgest.backend.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zapgest.backend.models.Variante;

public interface VarianteRepository extends JpaRepository<Variante, Long> {

    // Busca la combinacion exacta referencia + color + talla + calidad
    Optional<Variante> findByReferenciaIdAndColorIdAndTallaIdAndCalidadId(
            Long referenciaId, Long colorId, Long tallaId, Long calidadId);

    // Stock de una referencia agrupado por color / talla / calidad (solo lo que tiene unidades).
    // Lo usa CatalogoService.editar para no dejar quitar opciones que aun tienen pares.
    @Query("""
            select new com.zapgest.backend.repositories.StockPorOpcion(v.color.id, sum(i.cantidad))
            from Inventario i join i.variante v
            where v.referencia.id = :referenciaId and i.cantidad > 0
            group by v.color.id
            """)
    List<StockPorOpcion> stockPorColor(@Param("referenciaId") Long referenciaId);

    @Query("""
            select new com.zapgest.backend.repositories.StockPorOpcion(v.talla.id, sum(i.cantidad))
            from Inventario i join i.variante v
            where v.referencia.id = :referenciaId and i.cantidad > 0
            group by v.talla.id
            """)
    List<StockPorOpcion> stockPorTalla(@Param("referenciaId") Long referenciaId);

    @Query("""
            select new com.zapgest.backend.repositories.StockPorOpcion(v.calidad.id, sum(i.cantidad))
            from Inventario i join i.variante v
            where v.referencia.id = :referenciaId and i.cantidad > 0
            group by v.calidad.id
            """)
    List<StockPorOpcion> stockPorCalidad(@Param("referenciaId") Long referenciaId);
}
