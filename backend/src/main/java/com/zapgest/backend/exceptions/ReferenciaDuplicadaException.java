package com.zapgest.backend.exceptions;

// Se lanza cuando ya existe una referencia con el mismo codigo en el negocio
public class ReferenciaDuplicadaException extends RuntimeException {

    public ReferenciaDuplicadaException(String codigo) {
        super("Ya existe una referencia con el código " + codigo + ".");
    }
}
