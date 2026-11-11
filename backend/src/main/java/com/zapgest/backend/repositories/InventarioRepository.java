package com.zapgest.backend.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zapgest.backend.models.Inventario;

public interface InventarioRepository extends JpaRepository<Inventario, Long> {

    // Suma stock de forma ATOMICA, aunque entren varios ingresos a la vez:
    // si no hay fila para (variante, ubicacion) la crea; si ya la hay, le suma. Todo en una sola
    // sentencia de PostgreSQL, asi dos ingresos simultaneos nunca se pisan ni duplican la fila.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO inventario (negocio_id, variante_id, ubicacion_id, cantidad)
            VALUES (:negocioId, :varianteId, :ubicacionId, :cantidad)
            ON CONFLICT (variante_id, ubicacion_id)
            DO UPDATE SET cantidad = inventario.cantidad + EXCLUDED.cantidad
            """, nativeQuery = true)
    void sumar(@Param("negocioId") Long negocioId,
            @Param("varianteId") Long varianteId,
            @Param("ubicacionId") Long ubicacionId,
            @Param("cantidad") int cantidad);

    // Stock actual de una variante en una ubicacion (vacio si nunca ha entrado)
    @Query("select i.cantidad from Inventario i where i.variante.id = :varianteId and i.ubicacion.id = :ubicacionId")
    Optional<Integer> cantidadEn(@Param("varianteId") Long varianteId, @Param("ubicacionId") Long ubicacionId);

    // HU-10: donde hay unidades de estas variantes (solo filas con stock), ordenado por ubicacion
    @Query("""
            select i from Inventario i join fetch i.ubicacion u
            where i.negocioId = :negocioId and i.variante.id in :varianteIds and i.cantidad > 0
            order by u.nombre, u.id
            """)
    List<Inventario> conStockDe(@Param("negocioId") Long negocioId,
            @Param("varianteIds") Collection<Long> varianteIds);
}
