package com.zapgest.backend.dto.inventario;

// HU-10: cuantas unidades de una variante hay en una ubicacion
public record UbicacionCantidadResponse(Long ubicacionId, String ubicacion, int cantidad) {
}
