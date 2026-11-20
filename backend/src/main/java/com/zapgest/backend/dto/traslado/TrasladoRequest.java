package com.zapgest.backend.dto.traslado;

// POST /api/traslados: { origenId, destinoId, varianteId, cantidad }
public record TrasladoRequest(Long origenId, Long destinoId, Long varianteId, Integer cantidad) {
}
