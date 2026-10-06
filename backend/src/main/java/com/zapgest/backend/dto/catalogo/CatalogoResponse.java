package com.zapgest.backend.dto.catalogo;

import java.util.Comparator;
import java.util.List;

import com.zapgest.backend.models.Color;
import com.zapgest.backend.models.Referencia;
import com.zapgest.backend.models.Talla;

// Lo que la API devuelve de una referencia (misma forma que envia la pantalla + id y activa)
public record CatalogoResponse(
        Long id,
        String referencia,
        String nombre,
        String marca,
        String categoria,
        List<String> colores,
        List<String> tallas,
        List<PrecioResponse> precios,
        Boolean activa) {

    public static CatalogoResponse desde(Referencia r) {
        List<String> colores = r.getColores().stream()
                .map(Color::getNombre)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        List<String> tallas = r.getTallas().stream()
                .sorted(Comparator.comparing(Talla::getValor))
                .map(Talla::comoTexto)
                .toList();
        List<PrecioResponse> precios = r.getPrecios().stream()
                .map(p -> new PrecioResponse(
                        p.getCalidad().getId(),
                        p.getCalidad().getNombre(),
                        p.getCostoAdquisicion(),
                        p.getPrecioMinimo(),
                        p.getPrecioSugerido()))
                .sorted(Comparator.comparing(PrecioResponse::calidadId))
                .toList();
        return new CatalogoResponse(r.getId(), r.getCodigo(), r.getNombre(), r.getMarca(),
                r.getCategoria(), colores, tallas, precios, r.getActiva());
    }
}
