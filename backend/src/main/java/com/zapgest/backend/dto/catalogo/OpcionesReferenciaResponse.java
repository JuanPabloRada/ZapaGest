package com.zapgest.backend.dto.catalogo;

import java.util.List;

// HU-07: lo que la pantalla de ingreso necesita de UNA referencia para armar sus desplegables.
// No trae costos ni precios: solo colores, tallas y calidades que maneja la referencia.
// Cada opcion es {id, nombre}; en las tallas el nombre es el valor como texto ("38.5").
public record OpcionesReferenciaResponse(
        Long referenciaId,
        String codigo,
        String nombre,
        String marca,
        List<OpcionResponse> colores,
        List<OpcionResponse> tallas,
        List<OpcionResponse> calidades) {
}
