package com.zapgest.backend.models;

// Estados de un traslado entre ubicaciones (Sprint 3):
//   SOLICITADO    -> no mueve stock; se puede despachar o cancelar
//   EN_MOVIMIENTO -> el stock ya salio del origen y va en transito (no esta en ninguna ubicacion)
//   LLEGO         -> el stock entro al destino; el traslado se cierra
//   CANCELADO     -> solo desde SOLICITADO
public enum EstadoTraslado {
    SOLICITADO,
    EN_MOVIMIENTO,
    LLEGO,
    CANCELADO
}
