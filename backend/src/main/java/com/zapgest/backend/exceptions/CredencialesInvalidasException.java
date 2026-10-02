package com.zapgest.backend.exceptions;

// Se lanza cuando el usuario o la contraseña del login no son correctos
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Usuario o contraseña incorrectos.");
    }
}
