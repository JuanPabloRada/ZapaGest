package com.zapgest.backend.exceptions;

// La accion no se puede hacer en el estado actual (ej. despachar un traslado que ya llego).
// ManejadorErrores la convierte en 409 Conflict con { mensaje }.
public class EstadoNoPermitidoException extends RuntimeException {
    public EstadoNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}
