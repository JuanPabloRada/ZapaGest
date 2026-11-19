package com.zapgest.backend.controllers;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zapgest.backend.dto.inventario.IngresoRequest;
import com.zapgest.backend.dto.inventario.IngresoResponse;
import com.zapgest.backend.services.IngresoMercanciaService;

// HU-07: POST /api/inventario/ingresos (DUENO o ADMIN, ver SeguridadConfig).
// Va en su propio controlador para no tocar ConsultaInventarioController (de Juan).
@RestController
@RequestMapping("/api/inventario/ingresos")
public class IngresoMercanciaController {

    private final IngresoMercanciaService service;

    public IngresoMercanciaController(IngresoMercanciaService service) {
        this.service = service;
    }

    // principal.getName() es quien hace el ingreso (sale del token); queda en el movimiento
    @PostMapping
    public ResponseEntity<IngresoResponse> ingresar(@RequestBody IngresoRequest datos, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ingresar(datos, principal.getName()));
    }
}
