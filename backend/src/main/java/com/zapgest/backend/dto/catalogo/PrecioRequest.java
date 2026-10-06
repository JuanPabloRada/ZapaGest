package com.zapgest.backend.dto.catalogo;

import java.math.BigDecimal;

// Precio de una referencia para una calidad, tal como lo envia la pantalla
public record PrecioRequest(Long calidadId, BigDecimal costo, BigDecimal precioMinimo, BigDecimal precioSugerido) {
}
