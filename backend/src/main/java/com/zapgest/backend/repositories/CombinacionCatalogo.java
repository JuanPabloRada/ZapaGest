package com.zapgest.backend.repositories;

import java.math.BigDecimal;

// Una combinacion posible del catalogo (referencia + color + talla + calidad), exista o no su variante.
// La usa el buscador para mostrar tambien lo que nunca ha tenido stock (sale con total 0).
public record CombinacionCatalogo(
        Long referenciaId,
        String codigo,
        String nombre,
        Long colorId,
        String color,
        Long tallaId,
        BigDecimal talla,
        Long calidadId,
        String calidad,
        BigDecimal precioSugerido) {
}
