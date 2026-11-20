package com.zapgest.backend.dto.traslado;

import java.time.Instant;

// Lo que lee la pantalla de traslados (Frontend/js/traslados.js). Sin costos ni precios.
// estado: SOLICITADO, EN_MOVIMIENTO, LLEGO o CANCELADO. Las fechas son null hasta que pasa ese paso.
public record TrasladoResponse(
        Long id,
        Long origenId,
        String origen,
        Long destinoId,
        String destino,
        Long varianteId,
        String referencia,      // codigo, ej. "AF1-001"
        String modelo,          // nombre de la referencia
        String color,
        String talla,
        String calidad,
        int cantidad,
        String estado,
        String solicitadoPor,   // nombre de quien lo pidio
        Instant solicitadoEn,
        Instant despachadoEn,
        Instant recibidoEn,
        Instant canceladoEn) {
}
