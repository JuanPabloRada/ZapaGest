package com.zapgest.backend.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.zapgest.backend.models.Referencia;
import com.zapgest.backend.models.Variante;

// Buscador con agotados (decision del PO): recorre el CATALOGO, no solo las variantes que ya existen.
// Cada referencia activa sale con todas sus combinaciones de color, talla y calidad, aunque nunca
// hayan tenido un ingreso. Asi el vendedor ve "Agotado" en vez de "no existe".
public interface CatalogoBusquedaRepository extends Repository<Referencia, Long> {

    // Mismos filtros que InventarioBusquedaRepository: texto en codigo, nombre o marca; color, talla y calidad por id.
    String DESDE = """
            from Referencia r join r.colores c join r.tallas t join r.precios rc join rc.calidad ca
            where r.negocioId = :negocioId
              and r.activa = true
              and (:colorId is null or c.id = :colorId)
              and (:tallaId is null or t.id = :tallaId)
              and (:calidadId is null or ca.id = :calidadId)
              and (lower(r.codigo) like :patron
                   or lower(r.nombre) like :patron
                   or lower(coalesce(r.marca, '')) like :patron)
            """;

    @Query(value = "select new com.zapgest.backend.repositories.CombinacionCatalogo("
            + "r.id, r.codigo, r.nombre, c.id, c.nombre, t.id, t.valor, ca.id, ca.nombre, rc.precioSugerido) "
            + DESDE
            + "order by r.codigo, r.nombre, c.nombre, t.valor, ca.nombre",
            countQuery = "select count(r) " + DESDE)
    Page<CombinacionCatalogo> buscar(@Param("negocioId") Long negocioId,
            @Param("patron") String patron,
            @Param("colorId") Long colorId,
            @Param("tallaId") Long tallaId,
            @Param("calidadId") Long calidadId,
            Pageable pageable);

    // Las variantes que ya existen de esas referencias (para saber su id, precio y stock)
    @Query("select v from Variante v where v.negocioId = :negocioId and v.referencia.id in :referenciaIds")
    List<Variante> variantesDe(@Param("negocioId") Long negocioId,
            @Param("referenciaIds") Collection<Long> referenciaIds);
}
