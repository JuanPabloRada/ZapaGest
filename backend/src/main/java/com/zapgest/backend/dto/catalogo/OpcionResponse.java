package com.zapgest.backend.dto.catalogo;

// Una opcion de un desplegable: un color {id, nombre} o una talla {id, nombre: "38.5"}
public record OpcionResponse(Long id, String nombre) {
}
