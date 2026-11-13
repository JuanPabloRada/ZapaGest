package com.zapgest.backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.zapgest.backend.models.Variante;

// HU-09: busqueda de variantes con filtros. Va aparte de VarianteRepository (que es de Joseph)
// para no pisarnos al unir las ramas.
public interface InventarioBusquedaRepository extends Repository<Variante, Long> {

    // Mismos filtros para la lista y para el conteo.
    // :patron llega siempre con valor ("%air%" o "%%"), asi PostgreSQL nunca recibe un texto nulo.
    // Sin :incluirAgotados solo salen variantes con al menos una unidad en alguna ubicacion.
    String DONDE = """
            where v.negocioId = :negocioId
              and (:colorId is null or v.color.id = :colorId)
              and (:tallaId is null or v.talla.id = :tallaId)
              and (:calidadId is null or v.calidad.id = :calidadId)
              and (lower(r.codigo) like :patron
                   or lower(r.nombre) like :patron
                   or lower(coalesce(r.marca, '')) like :patron)
              and (:incluirAgotados = true
                   or exists (select 1 from Inventario i where i.variante = v and i.cantidad > 0))
            """;

    @Query(value = "select v from Variante v "
            + "join fetch v.referencia r join fetch v.color c join fetch v.talla t join fetch v.calidad ca "
            + DONDE
            + "order by r.codigo, r.nombre, c.nombre, t.valor, ca.nombre, v.id",
            countQuery = "select count(v) from Variante v join v.referencia r " + DONDE)
    Page<Variante> buscar(@Param("negocioId") Long negocioId,
            @Param("patron") String patron,
            @Param("colorId") Long colorId,
            @Param("tallaId") Long tallaId,
            @Param("calidadId") Long calidadId,
            @Param("incluirAgotados") boolean incluirAgotados,
            Pageable pageable);
}
