package com.zapgest.backend.models;

// Tipos de movimiento de inventario. En el Sprint 2 solo se usa ENTRADA (HU-07);
// SALIDA y TRASLADO quedan listos para el Sprint 3 (HU-08) para no tener que migrar la columna.
public enum TipoMovimiento {
    ENTRADA,
    SALIDA,
    TRASLADO
}
