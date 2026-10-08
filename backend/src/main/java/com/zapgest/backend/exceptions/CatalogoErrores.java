package com.zapgest.backend.exceptions;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Errores propios del catalogo. Va en un archivo aparte para no tocar
// el ManejadorErrores compartido. Responde con el mismo formato {"mensaje": "..."}.
@RestControllerAdvice
public class CatalogoErrores {

    @ExceptionHandler(ReferenciaDuplicadaException.class)
    public ResponseEntity<Map<String, String>> referenciaDuplicada(ReferenciaDuplicadaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", e.getMessage()));
    }
}
