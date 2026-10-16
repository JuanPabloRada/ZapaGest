package com.zapgest.backend.exceptions;

// Se lanza cuando se pide editar algo que no existe (o es de otro negocio). Responde 404.
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
