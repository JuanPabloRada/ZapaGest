package com.zapgest.backend.dto.inventario;

import java.math.BigDecimal;
import java.util.List;

// HU-09 y HU-10: una variante con su total y su desglose por ubicacion.
// A proposito NO tiene costo de adquisicion ni precio minimo: lo que no esta en este
// registro no puede filtrarse por la API. precioSugerido es null si la variante no tiene precio.
public record VarianteDisponibleResponse(
        Long varianteId,
        String referencia,      // codigo de la referencia, ej. "AF1-001"
        String modelo,          // nombre de la referencia, ej. "Air Force"
        String color,
        String talla,           // texto, ej. "38.5"
        String calidad,
        BigDecimal precioSugerido,
        int total,
        List<UbicacionCantidadResponse> ubicaciones) {
}
