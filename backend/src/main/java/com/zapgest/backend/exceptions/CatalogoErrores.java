package com.zapgest.backend.exceptions;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Errores propios del catalogo (y el 404 de "no encontrado" para las ediciones). Va en un archivo aparte para no tocar
// el ManejadorErrores compartido. Responde con el mismo formato {"mensaje": "..."}.
@RestControllerAdvice
public class CatalogoErrores {

    // Editar algo que no existe: 404 con {"mensaje": "..."}
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("mensaje", e.getMessage()));
    }

    @ExceptionHandler(ReferenciaDuplicadaException.class)
    public ResponseEntity<Map<String, String>> referenciaDuplicada(ReferenciaDuplicadaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", e.getMessage()));
    }
}
