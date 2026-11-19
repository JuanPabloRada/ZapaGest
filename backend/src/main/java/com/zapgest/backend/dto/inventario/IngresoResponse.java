package com.zapgest.backend.dto.inventario;

// Respuesta del ingreso: la variante usada (o creada) y cuantas unidades quedan
// de esa variante en esa ubicacion despues de sumar.
public record IngresoResponse(Long varianteId, int stockEnUbicacion) {
}
