package com.zapgest.backend.dto.catalogo;

import java.math.BigDecimal;

public record PrecioResponse(
        Long calidadId,
        String calidad,
        BigDecimal costo,
        BigDecimal precioMinimo,
        BigDecimal precioSugerido) {
}
