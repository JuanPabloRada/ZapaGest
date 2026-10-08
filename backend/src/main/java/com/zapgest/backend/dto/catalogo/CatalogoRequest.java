package com.zapgest.backend.dto.catalogo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Lo que envia la pantalla del catalogo al crear una referencia.
// Campos de mas (ej. "categoria", que ya no se usa) se ignoran.
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogoRequest(
        String referencia,
        String nombre,
        String marca,
        List<String> colores,
        List<String> tallas,
        List<PrecioRequest> precios) {
}
