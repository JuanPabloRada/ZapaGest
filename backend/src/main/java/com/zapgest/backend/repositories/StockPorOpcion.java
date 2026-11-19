package com.zapgest.backend.repositories;

// Cuantos pares hay en inventario de UNA opcion (un color, una talla o una calidad) de una referencia.
// Se usa para no dejar quitar de una referencia algo que todavia tiene stock.
public record StockPorOpcion(Long opcionId, Long cantidad) {
}
