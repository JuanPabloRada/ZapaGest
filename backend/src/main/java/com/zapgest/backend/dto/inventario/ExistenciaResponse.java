package com.zapgest.backend.dto.inventario;

// GET /api/inventario/existencias?ubicacionId=: lo que hay en UNA ubicacion (solo con stock),
// para elegir que trasladar. A proposito sin costos ni precios.
public record ExistenciaResponse(
        Long varianteId,
        String referencia,
        String modelo,
        String color,
        String talla,
        String calidad,
        int cantidad) {
}
