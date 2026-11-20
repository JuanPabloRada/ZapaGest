package com.zapgest.backend.exceptions;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Errores propios de los traslados. Va aparte para no tocar el ManejadorErrores compartido.
@RestControllerAdvice
public class TrasladoErrores {

    // Accion no permitida en el estado actual del traslado: 409 con {"mensaje": "..."}
    @ExceptionHandler(EstadoNoPermitidoException.class)
    public ResponseEntity<Map<String, String>> estadoNoPermitido(EstadoNoPermitidoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", e.getMessage()));
    }
}
