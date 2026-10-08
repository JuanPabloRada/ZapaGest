package com.zapgest.backend.dto.catalogo;

import java.util.List;

// Lo que envia la pantalla del catalogo al crear una referencia
public record CatalogoRequest(
        String referencia,
        String nombre,
        String marca,
        String categoria,
        List<String> colores,
        List<String> tallas,
        List<PrecioRequest> precios) {
}
