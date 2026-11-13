package com.zapgest.backend.dto.inventario;

// POST /api/inventario/ingresos (HU-07): lo que llega a una ubicacion.
// Todo por id, igual que el resto de la API.
public record IngresoRequest(
        Long ubicacionId,
        Long referenciaId,
        Long colorId,
        Long tallaId,
        Long calidadId,
        Integer cantidad) {
}
